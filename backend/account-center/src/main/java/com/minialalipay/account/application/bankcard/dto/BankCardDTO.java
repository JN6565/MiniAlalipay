package com.minialalipay.account.application.bankcard.dto;

import java.time.Instant;

/**
 * 银行卡对外展示 DTO：只包含掩码字段与 BIN 派生信息，
 * 完整卡号、证件号、手机号明文不允许出现在本类型中。
 *
 * @param cardId 银行卡 ID
 * @param bankCode 银行编码，如 ICBC、CMB
 * @param bankName 银行名称
 * @param cardType 卡类型：DEBIT 借记卡，CREDIT 信用卡
 * @param cardLast4 卡号后 4 位，前端拼接展示为 **** **** **** 1234
 * @param holderMasked 持卡人姓名掩码
 * @param idCardMasked 身份证号掩码
 * @param phoneMasked 预留手机号掩码
 * @param balanceFen 虚拟余额（分），与账户余额独立
 * @param isDefault 是否默认卡，同一用户至多一张
 * @param status 绑定状态：ACTIVE 已绑定，UNBOUND 已解绑
 * @param boundAt 绑定时间
 */
public record BankCardDTO(
        String cardId,
        String bankCode,
        String bankName,
        String cardType,
        String cardLast4,
        String holderMasked,
        String idCardMasked,
        String phoneMasked,
        long balanceFen,
        boolean isDefault,
        String status,
        Instant boundAt
) {
}
