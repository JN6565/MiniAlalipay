package com.minialalipay.business.infrastructure.http;

import com.minialalipay.business.application.port.PaymentProofPort;
import com.minialalipay.business.domain.transaction.BusinessErrorCode;
import com.minialalipay.common.error.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** 调用用户中心内部支付证明契约；任何依赖拒绝均转换为证明无效，不记录原始证明。 */
@Component
public class PaymentProofHttpAdapter implements PaymentProofPort {
    private final RestClient client;
    private final String serviceToken;
    public PaymentProofHttpAdapter(RestClient.Builder builder,
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
        }
    }
    private record VerifyRequest(String userId, String paymentProof, String purpose) { }
    private record PasswordVersion(long version) { }
}
