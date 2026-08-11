package com.minialalipay.business.application.collection;

import com.minialalipay.business.application.port.AccountDirectoryPort;
import com.minialalipay.business.application.port.CollectionStore;
import com.minialalipay.business.application.port.QrPayStore;
import com.minialalipay.business.application.port.SecurityMaterialPort;
import com.minialalipay.business.domain.collection.CollectionOrder;
import com.minialalipay.business.domain.collection.CollectionOrderEvent;
import com.minialalipay.business.domain.collection.CollectionRequest;
import com.minialalipay.business.domain.collection.PersonalCollectionCode;
import com.minialalipay.business.domain.qrpay.QrPayOrder;
import com.minialalipay.business.domain.qrpay.QrPayOrderStatus;
import com.minialalipay.business.domain.transaction.BusinessErrorCode;
import com.minialalipay.common.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Instant;

/**
 * 手动输入收款短码兑换的应用服务。
 *
 * <p>短码是订单指针，兑换结果与扫到对应二维码等价：个人码与固定请求分支复刻令牌交换的建单逻辑，
 * 动态二维码分支把既有订单绑定到当前 H5 会话。资金动作仍走验密、确认令牌与风控全套保护。</p>
 */
@Service
public class ShortCodeExchangeService {
    private final CollectionStore collectionStore;
    private final QrPayStore qrStore;
    private final AccountDirectoryPort accounts;
    private final SecurityMaterialPort security;
    private final Clock clock;

    /** 创建短码兑换服务。 */
    @Autowired
    public ShortCodeExchangeService(CollectionStore collectionStore, QrPayStore qrStore,
                                    AccountDirectoryPort accounts, SecurityMaterialPort security) {
        this(collectionStore, qrStore, accounts, security, Clock.systemUTC());
    }

    ShortCodeExchangeService(CollectionStore collectionStore, QrPayStore qrStore,
                             AccountDirectoryPort accounts, SecurityMaterialPort security, Clock clock) {
        this.collectionStore = collectionStore;
        this.qrStore = qrStore;
        this.accounts = accounts;
        this.security = security;
        this.clock = clock;
    }

