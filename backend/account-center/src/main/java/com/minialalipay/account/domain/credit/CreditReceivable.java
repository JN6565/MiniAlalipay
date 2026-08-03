package com.minialalipay.account.domain.credit;

import java.time.Instant;
import java.util.Objects;

/**
 * 信用应收汇总事实。
 *
 * <p>用户使用 Mini 花呗后平台形成的虚拟应收资产，与已用额度对等。
 * 应收分布在 ledger_db（不是 account_db）。</p>
 *
 * <p>关键不变量：
 * <ul>
 *   <li>{@code used_fen = receivable_outstanding = unbilled + sum(bill.outstanding)}</li>
 *   <li>{@code overdue_fen <= billed_fen}</li>
 * </ul>
 * </p>
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@code unbilledFen}：未出账应收</li>
 *   <li>{@code billedFen}：已出账应收</li>
 *   <li>{@code overdueFen}：逾期应收（是 billed 的子集）</li>
 * </ul>
 * </p>
 */
public class CreditReceivable {

    private final String creditAccountId;
    private long unbilledFen;
    private long billedFen;
    private long overdueFen;
    private long version;
    private Instant updatedAt;

    /**
     * 开户时创建应收汇总，三项均为 0。
     *
     * @param creditAccountId 信用账户 ID
     * @param now 创建时间
     */
    public CreditReceivable(String creditAccountId, Instant now) {
        this.creditAccountId = Objects.requireNonNull(creditAccountId, "信用账户 ID 不能为空");
        this.unbilledFen = 0L;
        this.billedFen = 0L;
        this.overdueFen = 0L;
        this.version = 0L;
        this.updatedAt = Objects.requireNonNull(now, "更新时间不能为空");
    }

    /**
     * 从持久化重建应收汇总。
     */
    public CreditReceivable(
            String creditAccountId, long unbilledFen, long billedFen,
            long overdueFen, long version, Instant updatedAt
    ) {
        this.creditAccountId = creditAccountId;
        this.unbilledFen = unbilledFen;
        this.billedFen = billedFen;
        this.overdueFen = overdueFen;
        this.version = version;
        this.updatedAt = updatedAt;
        validateInvariants();
    }

    /**
     * 信用消费确认后增加未出账应收。
     *
     * @param amountFen 消费金额（分），必须为正
     * @param now 当前时间
     */
    public void increaseUnbilled(long amountFen, Instant now) {
        if (amountFen <= 0) {
            throw new IllegalArgumentException("增加金额必须为正");
        }
        this.unbilledFen += amountFen;
        this.updatedAt = now;
        validateInvariants();
    }

    /**
     * 月度出账：将未出账转为已出账。
     *
     * @param amountFen 出账金额（分），必须为正且不超过未出账应收
     * @param now 当前时间
     */
    public void transferToBilled(long amountFen, Instant now) {
        if (amountFen <= 0) {
            throw new IllegalArgumentException("出账金额必须为正");
        }
        if (this.unbilledFen < amountFen) {
            throw new IllegalStateException("未出账应收不足以出账");
        }
        this.unbilledFen -= amountFen;
        this.billedFen += amountFen;
        this.updatedAt = now;
        validateInvariants();
    }

    /**
     * 到期检查：将已出账转为逾期。
     *
     * @param amountFen 逾期金额（分），必须为正且不超过已出账应收
     * @param now 当前时间
     */
    public void markOverdue(long amountFen, Instant now) {
        if (amountFen <= 0) {
            throw new IllegalArgumentException("逾期金额必须为正");
        }
        if (this.billedFen - this.overdueFen < amountFen) {
            throw new IllegalStateException("已出账非逾期应收不足以标记逾期");
        }
        this.overdueFen += amountFen;
        this.updatedAt = now;
        validateInvariants();
    }

    /**
     * 还款后减少应收。按分配顺序减少：先逾期、再已出账、最后未出账。
     *
     * @param amountFen 还款金额（分），必须为正且不超过未还应收总额
     * @param now 当前时间
     */
    public void decreaseByRepayment(long amountFen, Instant now) {
        if (amountFen <= 0) {
            throw new IllegalArgumentException("还款金额必须为正");
        }
        long remaining = amountFen;
        // 先扣逾期
        long overdueDeduct = Math.min(remaining, this.overdueFen);
        this.overdueFen -= overdueDeduct;
        remaining -= overdueDeduct;
        // 再扣已出账非逾期
        long billedNonOverdue = this.billedFen - this.overdueFen;
        long billedDeduct = Math.min(remaining, billedNonOverdue);
        this.billedFen -= billedDeduct;
        remaining -= billedDeduct;
        // 最后扣未出账
        long unbilledDeduct = Math.min(remaining, this.unbilledFen);
        this.unbilledFen -= unbilledDeduct;
        remaining -= unbilledDeduct;
        if (remaining > 0) {
            throw new IllegalStateException("应收不足以扣减还款金额");
        }
        this.updatedAt = now;
        validateInvariants();
    }

    /** @return 未出账应收（分） */
    public long getUnbilledFen() { return unbilledFen; }

    /** @return 已出账应收（分） */
    public long getBilledFen() { return billedFen; }

    /** @return 逾期应收（分） */
    public long getOverdueFen() { return overdueFen; }

    /** @return 应收总额（分）= 未出账 + 已出账 */
    public long getTotalOutstandingFen() { return unbilledFen + billedFen; }

    /** @return 信用账户 ID */
    public String getCreditAccountId() { return creditAccountId; }

    /** @return 版本号 */
    public long getVersion() { return version; }

    /** @return 更新时间 */
    public Instant getUpdatedAt() { return updatedAt; }

    /** 更新版本号 */
    public void updateVersion(long version) { this.version = version; }

    private void validateInvariants() {
        if (unbilledFen < 0 || billedFen < 0 || overdueFen < 0) {
            throw new IllegalStateException("应收各项不得为负");
        }
        if (overdueFen > billedFen) {
            throw new IllegalStateException("逾期应收不得超过已出账应收");
        }
    }
}
