package com.minialalipay.account.interfaces.bankcard;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 银行卡注册请求 DTO。
 *
 * @param bankCode 银行编码，如 ICBC、CMB
 * @param holderName 持卡人姓名
 * @param idCard 身份证号
 * @param phone 预留手机号
 */
public record RegisterBankCardRequest(
        @NotBlank(message = "银行编码不能为空")
        String bankCode,

        @NotBlank(message = "持卡人姓名不能为空")
        @Size(max = 32, message = "持卡人姓名过长")
        String holderName,

        @NotBlank(message = "身份证号不能为空")
        @Pattern(regexp = "^\\d{17}[\\dXx]$", message = "身份证号格式不正确")
        String idCard,

        @NotBlank(message = "预留手机号不能为空")
        @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
        String phone
) {
}
