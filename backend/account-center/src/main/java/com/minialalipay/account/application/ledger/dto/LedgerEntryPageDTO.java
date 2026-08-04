package com.minialalipay.account.application.ledger.dto;

import java.util.List;

/**
 * 本人账本明细游标分页结果。
 *
 * @param items 当前页明细
 * @param nextCursor 下一页游标；当前页不足请求上限时为空
 */
public record LedgerEntryPageDTO(List<LedgerEntryDTO> items, String nextCursor) {
    /** 固化为不可变列表。 */
    public LedgerEntryPageDTO {
        items = List.copyOf(items);
    }
}
