package com.minialalipay.account.interfaces.error;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AccountCenterFailingTestController.class)
class AccountCenterExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void hidesUnexpectedExceptionDetails() throws Exception {
        mockMvc.perform(get("/test/internal-error").header("X-Request-Id", "request-account-001"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().stringValues("X-Request-Id", "request-account-001"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("COMMON_INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("系统内部错误"))
                .andExpect(jsonPath("$.requestId").value("request-account-001"))
                .andExpect(jsonPath("$.traceId").doesNotExist())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void keepsNotFoundHttpSemantics() throws Exception {
        mockMvc.perform(get("/test/not-found").header("X-Request-Id", "request-account-404"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Request-Id", "request-account-404"))
                .andExpect(jsonPath("$.code").value("COMMON_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("资源不存在"));
    }

    @Test
    void mapsBusinessExceptionToStandardChineseResponse() throws Exception {
        mockMvc.perform(get("/test/business-error").header("X-Request-Id", "request-account-400"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", "request-account-400"))
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("请求参数不合法"));
    }

    @Test
    void mapsInvalidParameterToBadRequest() throws Exception {
        mockMvc.perform(get("/test/number").param("value", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
    }

    @Test
    void mapsMissingParameterToBadRequest() throws Exception {
        mockMvc.perform(get("/test/number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
    }

    @Test
    void keepsMethodNotAllowedHttpSemantics() throws Exception {
        mockMvc.perform(post("/test/request-id"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("COMMON_METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").value("请求方法不受支持"));
    }

    @Test
    void keepsUnsupportedMediaTypeHttpSemantics() throws Exception {
        mockMvc.perform(post("/test/json").contentType(MediaType.TEXT_PLAIN).content("not-json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("COMMON_UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.message").value("请求媒体类型不受支持"));
    }

    @Test
    void establishesSafeRequestIdForSuccessfulRequest() throws Exception {
        mockMvc.perform(get("/test/request-id").header("X-Request-Id", "包含非法字符"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.startsWith("req_")))
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("req_")));
    }
}
