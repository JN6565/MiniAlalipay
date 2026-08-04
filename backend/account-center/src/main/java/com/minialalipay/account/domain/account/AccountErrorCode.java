package com.minialalipay.account.domain.account;

import com.minialalipay.common.error.ErrorCode;

/** 账户与余额领域对外错误码，取值必须与统一错误码契约完全一致。 */
public enum AccountErrorCode implements ErrorCode {
    /** 账户非正常状态，当前资金操作不可执行。 */
    ACCOUNT_UNAVAILABLE("ACCOUNT_UNAVAILABLE", "账户当前不可用", 422),
    /** 可用余额小于请求扣款或冻结金额。 */
    INSUFFICIENT_BALANCE("INSUFFICIENT_BALANCE", "账户可用余额不足", 422),
    /** 账户、余额或冻结记录已被并发请求修改，调用方应重新读取后重试。 */
    VERSION_CONFLICT("VERSION_CONFLICT", "资源版本已经变化", 409),
    /** 同一开户幂等键被用于不同用户，禁止返回其他主体的账户事实。 */
    IDEMPOTENCY_CONFLICT("IDEMPOTENCY_CONFLICT", "相同幂等键对应的请求参数不一致", 409);

    private final String code;
    private final String message;
    private final int httpStatus;

    AccountErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int httpStatus() { return httpStatus; }
}
