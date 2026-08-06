package com.minialalipay.user.domain.auth;

import com.minialalipay.common.error.ErrorCode;

/**
 * 用户中心领域错误码枚举。
 *
 * <p>本枚举实现 {@link ErrorCode} 接口，定义用户中心所有业务错误码。
 * 错误码的 code、中文 message 和 httpStatus 必须与
 * contracts/error-codes/error-codes.yaml 完全一致，禁止在代码中
 * 使用未登记的错误码或自定义消息。</p>
 *
 * <p>使用场景：
 * <ul>
 *   <li>登录失败时抛出 LOGIN_INVALID 或 LOGIN_LOCKED</li>
 *   <li>注册时账户号碰撞抛出 ACCOUNT_NUMBER_EXISTS</li>
 *   <li>密码不符合规则抛出 PASSWORD_POLICY_VIOLATION</li>
 *   <li>会话无效时抛出 AUTH_REQUIRED</li>
 * </ul>
 * </p>
 *
 * @see com.minialalipay.common.error.ErrorCode
 * @see com.minialalipay.common.error.BusinessException
 */
public enum UserErrorCode implements ErrorCode {

    /**
     * 会话无效或尚未登录。
     */
    AUTH_REQUIRED("AUTH_REQUIRED", "会话无效或尚未登录", 401),

    /**
     * 登录名或登录密码错误。
     */
    LOGIN_INVALID("LOGIN_INVALID", "登录名或登录密码错误", 401),

    /**
     * 登录已被临时锁定。
     */
    LOGIN_LOCKED("LOGIN_LOCKED", "登录已被临时锁定", 429),

    /**
     * 登录名已存在。
     */
    ACCOUNT_NUMBER_EXISTS("ACCOUNT_NUMBER_EXISTS", "账户号已存在", 409),

    /** 手机号已经完成注册。 */
    PHONE_NUMBER_EXISTS("PHONE_NUMBER_EXISTS", "手机号已注册", 409),

    /**
     * 密码不符合安全规则。
     */
    PASSWORD_POLICY_VIOLATION("PASSWORD_POLICY_VIOLATION", "密码不符合安全规则", 422),

    /**
     * 注册开户处理中。
     */
    REGISTRATION_PROCESSING("REGISTRATION_PROCESSING", "注册开户处理中", 202),

    /**
     * 支付密码已经设置。
     */
    PAYMENT_PASSWORD_ALREADY_SET("PAYMENT_PASSWORD_ALREADY_SET", "支付密码已经设置", 409),

    /**
     * 支付密码错误。
     */
    PAY_PASSWORD_INVALID("PAY_PASSWORD_INVALID", "支付密码错误", 422),

    /**
     * 支付密码校验已被临时锁定。
     */
    PAYMENT_LOCKED("PAYMENT_LOCKED", "支付密码校验已被临时锁定", 429),

    /**
     * 支付密码证明无效或已过期。
     */
    PAYMENT_PROOF_INVALID("PAYMENT_PROOF_INVALID", "支付密码证明无效或已过期", 409),

    /**
     * 收款用户不存在。
     */
    PAYEE_NOT_FOUND("PAYEE_NOT_FOUND", "收款用户不存在", 404),

    /**
     * 常用收款人不存在。
     */
    CONTACT_NOT_FOUND("CONTACT_NOT_FOUND", "常用收款人不存在", 404),

    /**
     * 资源版本已经变化。
     */
    VERSION_CONFLICT("VERSION_CONFLICT", "资源版本已经变化", 409);

    private final String code;
    private final String message;
    private final int httpStatus;

    UserErrorCode(String code, String message, int httpStatus) {
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
