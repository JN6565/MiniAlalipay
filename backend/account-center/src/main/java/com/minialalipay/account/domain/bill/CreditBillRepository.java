package com.minialalipay.account.domain.bill;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 信用月度账单仓储接口。
 */
public interface CreditBillRepository {
    Optional<CreditBill> findById(String billId);
    Optional<CreditBill> findByCreditAccountIdAndPeriod(String creditAccountId, String period);
    List<CreditBill> findByCreditAccountId(String creditAccountId);

    /**
     * 查询已到期但未标记为 OVERDUE 且仍有未还金额的账单。
     *
     * <p>用于到期检查任务，查询条件：due_at &lt; cutoffTime AND status IN ('OPEN', 'PARTIALLY_PAID') AND outstanding_fen > 0</p>
     *
     * @param cutoffTime 截止时间，到期时间早于此值的账单视为已到期
     * @return 到期未还账单列表
     */
    List<CreditBill> findOverdueBills(Instant cutoffTime);

    void save(CreditBill bill);
}
