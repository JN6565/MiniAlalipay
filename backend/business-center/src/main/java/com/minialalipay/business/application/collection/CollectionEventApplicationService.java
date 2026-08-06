package com.minialalipay.business.application.collection;

import com.minialalipay.business.application.port.CollectionStore;
import com.minialalipay.business.application.port.SecurityMaterialPort;
import com.minialalipay.business.domain.collection.CollectionOrder;
import com.minialalipay.business.domain.collection.CollectionOrderEvent;
import com.minialalipay.business.domain.collection.CollectionRequest;
import com.minialalipay.business.domain.collection.CollectionRequestStatus;
import com.minialalipay.business.domain.transaction.BusinessErrorCode;
import com.minialalipay.common.error.BusinessException;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 固定收款请求的可重放 SSE 应用服务。
 *
 * <p>服务不维护业务状态或内存广播事实。每次建立和轮询连接都从 {@code collection_order_event}
 * 读取已提交事件，因此进程重启不会破坏 {@code Last-Event-ID} 续传。</p>
 */
@Service
public class CollectionEventApplicationService {
    private static final int REPLAY_LIMIT = 100;
    private final CollectionApplicationService collections;
    private final CollectionStore store;
    private final SecurityMaterialPort security;
    private final ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "collection-sse-poller");
        thread.setDaemon(true);
        return thread;
    });

    /** 创建固定收款请求 SSE 服务。 */
    public CollectionEventApplicationService(CollectionApplicationService collections, CollectionStore store,
                                             SecurityMaterialPort security) {
        this.collections = collections;
        this.store = store;
        this.security = security;
    }

    /**
     * 建立请求创建者专属的固定收款 SSE 连接。
     *
     * @param userId 服务端认证的请求创建者
     * @param requestId 固定收款请求 ID
     * @param lastEventId 断线续传游标，可为空
     * @return 已发送当前快照或补发事件的 SSE 连接；终态发送后立即关闭
     * @throws BusinessException 游标无法在保留期内验证时返回 EVENT_CURSOR_EXPIRED
     */
    public SseEmitter subscribeRequest(String userId, String requestId, String lastEventId) {
        if (lastEventId != null && (lastEventId.isBlank() || lastEventId.length() > 128)) {
            throw new BusinessException(BusinessErrorCode.EVENT_CURSOR_EXPIRED);
        }
        CollectionRequest request = collections.getRequest(userId, requestId);
        SseEmitter emitter = new SseEmitter(30_000L);
        AtomicReference<String> cursor = new AtomicReference<>();
        try {
            if (lastEventId == null) {
                CollectionOrderEvent snapshot = store.findLatestRequestEvent(requestId)
                        .orElseGet(() -> persistSnapshot(request));
                send(emitter, snapshot);
                cursor.set(snapshot.eventId());
                if (terminal(snapshot.status())) {
                    emitter.complete();
                    return emitter;
                }
            } else {
                CollectionOrderEvent cursorEvent = store.findRequestEvent(requestId, lastEventId)
                        .orElseThrow(() -> new BusinessException(BusinessErrorCode.EVENT_CURSOR_EXPIRED));
                cursor.set(lastEventId);
                if (terminal(cursorEvent.status())) {
                    emitter.complete();
                    return emitter;
                }
                if (sendAfter(emitter, requestId, cursor)) return emitter;
            }
        } catch (IOException failure) {
            emitter.completeWithError(failure);
            return emitter;
        }
        schedulePolling(emitter, requestId, cursor);
        return emitter;
    }

    private CollectionOrderEvent persistSnapshot(CollectionRequest request) {
        CollectionOrder order = request.getActiveOrderId() == null ? null : store.findOrder(request.getActiveOrderId()).orElse(null);
        String status = switch (request.getStatus()) {
            case RESERVED -> "PENDING_CONFIRMATION";
            case CLOSED, CANCELLED -> "CANCELLED";
            default -> request.getStatus().name();
        };
        CollectionOrderEvent event = new CollectionOrderEvent(security.newId(), request.getRequestId(), request.getActiveOrderId(),
                order == null ? null : order.getTransactionId(), status, request.getUpdatedAt());
        store.appendRequestEvent(event);
        return event;
    }

    private void schedulePolling(SseEmitter emitter, String requestId, AtomicReference<String> cursor) {
        AtomicReference<ScheduledFuture<?>> scheduled = new AtomicReference<>();
        Runnable task = () -> {
            try {
                if (sendAfter(emitter, requestId, cursor)) cancel(scheduled);
            } catch (IOException closed) {
                cancel(scheduled);
                emitter.complete();
            } catch (RuntimeException failure) {
                cancel(scheduled);
                emitter.completeWithError(failure);
            }
        };
        ScheduledFuture<?> future = poller.scheduleAtFixedRate(task, 1, 1, TimeUnit.SECONDS);
        scheduled.set(future);
        emitter.onCompletion(() -> cancel(scheduled));
        emitter.onTimeout(() -> {
            cancel(scheduled);
            emitter.complete();
        });
    }

    private boolean sendAfter(SseEmitter emitter, String requestId, AtomicReference<String> cursor) throws IOException {
        List<CollectionOrderEvent> events = store.findRequestEventsAfter(requestId, cursor.get(), REPLAY_LIMIT);
        for (CollectionOrderEvent event : events) {
            send(emitter, event);
            cursor.set(event.eventId());
            if (terminal(event.status())) {
                emitter.complete();
                return true;
            }
        }
        return false;
    }

    private static void send(SseEmitter emitter, CollectionOrderEvent event) throws IOException {
        emitter.send(SseEmitter.event().id(event.eventId()).name("p2p-collection-status").data(event));
    }

    private static boolean terminal(String status) {
        return "SUCCESS".equals(status) || "CANCELLED".equals(status)
                || "EXPIRED".equals(status) || "MANUAL_REVIEW".equals(status);
    }

    private static void cancel(AtomicReference<ScheduledFuture<?>> scheduled) {
        ScheduledFuture<?> future = scheduled.getAndSet(null);
        if (future != null) future.cancel(false);
    }

    /** 关闭连接轮询线程，避免服务停止后保留后台任务。 */
    @PreDestroy
    void shutdown() {
        poller.shutdownNow();
    }
}
