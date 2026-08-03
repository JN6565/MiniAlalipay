package com.minialalipay.account.domain.repayment;

import java.util.Optional;

/**
 * 信用还款草稿仓储接口。
 */
public interface CreditRepaymentDraftRepository {
    Optional<CreditRepaymentDraft> findById(String repaymentDraftId);
    void save(CreditRepaymentDraft draft);
}
