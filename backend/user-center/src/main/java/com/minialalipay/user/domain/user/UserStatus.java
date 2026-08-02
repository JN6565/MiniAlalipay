package com.minialalipay.user.domain.user;

/**
 * 用户主体状态，用于控制登录和用户级业务权限，不替代资金账户状态。
 */
public enum UserStatus {
    /** 注册信息已持久化但账户尚未完成幂等开户，禁止登录和发起业务。 */
    PROVISIONING,

    /** 用户正常，可以登录并在授权范围内使用系统。 */
    ACTIVE,

    /** 用户已被停用，禁止登录和发起新的业务。 */
    DISABLED;

    /**
     * 判断当前用户状态是否允许建立登录会话。
     *
     * @return 仅正常状态返回 {@code true}
     */
    public boolean allowsLogin() {
        return this == ACTIVE;
    }
}
