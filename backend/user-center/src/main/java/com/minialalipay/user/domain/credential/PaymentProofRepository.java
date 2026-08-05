package com.minialalipay.user.domain.credential;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

/**
 * 支付密码证明仓储接口。
 *
 * <p>定义支付证明的持久化操作，由基础设施层实现。</p>
 *
 * <p>职责边界：
 * <ul>
 *   <li>只负责支付证明的 CRUD 操作</li>
 *   <li>不包含业务逻辑（由领域模型和应用服务负责）</li>
 *   <li>不关心具体数据库实现（MySQL、Redis 等）</li>
 * </ul>
 * </p>
 *
 * @see PaymentProof 支付证明实体
 */
public interface PaymentProofRepository {

    /**
     * 保存支付证明。
     *
     * @param proof 支付证明实体
     */
    void save(PaymentProof proof);

    /**
     * 更新支付证明。
     *
     * @param proof 支付证明实体
     */
    void update(PaymentProof proof);

    /**
     * 根据证明 ID 查询支付证明。
     *
     * @param proofId 证明 ID
     * @return 支付证明（可能不存在）
     */
    Optional<PaymentProof> findById(String proofId);

    /**
     * 根据令牌摘要查询支付证明。
     *
     * <p>用于快速查找和防重放。</p>
     *
     * @param tokenDigest 令牌摘要（32 字节）
     * @return 支付证明（可能不存在）
     */
    Optional<PaymentProof> findByTokenDigest(byte[] tokenDigest);

    /**
     * 仅当证明仍为活动状态时原子标记为已消费。
     *
     * <p>条件更新用于保护一次性证明不被并发请求重复消费；调用方必须检查返回值，
     * 返回 {@code false} 表示证明已被其他事务消费或废弃。</p>
     *
     * @param proofId 证明 ID
     * @param consumedAt 消费时间
     * @return 是否成功完成状态转换
     */
    boolean consumeActive(String proofId, Instant consumedAt);

    /**
     * 查询用户的所有活动支付证明。
     *
     * <p>用于支付密码修改时批量废弃。</p>
     *
     * @param userId 用户 ID
     * @return 活动支付证明列表
     */
    List<PaymentProof> findActiveByUserId(String userId);

    /**
     * 废弃用户的所有活动支付证明。
     *
     * <p>支付密码修改时调用，将所有 ACTIVE 状态的证明转为 REVOKED。</p>
     *
     * @param userId 用户 ID
     * @return 废弃的证明数量
     */
    int revokeAllByUserId(String userId);
}
