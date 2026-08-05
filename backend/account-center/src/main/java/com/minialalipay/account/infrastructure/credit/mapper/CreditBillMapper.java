package com.minialalipay.account.infrastructure.credit.mapper;

import com.minialalipay.account.infrastructure.credit.po.CreditBillPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

/**
 * 信用月度账单 Mapper，对应 {@code ledger_db.credit_bill} 表。
 *
 * <p>提供账单的按 ID、账户 ID 与账期查询、插入及乐观锁 CAS 更新能力。
 * 唯一键 (creditAccountId, period) 保证每个账户每个账期只有一张账单。</p>
 */
@Mapper
public interface CreditBillMapper {

    /**
     * 根据账单 ID 查询账单。
     *
     * @param billId 账单 ID
     * @return 账单 PO，未找到时返回 null
     */
    @Select("SELECT * FROM ledger_db.credit_bill WHERE bill_id = #{billId}")
    CreditBillPO findById(@Param("billId") String billId);

    /**
     * 根据信用账户 ID 和账期查询账单。
     *
     * @param creditAccountId 信用账户 ID
     * @param period 账期，格式 yyyy-MM
     * @return 账单 PO，未找到时返回 null
     */
    @Select("SELECT * FROM ledger_db.credit_bill "
            + "WHERE credit_account_id = #{creditAccountId} AND period = #{period}")
    CreditBillPO findByCreditAccountIdAndPeriod(
            @Param("creditAccountId") String creditAccountId,
            @Param("period") String period);

    /**
     * 根据信用账户 ID 查询账单列表。
     *
     * @param creditAccountId 信用账户 ID
     * @return 账单 PO 列表
     */
    @Select("SELECT * FROM ledger_db.credit_bill "
            + "WHERE credit_account_id = #{creditAccountId}")
    List<CreditBillPO> findByCreditAccountId(@Param("creditAccountId") String creditAccountId);

    /**
     * 查询已到期但未标记为 OVERDUE 且仍有未还金额的账单。
     *
     * <p>查询条件：due_at &lt; #{cutoffTime} AND status IN ('OPEN', 'PARTIALLY_PAID') AND outstanding_fen > 0</p>
     *
     * @param cutoffTime 截止时间
     * @return 到期未还账单 PO 列表
     */
    @Select("SELECT * FROM ledger_db.credit_bill "
            + "WHERE due_at < #{cutoffTime} "
            + "AND status IN ('OPEN', 'PARTIALLY_PAID') "
            + "AND outstanding_fen > 0")
    List<CreditBillPO> findOverdueBills(@Param("cutoffTime") Instant cutoffTime);

    /**
     * 插入账单。
     *
     * @param po 账单持久化对象
     * @return 受影响行数
     */
    @Insert("INSERT INTO ledger_db.credit_bill "
            + "(bill_id, credit_account_id, period, statement_date, due_at, "
            + "total_fen, paid_fen, reversed_fen, outstanding_fen, status, version, "
            + "created_at, updated_at) "
            + "VALUES (#{billId}, #{creditAccountId}, #{period}, #{statementDate}, #{dueAt}, "
            + "#{totalFen}, #{paidFen}, #{reversedFen}, #{outstandingFen}, #{status}, #{version}, "
            + "#{createdAt}, #{updatedAt})")
    int insert(CreditBillPO po);

    /**
     * 乐观锁 CAS 更新账单。
     *
     * <p>仅当数据库中的 version 与传入的 version 一致时才更新，
     * 更新成功后 version 自增 1。</p>
     *
     * @param po 包含最新字段值及当前版本号的账单 PO
     * @return 受影响行数，0 表示版本号不匹配（并发冲突）
     */
    @Update("UPDATE ledger_db.credit_bill "
            + "SET paid_fen = #{paidFen}, reversed_fen = #{reversedFen}, "
            + "outstanding_fen = #{outstandingFen}, status = #{status}, "
            + "version = version + 1, updated_at = #{updatedAt} "
            + "WHERE bill_id = #{billId} AND version = #{version}")
    int updateByCas(CreditBillPO po);
}
