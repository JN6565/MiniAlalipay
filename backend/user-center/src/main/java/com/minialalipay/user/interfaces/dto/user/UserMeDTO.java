package com.minialalipay.user.interfaces.dto.user;

import java.time.Instant;

/**
 * 当前登录用户资料投影 DTO。
 *
 * <p>仅包含展示所需字段；真实姓名已在服务边界脱敏，完整手机号等敏感字段不下发。</p>
 *
 * @param userId         用户 ID
 * @param accountNumber  系统账户号
 * @param nickname       昵称
 * @param maskedPhone    脱敏手机号（如 138****9150）
 * @param maskedRealName 脱敏真实姓名（保留首字符，其余以星号遮蔽，如 吕*）
 * @param createdAt      注册时间
 */
public record UserMeDTO(
        String userId,
        String accountNumber,
        String nickname,
        String maskedPhone,
        String maskedRealName,
        Instant createdAt
) {
}
