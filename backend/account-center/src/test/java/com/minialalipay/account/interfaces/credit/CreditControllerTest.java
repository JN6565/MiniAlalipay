package com.minialalipay.account.interfaces.credit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minialalipay.account.application.credit.CreditJobService;
import com.minialalipay.account.application.credit.CreditQueryService;
import com.minialalipay.account.application.credit.CreditRepaymentService;
import com.minialalipay.account.application.credit.dto.CreditBillDetailDTO;
import com.minialalipay.account.application.credit.dto.CreditJobRunDTO;
import com.minialalipay.account.application.credit.dto.CreditSummaryDTO;
import com.minialalipay.account.application.credit.dto.RepaymentDTO;
import com.minialalipay.account.application.credit.dto.RepaymentDraftDTO;
import com.minialalipay.account.interfaces.error.AccountCenterExceptionHandler;
import com.minialalipay.common.error.CommonExceptionMapper;
import com.minialalipay.common.idempotency.IdempotencyKeyValidator;
import com.minialalipay.common.trace.RequestIdGenerator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 阶段五信用、账单和还款公开接口的路由与参数透传测试。 */
class CreditControllerTest {

    private CreditQueryService queryService;
    private CreditRepaymentService repaymentService;
    private CreditJobService jobService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        queryService = mock(CreditQueryService.class);
        repaymentService = mock(CreditRepaymentService.class);
        jobService = mock(CreditJobService.class);
        IdempotencyKeyValidator keyValidator = mock(IdempotencyKeyValidator.class);
        RequestIdGenerator requestIdGenerator = mock(RequestIdGenerator.class);
        when(keyValidator.isValid(any())).thenReturn(true);
        when(requestIdGenerator.resolve(any())).thenReturn("request-credit-001");

        CreditController creditController = new CreditController(
                queryService, repaymentService, keyValidator, requestIdGenerator);
        CreditOpsController opsController = new CreditOpsController(
                jobService, keyValidator, requestIdGenerator);
        mockMvc = MockMvcBuilders.standaloneSetup(creditController, opsController)
                .setControllerAdvice(new AccountCenterExceptionHandler(
                        new CommonExceptionMapper(), requestIdGenerator))
                .build();
    }

    @Test
    void shouldExposeCreditQueries() throws Exception {
        when(queryService.getMyCredit("user-1")).thenReturn(new CreditSummaryDTO(
                "credit-1", "ACTIVE", 500000, 10000, 0, 490000, 10000, 0, 0));
        when(queryService.listCreditPurchases("user-1", "UNBILLED")).thenReturn(List.of());
        when(queryService.listCreditBills("user-1")).thenReturn(List.of());
        when(queryService.getCreditBill("user-1", "bill-1")).thenReturn(new CreditBillDetailDTO(
                "bill-1", "2026-07", LocalDate.of(2026, 8, 1), Instant.now(),
                10000, 0, 10000, "OPEN", List.of()));

        mockMvc.perform(get("/api/v1/credit/me").header("X-User-Id", "user-1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/credit/purchases")
                        .header("X-User-Id", "user-1").param("billingStatus", "UNBILLED"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/credit/bills").header("X-User-Id", "user-1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/credit/bills/bill-1").header("X-User-Id", "user-1"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldPassIdempotencyAndPaymentProofToRepaymentService() throws Exception {
        Instant now = Instant.now();
        when(repaymentService.createRepaymentDraft("user-1", 10000, "draft-key"))
                .thenReturn(new RepaymentDraftDTO("draft-1", 10000, "hash", now, List.of()));
        when(repaymentService.submitRepayment(
                "user-1", "draft-1", "proof-secret", "repay-key"))
                .thenReturn(new RepaymentDTO("repay-1", 10000, "SUCCESS", now, now));
        when(repaymentService.getRepayment("user-1", "repay-1"))
                .thenReturn(new RepaymentDTO("repay-1", 10000, "SUCCESS", now, now));

        mockMvc.perform(post("/api/v1/credit/repayment-drafts")
                        .header("X-User-Id", "user-1")
                        .header("Idempotency-Key", "draft-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountFen\":10000}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/credit/repayments")
                        .header("X-User-Id", "user-1")
                        .header("Idempotency-Key", "repay-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repaymentDraftId\":\"draft-1\",\"paymentProofToken\":\"proof-secret\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/credit/repayments/repay-1")
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isOk());

        verify(repaymentService).createRepaymentDraft("user-1", 10000, "draft-key");
        verify(repaymentService).submitRepayment("user-1", "draft-1", "proof-secret", "repay-key");
    }

    @Test
    void shouldExposeCreditOperationsJobs() throws Exception {
        Instant now = Instant.now();
        LocalDate businessDate = LocalDate.of(2026, 8, 1);
        when(jobService.runStatement("admin-1", businessDate)).thenReturn(new CreditJobRunDTO(
                "run-1", "STATEMENT", businessDate, "SUCCESS", now, now, null));
        when(jobService.runDueCheck("admin-1", businessDate)).thenReturn(new CreditJobRunDTO(
                "run-2", "DUE_CHECK", businessDate, "SUCCESS", now, now, null));

        String body = "{\"businessDate\":\"2026-08-01\"}";
        mockMvc.perform(post("/api/v1/ops/credit/statement-runs")
                        .header("X-User-Id", "admin-1")
                        .header("X-User-Roles", "ADMIN")
                        .header("Idempotency-Key", "statement-key")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/ops/credit/due-check-runs")
                        .header("X-User-Id", "admin-1")
                        .header("X-User-Roles", "ADMIN")
                        .header("Idempotency-Key", "due-key")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectNonAdminCreditOps() throws Exception {
        String body = "{\"businessDate\":\"2026-08-01\"}";
        mockMvc.perform(post("/api/v1/ops/credit/statement-runs")
                        .header("X-User-Id", "ops-1")
                        .header("X-User-Roles", "OPERATOR")
                        .header("Idempotency-Key", "statement-key")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }
}
