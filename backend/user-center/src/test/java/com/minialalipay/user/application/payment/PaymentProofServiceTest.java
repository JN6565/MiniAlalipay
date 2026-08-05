package com.minialalipay.user.application.payment;

import com.minialalipay.common.error.BusinessException;
import com.minialalipay.user.domain.auth.UserErrorCode;
import com.minialalipay.user.domain.credential.Credential;
import com.minialalipay.user.domain.credential.CredentialRepository;
import com.minialalipay.user.domain.credential.PasswordHasherPort;
import com.minialalipay.user.domain.credential.PaymentProof;
import com.minialalipay.user.domain.credential.PaymentProofRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentProofServiceTest {

    @Mock
    private PaymentProofRepository proofRepository;
    @Mock
    private CredentialRepository credentialRepository;
    @Mock
    private PasswordHasherPort passwordHasher;

    private PaymentProofService service;
    private Credential credential;

    @BeforeEach
    void setUp() {
        service = new PaymentProofService(proofRepository, credentialRepository, passwordHasher);
        credential = new Credential("user-001", "login-hash");
        credential.setPaymentPasswordHash("pay-hash");
        credential.setPayPasswordVersion(3L);
    }

    @Test
    void 消费证明时返回证明标识和当前密码版本() {
        PaymentProof proof = activeProof("user-001", "TRANSFER_CONFIRM", 3L);
        when(proofRepository.findByTokenDigest(any(byte[].class))).thenReturn(Optional.of(proof));
        when(credentialRepository.findByUserId("user-001")).thenReturn(Optional.of(credential));
        when(proofRepository.consumeActive(eq("proof-001"), any(Instant.class))).thenReturn(true);

        PaymentProofService.VerifiedPaymentProof result =
                service.consumeProof("user-001", "raw-proof-token", "TRANSFER_CONFIRM");

        assertThat(result.paymentProofId()).isEqualTo("proof-001");
        assertThat(result.payPasswordVersion()).isEqualTo(3L);
    }

    @Test
    void 拒绝消费其他用户的支付证明() {
        PaymentProof proof = activeProof("user-002", "TRANSFER_CONFIRM", 3L);
        when(proofRepository.findByTokenDigest(any(byte[].class))).thenReturn(Optional.of(proof));

        assertThatThrownBy(() -> service.consumeProof("user-001", "raw-proof-token", "TRANSFER_CONFIRM"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(UserErrorCode.PAYMENT_PROOF_INVALID));
        verify(proofRepository, never()).consumeActive(any(), any());
    }

    @Test
    void 并发消费只有数据库条件更新成功的一方通过() {
        PaymentProof proof = activeProof("user-001", "TRANSFER_CONFIRM", 3L);
        when(proofRepository.findByTokenDigest(any(byte[].class))).thenReturn(Optional.of(proof));
        when(credentialRepository.findByUserId("user-001")).thenReturn(Optional.of(credential));
        when(proofRepository.consumeActive(eq("proof-001"), any(Instant.class))).thenReturn(false);

        assertThatThrownBy(() -> service.consumeProof("user-001", "raw-proof-token", "TRANSFER_CONFIRM"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(UserErrorCode.PAYMENT_PROOF_INVALID));
    }

    @Test
    void 查询当前支付密码版本() {
        when(credentialRepository.findByUserId("user-001")).thenReturn(Optional.of(credential));

        assertThat(service.currentPayPasswordVersion("user-001")).isEqualTo(3L);
    }

    private PaymentProof activeProof(String userId, String purpose, long version) {
        return new PaymentProof(
                "proof-001",
                new byte[32],
                userId,
                purpose,
                version,
                Instant.now().plusSeconds(120));
    }
}
