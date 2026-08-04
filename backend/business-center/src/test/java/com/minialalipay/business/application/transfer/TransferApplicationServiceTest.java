package com.minialalipay.business.application.transfer;

import com.minialalipay.business.application.port.AccountDirectoryPort;
import com.minialalipay.business.application.port.BusinessStore;
import com.minialalipay.business.application.port.PaymentProofPort;
import com.minialalipay.business.application.port.SecurityMaterialPort;
import com.minialalipay.business.application.port.TccCoordinatorPort;
import com.minialalipay.business.domain.transaction.BusinessErrorCode;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.idempotency.IdempotencyKeyValidator;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TransferApplicationServiceTest {
    @Test
    void 创建草稿时在调用账户中心前拒绝向本人付款() {
        BusinessStore store = mock(BusinessStore.class);
        AccountDirectoryPort accounts = mock(AccountDirectoryPort.class);
        SecurityMaterialPort secure = mock(SecurityMaterialPort.class);
        when(secure.digest(org.mockito.ArgumentMatchers.anyString())).thenReturn(new byte[32]);
        when(store.findIdempotency("user-1", "CREATE_TRANSFER_DRAFT", "idem-key-00000001"))
                .thenReturn(Optional.empty());
        TransferApplicationService service = new TransferApplicationService(store, accounts,
                mock(PaymentProofPort.class), mock(TccCoordinatorPort.class), secure,
                new IdempotencyKeyValidator(), Clock.fixed(Instant.parse("2026-08-04T08:00:00Z"), ZoneOffset.UTC));

        assertThatThrownBy(() -> service.createDraft("user-1", "user-1", 100L, null, "idem-key-00000001"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.errorCode())
                                .isEqualTo(BusinessErrorCode.SELF_PAYMENT_FORBIDDEN));
        verifyNoInteractions(accounts);
    }

    @Test
    void 创建草稿同一幂等键参数变化时拒绝() {
        BusinessStore store = mock(BusinessStore.class);
        SecurityMaterialPort secure = mock(SecurityMaterialPort.class);
        byte[] requested = new byte[32]; requested[0] = 1;
        when(secure.digest(org.mockito.ArgumentMatchers.anyString())).thenReturn(requested);
        when(store.findIdempotency("user-1", "CREATE_TRANSFER_DRAFT", "idem-key-00000001"))
                .thenReturn(Optional.of(new BusinessStore.IdempotencyRecord(new byte[32], "draft-1")));
        TransferApplicationService service = new TransferApplicationService(store, mock(AccountDirectoryPort.class),
                mock(PaymentProofPort.class), mock(TccCoordinatorPort.class), secure,
                new IdempotencyKeyValidator(), Clock.systemUTC());

        assertThatThrownBy(() -> service.createDraft("user-1", "user-2", 100L, null, "idem-key-00000001"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.errorCode())
                                .isEqualTo(BusinessErrorCode.IDEMPOTENCY_CONFLICT));
    }
}
