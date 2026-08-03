package com.minialalipay.account.infrastructure.credit.mapper;

import com.minialalipay.account.infrastructure.credit.po.CreditBillItemPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 信用账单明细 Mapper，对应 {@code ledger_db.credit_bill_item} 表。
 *
 * <p>提供账单明细的按账单 ID 和消费 ID 查询、插入及更新能力。
 * 记录账单中每一笔消费明细的入账与还款分配情况。</p>
 */
@Mapper
public interface CreditBillItemMapper {

    /**
     * 根据账单 ID 查询账单明细列表。
     *
     * @param billId 账单 ID
     * @return 账单明细 PO 列表
     */
    @Select("SELECT * FROM ledger_db.credit_bill_item WHERE bill_id = #{billId}")
    List<CreditBillItemPO> findByBillId(@Param("billId") String billId);

    /**
     * 根据消费 ID 查询账单明细。
     *
     * @param purchaseId 消费明细 ID
     * @return 账单明细 PO，未找到时返回 null
     */
    @Select("SELECT * FROM ledger_db.credit_bill_item WHERE purchase_id = #{purchaseId}")
    CreditBillItemPO findByPurchaseId(@Param("purchaseId") String purchaseId);

    /**
     * 插入账单明细。
     *
     * @param po 账单明细持久化对象
     * @return 受影响行数
     */
    @Insert("INSERT INTO ledger_db.credit_bill_item "
            + "(bill_id, purchase_id, amount_fen, allocated_paid_fen, reversed_fen, "
            + "status, created_at, updated_at) "
            + "VALUES (#{billId}, #{purchaseId}, #{amountFen}, #{allocatedPaidFen}, "
            + "#{reversedFen}, #{status}, #{createdAt}, #{updatedAt})")
    int insert(CreditBillItemPO po);

    /**
     * 更新账单明细。
     *
     * @param po 包含最新字段值的账单明细 PO
     * @return 受影响行数
     */
    @Update("UPDATE ledger_db.credit_bill_item "
            + "SET allocated_paid_fen = #{allocatedPaidFen}, reversed_fen = #{reversedFen}, "
            + "status = #{status}, updated_at = #{updatedAt} "
            + "WHERE bill_id = #{billId} AND purchase_id = #{purchaseId}")
    int update(CreditBillItemPO po);
}
