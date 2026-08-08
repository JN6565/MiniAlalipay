package com.minialalipay.account.application.bankcard.dto;

import java.time.Instant;

/**
 * 银行卡注册响应 DTO。
 *
 * @param registrationId 注册记录 ID
 * @param bankCode 银行编码
 * @param bankName 银行名称
 * @param cardType 卡类型
 * @param cardNumber 生成的完整卡号（仅注册时返回，绑定后不再返回）
 * @param cardBin BIN 前 6 位
 * @param cardLast4 卡号后 4 位
 * @param status 注册状态：REGISTERED/BOUND
 * @param createdAt 注册时间
 */
public record RegisteredCardDTO(
        String registrationId,
        String bankCode,
        String bankName,
        String cardType,
        String cardNumber,
        String cardBin,
        String cardLast4,
        String status,
        Instant createdAt
) {
}
