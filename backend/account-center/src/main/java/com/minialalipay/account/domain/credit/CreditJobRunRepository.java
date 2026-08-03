package com.minialalipay.account.domain.credit;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 信用定时任务执行记录仓储接口。
 */
public interface CreditJobRunRepository {
    Optional<CreditJobRun> findByJobTypeAndBusinessDate(String jobType, LocalDate businessDate);
    void save(CreditJobRun jobRun);
}
