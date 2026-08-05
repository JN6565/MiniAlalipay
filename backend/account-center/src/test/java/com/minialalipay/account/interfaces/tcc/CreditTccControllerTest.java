package com.minialalipay.account.interfaces.tcc;

import com.minialalipay.account.application.credit.CreditRepayTccParticipant;
import com.minialalipay.account.application.credit.CreditTccParticipant;
import com.minialalipay.account.domain.credit.CreditFreeze;
import com.minialalipay.account.domain.tcc.TccBranchStatus;
import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.common.error.CommonExceptionMapper;
import com.minialalipay.common.error.MappedError;
import com.minialalipay.common.trace.RequestIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证信用支付、信用还款内部 TCC HTTP 契约能够准确路由到对应参与者。
 */
@WebMvcTest(CreditTccController.class)
class CreditTccControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CreditTccParticipant creditTccParticipant;

    @MockBean
    private CreditRepayTccParticipant creditRepayTccParticipant;

    @MockBean
    private RequestIdGenerator requestIdGenerator;

    @MockBean
    private CommonExceptionMapper commonExceptionMapper;

    @BeforeEach
    void setUpErrorMapping() {
        when(requestIdGenerator.resolve(nullable(String.class))).thenReturn("request-test");
        when(commonExceptionMapper.map(any(), nullable(String.class), nullable(String.class)))
                .thenReturn(new MappedError(
                        400,
                        new ApiResponse<>("COMMON_INVALID_REQUEST", "请求参数不合法",
                                "request-test", null, null)));
        when(creditTccParticipant.tryFreeze(
                eq("transaction-1"), eq("credit-account-1"), eq(12_000L),
                eq("xid-credit-pay-1"), any(Instant.class)))
                .thenReturn(new CreditFreeze(
                        "freeze-1", "transaction-1", "credit-account-1",
                        12_000L, "xid-credit-pay-1", Instant.parse("2026-08-05T08:00:00Z")));
        when(creditRepayTccParticipant.tryRepay(
                eq("transaction-2"), eq("account-1"), eq("credit-account-1"),
                eq(8_000L), eq("xid-credit-repay-1"), any(Instant.class)))
                .thenReturn(TccBranchStatus.TRIED);
    }

    @Test
    void routesCreditPayTryConfirmAndCancel() throws Exception {
        mockMvc.perform(post("/internal/v1/tcc/credit-pay/try")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(creditPayBaseRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRIED"));

        mockMvc.perform(post("/internal/v1/tcc/credit-pay/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "xid":"xid-credit-pay-1",
                                  "transactionId":"transaction-1",
                                  "creditAccountId":"credit-account-1",
                                  "amountFen":12000,
                                  "qrOrderId":"qr-order-1",
                                  "merchantAccountId":"merchant-account-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(post("/internal/v1/tcc/credit-pay/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(creditPayBaseRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(creditTccParticipant).tryFreeze(
                eq("transaction-1"), eq("credit-account-1"), eq(12_000L),
                eq("xid-credit-pay-1"), any(Instant.class));
        verify(creditTccParticipant).confirmFreeze(
                eq("transaction-1"), eq("credit-account-1"), eq(12_000L),
                eq("xid-credit-pay-1"), eq("qr-order-1"), eq("merchant-account-1"),
                any(Instant.class));
        verify(creditTccParticipant).cancelFreeze(
                eq("transaction-1"), eq("credit-account-1"), eq(12_000L),
                eq("xid-credit-pay-1"), any(Instant.class));
    }

    @Test
    void routesCreditRepayTryConfirmAndCancel() throws Exception {
        String request = """
                {
                  "xid":"xid-credit-repay-1",
                  "transactionId":"transaction-2",
                  "accountId":"account-1",
                  "creditAccountId":"credit-account-1",
                  "amountFen":8000
                }
                """;

        mockMvc.perform(post("/internal/v1/tcc/credit-repay/try")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRIED"));
        mockMvc.perform(post("/internal/v1/tcc/credit-repay/confirm")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        mockMvc.perform(post("/internal/v1/tcc/credit-repay/cancel")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(creditRepayTccParticipant).tryRepay(
                eq("transaction-2"), eq("account-1"), eq("credit-account-1"),
                eq(8_000L), eq("xid-credit-repay-1"), any(Instant.class));
        verify(creditRepayTccParticipant).confirmRepay(
                eq("transaction-2"), eq("account-1"), eq("credit-account-1"),
                eq(8_000L), eq("xid-credit-repay-1"), any(Instant.class));
        verify(creditRepayTccParticipant).cancelRepay(
                eq("transaction-2"), eq("account-1"), eq("credit-account-1"),
                eq(8_000L), eq("xid-credit-repay-1"), any(Instant.class));
    }

    @Test
    void rejectsNonPositiveAmountBeforeEnteringParticipant() throws Exception {
        mockMvc.perform(post("/internal/v1/tcc/credit-pay/try")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "xid":"xid-credit-pay-1",
                                  "transactionId":"transaction-1",
                                  "creditAccountId":"credit-account-1",
                                  "amountFen":0
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    private String creditPayBaseRequest() {
        return """
                {
                  "xid":"xid-credit-pay-1",
                  "transactionId":"transaction-1",
                  "creditAccountId":"credit-account-1",
                  "amountFen":12000
                }
                """;
    }
}
