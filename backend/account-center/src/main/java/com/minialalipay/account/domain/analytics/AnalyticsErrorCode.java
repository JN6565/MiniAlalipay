package com.minialalipay.account.domain.analytics;

import com.minialalipay.common.error.ErrorCode;

/** 资产分析领域错误码，必须与统一错误码契约保持一致。 */
public enum AnalyticsErrorCode implements ErrorCode {
    /** 请求的统计时间范围不在 7 天、30 天或按月支持集合内。 */
    RANGE_NOT_SUPPORTED("RANGE_NOT_SUPPORTED", "不支持该统计范围", 400);

    private final String code;
    private final String message;
    private final int httpStatus;

    AnalyticsErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int httpStatus() { return httpStatus; }
}
