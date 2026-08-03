package com.minialalipay.common.api;

import com.minialalipay.common.error.ErrorCode;

/**
 * 对外 API 的统一响应外壳。
 *
 * @param code 稳定的机器可读结果码
 * @param message 面向调用方的中文结果说明
 * @param requestId 请求编号，用于定位一次网关请求
 * @param traceId 链路编号，用于关联跨服务 Trace；未建立链路时可为空
 * @param data 业务响应数据，失败时通常为空
 * @param <T> 业务响应数据类型
 */
public record ApiResponse<T>(String code, String message, String requestId, String traceId, T data) {

    public static <T> ApiResponse<T> success(T data, String requestId, String traceId) {
        return new ApiResponse<>("OK", "成功", requestId, traceId, data);
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode, String requestId, String traceId) {
        return new ApiResponse<>(errorCode.code(), errorCode.message(), requestId, traceId, null);
    }

    /**
     * 创建带显式安全详情的失败响应，调用方不得传入领域对象或敏感字段。
     */
    public static <T> ApiResponse<T> failure(
            ErrorCode errorCode,
            String requestId,
            String traceId,
            T safeData
    ) {
        return new ApiResponse<>(errorCode.code(), errorCode.message(), requestId, traceId, safeData);
    }

    /**
     * 兼容尚未接入 Trace 的基础模块，接入链路追踪后应使用三参数方法。
     */
    public static <T> ApiResponse<T> success(T data, String requestId) {
        return success(data, requestId, null);
    }

    /**
     * 兼容尚未接入 Trace 的基础模块，接入链路追踪后应使用三参数方法。
     */
    public static <T> ApiResponse<T> failure(ErrorCode errorCode, String requestId) {
        return failure(errorCode, requestId, null);
    }
}