    /**
     * 登录付款人以 8 位短码兑换收款订单，结果与扫码等价。
     *
     * <p>短码不存在、已过期、已停用或已被其他会话消费时一律返回短码无效，
     * 避免向猜测者暴露码的存在性与状态差异。</p>
     */
    @Transactional
    public ExchangeResult exchange(String payerUserId, String bootstrapSessionId, String shortCode) {
        if (shortCode == null || !shortCode.matches("\\d{8}")) {
            throw new BusinessException(BusinessErrorCode.SHORT_CODE_INVALID);
        }
        String sessionKey = security.stableId(bootstrapSessionId);
        // 会话旧订单一律解绑，避免连续输入不同短码时串单
        collectionStore.findOrderByBootstrapSessionId(sessionKey)
                .ifPresent(existing -> collectionStore.clearSessionBinding(existing.getOrderId()));
        Instant now = clock.instant();
        PersonalCollectionCode code = collectionStore.findActiveCodeByShortCode(shortCode).orElse(null);
        if (code != null) return exchangePersonalCode(code, payerUserId, sessionKey, now);
        CollectionRequest request = collectionStore.findRequestByShortCode(shortCode).orElse(null);
        if (request != null) return exchangeFixedRequest(request, payerUserId, sessionKey, now);
        QrPayOrder order = qrStore.findByShortCode(shortCode)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.SHORT_CODE_INVALID));
        return exchangeQrOrder(order, sessionKey, now);
    }

    private ExchangeResult exchangePersonalCode(PersonalCollectionCode code, String payerUserId,
                                                String sessionKey, Instant now) {
        var payer = accounts.resolvePersonalAccount(payerUserId);
        if (!"ACTIVE".equals(payer.status())) throw new BusinessException(BusinessErrorCode.ACCOUNT_UNAVAILABLE);
        if (code.getUserId().equals(payerUserId) || code.getAccountId().equals(payer.accountId())) {
            throw new BusinessException(BusinessErrorCode.SELF_PAYMENT_FORBIDDEN);
        }
        CollectionOrder order = CollectionOrder.forPersonalCode(security.newId(), code.getCodeId(), code.getUserId(),
                code.getAccountId(), payerUserId, payer.accountId(), now);
        if (!collectionStore.createPersonalOrder(order, sessionKey)) {
            throw new BusinessException(BusinessErrorCode.COLLECTION_REQUEST_PROCESSING);
        }
        return new ExchangeResult(CodeType.PERSONAL_CODE, order.getOrderId());
    }

    private ExchangeResult exchangeFixedRequest(CollectionRequest request, String payerUserId,
                                                String sessionKey, Instant now) {
        var payer = accounts.resolvePersonalAccount(payerUserId);
        if (!"ACTIVE".equals(payer.status())) throw new BusinessException(BusinessErrorCode.ACCOUNT_UNAVAILABLE);
        // 在读路径持久化过期终态；过期、取消等不可收状态对兑换者呈现为短码无效
        long version = request.getVersion();
        if (request.expireIfNecessary(now)) {
            collectionStore.updateRequest(request, version);
        }
        String status = request.getStatus().name();
        if (!"OPEN".equals(status) && !"PROCESSING".equals(status)) {
            throw new BusinessException(BusinessErrorCode.SHORT_CODE_INVALID);
        }
        if (request.getPayeeUserId().equals(payerUserId) || request.getPayeeAccountId().equals(payer.accountId())) {
            throw new BusinessException(BusinessErrorCode.SELF_PAYMENT_FORBIDDEN);
        }
        CollectionOrder order = CollectionOrder.forFixedRequest(security.newId(), request.getRequestId(),
                request.getPayeeUserId(), request.getPayeeAccountId(), payerUserId, payer.accountId(),
                request.getAmountFen(), request.getSubject(), now);
        if (!collectionStore.createFixedOrder(order, sessionKey)) {
            throw new BusinessException(BusinessErrorCode.COLLECTION_REQUEST_PROCESSING);
        }
        collectionStore.appendRequestEvent(new CollectionOrderEvent(security.newId(), request.getRequestId(),
                order.getOrderId(), null, "PENDING_CONFIRMATION", now));
        return new ExchangeResult(CodeType.COLLECTION_REQUEST, order.getOrderId());
    }

    private ExchangeResult exchangeQrOrder(QrPayOrder order, String sessionKey, Instant now) {
        long expectedVersion = order.getVersion();
        if (order.expireIfNecessary(now)) {
            qrStore.update(order, expectedVersion);
            throw new BusinessException(BusinessErrorCode.SHORT_CODE_INVALID);
        }
        // 动态订单单会话一次性消费：非待交换状态一律按短码无效处理
        if (order.getStatus() != QrPayOrderStatus.CREATED) {
            throw new BusinessException(BusinessErrorCode.SHORT_CODE_INVALID);
        }
        try {
            order.exchangeToken(sessionKey, order.getTokenDigest(), now);
        } catch (IllegalStateException consumed) {
            throw new BusinessException(BusinessErrorCode.SHORT_CODE_INVALID);
        }
        if (order.getVersion() != expectedVersion && !qrStore.update(order, expectedVersion)) {
            throw new BusinessException(BusinessErrorCode.VERSION_CONFLICT);
        }
        return new ExchangeResult(CodeType.QR_PAY_ORDER, order.getOrderId());
    }

    /** 短码指向的三类收款码。 */
    public enum CodeType { PERSONAL_CODE, COLLECTION_REQUEST, QR_PAY_ORDER }

    /** 兑换结果：码类型与新建或绑定的订单标识。 */
    public record ExchangeResult(CodeType codeType, String orderId) { }
}
