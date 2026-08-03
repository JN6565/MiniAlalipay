package com.minialalipay.account.domain.bill;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 月度信用账单聚合根。
 *
 * <p>按自然月管理，每月 1 日生成上月账单，10 日 23:59:59 到期。
 * 唯一键 (creditAccountId, period) 保证每个账户每个账期只有一张账单。</p>
 *
 * <p>关键不变量：
 * <ul>
 *   <li>{@code total = paid + reversed + outstanding}</li>
 *   <li>{@code total > 0}</li>
 * </ul>
 * </p>
 */
public class CreditBill {

    private final String billId;
    private final String creditAccountId;
    private final String period;
    private final LocalDate statementDate;
    private final Instant dueAt;
    private final long totalFen;
    private long paidFen;
    private long reversedFen;
    private long outstandingFen;
    private CreditBillStatus status;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;

    /**
     * 月度出账时创建账单。
     *
     * @param billId 账单 ID（ULID）
     * @param creditAccountId 信用账户 ID
     * @param period 账期，格式 yyyy-MM
     * @param statementDate 出账日期
     * @param dueAt 到期时间
     * @param totalFen 账单总额（分），必须为正
     * @param now 创建时间
     */
    public CreditBill(
            String billId, String creditAccountId, String period,
            LocalDate statementDate, Instant dueAt, long totalFen, Instant now
    ) {
        this.billId = Objects.requireNonNull(billId, "账单 ID 不能为空");
        this.creditAccountId = Objects.requireNonNull(creditAccountId, "信用账户 ID 不能为空");
        this.period = Objects.requireNonNull(period, "账期不能为空");
        this.statementDate = Objects.requireNonNull(statementDate, "出账日期不能为空");
        this.dueAt = Objects.requireNonNull(dueAt, "到期时间不能为空");
        if (totalFen <= 0) {
            throw new IllegalArgumentException("账单总额必须为正");
        }
        this.totalFen = totalFen;
        this.paidFen = 0L;
        this.reversedFen = 0L;
        this.outstandingFen = totalFen;
        this.status = CreditBillStatus.OPEN;
        this.version = 0L;
        this.createdAt = Objects.requireNonNull(now, "创建时间不能为空");
        this.updatedAt = now;
        validateInvariants();
    }

    /**
     * 从持久化重建账单。
     */
    public CreditBill(
            String billId, String creditAccountId, String period,
            LocalDate statementDate, Instant dueAt, long totalFen,
            long paidFen, long reversedFen, long outstandingFen,
            CreditBillStatus status, long version,
            Instant createdAt, Instant updatedAt
    ) {
        this.billId = billId;
        this.creditAccountId = creditAccountId;
        this.period = period;
        this.statementDate = statementDate;
        this.dueAt = dueAt;
        this.totalFen = totalFen;
        this.paidFen = paidFen;
        this.reversedFen = reversedFen;
        this.outstandingFen = outstandingFen;
        this.status = status;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        validateInvariants();
    }

    /**
     * 应用还款金额，更新 paid/outstanding 和状态。
     *
     * <p>状态流转：
     * <ul>
     *   <li>部分还款：OPEN/OVERDUE → PARTIALLY_PAID</li>
     *   <li>全额还款：任意非 PAID 状态 → PAID</li>
     * </ul>
     * </p>
     *
     * @param amountFen 还款金额（分），必须为正且不超过未还余额
     * @param now 当前时间
     */
    public void applyRepayment(long amountFen, Instant now) {
        if (amountFen <= 0) {
            throw new IllegalArgumentException("还款金额必须为正");
        }
        if (this.status == CreditBillStatus.PAID) {
            throw new IllegalStateException("已全额还清的账单不可再还款");
        }
        if (amountFen > this.outstandingFen) {
            throw new IllegalStateException("还款金额超过账单未还余额");
        }
        this.paidFen += amountFen;
        this.outstandingFen -= amountFen;
        if (this.outstandingFen == 0) {
            this.status = CreditBillStatus.PAID;
        } else {
            this.status = CreditBillStatus.PARTIALLY_PAID;
        }
        this.updatedAt = now;
        validateInvariants();
    }

    /**
     * 到期检查时标记为逾期。仅 OPEN 和 PARTIALLY_PAID 状态可转为 OVERDUE。
     *
     * @param now 当前时间
     */
    public void markOverdue(Instant now) {
        if (this.status != CreditBillStatus.OPEN
                && this.status != CreditBillStatus.PARTIALLY_PAID) {
            throw new IllegalStateException("仅 OPEN 和 PARTIALLY_PAID 状态可标记逾期，当前状态: " + this.status);
        }
        this.status = CreditBillStatus.OVERDUE;
        this.updatedAt = now;
    }

    /** @return 账单是否已全额还清 */
    public boolean isPaid() {
        return this.status == CreditBillStatus.PAID;
    }

    /** @return 账单是否逾期 */
    public boolean isOverdue() {
        return this.status == CreditBillStatus.OVERDUE;
    }

    /** @return 账单 ID */
    public String getBillId() { return billId; }

    /** @return 信用账户 ID */
    public String getCreditAccountId() { return creditAccountId; }

    /** @return 账期，格式 yyyy-MM */
    public String getPeriod() { return period; }

    /** @return 出账日期 */
    public LocalDate getStatementDate() { return statementDate; }

    /** @return 到期时间 */
    public Instant getDueAt() { return dueAt; }

    /** @return 账单总额（分） */
    public long getTotalFen() { return totalFen; }

    /** @return 已还金额（分） */
    public long getPaidFen() { return paidFen; }

    /** @return 已冲销金额（分） */
    public long getReversedFen() { return reversedFen; }

    /** @return 未还金额（分） */
    public long getOutstandingFen() { return outstandingFen; }

    /** @return 账单状态 */
    public CreditBillStatus getStatus() { return status; }

    /** @return 版本号 */
    public long getVersion() { return version; }

    /** @return 创建时间 */
    public Instant getCreatedAt() { return createdAt; }

    /** @return 更新时间 */
    public Instant getUpdatedAt() { return updatedAt; }

    /** 更新版本号 */
    public void updateVersion(long version) { this.version = version; }

    private void validateInvariants() {
        if (totalFen != paidFen + reversedFen + outstandingFen) {
            throw new IllegalStateException("账单金额不平衡: total != paid + reversed + outstanding");
        }
        if (totalFen <= 0) {
            throw new IllegalStateException("账单总额必须为正");
        }
    }
}
