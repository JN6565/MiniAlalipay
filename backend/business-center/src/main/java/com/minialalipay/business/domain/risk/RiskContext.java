package com.minialalipay.business.domain.risk;

import java.time.Instant;

/**
 * 受理前风控评估所需的订单与历史事实上下文。
 *
 * <p>规则引擎只依赖本上下文中的纯数据，不接触任何持久化端口；
 * 历史计数由应用层通过 {@link com.minialalipay.business.application.port.RiskHistoryPort} 汇总后传入。</p>
 *
 * @param subjectType 业务主体类型，与确认令牌的 {@link com.minialalipay.business.domain.confirmation.SubjectType} 一致
 * @param subjectId 业务主体标识（动态扫码订单或 C2C 来源订单号）
 * @param payerUserId 付款人用户标识，用于高频、重复特征和新对手规则
 * @param payeeAccountId 收款人账户标识，用于重复特征和新对手规则
 * @param amountFen 本次支付金额，单位分
 * @param recentPaymentCount 付款人在高频窗口内已受理的支付笔数
 * @param repeatedPaymentCount 付款人对同一收款账户、相同金额在重复窗口内的支付笔数
 * @param hasTradedWith 付款人历史上是否与该收款账户发生过交易
 * @param now 评估时间，保证规则窗口与决策落库使用同一时间基准
 */
public record RiskContext(
        String subjectType,
        String subjectId,
        String payerUserId,
        String payeeAccountId,
        long amountFen,
        int recentPaymentCount,
        int repeatedPaymentCount,
        boolean hasTradedWith,
        Instant now) {
}
