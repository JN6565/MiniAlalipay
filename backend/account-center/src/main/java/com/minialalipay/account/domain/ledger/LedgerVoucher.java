package com.minialalipay.account.domain.ledger;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 复式账本凭证聚合根，封装一组不可变借贷分录。
 *
 * <p>凭证准备时即校验实际借贷合计与声明合计全部相等，过账时再次校验。
 * 这样可以在进入数据库事务前阻止不平凭证，并由数据库事务完成最终锁定和发布。</p>
 */
public final class LedgerVoucher {

    private final String voucherId;
    private final String transactionId;
    private final String voucherType;
    private final int reversalNo;
    private final String originalVoucherId;
    private final LedgerReversalReason reversalReason;
    private LedgerVoucherStatus status;
    private final long totalDebitFen;
    private final long totalCreditFen;
    private final List<LedgerEntry> entries;
    private Instant postedAt;
    private final Instant createdAt;

    /** 创建待过账凭证并立即校验借贷平衡。 */
    public static LedgerVoucher prepare(String voucherId, String transactionId, String voucherType,
                                        int reversalNo, String originalVoucherId, long totalDebitFen,
                                        long totalCreditFen, List<LedgerEntry> entries, Instant now) {
        return new LedgerVoucher(voucherId, transactionId, voucherType, reversalNo, originalVoucherId,
                null, LedgerVoucherStatus.PREPARED, totalDebitFen, totalCreditFen, entries, null, now);
    }

    /** 创建引用原凭证和受控原因的新冲正凭证。 */
    public static LedgerVoucher prepareReversal(String voucherId, String transactionId, String voucherType,
                                                int reversalNo, String originalVoucherId,
                                                LedgerReversalReason reversalReason, long totalDebitFen,
                                                long totalCreditFen, List<LedgerEntry> entries, Instant now) {
        return new LedgerVoucher(voucherId, transactionId, voucherType, reversalNo, originalVoucherId,
                reversalReason, LedgerVoucherStatus.PREPARED, totalDebitFen, totalCreditFen, entries, null, now);
    }

    /** 从持久化事实重建凭证。 */
    public LedgerVoucher(String voucherId, String transactionId, String voucherType, int reversalNo,
                         String originalVoucherId, LedgerVoucherStatus status, long totalDebitFen,
                         long totalCreditFen, List<LedgerEntry> entries, Instant postedAt, Instant createdAt) {
        this(voucherId, transactionId, voucherType, reversalNo, originalVoucherId, null, status,
                totalDebitFen, totalCreditFen, entries, postedAt, createdAt);
    }

    /** 从包含冲正原因的持久化事实重建凭证。 */
    public LedgerVoucher(String voucherId, String transactionId, String voucherType, int reversalNo,
                         String originalVoucherId, LedgerReversalReason reversalReason,
                         LedgerVoucherStatus status, long totalDebitFen, long totalCreditFen,
                         List<LedgerEntry> entries, Instant postedAt, Instant createdAt) {
        this.voucherId = requireText(voucherId, "凭证 ID 不能为空");
        this.transactionId = requireText(transactionId, "交易 ID 不能为空");
        this.voucherType = requireText(voucherType, "凭证类型不能为空");
        if (reversalNo < 0) throw new IllegalArgumentException("冲正序号不得为负");
        boolean hasOriginal = originalVoucherId != null && !originalVoucherId.isBlank();
        if (reversalNo == 0 && (hasOriginal || reversalReason != null)) {
            throw new IllegalArgumentException("原始凭证不能携带冲正引用");
        }
        if (reversalNo > 0 && (!hasOriginal || reversalReason == null)) {
            throw new IllegalArgumentException("冲正凭证必须引用原凭证并说明原因");
        }
        this.reversalNo = reversalNo;
        this.originalVoucherId = originalVoucherId;
        this.reversalReason = reversalReason;
        this.status = Objects.requireNonNull(status, "凭证状态不能为空");
        this.totalDebitFen = totalDebitFen;
        this.totalCreditFen = totalCreditFen;
        this.entries = List.copyOf(Objects.requireNonNull(entries, "分录不能为空"));
        this.postedAt = postedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
        validateBalance();
    }

    /** 将平衡的 PREPARED 凭证过账；重复过账保持幂等。 */
    public void post(Instant now) {
        if (status == LedgerVoucherStatus.POSTED) return;
        if (status != LedgerVoucherStatus.PREPARED) {
            throw new IllegalStateException("当前凭证状态不允许过账");
        }
        validateBalance();
        status = LedgerVoucherStatus.POSTED;
        postedAt = Objects.requireNonNull(now, "过账时间不能为空");
    }

    /** Cancel 只允许取消尚未过账的预留凭证；重复取消保持幂等。 */
    public void cancel() {
        if (status == LedgerVoucherStatus.CANCELLED) return;
        if (status != LedgerVoucherStatus.PREPARED) throw new IllegalStateException("已过账凭证不能直接取消");
        status = LedgerVoucherStatus.CANCELLED;
    }

    private void validateBalance() {
        if (entries.isEmpty()) throw new IllegalArgumentException("凭证至少包含两条分录");
        Set<Integer> sequenceNumbers = new HashSet<>();
        long actualDebit = 0L;
        long actualCredit = 0L;
        for (LedgerEntry entry : entries) {
            if (!voucherId.equals(entry.voucherId()) || !transactionId.equals(entry.transactionId())) {
                throw new IllegalArgumentException("分录与凭证标识不一致");
            }
            if (!sequenceNumbers.add(entry.sequenceNo())) {
                throw new IllegalArgumentException("凭证内分录序号不能重复");
            }
            if (entry.direction() == LedgerDirection.DEBIT) {
                actualDebit = Math.addExact(actualDebit, entry.amountFen());
            } else {
                actualCredit = Math.addExact(actualCredit, entry.amountFen());
            }
        }
        if (totalDebitFen <= 0 || totalDebitFen != totalCreditFen
                || actualDebit != actualCredit || actualDebit != totalDebitFen) {
            throw new IllegalStateException("账本凭证借贷不平");
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value;
    }

    /** @return 凭证 ID */ public String getVoucherId() { return voucherId; }
    /** @return 交易 ID */ public String getTransactionId() { return transactionId; }
    /** @return 凭证类型 */ public String getVoucherType() { return voucherType; }
    /** @return 冲正序号 */ public int getReversalNo() { return reversalNo; }
    /** @return 原凭证 ID，非冲正凭证为空 */ public String getOriginalVoucherId() { return originalVoucherId; }
    /** @return 冲正原因，非冲正凭证为空 */ public LedgerReversalReason getReversalReason() { return reversalReason; }
    /** @return 凭证状态 */ public LedgerVoucherStatus getStatus() { return status; }
    /** @return 借方合计，单位分 */ public long getTotalDebitFen() { return totalDebitFen; }
    /** @return 贷方合计，单位分 */ public long getTotalCreditFen() { return totalCreditFen; }
    /** @return 不可变分录列表 */ public List<LedgerEntry> getEntries() { return entries; }
    /** @return 过账时间，未过账时为空 */ public Instant getPostedAt() { return postedAt; }
    /** @return 创建时间 */ public Instant getCreatedAt() { return createdAt; }
}
