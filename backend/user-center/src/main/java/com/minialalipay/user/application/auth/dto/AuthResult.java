package com.minialalipay.user.application.auth.dto;

/**
 * 认证结果 DTO。
 *
 * <p>应用层的认证结果数据传输对象，用于 {@link com.minialalipay.user.application.auth.AuthService#register}
 * 和 {@link com.minialalipay.user.application.auth.AuthService#login} 方法的返回值。
 * DTO 对象只在应用层使用，不暴露到领域层或接口层。</p>
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@code accessToken} - 会话令牌（用于后续请求的身份验证）</li>
 *   <li>{@code userId} - 用户 ID（用于标识用户）</li>
 *   <li>{@code nickname} - 昵称（用于前端展示）</li>
 *   <li>{@code status} - 用户状态（用于前端判断用户是否可以使用系统功能）</li>
 * </ul>
 * </p>
 *
 * <p>安全说明：
 * <ul>
 *   <li>会话令牌使用 UUID 生成，保证唯一性和不可预测性</li>
 *   <li>会话令牌存储在 Redis 中，TTL 为 24 小时</li>
 *   <li>会话令牌通过 HttpOnly Cookie 传递给前端，防止 XSS 攻击窃取</li>
 *   <li>不返回密码哈希、失败次数等敏感信息</li>
 * </ul>
 * </p>
 *
 * @see com.minialalipay.user.application.auth.AuthService#register
 * @see com.minialalipay.user.application.auth.AuthService#login
 */
public record AuthResult(
        /**
         * 会话令牌。
         * <p>用于后续请求的身份验证，存储在 Redis 中，TTL 为 24 小时。
         * 前端应将此令牌保存在 HttpOnly Cookie 中，防止 XSS 攻击窃取。</p>
         */
        String accessToken,

        /**
         * 用户 ID。
         * <p>用于标识用户，跨模块引用用户的稳定标识。
         * 用户 ID 在整个系统生命周期内不变。</p>
         */
        String userId,

        /** 系统生成的账户号，可用于后续登录。 */
        String accountNumber,

        /**
         * 昵称。
         * <p>用于前端展示，可重复的展示名称和模糊搜索条件。</p>
         */
        String nickname,

        /**
         * 用户状态。
         * <p>用于前端判断用户是否可以使用系统功能。
         * 取值：PROVISIONING / ACTIVE / DISABLED</p>
         */
        String status
) {
}
