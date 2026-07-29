package com.minialalipay.common.error;

public enum CommonErrorCode implements ErrorCode {
    SUCCESS("OK", "OK"),
    INVALID_REQUEST("COMMON_INVALID_REQUEST", "Invalid request"),
    UNAUTHORIZED("COMMON_UNAUTHORIZED", "Authentication required"),
    FORBIDDEN("COMMON_FORBIDDEN", "Access denied"),
    NOT_FOUND("COMMON_NOT_FOUND", "Resource not found"),
    INTERNAL_ERROR("COMMON_INTERNAL_ERROR", "Internal server error");

    private final String code;
    private final String message;

    CommonErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
