package com.minialalipay.user.infrastructure.security;

import com.minialalipay.user.domain.credential.PasswordHasherPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 密码哈希工具类。
 *
 * <p>使用 BCrypt 算法对密码进行强哈希，不存储明文密码。
 * BCrypt 是一种自适应哈希算法，计算成本可调，能够抵抗暴力破解攻击。</p>
 *
 * <p>安全规则：
 * <ul>
 *   <li>登录密码和支付密码使用相同的哈希算法</li>
 *   <li>每次哈希生成不同的盐值，相同密码的哈希结果不同</li>
 *   <li>哈希结果包含算法版本、成本因子、盐值和哈希值</li>
 *   <li>验证时自动提取盐值重新计算哈希，比较结果是否一致</li>
 * </ul>
 * </p>
 *
 * <p>使用示例：
 * <pre>{@code
 * // 注册时哈希密码
 * String hashedPassword = passwordHasher.hashPassword("myPassword123");
 *
 * // 登录时验证密码
 * boolean matches = passwordHasher.matches("myPassword123", hashedPassword);
 * }</pre>
 * </p>
 *
 * @see org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
 */
@Component
public class PasswordHasher implements PasswordHasherPort {

    /**
     * BCrypt 密码编码器。
     *
     * <p>使用默认的成本因子（10），即 2^10 = 1024 次迭代。
     * 成本因子越高，计算越慢，安全性越高，但用户体验越差。</p>
     */
    private final BCryptPasswordEncoder encoder;

    /**
     * 构造函数，初始化 BCrypt 编码器。
     */
    public PasswordHasher() {
        this.encoder = new BCryptPasswordEncoder();
    }

    /**
     * 对密码进行哈希。
     *
     * <p>注册时调用，将明文密码转换为 BCrypt 哈希值。
     * 每次调用生成不同的盐值，相同密码的哈希结果不同。</p>
     *
     * @param rawPassword 明文密码
     * @return BCrypt 哈希值（包含算法版本、成本因子、盐值和哈希值）
     * @throws IllegalArgumentException 如果密码为空
     */
    public String hashPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        return encoder.encode(rawPassword);
    }

    /**
     * 验证密码是否匹配。
     *
     * <p>登录时调用，比较明文密码与哈希值是否匹配。
     * 自动提取盐值重新计算哈希，比较结果是否一致。</p>
     *
     * @param rawPassword    明文密码
     * @param hashedPassword BCrypt 哈希值
     * @return 如果密码匹配则返回 true
     * @throws IllegalArgumentException 如果密码或哈希值为空
     */
    public boolean matches(String rawPassword, String hashedPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (hashedPassword == null || hashedPassword.isBlank()) {
            throw new IllegalArgumentException("哈希值不能为空");
        }
        return encoder.matches(rawPassword, hashedPassword);
    }
}
