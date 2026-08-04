package com.minialalipay.account.domain.ledger;

import java.time.Instant;
import java.util.Objects;

/**
 * 不可变账本分录。
 *
 * @param entryId 分录雪花 ID
 * @param voucherId 所属凭证 ID
 * @param transactionId 统一资金交易 ID
 * @param ledgerAccountId 借贷科目 ID
 * @param direction 借贷方向
 * @param amountFen 金额，单位分且必须大于 0
 * @param sequenceNo 凭证内稳定序号，必须大于 0
 * @param memo 脱敏摘要，可为空
 * @param createdAt 创建时间
 */
public record LedgerEntry(long entryId, String voucherId, String transactionId, String ledgerAccountId,
                          LedgerDirection direction, long amountFen, int sequenceNo, String memo,
                          Instant createdAt) {
    /** 校验不可变分录字段。 */
    public LedgerEntry {
        if (entryId <= 0) throw new IllegalArgumentException("分录 ID 必须为正");
        requireText(voucherId, "凭证 ID 不能为空");
        requireText(transactionId, "交易 ID 不能为空");
        requireText(ledgerAccountId, "账本科目 ID 不能为空");
        Objects.requireNonNull(direction, "借贷方向不能为空");
        if (amountFen <= 0) throw new IllegalArgumentException("分录金额必须为正");
        if (sequenceNo <= 0) throw new IllegalArgumentException("分录序号必须为正");
        Objects.requireNonNull(createdAt, "创建时间不能为空");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    }
}
