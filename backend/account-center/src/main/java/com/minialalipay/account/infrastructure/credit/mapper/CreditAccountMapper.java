package com.minialalipay.account.infrastructure.credit.mapper;

import com.minialalipay.account.infrastructure.credit.po.CreditAccountPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 信用账户 Mapper，对应 {@code account_db.credit_account} 表。
 *
 * <p>提供信用账户的按用户 ID 和账户 ID 查询、开户插入及乐观锁 CAS 更新能力。
 * UPDATE 语句通过 {@code WHERE version = #{version}} 实现乐观并发控制。</p>
 */
@Mapper
public interface CreditAccountMapper {

    /**
     * 根据用户 ID 查询信用账户。
     *
     * @param userId 用户 ID
     * @return 信用账户 PO，未找到时返回 null
     */
    @Select("SELECT * FROM account_db.credit_account WHERE user_id = #{userId}")
    CreditAccountPO findByUserId(@Param("userId") String userId);

    /**
     * 根据信用账户 ID 查询信用账户。
     *
     * @param creditAccountId 信用账户 ID
     * @return 信用账户 PO，未找到时返回 null
     */
    @Select("SELECT * FROM account_db.credit_account WHERE credit_account_id = #{creditAccountId}")
    CreditAccountPO findByCreditAccountId(@Param("creditAccountId") String creditAccountId);

    /**
     * 按状态查询信用账户列表。
     *
     * @param status 账户状态（ACTIVE / SUSPENDED / CLOSED）
     * @return 匹配状态的信用账户 PO 列表
     */
    @Select("SELECT * FROM account_db.credit_account WHERE status = #{status}")
    List<CreditAccountPO> findByStatus(@Param("status") String status);

    /**
     * 插入信用账户记录。
     *
     * @param po 信用账户持久化对象
     * @return 受影响行数
     */
    @Insert("INSERT INTO account_db.credit_account "
            + "(credit_account_id, user_id, total_limit_fen, used_fen, frozen_fen, "
            + "status, suspend_reason, version, created_at, updated_at) "
            + "VALUES (#{creditAccountId}, #{userId}, #{totalLimitFen}, #{usedFen}, #{frozenFen}, "
            + "#{status}, #{suspendReason}, #{version}, #{createdAt}, #{updatedAt})")
    int insert(CreditAccountPO po);

    /**
     * 乐观锁 CAS 更新信用账户。
     *
     * <p>仅当数据库中的 version 与传入的 version 一致时才更新，
     * 更新成功后 version 自增 1。</p>
     *
     * @param po 包含最新字段值及当前版本号的信用账户 PO
     * @return 受影响行数，0 表示版本号不匹配（并发冲突）
     */
    @Update("UPDATE account_db.credit_account "
            + "SET used_fen = #{usedFen}, frozen_fen = #{frozenFen}, "
            + "status = #{status}, suspend_reason = #{suspendReason}, "
            + "version = version + 1, updated_at = #{updatedAt} "
            + "WHERE credit_account_id = #{creditAccountId} AND version = #{version}")
    int updateByCas(CreditAccountPO po);
}
