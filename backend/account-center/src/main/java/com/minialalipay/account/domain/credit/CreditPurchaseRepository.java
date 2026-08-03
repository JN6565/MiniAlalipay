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
    void save(CreditPurchase purchase);
}
