package com.minialalipay.user.interfaces.internal;

import com.minialalipay.user.application.identity.IdentityApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 内部三要素校验接口切片测试（standalone MockMvc，不加载 Spring 上下文）。
 *
 * <p>验证响应契约：{@code identityBound} 无论匹配与否都返回，
 * 供 account-center 区分「未绑定身份」与「已绑定但不匹配」；
 * 未匹配时 realName/phone 不回填，避免泄露用户存储信息。</p>
 */
class InternalIdentityControllerTest {

    private static final String VERIFY_URL = "/internal/v1/identity/verify";

    private static final String REQUEST_BODY = """
            {"userId":"USER001","holderName":"张三","idCard":"330106199001011234","phone":"13812345678"}
            """;

    private IdentityApplicationService identityApplicationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        identityApplicationService = mock(IdentityApplicationService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new InternalIdentityController(identityApplicationService))
                .build();
    }

    /** 已绑定且三要素匹配：matched=true、identityBound=true，回填请求中的姓名与手机号。 */
    @Test
    void matchedReturnsIdentityBoundTrue() throws Exception {
        when(identityApplicationService.verifyThreeElements(any(), any(), any(), any()))
                .thenReturn(new IdentityApplicationService.VerifyResult(true, true));

        mockMvc.perform(post(VERIFY_URL).contentType(MediaType.APPLICATION_JSON).content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(true))
                .andExpect(jsonPath("$.identityBound").value(true))
                .andExpect(jsonPath("$.realName").value("张三"))
                .andExpect(jsonPath("$.phone").value("13812345678"));
    }

    /** 未绑定身份：matched=false 且 identityBound=false，realName/phone 为空。 */
    @Test
    void unboundReturnsIdentityBoundFalse() throws Exception {
        when(identityApplicationService.verifyThreeElements(any(), any(), any(), any()))
                .thenReturn(new IdentityApplicationService.VerifyResult(false, false));

        mockMvc.perform(post(VERIFY_URL).contentType(MediaType.APPLICATION_JSON).content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(false))
                .andExpect(jsonPath("$.identityBound").value(false))
                .andExpect(jsonPath("$.realName").isEmpty())
                .andExpect(jsonPath("$.phone").isEmpty());
    }

    /** 已绑定但不匹配：matched=false 但 identityBound=true，realName/phone 仍不回填。 */
    @Test
    void boundButMismatchedReturnsIdentityBoundTrue() throws Exception {
        when(identityApplicationService.verifyThreeElements(any(), any(), any(), any()))
                .thenReturn(new IdentityApplicationService.VerifyResult(false, true));

        mockMvc.perform(post(VERIFY_URL).contentType(MediaType.APPLICATION_JSON).content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(false))
                .andExpect(jsonPath("$.identityBound").value(true))
                .andExpect(jsonPath("$.realName").isEmpty())
                .andExpect(jsonPath("$.phone").isEmpty());
    }
}
