package com.minialalipay.account.interfaces.bankcard;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 查看完整卡号请求 API DTO。
 *
 * <p>采用 POST 请求体承载支付证明的原因：确认令牌属于敏感凭证，
 * 禁止出现在 URL（查询参数会进入访问日志与浏览器历史）；
 * 路径保持 REST 资源语义，动作由 POST 方法表达。</p>
 *
 * @param paymentProof 一次性支付证明，必须专以 BANK_CARD_NUMBER_VIEW 用途签发，
 *                     由用户中心网关路径 POST /api/v1/payment-password/proof 获取；
 *                     禁止写入日志、浏览器存储、埋点或 URL
 */
public record FullCardNumberRequest(
        @NotBlank(message = "支付证明不能为空")
        @Size(max = 256, message = "支付证明长度不合法")
        String paymentProof
) {
}
