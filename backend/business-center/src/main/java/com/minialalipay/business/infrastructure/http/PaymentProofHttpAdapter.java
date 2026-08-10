package com.minialalipay.business.infrastructure.http;

import com.minialalipay.business.application.port.PaymentProofPort;
import com.minialalipay.business.domain.transaction.BusinessErrorCode;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** 调用用户中心内部支付证明契约；任何依赖拒绝均转换为对应业务错误，不记录原始证明和密码。 */
@Component
public class PaymentProofHttpAdapter implements PaymentProofPort {
    private final RestClient client;
    private final String serviceToken;
    private final ObjectMapper objectMapper = new ObjectMapper();
    public PaymentProofHttpAdapter(@LoadBalanced RestClient.Builder builder,
                                   @Value("${minialalipay.internal.user-center-url}") String baseUrl,
                                   @Value("${minialalipay.internal.service-token:}") String serviceToken) {
        this.client = builder.baseUrl(baseUrl).build();
        this.serviceToken = serviceToken;
    }
    @Override public VerifiedProof verify(String userId, String paymentProof, String purpose) {
        try {
            VerifiedProof result = client.post().uri("/internal/v1/payment-proofs/verify")
                    .header("X-Service-Token", serviceToken)
                    .body(new VerifyRequest(userId, paymentProof, purpose)).retrieve().body(VerifiedProof.class);
            if (result == null) throw new BusinessException(BusinessErrorCode.PAYMENT_PROOF_INVALID);
            return result;
        } catch (RestClientResponseException rejected) {
            throw new BusinessException(BusinessErrorCode.PAYMENT_PROOF_INVALID);
        } catch (ResourceAccessException unreachable) {
            // 用户中心不可达属于下游服务故障，不得裸抛为 500 内部错误
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        }
    }
    @Override public String verifyAndIssueProof(String userId, String paymentPassword, String purpose) {
        try {
            IssuedProof result = client.post().uri("/internal/v1/payment-password/proof")
                    .header("X-Service-Token", serviceToken)
                    .body(new IssueProofRequest(userId, paymentPassword, purpose)).retrieve().body(IssuedProof.class);
            if (result == null || result.paymentProof() == null || result.paymentProof().isBlank()) {
                throw new BusinessException(BusinessErrorCode.PAYMENT_PROOF_INVALID);
            }
            return result.paymentProof();
        } catch (RestClientResponseException rejected) {
            // 按统一响应体的错误码精确区分密码错误与锁定，不得与请求校验失败、服务故障混为一谈
            throw switch (errorCodeOf(rejected)) {
                case "PAY_PASSWORD_INVALID" -> new BusinessException(BusinessErrorCode.PAY_PASSWORD_INVALID);
                case "PAYMENT_LOCKED" -> new BusinessException(BusinessErrorCode.PAYMENT_LOCKED);
                default -> new BusinessException(BusinessErrorCode.PAYMENT_PROOF_INVALID);
            };
        } catch (ResourceAccessException unreachable) {
            // 用户中心不可达属于下游服务故障，不得裸抛为 500 内部错误
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        }
    }
    @Override public long currentPayPasswordVersion(String userId) {
        try {
            PasswordVersion result = client.get().uri("/internal/v1/payment-password/version/{id}", userId)
                    .header("X-Service-Token", serviceToken)
                    .retrieve().body(PasswordVersion.class);
            if (result == null) throw new BusinessException(BusinessErrorCode.PAYMENT_PROOF_INVALID);
            return result.version();
        } catch (RestClientResponseException rejected) {
            throw new BusinessException(BusinessErrorCode.PAYMENT_PROOF_INVALID);
        } catch (ResourceAccessException unreachable) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        }
    }
    /** 从下游统一错误响应体中提取错误码；解析失败时返回空串，由调用方按证明无效兜底。 */
    private String errorCodeOf(RestClientResponseException rejected) {
        try {
            JsonNode body = objectMapper.readTree(rejected.getResponseBodyAsString());
            JsonNode code = body.get("code");
            return code == null || code.isNull() ? "" : code.asText();
        } catch (Exception ignored) {
            return "";
        }
    }
    private record VerifyRequest(String userId, String paymentProof, String purpose) { }
    private record IssueProofRequest(String userId, String paymentPassword, String purpose) { }
    private record IssuedProof(String paymentProof) { }
    private record PasswordVersion(long version) { }
}
