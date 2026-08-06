package com.minialalipay.account.interfaces.credit;

import com.minialalipay.account.domain.credit.CreditAccount;
import com.minialalipay.account.domain.credit.CreditAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 信用账户内部只读引用的接口测试。 */
class InternalCreditAccountDirectoryControllerTest {
    @Test
    void 仅返回确认和TCC所需的版本化信用账户引用() throws Exception {
        CreditAccountRepository repository = mock(CreditAccountRepository.class);
        String userId = "01K1ABCDEFGHJKMNPQRSTVWXYZ";
        CreditAccount account = new CreditAccount("01K1CREDITACCOUNT0000000000", userId, Instant.parse("2026-08-05T00:00:00Z"));
        when(repository.findByUserId(userId)).thenReturn(Optional.of(account));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new InternalCreditAccountDirectoryController(repository)).build();

        mvc.perform(get("/internal/v1/credit-accounts/by-user/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditAccountId").value("01K1CREDITACCOUNT0000000000"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.availableFen").doesNotExist())
                .andExpect(jsonPath("$.usedFen").doesNotExist())
                .andExpect(jsonPath("$.frozenFen").doesNotExist());
    }
}
