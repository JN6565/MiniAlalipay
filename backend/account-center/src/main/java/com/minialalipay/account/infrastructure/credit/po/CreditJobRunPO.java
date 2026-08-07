package com.minialalipay.account.infrastructure.credit.po;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 信用定时任务执行记录持久化对象，对应 {@code ledger_db.credit_job_run} 表。
 *
 * <p>该表记录月度出账、逾期检查等定时任务的执行流水，
 * 通过 (jobType, businessDate) 唯一键保证同一业务日期同一类型任务不重复执行。
 * requestDigest 为触发参数摘要（SHA-256，32 字节），retryCount 为恢复重试次数。</p>
 */
public class CreditJobRunPO {

    /** 任务执行 ID，对应 CHAR(26) */
    private String runId;

    /** 任务类型，对应 VARCHAR(16) */
    private String jobType;

    /** 业务日期，对应 DATE */
    private LocalDate businessDate;

    /** 执行状态，对应 VARCHAR(16) */
    private String status;

    /** 游标信用账户 ID，对应 CHAR(26) */
    private String cursorCreditAccountId;

    /** 触发类型，对应 VARCHAR(16) */
    private String triggerType;

    /** 触发用户 ID，对应 CHAR(26) */
    private String triggeredByUserId;

    /** 触发参数摘要，对应 BINARY(32) */
    private byte[] requestDigest;

    /** 恢复重试次数，对应 INT UNSIGNED */
    private Integer retryCount;

    /** 错误码，对应 VARCHAR(32) */
    private String errorCode;

    /** 乐观锁版本号，对应 BIGINT UNSIGNED */
    private Long version;

    /** 开始时间，对应 DATETIME(3) */
    private Instant startedAt;

    /** 完成时间，对应 DATETIME(3) */
    private Instant completedAt;

    /** 创建时间，对应 DATETIME(3) */
    private Instant createdAt;

    /** 更新时间，对应 DATETIME(3) */
    private Instant updatedAt;

    /** 无参构造器 */
    public CreditJobRunPO() {
    }

    /** 全参数构造器 */
    public CreditJobRunPO(String runId, String jobType, LocalDate businessDate, String status,
                          String cursorCreditAccountId, String triggerType, String triggeredByUserId,
                          byte[] requestDigest, Integer retryCount, String errorCode, Long version,
                          Instant startedAt, Instant completedAt, Instant createdAt, Instant updatedAt) {
        this.runId = runId;
        this.jobType = jobType;
        this.businessDate = businessDate;
        this.status = status;
        this.cursorCreditAccountId = cursorCreditAccountId;
        this.triggerType = triggerType;
        this.triggeredByUserId = triggeredByUserId;
        this.requestDigest = requestDigest;
        this.retryCount = retryCount;
        this.errorCode = errorCode;
        this.version = version;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
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

    public String getCursorCreditAccountId() {
        return cursorCreditAccountId;
    }

    public void setCursorCreditAccountId(String cursorCreditAccountId) {
        this.cursorCreditAccountId = cursorCreditAccountId;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public String getTriggeredByUserId() {
        return triggeredByUserId;
    }

    public void setTriggeredByUserId(String triggeredByUserId) {
        this.triggeredByUserId = triggeredByUserId;
    }

    public byte[] getRequestDigest() {
        return requestDigest;
    }

    public void setRequestDigest(byte[] requestDigest) {
        this.requestDigest = requestDigest;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
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
