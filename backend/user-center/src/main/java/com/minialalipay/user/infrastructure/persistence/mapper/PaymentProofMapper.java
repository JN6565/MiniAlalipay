package com.minialalipay.user.infrastructure.persistence.mapper;

import com.minialalipay.user.infrastructure.persistence.po.PaymentProofPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

/**
 * 支付密码证明 MyBatis Mapper 接口。
 *
 * <p>定义支付证明表 {@code user_db.payment_proof} 的数据库操作。</p>
 *
 * <p>命名规范：
 * <ul>
 *   <li>insert: 插入新记录</li>
 *   <li>update: 更新已有记录</li>
 *   <li>selectBy*: 按条件查询</li>
 *   <li>updateStatus*: 更新状态</li>
 * </ul>
 * </p>
 */
@Mapper
public interface PaymentProofMapper {

    /**
     * 插入支付证明。
     *
     * @param proof 支付证明持久化对象
     * @return 影响行数
     */
    int insert(PaymentProofPO proof);

    /**
     * 更新支付证明。
     *
     * @param proof 支付证明持久化对象
     * @return 影响行数
     */
    int update(PaymentProofPO proof);

    /**
     * 根据证明 ID 查询。
     *
     * @param proofId 证明 ID
     * @return 支付证明（可能不存在）
     */
    Optional<PaymentProofPO> selectByProofId(@Param("proofId") String proofId);

    /**
     * 根据令牌摘要查询。
     *
     * @param tokenDigest 令牌摘要（32 字节）
     * @return 支付证明（可能不存在）
     */
    Optional<PaymentProofPO> selectByTokenDigest(@Param("tokenDigest") byte[] tokenDigest);

    /**
     * 使用状态条件原子消费活动证明，防止并发确认重复使用同一原始令牌。
     *
     * @param proofId 证明 ID
     * @param consumedAt 消费时间
     * @return 影响行数，只允许为 0 或 1
     */
    int consumeActive(@Param("proofId") String proofId, @Param("consumedAt") Instant consumedAt);

    /**
     * 查询用户的所有活动支付证明。
     *
     * @param userId 用户 ID
     * @return 活动支付证明列表
     */
    List<PaymentProofPO> selectActiveByUserId(@Param("userId") String userId);

    /**
     * 废弃用户的所有活动支付证明。
     *
     * <p>将所有 ACTIVE 状态的证明转为 REVOKED。</p>
     *
     * @param userId 用户 ID
     * @return 影响行数
     */
    int revokeAllByUserId(@Param("userId") String userId);
}
