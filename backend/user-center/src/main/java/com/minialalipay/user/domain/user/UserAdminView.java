package com.minialalipay.user.domain.user;

import java.time.Instant;

/**
 * B 端用户管理只读投影。
 *
 * <p>由 {@link UserRepository#findAdminPage} 返回，在 {@link User} 主体之上补充
 * 同限界上下文（{@code user_db}）的登录锁定截止时间，供运营核对账号可用性；
 * 不携带密码、支付密码、手机号等敏感原值。</p>
 */
public record UserAdminView(User user, Instant loginLockedUntil) {
}
