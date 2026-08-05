package com.minialalipay.user.infrastructure.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** 用户中心调用账户中心开户契约的客户端测试。 */
class AccountCenterClientTest {

    private static final String USER_ID = "01K1ABCDEFGHJKMNPQRSTVWXYZ";
    private static final String REGISTRATION_ID = "01K1ZYXWVTSRQPNMKJHGFEDCBA";

    @Test
    void opensAccountThroughVersionedInternalEndpoint() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        AccountCenterClient client = new AccountCenterClient(restTemplate, "http://account-center:8083");
        server.expect(once(), requestTo(
                        "http://account-center:8083/internal/v1/accounts/registrations/" + REGISTRATION_ID))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("{\"userId\":\"" + USER_ID + "\"}", true))
                .andRespond(withSuccess("""
                        {"code":"OK","message":"成功","data":{"accountId":"account-1",
                        "accountType":"PERSONAL","currency":"CNY","status":"ACTIVE",
                        "availableFen":0,"frozenFen":0,"totalFen":0,"version":0}}
                        """, MediaType.APPLICATION_JSON));

        String accountId = client.openAccount(USER_ID, REGISTRATION_ID);

        assertThat(accountId).isEqualTo("account-1");
        server.verify();
    }
}
