package com.minialalipay.business.application.qrpay;

import com.minialalipay.business.application.port.AccountDirectoryPort;
import com.minialalipay.business.application.port.PayeeAnalyticsStore;
import com.minialalipay.business.domain.transaction.BusinessErrorCode;
import com.minialalipay.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** C 端本人收款统计应用服务测试：范围换算与账户隔离。 */
class QrCollectionAnalyticsServiceTest {
    /** 上海时区 2026-08-05 20:00。 */
    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");

    @Test
    void 今日范围从上海当日零点开始聚合() {
        CapturingStore store = new CapturingStore();
        QrCollectionAnalyticsService service = service(store, "account-me");

        service.analytics("user-1", QrCollectionAnalyticsService.Range.TODAY);

        assertEquals(Instant.parse("2026-08-04T16:00:00Z"), store.lastSince);
        assertEquals("account-me", store.lastPayeeAccountId);
    }

    @Test
    void 本月范围从上海当月一号零点开始聚合() {
        CapturingStore store = new CapturingStore();
        QrCollectionAnalyticsService service = service(store, "account-me");

        service.analytics("user-1", QrCollectionAnalyticsService.Range.MONTH);

        assertEquals(Instant.parse("2026-07-31T16:00:00Z"), store.lastSince);
    }

    @Test
    void 账户非正常状态时拒绝统计() {
        CapturingStore store = new CapturingStore();
        QrCollectionAnalyticsService service = new QrCollectionAnalyticsService(store,
                userId -> new AccountDirectoryPort.AccountReference("account-frozen", userId, "FROZEN"),
                Clock.fixed(NOW, ZoneOffset.UTC));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.analytics("user-1", QrCollectionAnalyticsService.Range.TODAY));

        assertEquals(BusinessErrorCode.ACCOUNT_UNAVAILABLE, error.errorCode());
    }

    @Test
    void 聚合结果原样返回给调用方() {
        PayeeAnalyticsStore.PayeeAnalytics expected = new PayeeAnalyticsStore.PayeeAnalytics(2, 3, 400L, 100L, 300L,
                List.of(new PayeeAnalyticsStore.PaymentMethodStat("QR_PAY", 2, 400L)), NOW, NOW);
        CapturingStore store = new CapturingStore(expected);
        QrCollectionAnalyticsService service = service(store, "account-me");

        PayeeAnalyticsStore.PayeeAnalytics result = service.analytics("user-1", QrCollectionAnalyticsService.Range.MONTH);

        assertEquals(expected, result);
    }

    private static QrCollectionAnalyticsService service(CapturingStore store, String accountId) {
        return new QrCollectionAnalyticsService(store,
                userId -> new AccountDirectoryPort.AccountReference(accountId, userId, "ACTIVE"),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** 记录调用参数并返回预设结果的测试替身。 */
    private static final class CapturingStore implements PayeeAnalyticsStore {
        private String lastPayeeAccountId;
        private Instant lastSince;
        private final PayeeAnalytics result;

        private CapturingStore() { this(empty(NOW)); }
        private CapturingStore(PayeeAnalytics result) { this.result = result; }

        private static PayeeAnalytics empty(Instant now) {
            return new PayeeAnalytics(0, 0, 0, 0, 0, List.of(), now, now);
        }

        @Override public PayeeAnalytics analytics(String payeeAccountId, Instant since, Instant now) {
            lastPayeeAccountId = payeeAccountId;
            lastSince = since;
            return result;
        }
    }
}
