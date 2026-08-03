package com.minialalipay.account.infrastructure.credit.mapper;

import com.minialalipay.account.infrastructure.credit.po.CreditFreezePO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

/**
 * 信用额度冻结 Mapper，对应 {@code account_db.credit_freeze} 表。
 *
 * <p>提供冻结记录的按交易 ID 和账户 ID 查询、插入及乐观锁 CAS 状态更新能力。
 * 唯一键 (transactionId, creditAccountId) 保证同一交易同一账户只有一条冻结记录。</p>
 */
@Mapper
public interface CreditFreezeMapper {

    /**
     * 根据交易 ID 和信用账户 ID 查询冻结记录。
     *
     * @param transactionId 统一交易 ID
     * @param creditAccountId 信用账户 ID
     * @return 冻结记录 PO，未找到时返回 null
     */
    @Select("SELECT * FROM account_db.credit_freeze "
            + "WHERE transaction_id = #{transactionId} "
            + "AND credit_account_id = #{creditAccountId}")
    CreditFreezePO findByTransactionIdAndAccountId(
            @Param("transactionId") String transactionId,
            @Param("creditAccountId") String creditAccountId);

    /**
     * 插入冻结记录。
     *
     * @param po 冻结记录持久化对象
     * @return 受影响行数
     */
    @Insert("INSERT INTO account_db.credit_freeze "
            + "(credit_freeze_id, transaction_id, credit_account_id, amount_fen, "
            + "status, branch_xid, version, created_at, updated_at) "
            + "VALUES (#{creditFreezeId}, #{transactionId}, #{creditAccountId}, #{amountFen}, "
            + "#{status}, #{branchXid}, #{version}, #{createdAt}, #{updatedAt})")
    int insert(CreditFreezePO po);

    /**
     * 乐观锁 CAS 更新冻结记录状态。
     *
     * <p>仅当数据库中的 version 与传入的 version 一致时才更新，
     * 更新成功后 version 自增 1。</p>
     *
     * @param creditFreezeId 冻结记录 ID
     * @param status 目标状态
     * @param updatedAt 更新时间
     * @param version 当前版本号
     * @return 受影响行数，0 表示版本号不匹配（并发冲突）
     */
    @Update("UPDATE account_db.credit_freeze "
            + "SET status = #{status}, version = version + 1, updated_at = #{updatedAt} "
            + "WHERE credit_freeze_id = #{creditFreezeId} AND version = #{version}")
    int updateStatus(
            @Param("creditFreezeId") String creditFreezeId,
            @Param("status") String status,
            @Param("updatedAt") Instant updatedAt,
            @Param("version") long version);
}
