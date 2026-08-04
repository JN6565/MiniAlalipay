package com.minialalipay.account.application.account.dto;

/**
 * 本人账户实时余额摘要。
 *
 * @param accountId 账户 ID
 * @param accountType 账户类型
 * @param currency 币种，当前为 CNY
 * @param status 账户状态
 * @param availableFen 可用金额，单位分
 * @param frozenFen 冻结金额，单位分
 * @param totalFen 总金额，单位分
 * @param version 余额版本
 */
public record AccountSummaryDTO(String accountId, String accountType, String currency, String status,
                                long availableFen, long frozenFen, long totalFen, long version) {
}
