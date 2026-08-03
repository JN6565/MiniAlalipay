package com.minialalipay.account.infrastructure.credit.mapper;

import com.minialalipay.account.infrastructure.credit.po.CreditRepaymentAllocationDetailPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 信用还款分配明细 Mapper，对应 {@code ledger_db.credit_repayment_allocation_detail} 表。
 *
 * <p>提供还款分配明细的按还款 ID 查询和插入能力。
 * 每条明细逐笔指向消费及可选账单，父分配金额必须等于其明细合计。</p>
 */
@Mapper
public interface CreditRepaymentAllocationDetailMapper {

    /**
     * 根据还款 ID 查询分配明细列表。
     *
     * @param repaymentId 还款 ID
     * @return 分配明细 PO 列表
     */
    @Select("SELECT * FROM ledger_db.credit_repayment_allocation_detail "
            + "WHERE repayment_id = #{repaymentId}")
    List<CreditRepaymentAllocationDetailPO> findByRepaymentId(@Param("repaymentId") String repaymentId);

    /**
     * 插入还款分配明细。
     *
     * @param po 还款分配明细持久化对象
     * @return 受影响行数
     */
    @Insert("INSERT INTO ledger_db.credit_repayment_allocation_detail "
            + "(repayment_id, sequence_no, detail_no, purchase_id, bill_id, amount_fen, created_at) "
            + "VALUES (#{repaymentId}, #{sequenceNo}, #{detailNo}, #{purchaseId}, "
            + "#{billId}, #{amountFen}, #{createdAt})")
    int insert(CreditRepaymentAllocationDetailPO po);
}
