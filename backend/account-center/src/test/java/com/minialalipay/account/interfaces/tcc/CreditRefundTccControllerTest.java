package com.minialalipay.account.interfaces.tcc;

import com.minialalipay.account.application.credit.CreditRefundTccParticipant;
import com.minialalipay.account.domain.tcc.TccBranch;
import com.minialalipay.account.domain.tcc.TccBranchStatus;
import com.minialalipay.account.domain.tcc.TccBranchType;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证信用支付退款冲正内部 TCC HTTP 契约能够准确路由到对应参与者。
 */
@WebMvcTest(CreditRefundTccController.class)
class CreditRefundTccControllerTest {

    private static final String XID = "xid-credit-refund-1";
    private static final String TX = "01K1TX0002GH3JK4MN5PQRSTVW";
    private static final String ORIGINAL_TX = "01K1SRC002GH3JK4MN5PQRSTVW";
    private static final String MERCHANT = "01K1MCH002GH3JK4MN5PQRSTVW";
    private static final long AMOUNT = 12_000L;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CreditRefundTccParticipant participant;

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
        when(participant.tryRefund(eq(TX), eq(ORIGINAL_TX), eq(MERCHANT), eq(AMOUNT), eq(XID), any(Instant.class)))
                .thenReturn(branch(TccBranchStatus.TRIED));
        when(participant.confirmRefund(eq(TX), eq(ORIGINAL_TX), eq(MERCHANT), eq(AMOUNT), eq(XID), any(Instant.class)))
                .thenReturn(branch(TccBranchStatus.CONFIRMED));
        when(participant.cancelRefund(eq(TX), eq(ORIGINAL_TX), eq(MERCHANT), eq(AMOUNT), eq(XID), any(Instant.class)))
                .thenReturn(branch(TccBranchStatus.CANCELLED));
    }

    @Test
    void routesCreditRefundTryConfirmAndCancel() throws Exception {
        String request = """
                {
                  "xid":"%s",
                  "transactionId":"%s",
                  "originalTransactionId":"%s",
                  "merchantAccountId":"%s",
                  "amountFen":%d
                }
                """.formatted(XID, TX, ORIGINAL_TX, MERCHANT, AMOUNT);

        mockMvc.perform(post("/internal/v1/tcc/credit-refund/try")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRIED"));

        mockMvc.perform(post("/internal/v1/tcc/credit-refund/confirm")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(post("/internal/v1/tcc/credit-refund/cancel")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(participant).tryRefund(eq(TX), eq(ORIGINAL_TX), eq(MERCHANT), eq(AMOUNT), eq(XID), any(Instant.class));
        verify(participant).confirmRefund(eq(TX), eq(ORIGINAL_TX), eq(MERCHANT), eq(AMOUNT), eq(XID), any(Instant.class));
        verify(participant).cancelRefund(eq(TX), eq(ORIGINAL_TX), eq(MERCHANT), eq(AMOUNT), eq(XID), any(Instant.class));
    }

    @Test
    void rejectsNonPositiveAmountBeforeEnteringParticipant() throws Exception {
        mockMvc.perform(post("/internal/v1/tcc/credit-refund/try")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "xid":"xid-credit-refund-1",
                                  "transactionId":"01K1TX0002GH3JK4MN5PQRSTVW",
                                  "originalTransactionId":"01K1SRC002GH3JK4MN5PQRSTVW",
                                  "merchantAccountId":"01K1MCH002GH3JK4MN5PQRSTVW",
                                  "amountFen":0
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidAccountIdBeforeEnteringParticipant() throws Exception {
        mockMvc.perform(post("/internal/v1/tcc/credit-refund/try")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "xid":"xid-credit-refund-1",
                                  "transactionId":"short",
                                  "originalTransactionId":"01K1SRC002GH3JK4MN5PQRSTVW",
                                  "merchantAccountId":"01K1MCH002GH3JK4MN5PQRSTVW",
                                  "amountFen":12000
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    private static TccBranch branch(TccBranchStatus status) {
        TccBranch branch = TccBranch.initialize(XID, TccBranchType.REFUND, ORIGINAL_TX, TX, AMOUNT,
                Instant.parse("2026-08-06T08:00:00Z"));
        switch (status) {
            case TRIED -> branch.markTried(Instant.parse("2026-08-06T08:00:01Z"));
            case CONFIRMED -> {
                branch.markTried(Instant.parse("2026-08-06T08:00:01Z"));
                branch.confirm(Instant.parse("2026-08-06T08:00:02Z"));
            }
            case CANCELLED -> branch.cancel(Instant.parse("2026-08-06T08:00:01Z"));
            default -> throw new IllegalArgumentException("未支持的分支状态: " + status);
        }
        return branch;
    }
}
