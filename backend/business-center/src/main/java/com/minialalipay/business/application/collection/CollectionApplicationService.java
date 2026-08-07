package com.minialalipay.business.application.collection;

import com.minialalipay.business.application.port.AccountDirectoryPort;
import com.minialalipay.business.application.port.CollectionStore;
import com.minialalipay.business.application.port.SecurityMaterialPort;
import com.minialalipay.business.domain.collection.CollectionRequest;
import com.minialalipay.business.domain.collection.CollectionOrder;
import com.minialalipay.business.domain.collection.CollectionOrderEvent;
import com.minialalipay.business.domain.collection.CollectionOrderStatus;
import com.minialalipay.business.domain.collection.PersonalCollectionCode;
import com.minialalipay.business.domain.transaction.BusinessErrorCode;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import com.minialalipay.common.idempotency.IdempotencyKeyValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;

/**
 * 个人收款码和固定金额请求的非资金应用服务。
 *
 * <p>本服务只管理收款入口和来源请求，不创建 C2C 资金交易、不消费确认令牌，也不调用账户写接口或 TCC。</p>
 */
@Service
public class CollectionApplicationService {
    private static final String REGENERATE_CODE = "REGENERATE_COLLECTION_CODE";
    private static final String DISABLE_CODE = "DISABLE_COLLECTION_CODE";
    private static final String CREATE_REQUEST = "CREATE_COLLECTION_REQUEST";
    private static final String CANCEL_REQUEST = "CANCEL_COLLECTION_REQUEST";

    private final CollectionStore store;
    private final AccountDirectoryPort accounts;
    private final SecurityMaterialPort security;
    private final IdempotencyKeyValidator keys;
    private final Clock clock;

    /** 创建 C2C 非资金应用服务。 */
    @Autowired
    public CollectionApplicationService(CollectionStore store, AccountDirectoryPort accounts, SecurityMaterialPort security,
                                        IdempotencyKeyValidator keys) {
        this(store, accounts, security, keys, Clock.systemUTC());
    }

    CollectionApplicationService(CollectionStore store, AccountDirectoryPort accounts, SecurityMaterialPort security,
                                 IdempotencyKeyValidator keys, Clock clock) {
        this.store = store; this.accounts = accounts; this.security = security; this.keys = keys; this.clock = clock;
    }

    /** 原子生成或换发当前用户唯一有效个人码。 */
    @Transactional
    public CreatedCode regenerateCode(String userId, String idempotencyKey) {
        requireKey(idempotencyKey);
        byte[] digest = security.digest("regenerate");
        CollectionStore.IdempotencyRecord existing = store.findIdempotency(userId, REGENERATE_CODE, idempotencyKey).orElse(null);
        if (existing != null) return replayCode(existing, digest, userId);
        var account = accounts.resolvePersonalAccount(userId);
        if (!"ACTIVE".equals(account.status())) throw new BusinessException(BusinessErrorCode.ACCOUNT_UNAVAILABLE);
        Instant now = clock.instant();
        PersonalCollectionCode oldCode = store.findActiveCode(userId).orElse(null);
        if (oldCode != null) oldCode.replace(oldCode.getVersion(), now);
        PersonalCollectionCode newCode = PersonalCollectionCode.activate(security.newId(), userId, account.accountId(), now);
        String token = security.newCollectionToken();
        if (!store.replaceCode(oldCode, newCode, security.digest(token), security.newId(), userId, idempotencyKey, digest)) {
            return replayCode(store.findIdempotency(userId, REGENERATE_CODE, idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("个人码幂等冲突后未找到既有记录")), digest, userId);
        }
        return new CreatedCode(newCode, token);
    }

    /** 查询本人当前有效个人码。 */
    @Transactional(readOnly = true)
    public PersonalCollectionCode getActiveCode(String userId) {
        return store.findActiveCode(userId).orElse(null);
    }

