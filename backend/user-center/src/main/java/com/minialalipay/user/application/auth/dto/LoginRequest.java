package com.minialalipay.user.application.auth.dto;

/**
 * 登录请求 DTO。
 *
 * <p>应用层的登录请求数据传输对象，用于 {@link com.minialalipay.user.application.auth.AuthService#login} 方法。
 * DTO 对象只在应用层使用，不暴露到领域层或接口层。</p>
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@code loginName} - 登录名（必填，用于查询用户）</li>
 *   <li>{@code loginPassword} - 登录密码（必填，用于验证身份）</li>
 * </ul>
 * </p>
 *
 * <p>校验规则：
 * <ul>
 *   <li>登录名不能为空</li>
 *   <li>密码不能为空</li>
 * </ul>
 * </p>
 *
 * @see com.minialalipay.user.application.auth.AuthService#login
 */
public record LoginRequest(
        /**
         * 登录名（必填）。
         * <p>用于查询用户，登录时规范化处理（转小写、去空格）。</p>
         */
        String loginIdentifier,

        /**
         * 登录密码（必填）。
         * <p>用于验证身份，与存储的 BCrypt 哈希值比较。</p>
         */
        String loginPassword
) {
}
