package com.minialalipay.account.application.reconciliation;

import com.minialalipay.account.domain.account.FreezeRecordRepository;
import com.minialalipay.account.domain.account.FreezeStatus;
import com.minialalipay.account.domain.credit.CreditFreeze;
import com.minialalipay.account.domain.credit.CreditFreezeRepository;
import com.minialalipay.account.domain.credit.CreditFreezeStatus;
import com.minialalipay.account.domain.ledger.LedgerRepository;
import com.minialalipay.account.domain.tcc.TccBranchRepository;
import com.minialalipay.account.domain.tcc.TccBranchStatus;
import com.minialalipay.account.domain.tcc.TccBranchType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 终态事实核验规则集测试。
 *
 * <p>核验必须按资金路径选择规则：花呗信用支付查信用冻结与 CREDIT_PAY_LEDGER 账本分支，
 * 余额转账查余额冻结与 TRANSFER 账本分支；两者互斥，禁止混用导致误判事实不一致。</p>
 */
class TransactionFactApplicationServiceTest {
    private static final String TX = "01K1TX0002GH3JK4MN5PQRSTV";

    @Test
    void 花呗支付确认事实按信用规则集核验为一致() {
        TccBranchRepository branches = mock(TccBranchRepository.class);
        FreezeRecordRepository freezes = mock(FreezeRecordRepository.class);
        LedgerRepository ledgers = mock(LedgerRepository.class);
        CreditFreezeRepository creditFreezes = mock(CreditFreezeRepository.class);
        when(branches.hasAccountBranch(TX, TccBranchType.CREDIT_PAY)).thenReturn(true);
        when(branches.allAccountBranches(TX, TccBranchStatus.CONFIRMED, 2)).thenReturn(true);
        when(branches.ledgerBranchIs(TX, TccBranchType.CREDIT_PAY_LEDGER, TccBranchStatus.CONFIRMED)).thenReturn(true);
        when(creditFreezes.findByTransactionId(TX)).thenReturn(Optional.of(creditFreeze(CreditFreezeStatus.CONFIRMED)));
        when(ledgers.isPostedAndBalanced(TX)).thenReturn(true);

        var facts = new TransactionFactApplicationService(branches, freezes, ledgers, creditFreezes).inspect(TX);

        assertThat(facts.successConsistent()).isTrue();
        assertThat(facts.cancelConsistent()).isFalse();
        // 花呗支付没有付款余额冻结，不得用余额冻结规则参与判定
        verifyNoInteractions(freezes);
    }

    @Test
    void 花呗支付取消事实按信用规则集核验为一致() {
        TccBranchRepository branches = mock(TccBranchRepository.class);
        FreezeRecordRepository freezes = mock(FreezeRecordRepository.class);
        LedgerRepository ledgers = mock(LedgerRepository.class);
        CreditFreezeRepository creditFreezes = mock(CreditFreezeRepository.class);
        when(branches.hasAccountBranch(TX, TccBranchType.CREDIT_PAY)).thenReturn(true);
        when(branches.allAccountBranches(TX, TccBranchStatus.CANCELLED, 2)).thenReturn(true);
        when(branches.ledgerBranchIs(TX, TccBranchType.CREDIT_PAY_LEDGER, TccBranchStatus.CANCELLED)).thenReturn(true);
        // 空回滚无冻结记录也视为取消事实一致；已有记录必须已释放
        when(creditFreezes.findByTransactionId(TX)).thenReturn(Optional.of(creditFreeze(CreditFreezeStatus.RELEASED)));
        when(freezes.transactionHasNoActiveFreeze(TX)).thenReturn(true);

        var facts = new TransactionFactApplicationService(branches, freezes, ledgers, creditFreezes).inspect(TX);

        assertThat(facts.cancelConsistent()).isTrue();
        assertThat(facts.successConsistent()).isFalse();
    }

