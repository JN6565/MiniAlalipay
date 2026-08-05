package com.minialalipay.account.application.credit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minialalipay.account.domain.account.Account;
import com.minialalipay.account.domain.account.AccountRepository;
import com.minialalipay.account.domain.bill.CreditBillItemRepository;
import com.minialalipay.account.domain.bill.CreditBillRepository;
import com.minialalipay.account.domain.credit.CreditAccount;
import com.minialalipay.account.domain.credit.CreditAccountRepository;
import com.minialalipay.account.domain.credit.CreditAccountStatus;
import com.minialalipay.account.domain.credit.CreditPurchase;
import com.minialalipay.account.domain.credit.CreditPurchaseRepository;
import com.minialalipay.account.domain.credit.CreditReceivable;
import com.minialalipay.account.domain.credit.CreditReceivableRepository;
import com.minialalipay.account.domain.repayment.CreditRepayment;
import com.minialalipay.account.domain.repayment.CreditRepaymentAllocationRepository;
import com.minialalipay.account.domain.repayment.CreditRepaymentDraft;
import com.minialalipay.account.domain.repayment.CreditRepaymentDraftRepository;
import com.minialalipay.account.domain.repayment.CreditRepaymentRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** 信用还款接口应用层的支付证明、幂等和逐笔分配测试。 */
class CreditRepaymentServiceTest {

    @Test
    void shouldConsumeProofAndApplyUnbilledPurchaseAllocation() {
        CreditAccountRepository creditAccountRepository = mock(CreditAccountRepository.class);
        CreditReceivableRepository receivableRepository = mock(CreditReceivableRepository.class);
        CreditPurchaseRepository purchaseRepository = mock(CreditPurchaseRepository.class);
        CreditBillRepository billRepository = mock(CreditBillRepository.class);
        CreditBillItemRepository billItemRepository = mock(CreditBillItemRepository.class);
        CreditRepaymentDraftRepository draftRepository = mock(CreditRepaymentDraftRepository.class);
        CreditRepaymentRepository repaymentRepository = mock(CreditRepaymentRepository.class);
        CreditRepaymentAllocationRepository allocationRepository = mock(CreditRepaymentAllocationRepository.class);
        AccountRepository accountRepository = mock(AccountRepository.class);
        CreditRepayTccParticipant tccParticipant = mock(CreditRepayTccParticipant.class);
        PaymentProofPort paymentProofPort = mock(PaymentProofPort.class);

        CreditAccount creditAccount = mock(CreditAccount.class);
        when(creditAccount.getCreditAccountId()).thenReturn("credit-1");
        when(creditAccount.getUserId()).thenReturn("user-1");
        when(creditAccount.getStatus()).thenReturn(CreditAccountStatus.ACTIVE);
        when(creditAccountRepository.findByUserId("user-1")).thenReturn(Optional.of(creditAccount));
        when(creditAccountRepository.findById("credit-1")).thenReturn(Optional.of(creditAccount));

        Account payerAccount = mock(Account.class);
        when(payerAccount.getAccountId()).thenReturn("account-1");
        when(accountRepository.findByUserId("user-1")).thenReturn(Optional.of(payerAccount));

        CreditReceivable receivable = mock(CreditReceivable.class);
        when(receivable.getTotalOutstandingFen()).thenReturn(10_000L);
        when(receivableRepository.findByCreditAccountId("credit-1")).thenReturn(Optional.of(receivable));

        CreditPurchase purchase = new CreditPurchase(
                "purchase-1", "transaction-pay-1", "credit-1",
                "qr-order-1", "merchant-1", 10_000L, Instant.parse("2026-08-01T00:00:00Z"));
        when(purchaseRepository.findByCreditAccountIdAndBillingStatus("credit-1", "UNBILLED"))
                .thenReturn(List.of(purchase));
        when(purchaseRepository.findById("purchase-1")).thenReturn(Optional.of(purchase));
        when(billRepository.findByCreditAccountId("credit-1")).thenReturn(List.of());

        AtomicReference<CreditRepaymentDraft> draftRef = new AtomicReference<>();
        when(draftRepository.findById(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(draftRef.get()));
        doAnswer(invocation -> {
            draftRef.set(invocation.getArgument(0));
            return null;
        }).when(draftRepository).save(any(CreditRepaymentDraft.class));

        AtomicReference<CreditRepayment> repaymentRef = new AtomicReference<>();
        when(repaymentRepository.findByRepaymentDraftId(anyString())).thenReturn(Optional.empty());
        when(repaymentRepository.findById(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(repaymentRef.get()));
        doAnswer(invocation -> {
            repaymentRef.set(invocation.getArgument(0));
            return null;
        }).when(repaymentRepository).save(any(CreditRepayment.class));
        doAnswer(invocation -> {
            repaymentRef.get().markSuccess(Instant.now());
            return null;
        }).when(tccParticipant).confirmRepay(anyString(), anyString(), anyString(),
                anyLong(), anyString(), any(Instant.class));

        CreditRepaymentService service = new CreditRepaymentService(
                creditAccountRepository, receivableRepository, purchaseRepository,
                billRepository, billItemRepository, draftRepository, repaymentRepository,
                allocationRepository, accountRepository, tccParticipant,
                paymentProofPort, new ObjectMapper());

        var draft = service.createRepaymentDraft("user-1", 10_000L, "draft-key-123456");
        var repayment = service.submitRepayment(
                "user-1", draft.repaymentDraftId(), "proof-secret", "repay-key-123456");

        assertThat(repayment.status()).isEqualTo("SUCCESS");
        assertThat(purchase.getRepaidFen()).isEqualTo(10_000L);
        assertThat(purchase.getOutstandingFen()).isZero();
        verify(paymentProofPort).verify("user-1", "proof-secret", "CREDIT_REPAY");
        verify(allocationRepository).saveAll(anyString(), any());
    }
}
