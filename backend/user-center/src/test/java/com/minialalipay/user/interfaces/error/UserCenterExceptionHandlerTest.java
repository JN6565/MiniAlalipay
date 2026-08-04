package com.minialalipay.user.interfaces.error;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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

@SpringBootTest(properties = {"spring.cloud.nacos.discovery.enabled=false"})
@AutoConfigureMockMvc
@Import(UserCenterFailingTestController.class)
@ExtendWith(OutputCaptureExtension.class)
class UserCenterExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void mapsBusinessExceptionToStandardChineseResponse() throws Exception {
        mockMvc.perform(get("/test/business-error").header("X-Request-Id", "request-user-001"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", "request-user-001"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("请求参数不合法"))
                .andExpect(jsonPath("$.requestId").value("request-user-001"))
                .andExpect(jsonPath("$.traceId").doesNotExist())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void hidesUnexpectedExceptionDetails() throws Exception {
        mockMvc.perform(get("/test/internal-error").header("X-Request-Id", "request-user-500"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("X-Request-Id", "request-user-500"))
                .andExpect(jsonPath("$.code").value("COMMON_INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("系统内部错误"));
    }

    @Test
    void recordsUnexpectedExceptionStackForServerDiagnostics(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/test/internal-error").header("X-Request-Id", "request-user-log"))
                .andExpect(status().isInternalServerError());

        org.assertj.core.api.Assertions.assertThat(output.getOut())
                .contains("用户中心发生未处理异常")
                .contains("request-user-log")
                .contains("java.lang.IllegalStateException: 数据库地址不得泄露")
                .contains("UserCenterFailingTestController.internalError");
    }

    @Test
    void mapsInvalidParameterToBadRequest() throws Exception {
        mockMvc.perform(get("/test/number").param("value", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
    }

    @Test
    void mapsMissingResourceToStandardNotFoundResponse() throws Exception {
        mockMvc.perform(get("/missing-resource").header("X-Request-Id", "request-user-404"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Request-Id", "request-user-404"))
                .andExpect(jsonPath("$.code").value("COMMON_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("资源不存在"));
    }

    @Test
    void mapsMethodNotAllowedToStandardResponse() throws Exception {
        mockMvc.perform(post("/test/request-id"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("COMMON_METHOD_NOT_ALLOWED"));
    }

    @Test
    void mapsUnsupportedMediaTypeToStandardResponse() throws Exception {
        mockMvc.perform(post("/test/json").contentType(MediaType.TEXT_PLAIN).content("not-json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("COMMON_UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void mapsNotAcceptableMediaTypeToStandardResponse() throws Exception {
        mockMvc.perform(get("/test/json-response")
                        .accept(MediaType.APPLICATION_XML)
                        .header("X-Request-Id", "request-user-406"))
                .andExpect(status().isNotAcceptable())
                .andExpect(header().string("X-Request-Id", "request-user-406"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("COMMON_NOT_ACCEPTABLE"))
                .andExpect(jsonPath("$.message").value("无法生成客户端可接受的响应格式"));
    }

    @Test
    void establishesSafeRequestIdForSuccessfulRequest() throws Exception {
        mockMvc.perform(get("/test/request-id").header("X-Request-Id", "包含非法字符"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.startsWith("req_")))
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("req_")));
    }
}
