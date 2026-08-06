package com.minialalipay.business.interfaces.recharge;

import com.minialalipay.business.application.recharge.RechargeApplicationService;
import com.minialalipay.business.domain.recharge.RechargeOrder;
import com.minialalipay.business.domain.recharge.RechargeOrderStatus;
import com.minialalipay.common.idempotency.IdempotencyKeyValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 充值渠道结果内部回调 Controller 切片测试。 */
class InternalRechargeControllerTest {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new InternalRechargeController(new FakeService())).build();
    }

    @Test
    void 渠道成功回调返回处理中订单引用() throws Exception {
        mvc.perform(post("/internal/v1/recharges/recharge-1/channel-result")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"success\":true,\"traceId\":\"" + "0".repeat(32) + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.transactionId").value("tx-1"));
    }

    @Test
    void 渠道拒绝回调返回拒绝终态() throws Exception {
        mvc.perform(post("/internal/v1/recharges/recharge-1/channel-result")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"success\":false,\"rejectReasonCode\":\"CHANNEL_TIMEOUT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void 缺少成功标志被拒绝() throws Exception {
        mvc.perform(post("/internal/v1/recharges/recharge-1/channel-result")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rejectReasonCode\":\"CHANNEL_TIMEOUT\"}"))
                .andExpect(status().isBadRequest());
    }

    /** 固定返回处理中或拒绝结果的充值服务替身。 */
    private static final class FakeService extends RechargeApplicationService {
        FakeService() {
            super(null, null, null, new IdempotencyKeyValidator(), null, null);
        }

        @Override public RechargeOrder onChannelResult(String id, boolean success, String rejectReasonCode, String traceId) {
            return new RechargeOrder(id, "user-1", "account-1", 100L, LocalDate.of(2026, 8, 5),
                    "policy-1", 1L, success ? RechargeOrderStatus.PROCESSING : RechargeOrderStatus.REJECTED,
                    success ? "tx-1" : null, success ? null : "CHANNEL_TIMEOUT", 1L, NOW, NOW);
        }
    }
}
