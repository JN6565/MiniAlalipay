package com.minialalipay.account.interfaces.account;

import com.minialalipay.account.application.account.AccountApplicationService;
import com.minialalipay.account.application.account.dto.AccountSummaryDTO;
import com.minialalipay.account.application.ledger.LedgerApplicationService;
import com.minialalipay.account.domain.ledger.LedgerDirection;
import com.minialalipay.account.domain.ledger.LedgerEntry;
import com.minialalipay.account.application.ledger.dto.LedgerEntryDTO;
import com.minialalipay.account.application.ledger.dto.LedgerEntryPageDTO;
import com.minialalipay.common.trace.RequestIdGenerator;
import com.minialalipay.common.error.CommonExceptionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private AccountApplicationService accountApplicationService;
    @MockBean private LedgerApplicationService ledgerApplicationService;
    @MockBean private RequestIdGenerator requestIdGenerator;
    @MockBean private CommonExceptionMapper commonExceptionMapper;

    @Test
    void getsAuthenticatedUsersRealBalance() throws Exception {
        when(requestIdGenerator.resolve("request-1")).thenReturn("request-1");
        when(accountApplicationService.getMyAccount("user-1")).thenReturn(new AccountSummaryDTO(
                "account-1", "PERSONAL", "CNY", "ACTIVE", 800L, 200L, 1_000L, 4L));

        mockMvc.perform(get("/api/v1/accounts/me")
                        .header("X-User-Id", "user-1")
                        .header("X-Request-Id", "request-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.availableFen").value(800))
                .andExpect(jsonPath("$.data.frozenFen").value(200))
                .andExpect(jsonPath("$.data.totalFen").value(1000))
                .andExpect(jsonPath("$.data.version").value(4));
    }

    @Test
    void listsEntriesWithOpaqueCompositeCursor() throws Exception {
        Instant occurredAt = Instant.parse("2026-08-04T08:00:00Z");
        when(requestIdGenerator.resolve("request-1")).thenReturn("request-1");
        when(ledgerApplicationService.listMyEntries("user-1", null, 1)).thenReturn(new LedgerEntryPageDTO(
                List.of(new LedgerEntryDTO(9L, "transaction", "DEBIT", 500L, "付款", occurredAt)),
                "opaque-cursor"));

        mockMvc.perform(get("/api/v1/accounts/me/entries?limit=1")
                        .header("X-User-Id", "user-1")
                        .header("X-Request-Id", "request-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].entryId").value(9))
                .andExpect(jsonPath("$.data.items[0].amountFen").value(500))
                .andExpect(jsonPath("$.data.nextCursor").value("opaque-cursor"));
    }
}
