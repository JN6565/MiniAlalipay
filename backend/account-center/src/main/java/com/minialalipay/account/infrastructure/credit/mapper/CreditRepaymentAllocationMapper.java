package com.minialalipay.account.infrastructure.credit.mapper;

import com.minialalipay.account.infrastructure.credit.po.CreditRepaymentAllocationPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 信用还款分配 Mapper，对应 {@code ledger_db.credit_repayment_allocation} 表。
 *
 * <p>提供还款分配计划的按还款 ID 查询和插入能力。
 * 分配按固定优先级顺序固化：逾期账单 → 已出账账单 → 未出账消费。</p>
 */
@Mapper
public interface CreditRepaymentAllocationMapper {

    /**
     * 根据还款 ID 查询分配计划列表。
     *
     * @param repaymentId 还款 ID
     * @return 分配计划 PO 列表
     */
    @Select("SELECT * FROM ledger_db.credit_repayment_allocation "
            + "WHERE repayment_id = #{repaymentId}")
    List<CreditRepaymentAllocationPO> findByRepaymentId(@Param("repaymentId") String repaymentId);

    /**
     * 插入还款分配记录。
     *
     * @param po 还款分配持久化对象
     * @return 受影响行数
     */
    @Insert("INSERT INTO ledger_db.credit_repayment_allocation "
            + "(repayment_id, sequence_no, target_type, target_id, amount_fen, created_at) "
            + "VALUES (#{repaymentId}, #{sequenceNo}, #{targetType}, #{targetId}, "
            + "#{amountFen}, #{createdAt})")
    int insert(CreditRepaymentAllocationPO po);
}
