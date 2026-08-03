package com.minialalipay.account.domain.credit;

import com.minialalipay.common.error.ErrorCode;

/**
 * 信用领域错误码枚举。
 *
 * <p>code、message 和 httpStatus 必须与 {@code contracts/error-codes/error-codes.yaml} 完全一致。
 * 禁止把用户、账户、交易、信用或 AI 领域错误码集中放入 platform-common。</p>
 */
public enum CreditErrorCode implements ErrorCode {
    /** 信用账户不存在。 */
    CREDIT_ACCOUNT_NOT_FOUND(
            "CREDIT_ACCOUNT_NOT_FOUND", "信用账户不存在", 404
    ),

    /** Mini 花呗当前不可用（未开通/关闭/暂停）。 */
    CREDIT_NOT_AVAILABLE(
            "CREDIT_NOT_AVAILABLE", "Mini 花呗当前不可用", 422
    ),

    /** 可用信用额度不足。 */
    CREDIT_LIMIT_INSUFFICIENT(
            "CREDIT_LIMIT_INSUFFICIENT", "可用信用额度不足", 422
    ),

    /** 存在逾期账单，暂不可信用支付。 */
    CREDIT_OVERDUE(
            "CREDIT_OVERDUE", "存在逾期账单，暂不可信用支付", 422
    ),

    /** 信用账单不存在。 */
    BILL_NOT_FOUND(
            "BILL_NOT_FOUND", "信用账单不存在", 404
    ),

    /** 还款金额不合法（超过余额/应收或小于1分）。 */
    REPAYMENT_AMOUNT_INVALID(
            "REPAYMENT_AMOUNT_INVALID", "还款金额不合法", 422
    ),

    /** 还款记录不存在。 */
    REPAYMENT_NOT_FOUND(
            "REPAYMENT_NOT_FOUND", "还款记录不存在", 404
    );

    private final String code;
    private final String message;
    private final int httpStatus;

    CreditErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }
}
