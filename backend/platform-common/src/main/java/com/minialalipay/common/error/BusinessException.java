package com.minialalipay.common.error;

import java.util.Map;

/**
 * 携带稳定错误码的业务异常，不允许使用异常消息替代错误码契约。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> safeDetails;

    /**
     * 创建业务异常。
     *
     * @param errorCode 已登记的错误码
     */
    public BusinessException(ErrorCode errorCode) {
        this(errorCode, Map.of());
    }

    /**
     * 创建带安全冲突详情的业务异常。详情只允许使用契约声明的标量、列表或映射。
     *
     * @param errorCode 已登记的错误码
     * @param safeDetails 可对外返回的安全详情
     */
    public BusinessException(ErrorCode errorCode, Map<String, ?> safeDetails) {
        super(errorCode.message());
        this.errorCode = errorCode;
        this.safeDetails = Map.copyOf(safeDetails);
    }

    /** @return 已登记的错误码 */
    public ErrorCode errorCode() {
        return errorCode;
    }

    /** @return 不可变的安全冲突详情 */
    public Map<String, Object> safeDetails() {
        return safeDetails;
    }
}
