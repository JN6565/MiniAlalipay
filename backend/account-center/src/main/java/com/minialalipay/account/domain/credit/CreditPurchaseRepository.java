package com.minialalipay.account.domain.credit;

import java.util.List;
import java.util.Optional;

/**
 * 信用消费明细仓储接口。
 */
public interface CreditPurchaseRepository {
    Optional<CreditPurchase> findById(String purchaseId);
    Optional<CreditPurchase> findByCreditTransactionId(String creditTransactionId);
    List<CreditPurchase> findByCreditAccountIdAndBillingStatus(String creditAccountId, String billingStatus);

    /**
     * 按出账状态查询所有信用消费明细（不限定账户）。
     *
     * <p>用于月度出账任务遍历所有未出账消费。</p>
     *
     * @param billingStatus 出账状态
     * @return 匹配状态的消费明细列表
     */
    List<CreditPurchase> findByBillingStatus(String billingStatus);

    void save(CreditPurchase purchase);
}
