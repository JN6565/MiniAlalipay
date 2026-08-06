package com.minialalipay.business.application.port;

import java.time.Instant;
import java.util.List;

/**
 * 收款人扫码收款分析投影查询端口。
 *
 * <p>只从统一交易主单（fund_transaction）聚合本人收款事实，按收款账户隔离；
 * 只统计确定终态（SUCCESS）并按来源订单去重，不触碰余额、冻结或账本分录。</p>
 */
public interface PayeeAnalyticsStore {

    /** 按收款账户统计指定时间区间内的动态扫码收款摘要。 */
    PayeeAnalytics analytics(String payeeAccountId, Instant since, Instant now);

    /**
     * 收款统计结果；netAmountFen 由收款总额减去退款总额得到。
     *
     * @param orderCount 去重来源订单数（按 source_order_id 去重，避免一单多次入账重复计数）
     * @param transactionCount 成功交易笔数，与去重订单数对比用于对账
     * @param grossAmountFen 收款总额，单位分
     * @param refundAmountFen 退款总额，单位分
     * @param netAmountFen 净收款，单位分
     * @param byPaymentMethod 按支付方式（QR_PAY/CREDIT_PAY）分组的订单数与金额
     */
    record PayeeAnalytics(long orderCount, long transactionCount, long grossAmountFen,
                          long refundAmountFen, long netAmountFen,
                          List<PaymentMethodStat> byPaymentMethod, Instant since, Instant now) { }

    /** 支付方式统计；businessType 与统一交易业务类型一致。 */
    record PaymentMethodStat(String businessType, long orderCount, long amountFen) { }
}
