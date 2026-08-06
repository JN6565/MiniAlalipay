package com.minialalipay.business.application.qrpay;

import com.minialalipay.business.application.port.QrPayStore;
import com.minialalipay.business.application.port.SecurityMaterialPort;
import com.minialalipay.business.domain.qrpay.QrPayOrder;
import com.minialalipay.business.domain.qrpay.QrPayOrderEvent;
import com.minialalipay.business.domain.transaction.BusinessErrorCode;
import com.minialalipay.common.error.BusinessException;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 动态扫码订单可重放 SSE 应用服务。
 *
 * <p>连接建立和轮询均从持久化事件表读取，不以进程内广播伪造重放能力；首次订阅先发送统一交易回源后的权威快照。</p>
 */
@Service
public class QrPayEventApplicationService {
    private static final int REPLAY_LIMIT = 100;
    private final QrPayApplicationService orders;
    private final QrPayStore store;
    private final SecurityMaterialPort security;
    private final ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "qr-pay-sse-poller");
        thread.setDaemon(true);
        return thread;
    });

    /** 创建二维码订单 SSE 服务。 */
    public QrPayEventApplicationService(QrPayApplicationService orders, QrPayStore store, SecurityMaterialPort security) {
        this.orders = orders;
        this.store = store;
        this.security = security;
    }

    /**
     * 订阅当前用户可见的二维码订单状态。
     *
     * @param userId 已认证用户
     * @param bootstrapSessionId 当前 H5 会话，可为空
     * @param orderId 订单 ID
     * @param lastEventId 断线续传游标，可为空
     * @return 只携带最小公开状态字段的 SSE 连接，终态后关闭
     */
    public SseEmitter subscribe(String userId, String bootstrapSessionId, String orderId, String lastEventId) {
        if (lastEventId != null && (lastEventId.isBlank() || lastEventId.length() > 128)) {
            throw new BusinessException(BusinessErrorCode.EVENT_CURSOR_EXPIRED);
        }
        QrPayOrder order = orders.getForAuthorizedUser(userId, bootstrapSessionId, orderId);
        SseEmitter emitter = new SseEmitter(30_000L);
        AtomicReference<String> cursor = new AtomicReference<>();
        try {
            if (lastEventId == null) {
                QrPayOrderEvent latest = store.findLatestOrderEvent(orderId).orElse(null);
                QrPayOrderEvent snapshot = latest != null && latest.status().equals(order.getStatus().name())
                        && java.util.Objects.equals(latest.transactionId(), order.getTransactionId())
                        ? latest : persistSnapshot(order);
                send(emitter, snapshot);
                cursor.set(snapshot.eventId());
                if (terminal(snapshot.status())) {
                    emitter.complete();
                    return emitter;
                }
            } else {
                QrPayOrderEvent existing = store.findOrderEvent(orderId, lastEventId)
                        .orElseThrow(() -> new BusinessException(BusinessErrorCode.EVENT_CURSOR_EXPIRED));
                cursor.set(existing.eventId());
                if (terminal(existing.status())) {
                    emitter.complete();
                    return emitter;
                }
                if (sendAfter(emitter, orderId, cursor)) return emitter;
            }
        } catch (IOException exception) {
            emitter.completeWithError(exception);
            return emitter;
        }
        schedule(emitter, orderId, cursor);
        return emitter;
    }

    private QrPayOrderEvent persistSnapshot(QrPayOrder order) {
        QrPayOrderEvent snapshot = new QrPayOrderEvent(security.newId(), order.getOrderId(), order.getTransactionId(),
                order.getStatus().name(), order.getUpdatedAt());
        store.appendOrderEvent(snapshot);
        return snapshot;
    }

    private void schedule(SseEmitter emitter, String orderId, AtomicReference<String> cursor) {
        AtomicReference<ScheduledFuture<?>> future = new AtomicReference<>();
        ScheduledFuture<?> scheduled = poller.scheduleAtFixedRate(() -> {
            try {
                if (sendAfter(emitter, orderId, cursor)) cancel(future);
            } catch (IOException exception) {
                cancel(future);
                emitter.complete();
            }
        }, 1, 1, TimeUnit.SECONDS);
        future.set(scheduled);
        emitter.onCompletion(() -> cancel(future));
        emitter.onTimeout(() -> { cancel(future); emitter.complete(); });
    }

    private boolean sendAfter(SseEmitter emitter, String orderId, AtomicReference<String> cursor) throws IOException {
        List<QrPayOrderEvent> events = store.findOrderEventsAfter(orderId, cursor.get(), REPLAY_LIMIT);
        for (QrPayOrderEvent event : events) {
            send(emitter, event);
            cursor.set(event.eventId());
            if (terminal(event.status())) {
                emitter.complete();
                return true;
            }
        }
        return false;
    }

    private static void send(SseEmitter emitter, QrPayOrderEvent event) throws IOException {
        emitter.send(SseEmitter.event().id(event.eventId()).name("qr-pay-status").data(event));
    }

    private static boolean terminal(String status) {
        return "SUCCESS".equals(status) || "CANCELLED".equals(status) || "EXPIRED".equals(status)
                || "MANUAL_REVIEW".equals(status) || "REJECTED".equals(status);
    }

    private static void cancel(AtomicReference<ScheduledFuture<?>> future) {
        ScheduledFuture<?> scheduled = future.getAndSet(null);
        if (scheduled != null) scheduled.cancel(false);
    }

    /** 停止 SSE 后台轮询线程。 */
    @PreDestroy
    void shutdown() {
        poller.shutdownNow();
    }
}
