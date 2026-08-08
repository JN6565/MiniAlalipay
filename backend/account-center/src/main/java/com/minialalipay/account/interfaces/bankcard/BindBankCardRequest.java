package com.minialalipay.account.interfaces.bankcard;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 绑卡请求 API DTO。
 *
 * <p>完整卡号、身份证号与手机号明文仅在本请求中出现一次，
 * 禁止写入日志、埋点或任何持久化存储；服务端处理后只保留掩码。</p>
 *
 * @param cardNumber 完整银行卡号，16 至 19 位数字，允许空格分组
 * @param holderName 持卡人姓名
 * @param idCard 身份证号，18 位，末位可为 X/x
 * @param phone 预留手机号，11 位
 */
public record BindBankCardRequest(
        @NotBlank(message = "银行卡号不能为空")
        @Size(min = 16, max = 26, message = "银行卡号长度不合法")
        String cardNumber,

        @NotBlank(message = "持卡人姓名不能为空")
        @Size(max = 32, message = "持卡人姓名过长")
        String holderName,

        @NotBlank(message = "身份证号不能为空")
        String idCard,

        @NotBlank(message = "预留手机号不能为空")
        String phone
) {
}
