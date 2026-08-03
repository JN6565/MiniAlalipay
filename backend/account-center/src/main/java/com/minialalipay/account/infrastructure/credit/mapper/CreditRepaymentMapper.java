package com.minialalipay.account.infrastructure.credit.mapper;

import com.minialalipay.account.infrastructure.credit.po.CreditRepaymentPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

/**
 * 信用还款 Mapper，对应 {@code ledger_db.credit_repayment} 表。
 *
 * <p>提供还款记录的按 ID、草稿 ID 和交易 ID 查询、插入及状态更新能力。
 * 唯一键 repaymentDraftId 和 transactionId 保证还款幂等。</p>
 */
@Mapper
public interface CreditRepaymentMapper {

    /**
     * 根据还款 ID 查询还款记录。
     *
     * @param repaymentId 还款 ID
     * @return 还款记录 PO，未找到时返回 null
     */
    @Select("SELECT * FROM ledger_db.credit_repayment WHERE repayment_id = #{repaymentId}")
    CreditRepaymentPO findById(@Param("repaymentId") String repaymentId);

    /**
     * 根据还款草稿 ID 查询还款记录。
     *
     * @param repaymentDraftId 还款草稿 ID
     * @return 还款记录 PO，未找到时返回 null
     */
    @Select("SELECT * FROM ledger_db.credit_repayment "
            + "WHERE repayment_draft_id = #{repaymentDraftId}")
    CreditRepaymentPO findByRepaymentDraftId(@Param("repaymentDraftId") String repaymentDraftId);

    /**
     * 根据统一交易 ID 查询还款记录。
     *
     * @param transactionId 统一交易 ID
     * @return 还款记录 PO，未找到时返回 null
     */
    @Select("SELECT * FROM ledger_db.credit_repayment "
            + "WHERE transaction_id = #{transactionId}")
    CreditRepaymentPO findByTransactionId(@Param("transactionId") String transactionId);

    /**
     * 插入还款记录。
     *
     * @param po 还款记录持久化对象
     * @return 受影响行数
     */
    @Insert("INSERT INTO ledger_db.credit_repayment "
            + "(repayment_id, repayment_draft_id, transaction_id, credit_account_id, "
            + "amount_fen, status, created_at, updated_at) "
            + "VALUES (#{repaymentId}, #{repaymentDraftId}, #{transactionId}, #{creditAccountId}, "
            + "#{amountFen}, #{status}, #{createdAt}, #{updatedAt})")
    int insert(CreditRepaymentPO po);

    /**
     * 更新还款状态。
     *
     * @param repaymentId 还款 ID
     * @param status 目标状态
     * @param updatedAt 更新时间
     * @return 受影响行数
     */
    @Update("UPDATE ledger_db.credit_repayment "
            + "SET status = #{status}, updated_at = #{updatedAt} "
            + "WHERE repayment_id = #{repaymentId}")
    int updateStatus(
            @Param("repaymentId") String repaymentId,
            @Param("status") String status,
            @Param("updatedAt") Instant updatedAt);
}