    /** 停用本人当前有效个人码，重复同参调用返回首次结果。 */
    @Transactional
    public PersonalCollectionCode disableCode(String userId, long version, String idempotencyKey) {
        requireKey(idempotencyKey);
        byte[] digest = security.digest(String.valueOf(version));
        CollectionStore.IdempotencyRecord existing = store.findIdempotency(userId, DISABLE_CODE, idempotencyKey).orElse(null);
        if (existing != null) return replayCode(existing, digest, userId).code();
        PersonalCollectionCode code = store.findActiveCode(userId).orElseThrow(() -> new BusinessException(BusinessErrorCode.P2P_CODE_INVALID));
        if (!store.reserveIdempotency(security.newId(), userId, DISABLE_CODE, idempotencyKey, digest, code.getCodeId(), "PERSONAL_COLLECTION_CODE")) {
            return replayCode(store.findIdempotency(userId, DISABLE_CODE, idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("个人码停用幂等冲突后未找到既有记录")), digest, userId).code();
        }
        try { code.deactivate(version, clock.instant()); }
        catch (IllegalStateException invalid) { throw new BusinessException(code.getVersion() != version ? BusinessErrorCode.VERSION_CONFLICT : BusinessErrorCode.ORDER_STATE_INVALID); }
        if (!store.updateCode(code, version)) throw new BusinessException(BusinessErrorCode.VERSION_CONFLICT);
        return code;
    }

    /** 创建 30 分钟有效且金额不可改的固定收款请求。 */
    @Transactional
    public CreatedRequest createRequest(String userId, long amountFen, String subject, String idempotencyKey) {
        requireKey(idempotencyKey);
        if (amountFen < 1 || amountFen > 5_000_000L) throw new BusinessException(BusinessErrorCode.AMOUNT_OUT_OF_RANGE);
        String normalized = subject == null || subject.isBlank() ? "固定收款" : subject.replaceAll("[\\p{Cntrl}]", "").trim();
        if (normalized.isBlank() || normalized.length() > 50) throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        byte[] digest = security.digest(amountFen + "|" + normalized);
        CollectionStore.IdempotencyRecord existing = store.findIdempotency(userId, CREATE_REQUEST, idempotencyKey).orElse(null);
        if (existing != null) return replayRequest(existing, digest, userId);
        var account = accounts.resolvePersonalAccount(userId);
        if (!"ACTIVE".equals(account.status())) throw new BusinessException(BusinessErrorCode.ACCOUNT_UNAVAILABLE);
        Instant now = clock.instant(); String token = security.newCollectionToken();
        CollectionRequest request = CollectionRequest.create(security.newId(), userId, account.accountId(), amountFen, normalized, now);
        if (!store.createRequest(request, security.digest(token), security.newId(), userId, idempotencyKey, digest)) {
            return replayRequest(store.findIdempotency(userId, CREATE_REQUEST, idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("固定请求幂等冲突后未找到既有记录")), digest, userId);
        }
        store.appendRequestEvent(requestEvent(request, null, null, "OPEN", now));
        return new CreatedRequest(request, token);
    }

    /** 读取本人创建的固定收款请求并在读路径持久化过期终态。 */
    @Transactional
    public CollectionRequest getRequest(String userId, String requestId) {
        CollectionRequest request = ownedRequest(userId, requestId);
        expireIfNecessary(request);
        return request;
    }

