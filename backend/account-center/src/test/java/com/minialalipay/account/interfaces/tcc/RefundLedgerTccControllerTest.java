package com.minialalipay.account.interfaces.tcc;

import com.minialalipay.account.application.tcc.RefundLedgerTccApplicationService;
import com.minialalipay.account.application.tcc.RefundLedgerTccApplicationService.RefundLedgerCommand;
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
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证退款专用账本内部 TCC HTTP 契约能够准确路由到账本参与者。
 */
@WebMvcTest(RefundLedgerTccController.class)
class RefundLedgerTccControllerTest {

    private static final String XID = "xid-refund-ledger-1";
    private static final String TX = "01K1TX0002GH3JK4MN5PQRSTVW";
    private static final String MERCHANT = "01K1MCH002GH3JK4MN5PQRSTVW";
    private static final String CREDIT = "01K1CRD002GH3JK4MN5PQRSTVW";
    private static final String VOUCHER = "01K1VCH002GH3JK4MN5PQRSTVW";
    private static final String EVENT = "01K1EVT002GH3JK4MN5PQRSTVW";
    private static final long AMOUNT = 12_000L;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RefundLedgerTccApplicationService service;

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
        when(service.tryLedger(any(RefundLedgerCommand.class), any(Instant.class)))
                .thenReturn(branch(TccBranchStatus.TRIED));
        when(service.confirmLedger(any(RefundLedgerCommand.class), any(Instant.class)))
                .thenReturn(branch(TccBranchStatus.CONFIRMED));
        when(service.cancelLedger(any(RefundLedgerCommand.class), any(Instant.class)))
                .thenReturn(branch(TccBranchStatus.CANCELLED));
    }

    @Test
    void routesRefundLedgerTryConfirmAndCancel() throws Exception {
        String request = """
                {
                  "xid":"%s",
                  "transactionId":"%s",
                  "merchantAccountId":"%s",
                  "creditAccountId":"%s",
                  "amountFen":%d,
                  "voucherId":"%s",
                  "debitEntryId":201,
                  "creditEntryId":202,
                  "eventId":"%s",
                  "traceId":"0123456789abcdef0123456789abcdef"
                }
                """.formatted(XID, TX, MERCHANT, CREDIT, AMOUNT, VOUCHER, EVENT);

        mockMvc.perform(post("/internal/v1/tcc/refund-ledger/try")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRIED"));

        mockMvc.perform(post("/internal/v1/tcc/refund-ledger/confirm")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(post("/internal/v1/tcc/refund-ledger/cancel")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(service).tryLedger(any(RefundLedgerCommand.class), any(Instant.class));
        verify(service).confirmLedger(any(RefundLedgerCommand.class), any(Instant.class));
        verify(service).cancelLedger(any(RefundLedgerCommand.class), any(Instant.class));
    }

    @Test
    void routesBalanceRefundWithoutCreditAccount() throws Exception {
        String request = """
                {
                  "xid":"%s",
                  "transactionId":"%s",
                  "merchantAccountId":"%s",
                  "payerAccountId":"01K1PAY002GH3JK4MN5PQRSTVW",
                  "amountFen":%d,
                  "voucherId":"%s",
                  "debitEntryId":201,
                  "creditEntryId":202,
                  "eventId":"%s",
                  "traceId":"0123456789abcdef0123456789abcdef"
                }
                """.formatted(XID, TX, MERCHANT, AMOUNT, VOUCHER, EVENT);

        mockMvc.perform(post("/internal/v1/tcc/refund-ledger/try")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRIED"));

        verify(service).tryLedger(any(RefundLedgerCommand.class), any(Instant.class));
    }

    @Test
    void rejectsNonPositiveAmountBeforeEnteringService() throws Exception {
        mockMvc.perform(post("/internal/v1/tcc/refund-ledger/try")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "xid":"xid-refund-ledger-1",
                                  "transactionId":"01K1TX0002GH3JK4MN5PQRSTVW",
                                  "merchantAccountId":"01K1MCH002GH3JK4MN5PQRSTVW",
                                  "creditAccountId":"01K1CRD002GH3JK4MN5PQRSTVW",
                                  "amountFen":0,
                                  "voucherId":"01K1VCH002GH3JK4MN5PQRSTVW",
                                  "debitEntryId":201,
                                  "creditEntryId":202,
                                  "eventId":"01K1EVT002GH3JK4MN5PQRSTVW",
                                  "traceId":"0123456789abcdef0123456789abcdef"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    private static TccBranch branch(TccBranchStatus status) {
        TccBranch branch = TccBranch.initialize(XID, TccBranchType.REFUND_LEDGER, VOUCHER, TX, AMOUNT,
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
