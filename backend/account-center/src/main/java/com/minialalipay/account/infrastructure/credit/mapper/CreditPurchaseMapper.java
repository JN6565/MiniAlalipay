package com.minialalipay.account.infrastructure.credit.mapper;

import com.minialalipay.account.infrastructure.credit.po.CreditPurchasePO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 信用消费明细 Mapper，对应 {@code ledger_db.credit_purchase} 表。
 *
 * <p>提供信用消费明细的按 ID、交易 ID、账户 ID 与出账状态查询、插入及乐观锁 CAS 更新能力。
 * 唯一键 creditTransactionId 保证一笔支付不能重复进入账单。</p>
 */
@Mapper
public interface CreditPurchaseMapper {

    /**
     * 根据消费 ID 查询消费明细。
     *
     * @param purchaseId 消费明细 ID
     * @return 消费明细 PO，未找到时返回 null
     */
    @Select("SELECT * FROM ledger_db.credit_purchase WHERE purchase_id = #{purchaseId}")
    CreditPurchasePO findById(@Param("purchaseId") String purchaseId);

    /**
     * 根据信用交易 ID 查询消费明细。
     *
     * @param creditTransactionId 信用支付交易 ID
     * @return 消费明细 PO，未找到时返回 null
     */
    @Select("SELECT * FROM ledger_db.credit_purchase "
            + "WHERE credit_transaction_id = #{creditTransactionId}")
    CreditPurchasePO findByCreditTransactionId(@Param("creditTransactionId") String creditTransactionId);

    /**
     * 根据信用账户 ID 和出账状态查询消费列表。
     *
     * @param creditAccountId 信用账户 ID
     * @param billingStatus 出账状态
     * @return 消费明细 PO 列表
     */
    @Select("SELECT * FROM ledger_db.credit_purchase "
            + "WHERE credit_account_id = #{creditAccountId} "
            + "AND billing_status = #{billingStatus}")
    List<CreditPurchasePO> findByCreditAccountIdAndBillingStatus(
            @Param("creditAccountId") String creditAccountId,
            @Param("billingStatus") String billingStatus);

    /**
     * 插入消费明细。
     *
     * @param po 消费明细持久化对象
     * @return 受影响行数
     */
    @Insert("INSERT INTO ledger_db.credit_purchase "
            + "(purchase_id, credit_transaction_id, credit_account_id, qr_order_id, "
            + "merchant_account_id, amount_fen, repaid_fen, refunded_fen, outstanding_fen, "
            + "refund_transaction_id, billing_status, version, occurred_at, updated_at) "
            + "VALUES (#{purchaseId}, #{creditTransactionId}, #{creditAccountId}, #{qrOrderId}, "
            + "#{merchantAccountId}, #{amountFen}, #{repaidFen}, #{refundedFen}, #{outstandingFen}, "
            + "#{refundTransactionId}, #{billingStatus}, #{version}, #{occurredAt}, #{updatedAt})")
    int insert(CreditPurchasePO po);

    /**
     * 乐观锁 CAS 更新消费明细。
     *
     * <p>仅当数据库中的 version 与传入的 version 一致时才更新，
     * 更新成功后 version 自增 1。</p>
     *
     * @param po 包含最新字段值及当前版本号的消费明细 PO
     * @return 受影响行数，0 表示版本号不匹配（并发冲突）
     */
    @Update("UPDATE ledger_db.credit_purchase "
            + "SET repaid_fen = #{repaidFen}, refunded_fen = #{refundedFen}, "
            + "outstanding_fen = #{outstandingFen}, refund_transaction_id = #{refundTransactionId}, "
            + "billing_status = #{billingStatus}, version = version + 1, updated_at = #{updatedAt} "
            + "WHERE purchase_id = #{purchaseId} AND version = #{version}")
    int updateByCas(CreditPurchasePO po);
}
