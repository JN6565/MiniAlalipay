package com.minialalipay.account.domain.credit;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 信用定时任务执行记录领域实体。
 *
 * <p>对应 {@code ledger_db.credit_job_run} 表，记录月度出账、逾期检查等定时任务的执行流水。
 * 通过 (jobType, businessDate) 唯一键保证同一业务日期同一类型任务不重复执行。</p>
 *
 * <p>状态流转：
 * <ul>
 *   <li>{@link CreditJobStatus#PENDING} → {@link CreditJobStatus#RUNNING}（开始执行）</li>
 *   <li>{@link CreditJobStatus#RUNNING} → {@link CreditJobStatus#SUCCESS}（执行成功）</li>
 *   <li>{@link CreditJobStatus#RUNNING} → {@link CreditJobStatus#FAILED}（执行失败）</li>
 * </ul>
 * SUCCESS 和 FAILED 为终态。</p>
 */
public class CreditJobRun {

    private final String runId;
    private final CreditJobType jobType;
    private final LocalDate businessDate;
    private CreditJobStatus status;
    private String cursorCreditAccountId;
    private final CreditJobTriggerType triggerType;
    private final String triggeredByUserId;
    private final byte[] requestDigest;
    private int retryCount;
    private long version;
    private Instant startedAt;
    private Instant completedAt;
    private final Instant createdAt;
    private Instant updatedAt;
    private String errorCode;

    /**
     * 创建定时任务执行记录，初始状态为 PENDING。
     *
     * @param runId 任务执行 ID（ULID）
     * @param jobType 任务类型
     * @param businessDate 业务日期
     * @param triggerType 触发类型
     * @param triggeredByUserId 触发用户 ID，定时调度时为 null
     * @param requestDigest 触发参数摘要（SHA-256，32 字节），识别同键异参
     * @param now 创建时间
     */
    public CreditJobRun(
            String runId, CreditJobType jobType, LocalDate businessDate,
            CreditJobTriggerType triggerType, String triggeredByUserId,
            byte[] requestDigest, Instant now
    ) {
        this.runId = Objects.requireNonNull(runId, "任务执行 ID 不能为空");
        this.jobType = Objects.requireNonNull(jobType, "任务类型不能为空");
        this.businessDate = Objects.requireNonNull(businessDate, "业务日期不能为空");
        this.status = CreditJobStatus.PENDING;
        this.cursorCreditAccountId = null;
        this.triggerType = Objects.requireNonNull(triggerType, "触发类型不能为空");
        this.triggeredByUserId = triggeredByUserId;
        this.requestDigest = Objects.requireNonNull(requestDigest, "触发参数摘要不能为空").clone();
        this.retryCount = 0;
        this.version = 0L;
        this.startedAt = null;
        this.completedAt = null;
        this.createdAt = Objects.requireNonNull(now, "创建时间不能为空");
        this.updatedAt = now;
        this.errorCode = null;
    }

    /**
     * 从持久化重建任务执行记录。
     *
     * @param runId 任务执行 ID
     * @param jobType 任务类型
     * @param businessDate 业务日期
     * @param status 执行状态
     * @param cursorCreditAccountId 游标信用账户 ID
     * @param triggerType 触发类型
     * @param triggeredByUserId 触发用户 ID
     * @param requestDigest 触发参数摘要（SHA-256，32 字节）
     * @param retryCount 恢复重试次数
     * @param version 版本号
     * @param startedAt 开始时间
     * @param completedAt 完成时间
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     * @param errorCode 错误码
     */
    public CreditJobRun(
            String runId, CreditJobType jobType, LocalDate businessDate,
            CreditJobStatus status, String cursorCreditAccountId,
            CreditJobTriggerType triggerType, String triggeredByUserId,
            byte[] requestDigest, int retryCount,
            long version, Instant startedAt, Instant completedAt,
            Instant createdAt, Instant updatedAt, String errorCode
    ) {
        this.runId = runId;
        this.jobType = jobType;
        this.businessDate = businessDate;
        this.status = status;
        this.cursorCreditAccountId = cursorCreditAccountId;
        this.triggerType = triggerType;
        this.triggeredByUserId = triggeredByUserId;
        this.requestDigest = requestDigest.clone();
        this.retryCount = retryCount;
        this.version = version;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.errorCode = errorCode;
    }

    /**
     * 开始执行任务。PENDING → RUNNING。
     *
     * @param now 当前时间
     */
    public void start(Instant now) {
        if (this.status != CreditJobStatus.PENDING) {
            throw new IllegalStateException("仅 PENDING 状态可开始执行，当前状态: " + this.status);
        }
        this.status = CreditJobStatus.RUNNING;
        this.startedAt = Objects.requireNonNull(now, "开始时间不能为空");
        this.updatedAt = now;
    }

    /**
     * 重新执行失败或中断的任务（手动重触发或恢复任务接管）。
     *
     * <p>FAILED、RUNNING、MANUAL_REVIEW 或 PENDING 均可回到 RUNNING，
     * 清除上次执行的游标与错误信息并递增恢复重试次数；
     * SUCCESS 为终态，不允许重跑，避免同一业务日期重复建账单或重复汇总消费。</p>
     *
     * @param now 当前时间
     */
    public void restart(Instant now) {
        if (this.status == CreditJobStatus.SUCCESS) {
            throw new IllegalStateException("已成功的任务不可重跑，runId: " + this.runId);
        }
        this.status = CreditJobStatus.RUNNING;
        this.startedAt = Objects.requireNonNull(now, "开始时间不能为空");
        this.cursorCreditAccountId = null;
        this.completedAt = null;
        this.errorCode = null;
        this.retryCount = this.retryCount + 1;
        this.updatedAt = now;
    }

    /**
     * 任务执行成功。RUNNING → SUCCESS。终态。
     *
     * @param cursorCreditAccountId 游标信用账户 ID，用于断点续传
     * @param now 当前时间
     */
    public void succeed(String cursorCreditAccountId, Instant now) {
        if (this.status != CreditJobStatus.RUNNING) {
            throw new IllegalStateException("仅 RUNNING 状态可标记成功，当前状态: " + this.status);
        }
        this.status = CreditJobStatus.SUCCESS;
        this.cursorCreditAccountId = cursorCreditAccountId;
        this.completedAt = Objects.requireNonNull(now, "完成时间不能为空");
        this.updatedAt = now;
    }

    /**
     * 任务执行失败。RUNNING → FAILED。终态。
     *
     * @param errorCode 错误码
     * @param now 当前时间
     */
    public void fail(String errorCode, Instant now) {
        if (this.status != CreditJobStatus.RUNNING) {
            throw new IllegalStateException("仅 RUNNING 状态可标记失败，当前状态: " + this.status);
        }
        this.status = CreditJobStatus.FAILED;
        this.errorCode = errorCode;
        this.completedAt = Objects.requireNonNull(now, "完成时间不能为空");
        this.updatedAt = now;
    }

    /**
     * 判断是否处于终态。
     *
     * @return SUCCESS、FAILED 或 MANUAL_REVIEW 时返回 true
     */
    public boolean isTerminal() {
        return this.status == CreditJobStatus.SUCCESS
                || this.status == CreditJobStatus.FAILED
                || this.status == CreditJobStatus.MANUAL_REVIEW;
    }

    /** @return 任务执行 ID */
    public String getRunId() { return runId; }

    /** @return 任务类型 */
    public CreditJobType getJobType() { return jobType; }

    /** @return 业务日期 */
    public LocalDate getBusinessDate() { return businessDate; }

    /** @return 执行状态 */
    public CreditJobStatus getStatus() { return status; }

    /** @return 游标信用账户 ID */
    public String getCursorCreditAccountId() { return cursorCreditAccountId; }

    /** @return 触发类型 */
    public CreditJobTriggerType getTriggerType() { return triggerType; }

    /** @return 触发用户 ID */
    public String getTriggeredByUserId() { return triggeredByUserId; }

    /** @return 触发参数摘要（SHA-256，32 字节）的防御性副本 */
    public byte[] getRequestDigest() { return requestDigest == null ? null : requestDigest.clone(); }

    /** @return 恢复重试次数 */
    public int getRetryCount() { return retryCount; }

    /** @return 版本号 */
    public long getVersion() { return version; }

    /** @return 开始时间 */
    public Instant getStartedAt() { return startedAt; }

    /** @return 完成时间 */
    public Instant getCompletedAt() { return completedAt; }

    /** @return 创建时间 */
    public Instant getCreatedAt() { return createdAt; }

    /** @return 更新时间 */
    public Instant getUpdatedAt() { return updatedAt; }

    /** @return 错误码 */
    public String getErrorCode() { return errorCode; }

    /** 更新版本号 */
    public void updateVersion(long version) { this.version = version; }
}
