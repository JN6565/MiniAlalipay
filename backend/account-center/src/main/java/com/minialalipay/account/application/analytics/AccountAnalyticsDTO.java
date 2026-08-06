package com.minialalipay.account.application.analytics;

import java.util.List;

/** 个人资产分析返回模型，金额统一使用分。 */
public record AccountAnalyticsDTO(String range, String definitionVersion, long incomeFen, long expenseFen,
                                  List<TrendPoint> trend, List<Payee> topPayees,
                                  long balanceFlowFen, long creditFlowFen) {
    public record TrendPoint(String date, long incomeFen, long expenseFen) { }
    public record Payee(String userId, String nickname, long totalFen) { }
}
