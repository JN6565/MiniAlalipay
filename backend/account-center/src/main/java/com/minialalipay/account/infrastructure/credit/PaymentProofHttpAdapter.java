package com.minialalipay.account.infrastructure.credit;

import com.minialalipay.account.application.credit.PaymentProofPort;
import com.minialalipay.account.domain.credit.CreditErrorCode;
import com.minialalipay.common.error.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 通过版本化内部 HTTP 契约调用用户中心校验支付证明。 */
@Component
public class PaymentProofHttpAdapter implements PaymentProofPort {

    private final RestClient client;
    private final String serviceToken;

    public PaymentProofHttpAdapter(
            @LoadBalanced RestClient.Builder builder,
            @Value("${minialalipay.internal.user-center-url:http://user-center}") String userCenterUrl,
            @Value("${minialalipay.internal.service-token:}") String serviceToken
    ) {
        this.client = builder.baseUrl(userCenterUrl).build();
        this.serviceToken = serviceToken;
    }

    /**
     * 校验并消费证明。依赖拒绝或不可用时统一按证明无效处理，且绝不记录原始证明。
     */
    @Override
    public VerifiedProof verify(String userId, String paymentProof, String purpose) {
        try {
            VerifiedProof proof = client.post()
                    .uri("/internal/v1/payment-proofs/verify")
                    .header("X-Service-Token", serviceToken)
                    .body(new VerifyRequest(userId, paymentProof, purpose))
                    .retrieve()
                    .body(VerifiedProof.class);
            if (proof == null) {
                throw new BusinessException(CreditErrorCode.PAYMENT_PROOF_INVALID);
            }
            return proof;
        } catch (RestClientException rejected) {
            throw new BusinessException(CreditErrorCode.PAYMENT_PROOF_INVALID);
        }
    }

    /** 用户中心内部证明校验请求。 */
    private record VerifyRequest(String userId, String paymentProof, String purpose) { }
}
