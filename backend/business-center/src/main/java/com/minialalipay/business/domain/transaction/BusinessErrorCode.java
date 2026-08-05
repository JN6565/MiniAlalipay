package com.minialalipay.business.domain.transaction;

import com.minialalipay.common.error.ErrorCode;

/** 业务中心普通转账、确认和统一交易对外错误码。 */
public enum BusinessErrorCode implements ErrorCode {
    /** 收款用户不存在。 */ PAYEE_NOT_FOUND("PAYEE_NOT_FOUND", "收款用户不存在", 404),
    /** 禁止付款给本人。 */ SELF_PAYMENT_FORBIDDEN("SELF_PAYMENT_FORBIDDEN", "不允许向本人账户付款", 422),
    /** 账户非正常状态。 */ ACCOUNT_UNAVAILABLE("ACCOUNT_UNAVAILABLE", "账户当前不可用", 422),
    /** 转账金额不在产品边界内。 */ AMOUNT_OUT_OF_RANGE("AMOUNT_OUT_OF_RANGE", "金额超出允许范围", 422),
    /** 草稿不存在或不属于当前用户。 */ DRAFT_NOT_FOUND("DRAFT_NOT_FOUND", "交易草稿不存在", 404),
    /** 草稿状态不允许编辑或提交。 */ DRAFT_NOT_EDITABLE("DRAFT_NOT_EDITABLE", "交易草稿当前不可编辑", 409),
    /** CAS 版本已变化。 */ VERSION_CONFLICT("VERSION_CONFLICT", "资源版本已经变化", 409),
    /** 支付密码证明失效。 */ PAYMENT_PROOF_INVALID("PAYMENT_PROOF_INVALID", "支付密码证明无效或已过期", 409),
    /** 确认已过期。 */ CONFIRMATION_EXPIRED("CONFIRMATION_EXPIRED", "确认令牌已过期", 409),
    /** 确认主体或用户不匹配。 */ CONFIRMATION_MISMATCH("CONFIRMATION_MISMATCH", "确认内容与当前业务对象不一致", 409),
    /** 草稿字段或密码版本已变化。 */ CONFIRMATION_STALE("CONFIRMATION_STALE", "业务对象已变化，请重新确认", 409),
    /** 同一幂等键用于不同请求。 */ IDEMPOTENCY_CONFLICT("IDEMPOTENCY_CONFLICT", "相同幂等键对应的请求参数不一致", 409),
    /** 交易不存在或不属于当前用户。 */ TRANSACTION_NOT_FOUND("TRANSACTION_NOT_FOUND", "交易不存在", 404),
    /** 回执尚未达到确定终态。 */ RECEIPT_NOT_READY("RECEIPT_NOT_READY", "交易尚未形成确定回执", 409);

    private final String code; private final String message; private final int httpStatus;
    BusinessErrorCode(String code, String message, int httpStatus) {
        this.code = code; this.message = message; this.httpStatus = httpStatus;
    }
    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int httpStatus() { return httpStatus; }
}
