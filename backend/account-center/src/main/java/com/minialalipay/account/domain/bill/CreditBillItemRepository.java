package com.minialalipay.account.domain.bill;

import java.util.List;
import java.util.Optional;

/**
 * 信用账单明细仓储接口。
 */
public interface CreditBillItemRepository {
    List<CreditBillItem> findByBillId(String billId);
    Optional<CreditBillItem> findByPurchaseId(String purchaseId);
    void save(CreditBillItem item);
}