    /** 取消尚未被付款订单占用的固定收款请求。 */
    @Transactional
    public CollectionRequest cancelRequest(String userId, String requestId, long version, String idempotencyKey) {
        requireKey(idempotencyKey); byte[] digest = security.digest(requestId + "|" + version);
        CollectionStore.IdempotencyRecord existing = store.findIdempotency(userId, CANCEL_REQUEST, idempotencyKey).orElse(null);
        if (existing != null) return replayRequest(existing, digest, userId).request();
        CollectionRequest request = ownedRequest(userId, requestId); expireIfNecessary(request);
        if (!store.reserveIdempotency(security.newId(), userId, CANCEL_REQUEST, idempotencyKey, digest, requestId, "COLLECTION_REQUEST")) {
            return replayRequest(store.findIdempotency(userId, CANCEL_REQUEST, idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("固定请求取消幂等冲突后未找到既有记录")), digest, userId).request();
        }
        try { request.close(version, clock.instant()); }
        catch (IllegalStateException invalid) { throw new BusinessException(request.getVersion() != version ? BusinessErrorCode.VERSION_CONFLICT : BusinessErrorCode.REQUEST_NOT_CANCELLABLE); }
        if (!store.updateRequest(request, version)) throw new BusinessException(BusinessErrorCode.VERSION_CONFLICT);
        store.appendRequestEvent(requestEvent(request, null, null, "CANCELLED", request.getUpdatedAt()));
        return request;
    }

