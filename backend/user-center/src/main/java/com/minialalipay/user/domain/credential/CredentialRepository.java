package com.minialalipay.user.domain.credential;

import java.util.Optional;

/**
 * 凭证仓储接口。
 *
 * <p>定义用户凭证的持久化操作，由基础设施层实现。
 * 仓储接口属于领域层，不依赖具体的持久化技术（如 MyBatis、JPA）。</p>
 *
 * <p>职责边界：
 * <ul>
 *   <li>只负责凭证（{@code credential} 表）的持久化</li>
 *   <li>不负责用户主体（{@code app_user} 表）的持久化，用户由 {@link com.minialalipay.user.domain.user.UserRepository} 管理</li>
 *   <li>凭证和用户通过 {@code user_id} 关联，但仓储各自独立</li>
 * </ul>
 * </p>
 *
 * <p>实现要求：
 * <ul>
 *   <li>所有查询方法返回 {@link Optional}，避免返回 null</li>
 *   <li>保存操作必须保证原子性（在同一事务内）</li>
 *   <li>更新操作必须使用乐观锁（version）保证并发安全</li>
 *   <li>凭证表与用户表在同一事务内保存，保证注册的原子性</li>
 * </ul>
 * </p>
 *
 * @see Credential 凭证实体
 * @see com.minialalipay.user.infrastructure.persistence.CredentialRepositoryImpl 仓储实现
 */
public interface CredentialRepository {

    /**
     * 根据用户 ID 查询凭证。
     *
     * @param userId 用户 ID（ULID 格式，26 位字符）
     * @return 凭证对象，如果不存在则返回 empty
     */
    Optional<Credential> findByUserId(String userId);

    /**
     * 保存新凭证。
     *
     * <p>注册时调用，插入新凭证记录。
     * 必须与用户记录在同一事务内保存，保证注册的原子性。</p>
     *
     * @param credential 凭证实体
     */
    void save(Credential credential);

    /**
     * 更新凭证信息。
     *
     * <p>更新密码、失败次数或锁定状态时调用。
     * 必须使用乐观锁（version）保证并发安全。</p>
     *
     * @param credential 凭证实体（包含更新后的数据）
     * @throws com.minialalipay.common.error.BusinessException 如果版本冲突
     */
    void update(Credential credential);
}
