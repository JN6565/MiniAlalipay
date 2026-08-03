package com.minialalipay.account.infrastructure.credit.mapper;

import com.minialalipay.account.infrastructure.credit.po.CreditReceivablePO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 信用应收汇总 Mapper，对应 {@code ledger_db.credit_receivable} 表。
 *
 * <p>提供信用应收汇总的按信用账户 ID 查询、插入及乐观锁 CAS 更新能力。
 * 应收分布在 ledger_db，区分未出账、已出账及逾期金额。</p>
 */
@Mapper
public interface CreditReceivableMapper {

    /**
     * 根据信用账户 ID 查询应收汇总。
     *
     * @param creditAccountId 信用账户 ID
     * @return 应收汇总 PO，未找到时返回 null
     */
    @Select("SELECT * FROM ledger_db.credit_receivable "
            + "WHERE credit_account_id = #{creditAccountId}")
    CreditReceivablePO findByCreditAccountId(@Param("creditAccountId") String creditAccountId);

    /**
     * 插入应收汇总记录。
     *
     * @param po 应收汇总持久化对象
     * @return 受影响行数
     */
    @Insert("INSERT INTO ledger_db.credit_receivable "
            + "(credit_account_id, unbilled_fen, billed_fen, overdue_fen, version, updated_at) "
            + "VALUES (#{creditAccountId}, #{unbilledFen}, #{billedFen}, #{overdueFen}, "
            + "#{version}, #{updatedAt})")
    int insert(CreditReceivablePO po);

    /**
     * 乐观锁 CAS 更新应收汇总。
     *
     * <p>仅当数据库中的 version 与传入的 version 一致时才更新，
     * 更新成功后 version 自增 1。</p>
     *
     * @param po 包含最新字段值及当前版本号的应收汇总 PO
     * @return 受影响行数，0 表示版本号不匹配（并发冲突）
     */
    @Update("UPDATE ledger_db.credit_receivable "
            + "SET unbilled_fen = #{unbilledFen}, billed_fen = #{billedFen}, "
            + "overdue_fen = #{overdueFen}, version = version + 1, updated_at = #{updatedAt} "
            + "WHERE credit_account_id = #{creditAccountId} AND version = #{version}")
    int updateByCas(CreditReceivablePO po);
}