    /**
     * 匿名 H5 引导时只验证个人码或固定请求令牌，不创建订单也不返回业务数据。
     */
    @Transactional
    public void validateBootstrapToken(String rawToken) {
        byte[] digest = security.digest(rawToken);
        if (store.findActiveCodeByTokenDigest(digest).isPresent()) return;
        CollectionRequest request = store.findRequestByTokenDigest(digest)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.COLLECTION_TOKEN_INVALID));
        expireIfNecessary(request);
        if (request.getStatus().name().equals("CANCELLED")) throw new BusinessException(BusinessErrorCode.COLLECTION_REQUEST_CANCELLED);
        if (request.getStatus().name().equals("EXPIRED")) throw new BusinessException(BusinessErrorCode.COLLECTION_REQUEST_EXPIRED);
    }

    /**
     * 登录付款人在同一 bootstrap 会话中交换令牌并创建或恢复 C2C 订单。
     *
     * <p>付款人账户只从账户中心解析，令牌摘要只用于服务端查找；固定请求先 CAS 占用后写订单，保证多付款会话最多一个进入确认。</p>
     */
    @Transactional
    public CollectionOrder exchange(String payerUserId, String bootstrapSessionId, String rawToken) {
        String sessionKey = security.stableId(bootstrapSessionId);
        CollectionOrder existing = store.findOrderByBootstrapSessionId(sessionKey).orElse(null);
        if (existing != null) {
            if (!existing.getPayerUserId().equals(payerUserId)) throw new BusinessException(BusinessErrorCode.ORDER_NOT_FOUND);
            // 如果旧订单是终态，清除会话绑定，允许创建新订单
            if (isTerminalStatus(existing.getStatus())) {
                store.clearSessionBinding(existing.getOrderId());
            } else {
                return existing;
            }
        }
        var payer = accounts.resolvePersonalAccount(payerUserId);
        if (!"ACTIVE".equals(payer.status())) throw new BusinessException(BusinessErrorCode.ACCOUNT_UNAVAILABLE);
        byte[] digest = security.digest(rawToken);
        PersonalCollectionCode code = store.findActiveCodeByTokenDigest(digest).orElse(null);
        Instant now = clock.instant();
        if (code != null) {
            if (code.getUserId().equals(payerUserId) || code.getAccountId().equals(payer.accountId())) {
                throw new BusinessException(BusinessErrorCode.SELF_PAYMENT_FORBIDDEN);
            }
            CollectionOrder order = CollectionOrder.forPersonalCode(security.newId(), code.getCodeId(), code.getUserId(),
                    code.getAccountId(), payerUserId, payer.accountId(), now);
            if (!store.createPersonalOrder(order, sessionKey)) return replaySessionOrder(sessionKey, payerUserId);
            return order;
        }
        CollectionRequest request = store.findRequestByTokenDigest(digest)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.COLLECTION_TOKEN_INVALID));
        expireIfNecessary(request);
        if (request.getStatus().name().equals("CANCELLED")) throw new BusinessException(BusinessErrorCode.COLLECTION_REQUEST_CANCELLED);
        if (request.getStatus().name().equals("EXPIRED")) throw new BusinessException(BusinessErrorCode.COLLECTION_REQUEST_EXPIRED);
        if (request.getPayeeUserId().equals(payerUserId) || request.getPayeeAccountId().equals(payer.accountId())) {
            throw new BusinessException(BusinessErrorCode.SELF_PAYMENT_FORBIDDEN);
        }
        long expectedVersion = request.getVersion();
        CollectionOrder order = CollectionOrder.forFixedRequest(security.newId(), request.getRequestId(), request.getPayeeUserId(),
                request.getPayeeAccountId(), payerUserId, payer.accountId(), request.getAmountFen(), request.getSubject(), now);
        try { request.reserveForOrder(order.getOrderId(), expectedVersion, now); }
        catch (IllegalStateException invalid) { throw requestStateError(request); }
        if (!store.reserveRequestAndCreateOrder(request, expectedVersion, order, sessionKey)) {
            CollectionOrder replay = store.findOrderByBootstrapSessionId(sessionKey).orElse(null);
            if (replay != null && replay.getPayerUserId().equals(payerUserId)) return replay;
            throw new BusinessException(BusinessErrorCode.COLLECTION_REQUEST_PROCESSING);
        }
        store.appendRequestEvent(requestEvent(request, order.getOrderId(), null, "PENDING_CONFIRMATION", now));
        return order;
    }

    /** 仅已绑定付款人可锁定个人码订单的金额和备注。 */
    @Transactional
    public CollectionOrder lockPersonalOrder(String payerUserId, String bootstrapSessionId, String orderId,
                                              long version, long amountFen, String subject) {
        CollectionOrder order = store.findOrderByBootstrapSessionId(security.stableId(bootstrapSessionId))
                .filter(value -> value.getOrderId().equals(orderId))
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.ORDER_NOT_FOUND));
        if (!order.getPayerUserId().equals(payerUserId)) throw new BusinessException(BusinessErrorCode.ORDER_NOT_FOUND);
        try { order.lockPersonalAmount(payerUserId, version, amountFen, normalizeSubject(subject), clock.instant()); }
        catch (IllegalArgumentException invalid) { throw new BusinessException(BusinessErrorCode.AMOUNT_OUT_OF_RANGE); }
        catch (IllegalStateException invalid) {
            throw new BusinessException(invalid.getMessage().contains("版本") ? BusinessErrorCode.VERSION_CONFLICT : BusinessErrorCode.ORDER_NOT_EDITABLE);
        }
        if (!store.updateOrder(order, version)) throw new BusinessException(BusinessErrorCode.VERSION_CONFLICT);
        return order;
    }

    /**
     * 查询 C2C 订单并执行对象级授权。
     *
     * <p>付款人、收款人或绑定的 H5 会话可读取；其他主体一律返回不存在，避免暴露订单标识的有效性。</p>
     */
    @Transactional(readOnly = true)
    public CollectionOrder getOrderForAuthorizedUser(String userId, String bootstrapSessionId, String orderId) {
        CollectionOrder order = store.findOrder(orderId).orElseThrow(() -> new BusinessException(BusinessErrorCode.ORDER_NOT_FOUND));
        if (order.getPayerUserId().equals(userId) || order.getPayeeUserId().equals(userId)) return order;
        if (bootstrapSessionId != null && store.findOrderByBootstrapSessionId(security.stableId(bootstrapSessionId))
                .map(value -> value.getOrderId().equals(orderId)).orElse(false)) return order;
        throw new BusinessException(BusinessErrorCode.ORDER_NOT_FOUND);
    }

    private CollectionRequest ownedRequest(String userId, String requestId) {
        CollectionRequest request = store.findRequest(requestId).orElseThrow(() -> new BusinessException(BusinessErrorCode.REQUEST_NOT_FOUND));
        if (!request.getPayeeUserId().equals(userId)) throw new BusinessException(BusinessErrorCode.REQUEST_NOT_FOUND);
        return request;
    }
    private CollectionOrder replaySessionOrder(String sessionKey, String payerUserId) {
        CollectionOrder order = store.findOrderByBootstrapSessionId(sessionKey)
                .orElseThrow(() -> new IllegalStateException("C2C 会话唯一冲突后未读取到既有订单"));
        if (!order.getPayerUserId().equals(payerUserId)) throw new BusinessException(BusinessErrorCode.ORDER_NOT_FOUND);
        return order;
    }
    private BusinessException requestStateError(CollectionRequest request) {
        return switch (request.getStatus().name()) {
            case "EXPIRED" -> new BusinessException(BusinessErrorCode.COLLECTION_REQUEST_EXPIRED);
            case "CANCELLED" -> new BusinessException(BusinessErrorCode.COLLECTION_REQUEST_CANCELLED);
            default -> new BusinessException(BusinessErrorCode.COLLECTION_REQUEST_PROCESSING);
        };
    }
    private void expireIfNecessary(CollectionRequest request) {
        long version = request.getVersion();
        Instant now = clock.instant();
        if (request.expireIfNecessary(now)) {
            if (!store.updateRequest(request, version)) throw new BusinessException(BusinessErrorCode.VERSION_CONFLICT);
            store.appendRequestEvent(requestEvent(request, request.getActiveOrderId(), null, "EXPIRED", now));
        }
    }

    /** 将请求状态变更写为不含账户、会话与令牌的公开重放事件。 */
    private CollectionOrderEvent requestEvent(CollectionRequest request, String activeOrderId, String transactionId,
                                              String status, Instant occurredAt) {
        return new CollectionOrderEvent(security.newId(), request.getRequestId(), activeOrderId, transactionId, status, occurredAt);
    }
    private CreatedCode replayCode(CollectionStore.IdempotencyRecord record, byte[] digest, String userId) {
        if (!Arrays.equals(record.requestDigest(), digest)) throw new BusinessException(BusinessErrorCode.IDEMPOTENCY_CONFLICT);
        PersonalCollectionCode code = store.findCode(record.resourceId()).orElseThrow(() -> new IllegalStateException("个人码幂等记录缺少资源"));
        if (!code.getUserId().equals(userId)) throw new BusinessException(BusinessErrorCode.P2P_CODE_INVALID);
        return new CreatedCode(code, null);
    }
    private CreatedRequest replayRequest(CollectionStore.IdempotencyRecord record, byte[] digest, String userId) {
        if (!Arrays.equals(record.requestDigest(), digest)) throw new BusinessException(BusinessErrorCode.IDEMPOTENCY_CONFLICT);
        return new CreatedRequest(getRequest(userId, record.resourceId()), null);
    }
    private void requireKey(String key) { if (!keys.isValid(key)) throw new BusinessException(CommonErrorCode.INVALID_REQUEST); }
    private static String normalizeSubject(String value) {
        if (value == null || value.isBlank()) return "个人收款";
        String normalized = value.replaceAll("[\\p{Cntrl}]", "").trim();
        if (normalized.isBlank() || normalized.length() > 50) throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        return normalized;
    }
    private static boolean isTerminalStatus(CollectionOrderStatus status) {
        return status == CollectionOrderStatus.SUCCESS || status == CollectionOrderStatus.FAILED
                || status == CollectionOrderStatus.CANCELLED
                || status == CollectionOrderStatus.MANUAL_REVIEW || status == CollectionOrderStatus.EXPIRED;
    }

    /** 新个人码和仅本次响应可见的公开令牌。 */
    public record CreatedCode(PersonalCollectionCode code, String rawToken) { }
    /** 新固定请求和仅本次响应可见的公开令牌。 */
    public record CreatedRequest(CollectionRequest request, String rawToken) { }
}
