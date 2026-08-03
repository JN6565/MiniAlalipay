package com.minialalipay.common.error;

/**
 * 对外错误码的最小契约，领域服务可在自己的限界上下文中实现。
 */
public interface ErrorCode {

    /** @return 稳定的机器可读错误码 */
    String code();

    /** @return 面向调用方的中文错误说明 */
    String message();

    /** @return 对应的 HTTP 状态码 */
    int httpStatus();
}
