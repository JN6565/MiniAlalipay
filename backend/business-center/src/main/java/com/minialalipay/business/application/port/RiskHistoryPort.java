package com.minialalipay.business.application.port;

import java.time.Instant;

/**
 * 受理前风控所需的历史支付事实查询端口。
 *
 * <p>只读取统一交易投影（fund_transaction），用于高频、重复特征和新交易对手规则；
 * 不得通过本端口修改交易、余额或账本事实。付款人以统一交易的发起人标识识别。</p>
 */
public interface RiskHistoryPort {

    /** 付款人在指定时间之后已受理的支付笔数（含转账、扫码、C2C 等全部资金来源）。 */
    int countRecentPayments(String payerUserId, Instant since);

    /** 付款人对同一收款账户、相同金额在指定时间之后的支付笔数，用于重复特征判定。 */
    int countRepeatedPayments(String payerUserId, String payeeAccountId, long amountFen, Instant since);

    /** 付款人历史上是否与该收款账户发生过交易，用于新交易对手判定。 */
    boolean hasTradedWith(String payerUserId, String payeeAccountId);
}
