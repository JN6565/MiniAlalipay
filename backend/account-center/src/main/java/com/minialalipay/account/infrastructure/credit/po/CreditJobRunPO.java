package com.minialalipay.account.infrastructure.credit.po;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 信用定时任务执行记录持久化对象，对应 {@code ledger_db.credit_job_run} 表。
 *
 * <p>该表记录月度出账、逾期检查等定时任务的执行流水，
 * 通过 (jobType, businessDate) 唯一键保证同一业务日期同一类型任务不重复执行。</p>
 */
public class CreditJobRunPO {

    /** 任务执行 ID，对应 CHAR(26) */
    private String jobRunId;

    /** 任务类型，对应 VARCHAR */
    private String jobType;

    /** 业务日期，对应 DATE */
    private LocalDate businessDate;

    /** 执行状态，对应 CHAR */
    private String status;

    /** 开始时间，对应 DATETIME(3) */
    private Instant startedAt;

    /** 结束时间，对应 DATETIME(3) */
    private Instant finishedAt;

    /** 错误信息，对应 TEXT */
    private String errorMessage;

    /** 乐观锁版本号，对应 BIGINT UNSIGNED */
    private Long version;

    /** 创建时间，对应 DATETIME(3) */
    private Instant createdAt;

    /** 更新时间，对应 DATETIME(3) */
    private Instant updatedAt;

    /** 无参构造器 */
    public CreditJobRunPO() {
    }

    /** 全参数构造器 */
    public CreditJobRunPO(String jobRunId, String jobType, LocalDate businessDate, String status,
                          Instant startedAt, Instant finishedAt, String errorMessage, Long version,
                          Instant createdAt, Instant updatedAt) {
        this.jobRunId = jobRunId;
        this.jobType = jobType;
        this.businessDate = businessDate;
        this.status = status;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.errorMessage = errorMessage;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getJobRunId() {
        return jobRunId;
    }

    public void setJobRunId(String jobRunId) {
        this.jobRunId = jobRunId;
    }

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public void setBusinessDate(LocalDate businessDate) {
        this.businessDate = businessDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
