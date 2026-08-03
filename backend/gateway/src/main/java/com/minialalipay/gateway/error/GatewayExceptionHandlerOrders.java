package com.minialalipay.gateway.error;

/**
 * 网关异常处理器顺序常量。
 *
 * <p>{@link GatewayExceptionHandler} 需要在所有 GlobalFilter 之后执行，
 * 以捕获 Filter 链中抛出的异常。因此使用较大的顺序值。</p>
 */
public final class GatewayExceptionHandlerOrders {

    private GatewayExceptionHandlerOrders() {
        throw new UnsupportedOperationException("工具类不可实例化");
    }

    /** 全局异常处理器顺序：在所有 Filter 之后。 */
    public static final int EXCEPTION_HANDLER = -1;
}