    @Test
    void 余额转账继续使用原有余额规则集不受影响() {
        TccBranchRepository branches = mock(TccBranchRepository.class);
        FreezeRecordRepository freezes = mock(FreezeRecordRepository.class);
        LedgerRepository ledgers = mock(LedgerRepository.class);
        CreditFreezeRepository creditFreezes = mock(CreditFreezeRepository.class);
        when(branches.hasAccountBranch(TX, TccBranchType.CREDIT_PAY)).thenReturn(false);
        when(branches.allAccountBranches(TX, TccBranchStatus.CONFIRMED, 2)).thenReturn(true);
        when(branches.ledgerBranchIs(TX, TccBranchStatus.CONFIRMED)).thenReturn(true);
        when(freezes.transactionFreezeIs(TX, FreezeStatus.CONFIRMED)).thenReturn(true);
        when(ledgers.isPostedAndBalanced(TX)).thenReturn(true);

        var facts = new TransactionFactApplicationService(branches, freezes, ledgers, creditFreezes).inspect(TX);

        assertThat(facts.successConsistent()).isTrue();
        // 余额路径不得触碰信用冻结表
        verifyNoInteractions(creditFreezes);
        verify(freezes).transactionFreezeIs(TX, FreezeStatus.CONFIRMED);
    }

    @Test
    void 银行卡出资转账必须等待收款方账本过账后才一致() {
        TccBranchRepository branches = mock(TccBranchRepository.class);
        FreezeRecordRepository freezes = mock(FreezeRecordRepository.class);
        LedgerRepository ledgers = mock(LedgerRepository.class);
        CreditFreezeRepository creditFreezes = mock(CreditFreezeRepository.class);
        when(branches.hasAccountBranch(TX, TccBranchType.CREDIT_PAY)).thenReturn(false);
        when(branches.hasAccountBranch(TX, TccBranchType.BANK_CARD_WITHDRAW)).thenReturn(false);
        when(branches.hasAccountBranch(TX, TccBranchType.BANK_CARD_RECHARGE)).thenReturn(true);
        when(branches.allAccountBranches(TX, TccBranchStatus.CONFIRMED, 2)).thenReturn(true);
        when(branches.hasLedgerBranch(TX, TccBranchType.LEDGER)).thenReturn(true);
        when(branches.ledgerBranchIs(TX, TccBranchStatus.CONFIRMED)).thenReturn(true);
        when(ledgers.isPostedAndBalanced(TX)).thenReturn(false);

        var facts = new TransactionFactApplicationService(branches, freezes, ledgers, creditFreezes).inspect(TX);

        assertThat(facts.accountsConfirmed()).isTrue();
        assertThat(facts.ledgerConfirmed()).isTrue();
        assertThat(facts.ledgerPosted()).isFalse();
        assertThat(facts.successConsistent()).isFalse();
    }

    @Test
    void 普通银行卡充值仍可不要求账本分支() {
        TccBranchRepository branches = mock(TccBranchRepository.class);
        FreezeRecordRepository freezes = mock(FreezeRecordRepository.class);
        LedgerRepository ledgers = mock(LedgerRepository.class);
        CreditFreezeRepository creditFreezes = mock(CreditFreezeRepository.class);
        when(branches.hasAccountBranch(TX, TccBranchType.CREDIT_PAY)).thenReturn(false);
        when(branches.hasAccountBranch(TX, TccBranchType.BANK_CARD_WITHDRAW)).thenReturn(false);
        when(branches.hasAccountBranch(TX, TccBranchType.BANK_CARD_RECHARGE)).thenReturn(true);
        when(branches.allAccountBranches(TX, TccBranchStatus.CONFIRMED, 2)).thenReturn(true);
        when(branches.hasLedgerBranch(TX, TccBranchType.LEDGER)).thenReturn(false);

        var facts = new TransactionFactApplicationService(branches, freezes, ledgers, creditFreezes).inspect(TX);

        assertThat(facts.successConsistent()).isTrue();
        assertThat(facts.ledgerPosted()).isTrue();
        verify(ledgers, never()).isPostedAndBalanced(TX);
    }

    private static CreditFreeze creditFreeze(CreditFreezeStatus status) {
        return new CreditFreeze("01K1CFZ002GH3JK4MN5PQRSTV", TX, "01K1CRD002GH3JK4MN5PQRSTV",
                2200L, status, "tcc:" + TX, 0L,
                Instant.parse("2026-08-04T08:00:00Z"), Instant.parse("2026-08-04T08:00:05Z"));
    }
}
