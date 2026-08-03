package com.minialalipay.account.domain.repayment;

import java.util.List;

/**
 * 信用还款分配计划仓储接口。
 */
public interface CreditRepaymentAllocationRepository {
    List<CreditRepaymentAllocation> findByRepaymentId(String repaymentId);
    void saveAll(String repaymentId, List<CreditRepaymentAllocation> allocations);
}
