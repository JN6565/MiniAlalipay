package com.minialalipay.account.domain.repayment;

import java.util.Optional;

/**
 * 信用还款记录仓储接口。
 */
public interface CreditRepaymentRepository {
    Optional<CreditRepayment> findById(String repaymentId);
    Optional<CreditRepayment> findByRepaymentDraftId(String repaymentDraftId);
    Optional<CreditRepayment> findByTransactionId(String transactionId);
    void save(CreditRepayment repayment);
}
