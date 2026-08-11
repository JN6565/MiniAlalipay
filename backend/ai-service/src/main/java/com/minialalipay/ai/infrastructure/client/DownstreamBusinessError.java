package com.minialalipay.ai.infrastructure.client;

import com.minialalipay.common.error.ErrorCode;

/**
 * 下游服务业务错误码包装。
 *
 * <p>为什么需要它：{@link com.minialalipay.common.error.BusinessException} 的异常文案
 * 取自 {@link ErrorCode#message()}，而三中心返回的 4xx 业务错误（余额不足、金额超限、
 * 参数缺失等）携带各自的中文文案。若统一映射为 {@code TOOL_UNAVAILABLE}，用户看到的
 * 永远是"服务暂不可用"，无法自助纠正。此类把下游 ApiResponse 中的 code/message 原样
 * 包装为 ErrorCode，使异常文案即下游真实业务原因，经解释引擎透传给用户。</p>
 *
 * <p>边界：仅用于下游已对齐 {@code error-codes.yaml} 契约的业务错误透传，
 * 不新增对外错误码；5xx 与网络异常仍映射 {@code TOOL_UNAVAILABLE}。</p>
 *
 * @param downstreamCode 下游稳定错误码（如 INSUFFICIENT_BALANCE）
 * @param downstreamMessage 下游中文错误说明，直接展示给用户
 * @param downstreamHttpStatus 下游 HTTP 状态码
 */
public record DownstreamBusinessError(
        String downstreamCode,
        String downstreamMessage,
        int downstreamHttpStatus
) implements ErrorCode {

    @Override
    public String code() {
        return downstreamCode;
    }

    @Override
    public String message() {
        return downstreamMessage;
    }

    @Override
    public int httpStatus() {
        return downstreamHttpStatus;
    }
}
