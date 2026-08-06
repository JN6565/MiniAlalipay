package com.minialalipay.business.application.qrpay;

import com.minialalipay.business.application.port.AccountDirectoryPort;
import com.minialalipay.business.application.port.PayeeAnalyticsStore;
import com.minialalipay.business.domain.transaction.BusinessErrorCode;
import com.minialalipay.common.error.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * C 端本人动态扫码收款统计应用服务。
 *
 * <p>从登录会话派生本人收款账户，按统计范围（今日/本月）从统一交易投影聚合
 * 收款金额、订单、支付方式、退款与净收款。只读本人数据，按收款账户隔离，
 * 不创建资金事实。</p>
 */
@Service
public class QrCollectionAnalyticsService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final PayeeAnalyticsStore store;
    private final AccountDirectoryPort accounts;
    private final Clock clock;

    /** 创建收款统计应用服务。 */
    @Autowired
    public QrCollectionAnalyticsService(PayeeAnalyticsStore store, AccountDirectoryPort accounts) {
        this(store, accounts, Clock.systemUTC());
    }

    /** 供测试注入固定时钟构造。 */
    public QrCollectionAnalyticsService(PayeeAnalyticsStore store, AccountDirectoryPort accounts, Clock clock) {
        this.store = store;
        this.accounts = accounts;
        this.clock = clock;
    }

    /** 查询当前登录用户本人的动态扫码收款统计摘要。 */
    public PayeeAnalyticsStore.PayeeAnalytics analytics(String userId, Range range) {
        var account = accounts.resolvePersonalAccount(userId);
        if (!"ACTIVE".equals(account.status())) throw new BusinessException(BusinessErrorCode.ACCOUNT_UNAVAILABLE);
        Instant now = clock.instant();
        ZonedDateTime zoned = now.atZone(BUSINESS_ZONE);
        Instant since = switch (range) {
            case TODAY -> zoned.toLocalDate().atStartOfDay(BUSINESS_ZONE).toInstant();
            case MONTH -> zoned.withDayOfMonth(1).toLocalDate().atStartOfDay(BUSINESS_ZONE).toInstant();
        };
        return store.analytics(account.accountId(), since, now);
    }

    /** 收款统计范围；无效范围由接口层以 RANGE_NOT_SUPPORTED 拒绝。 */
    public enum Range { TODAY, MONTH }
}
