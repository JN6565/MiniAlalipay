package com.minialalipay.account.domain.ledger;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

/** 复式账本仓储端口，仅允许新增凭证/分录及只读查询，不暴露分录更新和删除能力。 */
public interface LedgerRepository {

    /** @return 交易、凭证类型和冲正序号唯一确定的凭证 */
    Optional<LedgerVoucher> find(String transactionId, String voucherType, int reversalNo);

    /** @return 锁定后的指定凭证，不存在时为空；锁持续到当前事务结束 */
    Optional<LedgerVoucher> findByIdForUpdate(String voucherId);

    /** 原子新增 PREPARED 凭证和全部不可变分录。 */
    void savePrepared(LedgerVoucher voucher);

    /** @return 数据库实际分录的借方与贷方合计，单位分 */
    LedgerTotals summarizeEntries(String voucherId);

    /**
     * 将 PREPARED 凭证原子推进为 POSTED，并在同一事务写入账本 Outbox。
     *
     * @return 仅当状态推进成功时返回 true
     */
    boolean postAndAppendOutbox(LedgerVoucher voucher, String eventId, String traceId, Instant now);

    /**
     * 查询用户拥有科目的账本分录。
     *
     * @param userId 用户 ID
     * @param cursorCreatedAt 上一页末尾分录时间，首页为空
     * @param cursorEntryId 上一页末尾分录 ID，首页为 0
     * @param limit 返回上限，不超过 100
     */
    List<LedgerEntry> findEntriesByUserId(String userId, Instant cursorCreatedAt, long cursorEntryId, int limit);

    /** 数据库实际分录的借贷汇总。 */
    record LedgerTotals(long debitFen, long creditFen) {
    }
}
