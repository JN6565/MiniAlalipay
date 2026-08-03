package com.minialalipay.account.domain.bill;

import java.util.List;
import java.util.Optional;

/**
 * 信用月度账单仓储接口。
 */
public interface CreditBillRepository {
    Optional<CreditBill> findById(String billId);
    Optional<CreditBill> findByCreditAccountIdAndPeriod(String creditAccountId, String period);
    List<CreditBill> findByCreditAccountId(String creditAccountId);
    void save(CreditBill bill);
}
