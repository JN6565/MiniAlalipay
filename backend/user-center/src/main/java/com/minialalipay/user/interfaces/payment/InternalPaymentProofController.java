package com.minialalipay.user.interfaces.payment;

import com.minialalipay.user.application.payment.PaymentProofService;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 用户中心支付证明的版本化内部接口。
 *
 * <p>只允许业务中心在受信服务网络内调用，不经前端网关暴露。原始支付证明只在请求体中传输，
 * 不进入 URL、响应、日志或业务中心持久化；验证成功会在用户中心本地事务内原子消费证明。</p>
 *
 * <p>合并提交端点使用的“验密并签发证明”接口同样只允许服务身份调用；原始支付密码仅在该
 * 请求体中传输，不落日志、URL、响应或任何持久化存储。</p>
 */
@RestController
@Validated
@RequestMapping("/internal/v1")
public class InternalPaymentProofController {

    private final PaymentProofService paymentProofService;
    private final String serviceToken;

    public InternalPaymentProofController(
            PaymentProofService paymentProofService,
            @Value("${internal.auth.service-token:local-internal-token}") String serviceToken
    ) {
        this.paymentProofService = paymentProofService;
        this.serviceToken = serviceToken;
    }

    /**
     * 校验证明的主体、用途、有效期和支付密码版本，并原子消费一次性证明。
     *
     * @param request 用户、原始证明和确认用途
     * @return 仅供业务中心持久化的证明逻辑 ID 与支付密码版本
     */
    @PostMapping("/payment-proofs/verify")
    public ResponseEntity<VerifiedProofResponse> verify(
            @RequestHeader(value = "X-Service-Token", required = false) String providedServiceToken,
            @Valid @RequestBody VerifyProofRequest request
    ) {
        verifyServiceToken(providedServiceToken);
        PaymentProofService.VerifiedPaymentProof proof = paymentProofService.consumeProof(
                request.userId(), request.paymentProof(), request.purpose());
        return ResponseEntity.ok(new VerifiedProofResponse(
                proof.paymentProofId(), proof.payPasswordVersion()));
    }

    /**
     * 校验原始支付密码并签发一次性支付证明，供转账合并提交端点在服务端内部完成
     * “验密 + 证明签发”，避免前端多一次网关往返。
     *
     * <p>密码错误返回 PAY_PASSWORD_INVALID(422)，连续失败被锁定返回 PAYMENT_LOCKED(429)，
     * 语义与网关路径 {@code POST /api/v1/payment-password/proof} 完全一致。</p>
     *
     * @param request 用户 ID、原始支付密码和确认用途；密码属敏感字段，禁止进入日志
     * @return 原始证明令牌，仅本次响应可见
     */
    @PostMapping("/payment-password/proof")
    public ResponseEntity<IssuedProofResponse> verifyPasswordAndIssueProof(
            @RequestHeader(value = "X-Service-Token", required = false) String providedServiceToken,
            @Valid @RequestBody IssueProofByPasswordRequest request
    ) {
        verifyServiceToken(providedServiceToken);
        String rawToken = paymentProofService.verifyAndIssueProof(
                request.userId(), request.paymentPassword(), request.purpose());
        return ResponseEntity.ok(new IssuedProofResponse(rawToken));
    }

    /**
     * 查询当前支付密码版本，用于业务中心拒绝改密前签发的确认令牌。
     *
     * @param userId 用户 ID
     * @return 当前支付密码版本
     */
    @GetMapping("/payment-password/version/{userId}")
    public ResponseEntity<PasswordVersionResponse> currentVersion(
            @RequestHeader(value = "X-Service-Token", required = false) String providedServiceToken,
            @PathVariable @NotBlank @Size(max = 64) String userId) {
        verifyServiceToken(providedServiceToken);
        return ResponseEntity.ok(new PasswordVersionResponse(
                paymentProofService.currentPayPasswordVersion(userId)));
    }

    private void verifyServiceToken(String providedServiceToken) {
        if (providedServiceToken == null || serviceToken.isBlank() || !MessageDigest.isEqual(
                serviceToken.getBytes(StandardCharsets.UTF_8),
                providedServiceToken.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
    }

    /** 内部支付证明验证请求，原始证明属于敏感字段。 */
    public record VerifyProofRequest(
            @NotBlank @Size(max = 64) String userId,
            @NotBlank @Size(max = 256) String paymentProof,
            @NotBlank @Size(max = 64) String purpose) { }

    /** 已消费证明的安全逻辑引用。 */
    public record VerifiedProofResponse(String paymentProofId, long payPasswordVersion) { }

    /** 内部验密签发证明请求；原始支付密码属敏感字段，仅在请求体中传输。 */
    public record IssueProofByPasswordRequest(
            @NotBlank @Size(max = 64) String userId,
            @NotBlank @Pattern(regexp = "^\\d{6}$") String paymentPassword,
            @NotBlank @Size(max = 32) String purpose) { }

    /** 验密签发证明响应；原始令牌只存在于该响应体。 */
    public record IssuedProofResponse(String paymentProof) { }

    /** 当前支付密码版本响应。 */
    public record PasswordVersionResponse(long version) { }
}
