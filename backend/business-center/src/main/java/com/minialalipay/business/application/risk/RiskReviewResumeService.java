package com.minialalipay.business.application.risk;

import com.minialalipay.business.application.port.CollectionStore;
import com.minialalipay.business.application.port.QrPayStore;
import com.minialalipay.business.application.port.RiskReviewResumePort;
import com.minialalipay.business.domain.collection.CollectionOrder;
import com.minialalipay.business.domain.collection.CollectionOrderStatus;
import com.minialalipay.business.domain.confirmation.SubjectType;
import com.minialalipay.business.domain.qrpay.QrPayOrder;
import com.minialalipay.business.domain.qrpay.QrPayOrderStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 人工复核批准后的来源订单恢复服务。
 *
 * <p>仅把 {@code RISK_REVIEW} 的来源订单恢复为待确认，用户可重新发起确认；主体不在复核状态或已过期取消时
 * 视为已处理，避免工单处置因为来源订单生命周期结束而失败。</p>
 */
@Service
public class RiskReviewResumeService implements RiskReviewResumePort {
    private final QrPayStore qrPayStore;
    private final CollectionStore collectionStore;

    /** 创建订单恢复服务。 */
    public RiskReviewResumeService(QrPayStore qrPayStore, CollectionStore collectionStore) {
        this.qrPayStore = qrPayStore;
        this.collectionStore = collectionStore;
    }

    @Override
    public boolean resumeToConfirmation(String subjectType, String subjectId, Instant now) {
        if (SubjectType.QR_PAY_ORDER.name().equals(subjectType)) {
            return resumeQrPayOrder(subjectId, now);
        }
        if (SubjectType.PERSONAL_QR_ORDER.name().equals(subjectType)
                || SubjectType.COLLECTION_REQUEST_ORDER.name().equals(subjectType)) {
            return resumeCollectionOrder(subjectId, now);
        }
        return false;
    }

    private boolean resumeQrPayOrder(String orderId, Instant now) {
        QrPayOrder order = qrPayStore.findById(orderId).orElse(null);
        if (order == null) return true;
        if (order.getStatus() != QrPayOrderStatus.RISK_REVIEW) return true;
        long expectedVersion = order.getVersion();
        try {
            order.resumeFromRiskReview(expectedVersion, now);
        } catch (IllegalStateException invalid) {
            return false;
        }
        return qrPayStore.update(order, expectedVersion);
    }

    private boolean resumeCollectionOrder(String orderId, Instant now) {
        CollectionOrder order = collectionStore.findOrder(orderId).orElse(null);
        if (order == null) return true;
        if (order.getStatus() != CollectionOrderStatus.RISK_REVIEW) return true;
        long expectedVersion = order.getVersion();
        try {
            order.resumeFromRiskReview(expectedVersion, now);
        } catch (IllegalStateException invalid) {
            return false;
        }
        return collectionStore.updateOrder(order, expectedVersion);
    }
}
