package com.minialalipay.common.api;

import com.minialalipay.common.error.ErrorCode;

public record ApiResponse<T>(String code, String message, String requestId, T data) {

    public static <T> ApiResponse<T> success(T data, String requestId) {
        return new ApiResponse<>("OK", "OK", requestId, data);
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode, String requestId) {
        return new ApiResponse<>(errorCode.code(), errorCode.message(), requestId, null);
    }
}
