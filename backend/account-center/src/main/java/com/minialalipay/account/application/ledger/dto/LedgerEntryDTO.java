package com.minialalipay.account.application.ledger.dto;

import java.time.Instant;

/**
 * 本人账本明细展示对象。
 *
 * @param entryId 分录 ID，与创建时间共同组成服务端分页游标
 * @param transactionId 统一资金交易 ID
 * @param direction 借贷方向
 * @param amountFen 金额，单位分
 * @param memo 脱敏摘要，可为空
 * @param counterpartyName 交易对方显示名称（昵称或脱敏手机号），可为空
 * @param balanceAfterFen 交易完成后本人账户可用余额（分），可为空：
 *                        存量分录与系统账户分录无值，前端回退为不展示
 * @param createdAt 分录时间
 */
public record LedgerEntryDTO(long entryId, String transactionId, String direction, long amountFen,
                             String memo, String counterpartyName, Long balanceAfterFen, Instant createdAt) {
}
