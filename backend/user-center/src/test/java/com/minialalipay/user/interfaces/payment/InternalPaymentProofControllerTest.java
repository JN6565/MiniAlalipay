package com.minialalipay.user.interfaces.payment;

import com.minialalipay.user.application.payment.PaymentProofService;
import com.minialalipay.common.error.CommonExceptionMapper;
import com.minialalipay.common.trace.RequestIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = InternalPaymentProofController.class,
        properties = "internal.auth.service-token=test-internal-service-token")
@AutoConfigureMockMvc(addFilters = false)
@Import(InternalPaymentProofControllerTest.TestSupportConfiguration.class)
class InternalPaymentProofControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentProofService paymentProofService;

    @Test
    void 验证并消费一次性支付证明() throws Exception {
        when(paymentProofService.consumeProof("user-001", "raw-proof-token", "TRANSFER_CONFIRM"))
                .thenReturn(new PaymentProofService.VerifiedPaymentProof("proof-001", 3L));

        mockMvc.perform(post("/internal/v1/payment-proofs/verify")
                        .header("X-Service-Token", "test-internal-service-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"user-001","paymentProof":"raw-proof-token","purpose":"TRANSFER_CONFIRM"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentProofId").value("proof-001"))
                .andExpect(jsonPath("$.payPasswordVersion").value(3));
    }

    @Test
    void 查询当前支付密码版本() throws Exception {
        when(paymentProofService.currentPayPasswordVersion("user-001")).thenReturn(3L);

        mockMvc.perform(get("/internal/v1/payment-password/version/user-001")
                        .header("X-Service-Token", "test-internal-service-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void 拒绝缺少用途的支付证明请求() throws Exception {
        mockMvc.perform(post("/internal/v1/payment-proofs/verify")
                        .header("X-Service-Token", "test-internal-service-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"user-001","paymentProof":"raw-proof-token"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 拒绝缺少服务身份令牌的调用() throws Exception {
        mockMvc.perform(get("/internal/v1/payment-password/version/user-001"))
                .andExpect(status().isUnauthorized());
    }

    /** 为 Web MVC 切片提供平台通用异常映射组件，不引入基础设施层配置。 */
    @TestConfiguration
    static class TestSupportConfiguration {
        @Bean
        CommonExceptionMapper commonExceptionMapper() {
            return new CommonExceptionMapper();
        }

        @Bean
        RequestIdGenerator requestIdGenerator() {
            return new RequestIdGenerator();
        }
    }
}
