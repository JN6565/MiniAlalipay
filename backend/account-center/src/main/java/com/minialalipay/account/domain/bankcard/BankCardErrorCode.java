package com.minialalipay.account.domain.bankcard;

import com.minialalipay.common.error.ErrorCode;

/**
 * 银行卡领域对外错误码，取值必须与统一错误码契约 error-codes.yaml 完全一致。
 */
public enum BankCardErrorCode implements ErrorCode {
    /** 卡号未通过 Luhn 校验、长度不合法或 BIN 字典无法识别发卡行。 */
    BANK_CARD_INVALID("BANK_CARD_INVALID", "银行卡号无效或暂不支持", 422),
    /** 持卡人姓名、身份证号或预留手机号格式校验未通过（模拟四要素校验）。 */
    BANK_CARD_HOLDER_INVALID("BANK_CARD_HOLDER_INVALID", "持卡人信息校验未通过", 422),
    /** 同一用户对同一 BIN+尾号的卡已存在 ACTIVE 绑定，禁止重复绑卡。 */
    BANK_CARD_ALREADY_BOUND("BANK_CARD_ALREADY_BOUND", "该银行卡已绑定", 409),
    /** 银行卡不存在或不属于当前会话用户（不暴露他人资源是否存在）。 */
    BANK_CARD_NOT_FOUND("BANK_CARD_NOT_FOUND", "银行卡不存在", 404),
    /** 银行卡已解绑（终态），禁止再次设默认或解绑。 */
    BANK_CARD_ALREADY_UNBOUND("BANK_CARD_ALREADY_UNBOUND", "银行卡已解绑", 409),
    /** 用户绑定银行卡数量已达上限（10 张）。 */
    BANK_CARD_LIMIT_EXCEEDED("BANK_CARD_LIMIT_EXCEEDED", "绑定银行卡数量已达上限", 422),
    /** 用户尚未绑定身份信息，无法进行银行卡注册或绑定。 */
    IDENTITY_NOT_BOUND("IDENTITY_NOT_BOUND", "请先绑定身份信息", 422),
    /** 银行卡注册记录不存在或卡号与注册信息不匹配。 */
    REGISTRATION_NOT_FOUND("REGISTRATION_NOT_FOUND", "银行卡注册记录不存在或卡号不匹配", 404),
    /** 绑卡三要素与注册记录或用户存储身份不匹配。 */
    IDENTITY_MISMATCH("IDENTITY_MISMATCH", "持卡人信息与注册记录或用户身份不匹配", 422),
    /** 银行卡余额不足，无法完成提现或支付扣减。 */
    BANK_CARD_INSUFFICIENT_BALANCE("BANK_CARD_INSUFFICIENT_BALANCE", "银行卡余额不足", 422),
    /** 日累计充值金额已超过限额（50000.00 元）。 */
    BANK_CARD_DAILY_RECHARGE_LIMIT_EXCEEDED("BANK_CARD_DAILY_RECHARGE_LIMIT_EXCEEDED", "日累计充值限额已用完", 422),
    /** 日累计提现金额已超过限额（50000.00 元）。 */
    BANK_CARD_DAILY_WITHDRAW_LIMIT_EXCEEDED("BANK_CARD_DAILY_WITHDRAW_LIMIT_EXCEEDED", "日累计提现限额已用完", 422),
    /** 充值金额不在允许范围内（0.01-50000.00 元）。 */
    BANK_CARD_RECHARGE_AMOUNT_INVALID("BANK_CARD_RECHARGE_AMOUNT_INVALID", "充值金额不在允许范围内", 400),
    /** 提现金额不在允许范围内（0.01-50000.00 元）。 */
    BANK_CARD_WITHDRAW_AMOUNT_INVALID("BANK_CARD_WITHDRAW_AMOUNT_INVALID", "提现金额不在允许范围内", 400),
    /** 银行卡有余额未清零，禁止解绑。 */
    BANK_CARD_HAS_BALANCE("BANK_CARD_HAS_BALANCE", "请先提现银行卡余额后再解绑", 422);

    private final String code;
    private final String message;
    private final int httpStatus;

    BankCardErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int httpStatus() { return httpStatus; }
}
