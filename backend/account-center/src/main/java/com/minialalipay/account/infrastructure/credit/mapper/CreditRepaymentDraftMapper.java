package com.minialalipay.account.infrastructure.credit.mapper;

import com.minialalipay.account.infrastructure.credit.po.CreditRepaymentDraftPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

/**
 * 信用还款草稿 Mapper，对应 {@code account_db.credit_repayment_draft} 表。
 *
 * <p>提供还款草稿的按 ID 查询、插入及乐观锁 CAS 状态更新能力。
 * 草稿包含还款分配方案快照与哈希校验值，超过有效期后自动失效。</p>
 */
@Mapper
public interface CreditRepaymentDraftMapper {

    /**
     * 根据还款草稿 ID 查询草稿。
     *
     * @param repaymentDraftId 还款草稿 ID
     * @return 还款草稿 PO，未找到时返回 null
     */
    @Select("SELECT * FROM account_db.credit_repayment_draft "
            + "WHERE repayment_draft_id = #{repaymentDraftId}")
    CreditRepaymentDraftPO findById(@Param("repaymentDraftId") String repaymentDraftId);

    /**
     * 插入还款草稿。
     *
     * @param po 还款草稿持久化对象
     * @return 受影响行数
     */
    @Insert("INSERT INTO account_db.credit_repayment_draft "
            + "(repayment_draft_id, user_id, credit_account_id, payer_account_id, "
            + "amount_fen, allocation_snapshot, allocation_hash, status, version, "
            + "expires_at, created_at, updated_at) "
            + "VALUES (#{repaymentDraftId}, #{userId}, #{creditAccountId}, #{payerAccountId}, "
            + "#{amountFen}, #{allocationSnapshot}, #{allocationHash}, #{status}, #{version}, "
            + "#{expiresAt}, #{createdAt}, #{updatedAt})")
    int insert(CreditRepaymentDraftPO po);

    /**
     * 乐观锁 CAS 更新草稿状态。
     *
     * <p>仅当数据库中的 version 与传入的 version 一致时才更新，
     * 更新成功后 version 自增 1。</p>
     *
     * @param repaymentDraftId 还款草稿 ID
     * @param status 目标状态
     * @param updatedAt 更新时间
     * @param version 当前版本号
     * @return 受影响行数，0 表示版本号不匹配（并发冲突）
     */
    @Update("UPDATE account_db.credit_repayment_draft "
            + "SET status = #{status}, version = version + 1, updated_at = #{updatedAt} "
            + "WHERE repayment_draft_id = #{repaymentDraftId} AND version = #{version}")
    int updateStatus(
            @Param("repaymentDraftId") String repaymentDraftId,
            @Param("status") String status,
            @Param("updatedAt") Instant updatedAt,
            @Param("version") long version);
}
