package com.minialalipay.account.interfaces.tcc;

import com.minialalipay.account.application.tcc.CreditPayLedgerTccApplicationService;
import com.minialalipay.account.application.tcc.CreditPayLedgerTccApplicationService.CreditPayLedgerCommand;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 验证信用支付账本内部接口只接受信用账户与收款账户，不含付款余额账户字段。 */
@WebMvcTest(CreditPayLedgerTccController.class)
class CreditPayLedgerTccControllerTest {

    private static final String TRANSACTION_ID = "01K1ABCDEFGHJKMNPQRSTVWXYZ";
    private static final String CREDIT_ACCOUNT_ID = "01K1BCDEFGHJKMNPQRSTVWXYZ0";
    private static final String PAYEE_ACCOUNT_ID = "01K1CDEFGHJKMNPQRSTVWXYZ01";
    private static final String VOUCHER_ID = "01K1DEFGHJKMNPQRSTVWXYZ012";
    private static final String EVENT_ID = "01K1EFGHJKMNPQRSTVWXYZ0123";

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private CreditPayLedgerTccApplicationService service;
    @MockBean
    private RequestIdGenerator requestIdGenerator;
    @MockBean
    private CommonExceptionMapper commonExceptionMapper;

    @BeforeEach
    void setUp() {
        when(requestIdGenerator.resolve(nullable(String.class))).thenReturn("request-test");
        when(commonExceptionMapper.map(any(), nullable(String.class), nullable(String.class)))
                .thenReturn(new MappedError(400, new ApiResponse<>("COMMON_INVALID_REQUEST", "请求参数不合法",
                        "request-test", null, null)));
        when(service.tryLedger(any(CreditPayLedgerCommand.class), any(Instant.class)))
                .thenReturn(new TccBranch("xid-credit-ledger", TccBranchType.CREDIT_PAY_LEDGER, VOUCHER_ID,
                        TRANSACTION_ID, 12_000L, TccBranchStatus.TRIED, null, 1L,
                        Instant.parse("2026-08-05T12:00:00Z"), Instant.parse("2026-08-05T12:00:00Z")));
    }

    @Test
    void routesCreditLedgerTryAndRejectsPayerBalanceField() throws Exception {
        mockMvc.perform(post("/internal/v1/tcc/credit-ledger/try")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRIED"));

        mockMvc.perform(post("/internal/v1/tcc/credit-ledger/try")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson().replace("\"amountFen\":12000", "\"payerAccountId\":\"" + PAYEE_ACCOUNT_ID
                                + "\",\"amountFen\":12000")))
                .andExpect(status().isBadRequest());
    }

    private static String requestJson() {
        return """
                {
                  "xid":"xid-credit-ledger",
                  "transactionId":"%s",
                  "creditAccountId":"%s",
                  "payeeAccountId":"%s",
                  "amountFen":12000,
                  "voucherId":"%s",
                  "debitEntryId":101,
                  "creditEntryId":102,
                  "eventId":"%s",
                  "traceId":"0123456789abcdef0123456789abcdef"
                }
                """.formatted(TRANSACTION_ID, CREDIT_ACCOUNT_ID, PAYEE_ACCOUNT_ID, VOUCHER_ID, EVENT_ID);
    }
}
