package com.minialalipay.common.error;

/**
 * 跨服务共享的技术通用错误码。
 *
 * <p>用户、账户、交易等领域错误码必须在各自限界上下文定义，不得加入本枚举。</p>
 */
public enum CommonErrorCode implements ErrorCode {
    /** 请求处理成功。 */
    SUCCESS("OK", "成功", 200),

    /** 请求参数、格式或通用校验不合法。 */
    INVALID_REQUEST("COMMON_INVALID_REQUEST", "请求参数不合法", 400),

    /** 请求缺少有效身份认证。 */
    UNAUTHORIZED("COMMON_UNAUTHORIZED", "需要身份认证", 401),

    /** 当前主体已经认证，但没有访问目标资源的权限。 */
    FORBIDDEN("COMMON_FORBIDDEN", "无权访问该资源", 403),

    /** 请求的资源不存在，或为避免越权而按不存在处理。 */
    NOT_FOUND("COMMON_NOT_FOUND", "资源不存在", 404),

    /** 未映射的服务端内部异常，响应不得暴露堆栈和敏感信息。 */
    INTERNAL_ERROR("COMMON_INTERNAL_ERROR", "系统内部错误", 500);

    private final String code;
    private final String message;
    private final int httpStatus;

    CommonErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    /**
     * 返回稳定的机器可读错误码。
     *
     * @return 错误码字符串
     */
    @Override
    public String code() {
        return code;
    }

    /**
     * 返回面向调用方的错误说明。
     *
     * @return 错误说明
     */
    @Override
    public String message() {
        return message;
    }

    /**
     * 返回该错误的标准 HTTP 状态码。
     *
     * @return HTTP 状态码
     */
    @Override
    public int httpStatus() {
        return httpStatus;
    }
}
