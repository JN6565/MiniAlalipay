package com.minialalipay.business.application.collection;

import com.minialalipay.business.application.port.BusinessStore;
import com.minialalipay.business.application.port.CollectionStore;
import com.minialalipay.business.application.port.PaymentProofPort;
import com.minialalipay.business.application.port.SecurityMaterialPort;
import com.minialalipay.business.application.port.TccCoordinatorPort;
import com.minialalipay.business.domain.collection.CollectionOrder;
import com.minialalipay.business.domain.confirmation.Confirmation;
import com.minialalipay.business.domain.transaction.FundingSource;
import com.minialalipay.business.domain.transaction.SourceType;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.idempotency.IdempotencyKeyValidator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** C2C 确认与资金受理应用服务测试。 */
class CollectionPaymentApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");
    private static final String KEY = "123e4567-e89b-12d3-a456-426614174000";

    @Test
    void C2C拒绝MiniCredit且不签发确认() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> fixture.service.issueConfirmation("payer-1", "order-1", "session-1", 0L,
                "proof", FundingSource.MINI_CREDIT))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.errorCode().code()).isEqualTo("FUNDING_SOURCE_NOT_ALLOWED"));
        verify(fixture.business, never()).replaceCollectionConfirmation(any());
    }

    @Test
    void 个人码订单确认并受理为唯一余额Transfer() {
        Fixture fixture = fixture();
        CollectionPaymentApplicationService.IssuedConfirmation issued = fixture.service.issueConfirmation(
                "payer-1", "order-1", "session-1", 1L, "proof", FundingSource.BALANCE);
        ArgumentCaptor<Confirmation> confirmationCaptor = ArgumentCaptor.forClass(Confirmation.class);
        verify(fixture.business).replaceCollectionConfirmation(confirmationCaptor.capture());
        Confirmation confirmation = confirmationCaptor.getValue();
        when(fixture.business.findConfirmationForUpdate(any()))
                .thenReturn(Optional.of(confirmation));
        when(fixture.business.findByIdempotency("payer-1", com.minialalipay.business.domain.transaction.TransactionType.TRANSFER, KEY))
                .thenReturn(Optional.empty());
        when(fixture.business.findBySource(SourceType.PERSONAL_QR_ORDER.name(), "order-1")).thenReturn(Optional.empty());
        when(fixture.business.updateConfirmation(any(), anyString())).thenReturn(true);
        when(fixture.collections.acceptOrderForPayment(any(), anyLong(), any())).thenReturn(true);

        var transaction = fixture.service.pay("payer-1", "order-1", "session-1", issued.confirmationToken(), KEY, "0".repeat(32));

        assertThat(transaction.getSourceType()).isEqualTo(SourceType.PERSONAL_QR_ORDER);
        assertThat(transaction.getFundingSource()).isEqualTo(FundingSource.BALANCE);
        assertThat(transaction.getStatus().name()).isEqualTo("PROCESSING");
        assertThat(fixture.order.getTransactionId()).isEqualTo(transaction.getTransactionId());
        assertThat(fixture.order.getStatus().name()).isEqualTo("PROCESSING");
        verify(fixture.coordinator).startOrResume(transaction);
    }

    private static Fixture fixture() {
        CollectionStore collections = mock(CollectionStore.class);
        BusinessStore business = mock(BusinessStore.class);
        PaymentProofPort proofs = mock(PaymentProofPort.class);
        TccCoordinatorPort coordinator = mock(TccCoordinatorPort.class);
        TestSecurity security = new TestSecurity();
        CollectionOrder order = CollectionOrder.forPersonalCode("order-1", "code-1", "payee-1", "account-payee-1",
                "payer-1", "account-payer-1", NOW);
        order.lockPersonalAmount("payer-1", 0L, 5200L, "午餐", NOW);
        when(collections.findOrder("order-1")).thenReturn(Optional.of(order));
        when(collections.findOrderByBootstrapSessionId("session-1")).thenReturn(Optional.of(order));
        when(proofs.verify("payer-1", "proof", "COLLECTION_CONFIRM"))
                .thenReturn(new PaymentProofPort.VerifiedProof("proof-1", 3L));
        when(proofs.currentPayPasswordVersion("payer-1")).thenReturn(3L);
        CollectionPaymentApplicationService service = new CollectionPaymentApplicationService(collections, business, proofs,
                security, coordinator, new IdempotencyKeyValidator(), null, null, Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(service, collections, business, coordinator, security, order);
    }

    private record Fixture(CollectionPaymentApplicationService service, CollectionStore collections, BusinessStore business,
                           TccCoordinatorPort coordinator, TestSecurity security, CollectionOrder order) { }

    private static final class TestSecurity implements SecurityMaterialPort {
        private int sequence;
        @Override public String newId() { return "id-" + ++sequence; }
        @Override public String newTraceId() { return "0".repeat(32); }
        @Override public String newConfirmationToken() { return "confirmation-token-0123456789abcdef"; }
        @Override public String newQrToken() { return "qr"; }
        @Override public String newCollectionToken() { return "collection"; }
        @Override public byte[] digest(String value) {
            try { return java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
            catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
        }
        @Override public String stableId(String value) { return value; }
        @Override public long stablePositiveLong(String value) { return 1L; }
    }
}
