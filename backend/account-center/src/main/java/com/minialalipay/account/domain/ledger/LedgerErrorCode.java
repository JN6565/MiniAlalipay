package com.minialalipay.account.domain.ledger;

import com.minialalipay.common.error.ErrorCode;

/** 账本领域对外错误码，取值必须与统一错误码契约完全一致。 */
public enum LedgerErrorCode implements ErrorCode {
    /** 同一凭证业务键对应的不可变分录或金额不同。 */
    IDEMPOTENCY_CONFLICT("IDEMPOTENCY_CONFLICT", "相同幂等键对应的请求参数不一致", 409);

    private final String code;
    private final String message;
    private final int httpStatus;

    LedgerErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int httpStatus() { return httpStatus; }
}
