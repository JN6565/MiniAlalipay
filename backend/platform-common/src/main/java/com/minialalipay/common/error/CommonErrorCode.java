package com.minialalipay.common.error;

/**
 * 跨服务共享的技术通用错误码。
 *
 * <p>用户、账户、交易等领域错误码必须在各自限界上下文定义，不得加入本枚举。</p>
 */
public enum CommonErrorCode implements ErrorCode {
    /** 请求处理成功。 */
    SUCCESS("OK", "OK"),

    /** 请求参数、格式或通用校验不合法。 */
    INVALID_REQUEST("COMMON_INVALID_REQUEST", "Invalid request"),

    /** 请求缺少有效身份认证。 */
    UNAUTHORIZED("COMMON_UNAUTHORIZED", "Authentication required"),

    /** 当前主体已经认证，但没有访问目标资源的权限。 */
    FORBIDDEN("COMMON_FORBIDDEN", "Access denied"),

    /** 请求的资源不存在，或为避免越权而按不存在处理。 */
    NOT_FOUND("COMMON_NOT_FOUND", "Resource not found"),

    /** 未映射的服务端内部异常，响应不得暴露堆栈和敏感信息。 */
    INTERNAL_ERROR("COMMON_INTERNAL_ERROR", "Internal server error");

    private final String code;
    private final String message;

    CommonErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
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
}
