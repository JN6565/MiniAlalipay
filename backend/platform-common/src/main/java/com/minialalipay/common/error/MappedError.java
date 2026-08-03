package com.minialalipay.common.error;

import com.minialalipay.common.api.ApiResponse;

/**
 * 框架无关的异常转换结果，由 MVC、WebFlux 或其他接口适配器写入响应。
 *
 * @param httpStatus HTTP 状态码
 * @param body 安全的统一错误响应
 */
public record MappedError(int httpStatus, ApiResponse<?> body) {
}
