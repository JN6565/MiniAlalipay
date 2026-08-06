package com.minialalipay.business.interfaces.qrpay;

import com.minialalipay.business.application.port.AccountDirectoryPort;
import com.minialalipay.business.application.port.PayeeAnalyticsStore;
import com.minialalipay.business.application.qrpay.QrCollectionAnalyticsService;
import com.minialalipay.business.interfaces.error.BusinessCenterExceptionHandler;
import com.minialalipay.common.error.CommonExceptionMapper;
import com.minialalipay.common.trace.RequestIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** C 端本人收款统计 Controller 切片测试：范围校验与金额口径。 */
class QrCollectionAnalyticsControllerTest {
    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        RequestIdGenerator requestIds = new RequestIdGenerator();
        PayeeAnalyticsStore store = (payeeAccountId, since, now) -> new PayeeAnalyticsStore.PayeeAnalytics(
                2, 3, 400L, 100L, 300L,
                List.of(new PayeeAnalyticsStore.PaymentMethodStat("QR_PAY", 2, 400L)), since, now);
        AccountDirectoryPort accounts = userId -> new AccountDirectoryPort.AccountReference("account-me", userId, "ACTIVE");
        QrCollectionAnalyticsService service = new QrCollectionAnalyticsService(store, accounts, Clock.fixed(NOW, ZoneOffset.UTC));
        mvc = MockMvcBuilders.standaloneSetup(new QrCollectionAnalyticsController(service, requestIds))
                .setControllerAdvice(new BusinessCenterExceptionHandler(new CommonExceptionMapper(), requestIds)).build();
    }

    @Test
    void 查询今日统计返回净收款与支付方式分组() throws Exception {
        mvc.perform(get("/api/v1/qr-pay/me/qr-collection-analytics?range=today")
                        .header("X-User-Id", "user-1").header("X-Request-Id", "req-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-stats"))
                .andExpect(jsonPath("$.data.orderCount").value(2))
                .andExpect(jsonPath("$.data.grossAmountFen").value(400))
                .andExpect(jsonPath("$.data.refundAmountFen").value(100))
                .andExpect(jsonPath("$.data.netAmountFen").value(300))
                .andExpect(jsonPath("$.data.range").value("today"))
                .andExpect(jsonPath("$.data.byPaymentMethod[0].businessType").value("QR_PAY"));
    }

    @Test
    void 不支持的统计范围返回契约错误码() throws Exception {
        mvc.perform(get("/api/v1/qr-pay/me/qr-collection-analytics?range=week")
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RANGE_NOT_SUPPORTED"));
    }
}
