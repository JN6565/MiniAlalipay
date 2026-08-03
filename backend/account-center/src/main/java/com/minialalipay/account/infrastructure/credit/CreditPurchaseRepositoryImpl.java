package com.minialalipay.account.infrastructure.credit;

import com.minialalipay.account.domain.credit.CreditPurchase;
import com.minialalipay.account.domain.credit.CreditPurchaseBillingStatus;
import com.minialalipay.account.domain.credit.CreditPurchaseRepository;
import com.minialalipay.account.infrastructure.credit.mapper.CreditPurchaseMapper;
import com.minialalipay.account.infrastructure.credit.po.CreditPurchasePO;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 信用消费明细仓储实现类。
 *
 * <p>基于 {@link CreditPurchaseMapper} 实现消费明细的持久化操作，
 * 负责领域对象 {@link CreditPurchase} 与 {@link CreditPurchasePO} 之间的转换。</p>
 */
@Repository
public class CreditPurchaseRepositoryImpl implements CreditPurchaseRepository {

    private final CreditPurchaseMapper creditPurchaseMapper;

    public CreditPurchaseRepositoryImpl(CreditPurchaseMapper creditPurchaseMapper) {
        this.creditPurchaseMapper = creditPurchaseMapper;
    }

    @Override
    public Optional<CreditPurchase> findById(String purchaseId) {
        CreditPurchasePO po = creditPurchaseMapper.findById(purchaseId);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public Optional<CreditPurchase> findByCreditTransactionId(String creditTransactionId) {
        CreditPurchasePO po = creditPurchaseMapper.findByCreditTransactionId(creditTransactionId);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public List<CreditPurchase> findByCreditAccountIdAndBillingStatus(String creditAccountId, String billingStatus) {
        List<CreditPurchasePO> pos = creditPurchaseMapper.findByCreditAccountIdAndBillingStatus(creditAccountId, billingStatus);
        return pos.stream().map(this::toDomain).toList();
    }

    @Override
    public void save(CreditPurchase purchase) {
        CreditPurchasePO existing = creditPurchaseMapper.findById(purchase.getPurchaseId());
        if (existing == null) {
            creditPurchaseMapper.insert(toPO(purchase));
        } else {
            int updated = creditPurchaseMapper.updateByCas(toPO(purchase));
            if (updated == 0) {
                throw new IllegalStateException("消费明细乐观锁冲突，purchaseId=" + purchase.getPurchaseId());
            }
            purchase.updateVersion(purchase.getVersion() + 1);
        }
    }

    /**
     * 将持久化对象转换为领域对象。
     */
    private CreditPurchase toDomain(CreditPurchasePO po) {
        return new CreditPurchase(
                po.getPurchaseId(),
                po.getCreditTransactionId(),
                po.getCreditAccountId(),
                po.getQrOrderId(),
                po.getMerchantAccountId(),
                po.getAmountFen(),
                po.getRepaidFen(),
                po.getRefundedFen(),
                po.getRefundTransactionId(),
                CreditPurchaseBillingStatus.valueOf(po.getBillingStatus()),
                po.getVersion(),
                po.getOccurredAt(),
                po.getUpdatedAt()
        );
    }

    /**
     * 将领域对象转换为持久化对象。
     */
    private CreditPurchasePO toPO(CreditPurchase purchase) {
        CreditPurchasePO po = new CreditPurchasePO();
        po.setPurchaseId(purchase.getPurchaseId());
        po.setCreditTransactionId(purchase.getCreditTransactionId());
        po.setCreditAccountId(purchase.getCreditAccountId());
        po.setQrOrderId(purchase.getQrOrderId());
        po.setMerchantAccountId(purchase.getMerchantAccountId());
        po.setAmountFen(purchase.getAmountFen());
        po.setRepaidFen(purchase.getRepaidFen());
        po.setRefundedFen(purchase.getRefundedFen());
        po.setOutstandingFen(purchase.getOutstandingFen());
        po.setRefundTransactionId(purchase.getRefundTransactionId());
        po.setBillingStatus(purchase.getBillingStatus().name());
        po.setVersion(purchase.getVersion());
        po.setOccurredAt(purchase.getOccurredAt());
        po.setUpdatedAt(purchase.getUpdatedAt());
        return po;
    }
}
