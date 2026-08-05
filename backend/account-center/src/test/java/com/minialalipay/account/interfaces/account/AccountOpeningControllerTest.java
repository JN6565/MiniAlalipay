package com.minialalipay.account.interfaces.account;

import com.minialalipay.account.application.account.AccountApplicationService;
import com.minialalipay.account.application.account.dto.AccountSummaryDTO;
import com.minialalipay.common.error.CommonExceptionMapper;
import com.minialalipay.common.trace.RequestIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 用户中心幂等开户内部接口测试。 */
@WebMvcTest(AccountOpeningController.class)
class AccountOpeningControllerTest {

    private static final String USER_ID = "01K1ABCDEFGHJKMNPQRSTVWXYZ";
    private static final String REGISTRATION_ID = "01K1ZYXWVTSRQPNMKJHGFEDCBA";

    @Autowired private MockMvc mockMvc;
    @MockBean private AccountApplicationService accountApplicationService;
    @MockBean private RequestIdGenerator requestIdGenerator;
    @MockBean private CommonExceptionMapper commonExceptionMapper;

    @BeforeEach
    void mapErrorsWithRealCommonMapper() {
        CommonExceptionMapper delegate = new CommonExceptionMapper();
        when(commonExceptionMapper.map(any(Throwable.class), any(), any()))
                .thenAnswer(invocation -> delegate.map(invocation.getArgument(0),
                        invocation.getArgument(1), invocation.getArgument(2)));
    }

    @Test
    void userCenterProvisionsAccountByRegistrationId() throws Exception {
        when(requestIdGenerator.resolve("request-1")).thenReturn("request-1");
        when(accountApplicationService.openAccount(any(), eq(USER_ID), eq(REGISTRATION_ID), any()))
                .thenReturn(new AccountSummaryDTO("account-1", "PERSONAL", "CNY", "ACTIVE",
                        0L, 0L, 0L, 0L));

        mockMvc.perform(put("/internal/v1/accounts/registrations/{registrationId}", REGISTRATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Request-Id", "request-1")
                        .content("{\"userId\":\"" + USER_ID + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.accountId").value("account-1"))
                .andExpect(jsonPath("$.data.totalFen").value(0));

        verify(accountApplicationService).openAccount(any(), eq(USER_ID), eq(REGISTRATION_ID), any());
    }

    @Test
    void rejectsBlankUserIdBeforeCallingApplicationService() throws Exception {
        mockMvc.perform(put("/internal/v1/accounts/registrations/{registrationId}", REGISTRATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"\"}"))
                .andExpect(status().isBadRequest());

        verify(accountApplicationService, never()).openAccount(any(), any(), any(), any());
    }

    @Test
    void doesNotExposeAccountProvisioningThroughGatewayPath() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/open")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + USER_ID + "\",\"registrationId\":\""
                                + REGISTRATION_ID + "\"}"))
                .andExpect(status().isNotFound());
    }
}
