package com.minialalipay.user.domain.credential;

/**
 * 密码哈希端口（接口）。
 *
 * <p>定义密码哈希和验证的能力，由基础设施层实现。
 * 应用层通过此端口访问密码哈希功能，不直接依赖基础设施层实现。</p>
 *
 * <p>职责边界：
 * <ul>
 *   <li>只负责密码哈希和验证</li>
 *   <li>不关心具体实现（BCrypt、Argon2id 等）</li>
 *   <li>不包含业务逻辑</li>
 * </ul>
 * </p>
 */
public interface PasswordHasherPort {

    /**
     * 对密码进行哈希。
     *
     * @param password 原始密码
     * @return 哈希后的密码
     */
    String hashPassword(String password);

    /**
     * 验证密码是否匹配。
     *
     * @param password       原始密码
     * @param hashedPassword 哈希后的密码
     * @return 如果匹配则返回 true
     */
    boolean matches(String password, String hashedPassword);
}
