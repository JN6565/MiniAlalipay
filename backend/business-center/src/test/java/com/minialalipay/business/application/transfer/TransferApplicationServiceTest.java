package com.minialalipay.business.application.transfer;

import com.minialalipay.business.application.port.AccountDirectoryPort;
import com.minialalipay.business.application.port.BusinessStore;
import com.minialalipay.business.application.port.ContactArchivePort;
import com.minialalipay.business.application.port.PaymentProofPort;
import com.minialalipay.business.application.port.SecurityMaterialPort;
import com.minialalipay.business.application.port.TccCoordinatorPort;
import com.minialalipay.business.application.port.UserInfoPort;
import com.minialalipay.business.domain.transaction.BusinessErrorCode;
import com.minialalipay.business.domain.transaction.FundTransaction;
import com.minialalipay.business.domain.transaction.FundingSource;
import com.minialalipay.business.domain.transaction.SourceType;
import com.minialalipay.business.domain.transaction.TransactionType;
import com.minialalipay.business.domain.transfer.TransferDraft;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.idempotency.IdempotencyKeyValidator;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TransferApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-04T08:00:00Z");

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
                new IdempotencyKeyValidator(), mock(ContactArchivePort.class), mock(UserInfoPort.class),
                Clock.fixed(Instant.parse("2026-08-04T08:00:00Z"), ZoneOffset.UTC));

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
                new IdempotencyKeyValidator(), mock(ContactArchivePort.class), mock(UserInfoPort.class),
                Clock.systemUTC());

        assertThatThrownBy(() -> service.createDraft("user-1", "user-2", 100L, null, "idem-key-00000001"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.errorCode())
                                .isEqualTo(BusinessErrorCode.IDEMPOTENCY_CONFLICT));
    }

    @Test
    void 收款人可以查询本人参与的普通转账() {
        BusinessStore store = mock(BusinessStore.class);
        AccountDirectoryPort accounts = mock(AccountDirectoryPort.class);
        FundTransaction transaction = transfer();
        when(store.findTransaction(transaction.getTransactionId()))
                .thenReturn(Optional.of(new BusinessStore.FundTransactionRecord(transaction, null)));
        when(accounts.resolvePersonalAccount("payee-user"))
                .thenReturn(new AccountDirectoryPort.AccountReference("payee-account", "payee-user", "ACTIVE"));

        FundTransaction result = service(store, accounts, mock(UserInfoPort.class))
                .getTransaction("payee-user", transaction.getTransactionId());

        assertThat(result).isSameAs(transaction);
    }

    @Test
    void 无关用户查询普通转账时按不存在处理() {
        BusinessStore store = mock(BusinessStore.class);
        AccountDirectoryPort accounts = mock(AccountDirectoryPort.class);
        FundTransaction transaction = transfer();
        when(store.findTransaction(transaction.getTransactionId()))
                .thenReturn(Optional.of(new BusinessStore.FundTransactionRecord(transaction, null)));
        when(accounts.resolvePersonalAccount("other-user"))
                .thenReturn(new AccountDirectoryPort.AccountReference("other-account", "other-user", "ACTIVE"));

        assertThatThrownBy(() -> service(store, accounts, mock(UserInfoPort.class))
                .getTransaction("other-user", transaction.getTransactionId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(BusinessErrorCode.TRANSACTION_NOT_FOUND));
    }

    @Test
    void 交易详情返回双方展示信息和转账备注() {
        BusinessStore store = mock(BusinessStore.class);
        AccountDirectoryPort accounts = mock(AccountDirectoryPort.class);
        UserInfoPort userInfo = mock(UserInfoPort.class);
        FundTransaction transaction = transfer();
        TransferDraft draft = TransferDraft.create("draft-1", "payer-user", "payee-user",
                "payer-account", "payee-account", 2_000L, "晚餐AA", NOW);
        when(store.findTransaction(transaction.getTransactionId()))
                .thenReturn(Optional.of(new BusinessStore.FundTransactionRecord(transaction, null)));
        when(store.findDraft("draft-1")).thenReturn(Optional.of(draft));
        when(userInfo.findUserInfo("payer-user"))
                .thenReturn(new UserInfoPort.UserInfo("payer-user", "付款人实名", "小王"));
        when(userInfo.findUserInfo("payee-user"))
                .thenReturn(new UserInfoPort.UserInfo("payee-user", "收款人实名", "小李"));

        var detail = service(store, accounts, userInfo)
                .getTransactionDetail("payer-user", transaction.getTransactionId());

        assertThat(detail.payerUserId()).isEqualTo("payer-user");
        assertThat(detail.payerDisplayName()).isEqualTo("小王");
        assertThat(detail.payeeUserId()).isEqualTo("payee-user");
        assertThat(detail.payeeDisplayName()).isEqualTo("小李");
        assertThat(detail.remark()).isEqualTo("晚餐AA");
    }

    private TransferApplicationService service(BusinessStore store, AccountDirectoryPort accounts,
                                               UserInfoPort userInfo) {
        return new TransferApplicationService(store, accounts, mock(PaymentProofPort.class),
                mock(TccCoordinatorPort.class), mock(SecurityMaterialPort.class),
                new IdempotencyKeyValidator(), mock(ContactArchivePort.class), userInfo,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private FundTransaction transfer() {
        return FundTransaction.accept("transaction-1", TransactionType.TRANSFER, SourceType.TRANSFER_DRAFT,
                "draft-1", "payer-user", "payer-account", "payee-account", FundingSource.BALANCE,
                2_000L, "idem-key-00000001", "LOW", "0".repeat(32), NOW);
    }
}
