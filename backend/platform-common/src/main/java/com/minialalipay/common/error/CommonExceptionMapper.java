package com.minialalipay.common.error;

import com.minialalipay.common.api.ApiResponse;

/**
 * 将跨服务通用异常转换为不泄露内部细节的响应描述。
 */
public final class CommonExceptionMapper {

    /**
     * 转换业务异常或未知异常，未知异常统一隐藏原始消息。
     *
     * @param throwable 待转换异常
     * @param requestId 请求编号
     * @param traceId 链路编号
     * @return 框架无关的错误响应描述
     */
    public MappedError map(Throwable throwable, String requestId, String traceId) {
        if (throwable instanceof BusinessException businessException) {
            ErrorCode errorCode = businessException.errorCode();
            ApiResponse<?> body = businessException.safeDetails().isEmpty()
                    ? ApiResponse.failure(errorCode, requestId, traceId)
                    : ApiResponse.failure(errorCode, requestId, traceId, businessException.safeDetails());
            return new MappedError(errorCode.httpStatus(), body);
        }
        ErrorCode errorCode = CommonErrorCode.INTERNAL_ERROR;
        return new MappedError(
                errorCode.httpStatus(),
                ApiResponse.failure(errorCode, requestId, traceId)
        );
    }
}
