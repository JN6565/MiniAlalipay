package com.minialalipay.user.domain.user;

/**
 * 用户 ID 生成端口（接口）。
 *
 * <p>定义注册时成对生成 userId 和注册幂等键 registrationId 的能力，
 * 具体生成策略（数据库序列 + 安全随机数）由基础设施层实现。
 * 应用层通过此端口访问 ID 生成能力，不直接依赖基础设施实现。</p>
 */
public interface UserIdGeneratorPort {

    /**
     * 成对生成 userId 和 registrationId。
     *
     * <p>两者共享同一日程序列号，均为 26 位字符，
     * 通过前缀（USR/REG）区分类型。</p>
     *
     * @return 成对的 userId 和 registrationId
     */
    IdPair generatePair();

    /**
     * userId 和 registrationId 的成对生成结果。
     *
     * @param userId         26 位用户 ID（USR 前缀）
     * @param registrationId 26 位注册幂等键（REG 前缀）
     */
    record IdPair(String userId, String registrationId) {
    }
}
