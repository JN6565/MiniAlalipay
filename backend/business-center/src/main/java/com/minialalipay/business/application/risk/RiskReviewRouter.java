package com.minialalipay.business.application.risk;

import com.minialalipay.business.application.manualcase.ManualCaseApplicationService;
import com.minialalipay.business.application.port.CollectionStore;
import com.minialalipay.business.application.port.QrPayStore;
import com.minialalipay.business.domain.collection.CollectionOrder;
import com.minialalipay.business.domain.confirmation.SubjectType;
import com.minialalipay.business.domain.manualcase.ManualCaseType;
import com.minialalipay.business.domain.qrpay.QrPayOrder;
import com.minialalipay.business.domain.qrpay.QrPayOrderStatus;
import com.minialalipay.business.domain.transaction.BusinessErrorCode;
import com.minialalipay.common.error.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 受理前人工风控复核路由。
 *
 * <p>命中转人工规则时，在独立事务中将来源订单置为 {@code RISK_REVIEW} 并创建 {@code RISK_PRECHECK} 工单，
 * 再向调用方返回“已进入人工审核”。独立事务保证复核状态与工单先于外层确认失败提交，避免确认事务回滚撤销复核。
 * 操作时间由调用方应用服务传入，保证与来源订单使用同一时间基准。</p>
 */
@Service
public class RiskReviewRouter {
    private final QrPayStore qrPayStore;
    private final CollectionStore collectionStore;
    private final ManualCaseApplicationService manualCases;

    /** 创建风控复核路由。 */
    @Autowired
    public RiskReviewRouter(QrPayStore qrPayStore, CollectionStore collectionStore,
                            ManualCaseApplicationService manualCases) {
        this.qrPayStore = qrPayStore;
        this.collectionStore = collectionStore;
        this.manualCases = manualCases;
    }

    /** 将动态扫码订单转人工风控复核并创建预检工单；已在复核状态时重复路由幂等。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void routeQrPayOrderToReview(QrPayOrder order, long expectedVersion, String reasonCode, Instant now) {
        if (order.getStatus() != QrPayOrderStatus.RISK_REVIEW) {
            order.markRiskReview(order.getVersion(), now);
            if (!qrPayStore.update(order, expectedVersion)) {
                throw new BusinessException(BusinessErrorCode.VERSION_CONFLICT);
            }
        }
        manualCases.createCase(ManualCaseType.RISK_PRECHECK, SubjectType.QR_PAY_ORDER.name(),
                order.getOrderId(), reasonCode);
    }

    /** 将 C2C 订单转人工风控复核并创建预检工单；已在复核状态时重复路由幂等。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void routeCollectionOrderToReview(CollectionOrder order, long expectedVersion,
                                             String subjectType, String reasonCode, Instant now) {
        if (order.getStatus() != com.minialalipay.business.domain.collection.CollectionOrderStatus.RISK_REVIEW) {
            order.markRiskReview(order.getVersion(), now);
            if (!collectionStore.updateOrder(order, expectedVersion)) {
                throw new BusinessException(BusinessErrorCode.VERSION_CONFLICT);
            }
        }
        manualCases.createCase(ManualCaseType.RISK_PRECHECK, subjectType, order.getOrderId(), reasonCode);
    }
}
