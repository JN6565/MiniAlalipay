package com.minialalipay.account.application.credit;

import com.minialalipay.account.application.account.BalanceApplicationService;
import com.minialalipay.account.domain.account.Account;
import com.minialalipay.account.domain.account.AccountBalance;
import com.minialalipay.account.domain.account.AccountRepository;
import com.minialalipay.account.domain.account.AccountStatus;
import com.minialalipay.account.domain.account.AccountType;
import com.minialalipay.account.domain.account.FreezePurpose;
import com.minialalipay.account.domain.account.FreezeRecord;
import com.minialalipay.account.domain.account.FreezeRecordRepository;
import com.minialalipay.account.domain.account.FreezeStatus;
import com.minialalipay.account.domain.credit.CreditAccount;
import com.minialalipay.account.domain.credit.CreditAccountRepository;
import com.minialalipay.account.domain.credit.CreditAccountStatus;
import com.minialalipay.account.domain.credit.CreditReceivable;
import com.minialalipay.account.domain.credit.CreditReceivableRepository;
import com.minialalipay.account.domain.repayment.CreditRepayment;
import com.minialalipay.account.domain.repayment.CreditRepaymentRepository;
import com.minialalipay.account.domain.repayment.CreditRepaymentStatus;
import com.minialalipay.account.domain.tcc.RollbackType;
import com.minialalipay.account.domain.tcc.TccBranchStatus;
import com.minialalipay.account.domain.tcc.TccBranchType;
import com.minialalipay.common.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CreditRepayTccParticipant（CREDIT_REPAY 分支）单元测试。
 *
 * <p>覆盖还款 Try/Confirm/Cancel 的幂等、空回滚、金额守恒和余额联动。</p>
 */
@DisplayName("CREDIT_REPAY TCC 分支参与者测试")
class CreditRepayTccParticipantTest {

    private static final Instant NOW = Instant.parse("2026-08-04T08:00:00Z");
    private static final String ACCOUNT_ID = "acc-1";
    private static final String CREDIT_ACCOUNT_ID = "credit-acc-1";
    private static final String TRANSACTION_ID = "tx-repay-001";
    private static final String BRANCH_XID = "branch-repay-001";
    private static final long REPAY_AMOUNT = 30_000L;
    private static final long INITIAL_BALANCE = 100_000L;

    private TestAccountRepository accountRepo;
    private TestFreezeRecordRepository freezeRepo;
    private BalanceApplicationService balanceService;
    private TestCreditAccountRepository creditAccountRepo;
    private TestCreditReceivableRepository receivableRepo;
    private TestCreditRepaymentRepository repaymentRepo;
    private InMemoryTccBranchRepository branchRepo;
    private CreditRepayTccParticipant participant;

    @BeforeEach
    void setUp() {
        accountRepo = new TestAccountRepository();
        freezeRepo = new TestFreezeRecordRepository();
        balanceService = new BalanceApplicationService(accountRepo, freezeRepo);
        creditAccountRepo = new TestCreditAccountRepository();
        receivableRepo = new TestCreditReceivableRepository();
        repaymentRepo = new TestCreditRepaymentRepository();
        branchRepo = new InMemoryTccBranchRepository();
        participant = new CreditRepayTccParticipant(
                balanceService, creditAccountRepo, receivableRepo, repaymentRepo, branchRepo
        );

        // 初始化余额账户
        accountRepo.create(
                Account.open(ACCOUNT_ID, "user-1", "reg-1", NOW),
                new AccountBalance(ACCOUNT_ID, INITIAL_BALANCE, 0L, 0L, NOW)
        );

        // 初始化信用账户：有已用额度
        CreditAccount creditAccount = new CreditAccount(
                CREDIT_ACCOUNT_ID, "user-1",
                CreditAccount.FIXED_TOTAL_LIMIT_FEN, REPAY_AMOUNT, 0L,
                com.minialalipay.account.domain.credit.CreditAccountStatus.ACTIVE,
                null, 0L, NOW, NOW
        );
        creditAccountRepo.save(creditAccount);

        // 初始化信用应收：有未出账应收
        CreditReceivable receivable = new CreditReceivable(
                CREDIT_ACCOUNT_ID, REPAY_AMOUNT, 0L, 0L, 0L, NOW
        );
        receivableRepo.save(receivable);

        // 初始化还款记录
        CreditRepayment repayment = new CreditRepayment(
                "repayment-1", "draft-1", TRANSACTION_ID,
                CREDIT_ACCOUNT_ID, REPAY_AMOUNT, NOW
        );
        repaymentRepo.save(repayment);
    }

    // ========== Try 阶段测试 ==========

    @Test
    @DisplayName("Try 成功：冻结余额")
    void tryRepaySuccess() {
        participant.tryRepay(TRANSACTION_ID, ACCOUNT_ID, CREDIT_ACCOUNT_ID,
                REPAY_AMOUNT, BRANCH_XID, NOW);

        AccountBalance balance = accountRepo.findBalance(ACCOUNT_ID).orElseThrow();
        assertThat(balance.getAvailableFen()).isEqualTo(INITIAL_BALANCE - REPAY_AMOUNT);
        assertThat(balance.getFrozenFen()).isEqualTo(REPAY_AMOUNT);
    }

    @Test
    @DisplayName("Try 幂等：重复调用余额不变")
    void tryRepayIdempotent() {
        participant.tryRepay(TRANSACTION_ID, ACCOUNT_ID, CREDIT_ACCOUNT_ID,
                REPAY_AMOUNT, BRANCH_XID, NOW);
        participant.tryRepay(TRANSACTION_ID, ACCOUNT_ID, CREDIT_ACCOUNT_ID,
                REPAY_AMOUNT, BRANCH_XID, NOW.plusSeconds(1));

        AccountBalance balance = accountRepo.findBalance(ACCOUNT_ID).orElseThrow();
        assertThat(balance.getAvailableFen()).isEqualTo(INITIAL_BALANCE - REPAY_AMOUNT);
        assertThat(balance.getFrozenFen()).isEqualTo(REPAY_AMOUNT);
    }

    @Test
    @DisplayName("Try 余额不足 → INSUFFICIENT_BALANCE")
    void tryRepayInsufficientBalanceThrows() {
        assertThatThrownBy(() -> participant.tryRepay(
                TRANSACTION_ID, ACCOUNT_ID, CREDIT_ACCOUNT_ID,
                INITIAL_BALANCE + 1, BRANCH_XID, NOW
        )).isInstanceOf(BusinessException.class)
          .extracting(e -> ((BusinessException) e).errorCode().code())
          .isEqualTo("INSUFFICIENT_BALANCE");
    }

    // ========== Confirm 阶段测试 ==========

    @Test
    @DisplayName("Confirm 成功：扣减余额，减少应收，恢复额度，标记还款成功")
    void confirmRepaySuccess() {
        participant.tryRepay(TRANSACTION_ID, ACCOUNT_ID, CREDIT_ACCOUNT_ID,
                REPAY_AMOUNT, BRANCH_XID, NOW);
        participant.confirmRepay(TRANSACTION_ID, ACCOUNT_ID, CREDIT_ACCOUNT_ID,
                REPAY_AMOUNT, BRANCH_XID, NOW.plusSeconds(1));

        // 余额已扣减
        AccountBalance balance = accountRepo.findBalance(ACCOUNT_ID).orElseThrow();
        assertThat(balance.getAvailableFen()).isEqualTo(INITIAL_BALANCE - REPAY_AMOUNT);
        assertThat(balance.getFrozenFen()).isZero();

        // 信用应收已减少
        CreditReceivable receivable = receivableRepo.findByCreditAccountId(CREDIT_ACCOUNT_ID).orElseThrow();
        assertThat(receivable.getUnbilledFen()).isZero();

        // 信用账户已用额度已恢复
        CreditAccount account = creditAccountRepo.findById(CREDIT_ACCOUNT_ID).orElseThrow();
        assertThat(account.getUsedFen()).isZero();
        assertThat(account.getAvailableFen()).isEqualTo(CreditAccount.FIXED_TOTAL_LIMIT_FEN);

        // 还款记录已标记成功
        CreditRepayment repayment = repaymentRepo.findByTransactionId(TRANSACTION_ID).orElseThrow();
        assertThat(repayment.getStatus()).isEqualTo(CreditRepaymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("Confirm 幂等：重复调用不产生副作用")
    void confirmRepayIdempotent() {
        participant.tryRepay(TRANSACTION_ID, ACCOUNT_ID, CREDIT_ACCOUNT_ID,
                REPAY_AMOUNT, BRANCH_XID, NOW);
        participant.confirmRepay(TRANSACTION_ID, ACCOUNT_ID, CREDIT_ACCOUNT_ID,
                REPAY_AMOUNT, BRANCH_XID, NOW.plusSeconds(1));
        participant.confirmRepay(TRANSACTION_ID, ACCOUNT_ID, CREDIT_ACCOUNT_ID,
                REPAY_AMOUNT, BRANCH_XID, NOW.plusSeconds(2));

        AccountBalance balance = accountRepo.findBalance(ACCOUNT_ID).orElseThrow();
        assertThat(balance.getAvailableFen()).isEqualTo(INITIAL_BALANCE - REPAY_AMOUNT);

        CreditAccount account = creditAccountRepo.findById(CREDIT_ACCOUNT_ID).orElseThrow();
        assertThat(account.getUsedFen()).isZero();
    }

    // ========== Cancel 阶段测试 ==========

    @Test
    @DisplayName("Cancel 成功：释放余额冻结，标记还款取消")
    void cancelRepaySuccess() {
        participant.tryRepay(TRANSACTION_ID, ACCOUNT_ID, CREDIT_ACCOUNT_ID,
                REPAY_AMOUNT, BRANCH_XID, NOW);
        participant.cancelRepay(
                TRANSACTION_ID, ACCOUNT_ID, CREDIT_ACCOUNT_ID,
                REPAY_AMOUNT, BRANCH_XID, NOW.plusSeconds(1));

        // 余额已恢复
        AccountBalance balance = accountRepo.findBalance(ACCOUNT_ID).orElseThrow();
        assertThat(balance.getAvailableFen()).isEqualTo(INITIAL_BALANCE);
        assertThat(balance.getFrozenFen()).isZero();

        // 信用账户不变（未恢复额度）
        CreditAccount account = creditAccountRepo.findById(CREDIT_ACCOUNT_ID).orElseThrow();
        assertThat(account.getUsedFen()).isEqualTo(REPAY_AMOUNT);

        // 还款记录已标记取消
        CreditRepayment repayment = repaymentRepo.findByTransactionId(TRANSACTION_ID).orElseThrow();
        assertThat(repayment.getStatus()).isEqualTo(CreditRepaymentStatus.CANCELLED);
    }

    @Test
    @DisplayName("Cancel 幂等：重复调用不产生副作用")
    void cancelRepayIdempotent() {
        participant.tryRepay(TRANSACTION_ID, ACCOUNT_ID, CREDIT_ACCOUNT_ID,
                REPAY_AMOUNT, BRANCH_XID, NOW);
        participant.cancelRepay(
                TRANSACTION_ID, ACCOUNT_ID, CREDIT_ACCOUNT_ID,
                REPAY_AMOUNT, BRANCH_XID, NOW.plusSeconds(1));
        participant.cancelRepay(
                TRANSACTION_ID, ACCOUNT_ID, CREDIT_ACCOUNT_ID,
                REPAY_AMOUNT, BRANCH_XID, NOW.plusSeconds(2));

        AccountBalance balance = accountRepo.findBalance(ACCOUNT_ID).orElseThrow();
        assertThat(balance.getAvailableFen()).isEqualTo(INITIAL_BALANCE);
    }

    @Test
    @DisplayName("Cancel 空回滚：Try 未执行时快速返回")
    void cancelRepayEmptyRollback() {
        // 不执行 Try，直接 Cancel
        participant.cancelRepay(
                TRANSACTION_ID, ACCOUNT_ID, CREDIT_ACCOUNT_ID, REPAY_AMOUNT, BRANCH_XID, NOW);

        // 余额不变
        AccountBalance balance = accountRepo.findBalance(ACCOUNT_ID).orElseThrow();
        assertThat(balance.getAvailableFen()).isEqualTo(INITIAL_BALANCE);
        assertThat(balance.getFrozenFen()).isZero();

        // 还款记录标记取消
        CreditRepayment repayment = repaymentRepo.findByTransactionId(TRANSACTION_ID).orElseThrow();
        assertThat(repayment.getStatus()).isEqualTo(CreditRepaymentStatus.CANCELLED);

        var branch = branchRepo.findAccountBranchForUpdate(
                BRANCH_XID, TccBranchType.CREDIT_REPAY, CREDIT_ACCOUNT_ID).orElseThrow();
        assertThat(branch.getStatus()).isEqualTo(TccBranchStatus.CANCELLED);
        assertThat(branch.getRollbackType()).isEqualTo(RollbackType.EMPTY);

        assertThatThrownBy(() -> participant.tryRepay(
                TRANSACTION_ID, ACCOUNT_ID, CREDIT_ACCOUNT_ID,
                REPAY_AMOUNT, BRANCH_XID, NOW.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("拒绝晚到 Try");
    }

    // ========== 金额守恒测试 ==========

    @Test
    @DisplayName("金额守恒：Try 冻结 == Confirm 扣减 == 还款金额")
    void amountConservation() {
        participant.tryRepay(TRANSACTION_ID, ACCOUNT_ID, CREDIT_ACCOUNT_ID,
                REPAY_AMOUNT, BRANCH_XID, NOW);

        // Try 后：冻结 == 还款金额
        AccountBalance afterTry = accountRepo.findBalance(ACCOUNT_ID).orElseThrow();
        assertThat(afterTry.getFrozenFen()).isEqualTo(REPAY_AMOUNT);
        assertThat(afterTry.getTotalFen()).isEqualTo(INITIAL_BALANCE);

        participant.confirmRepay(TRANSACTION_ID, ACCOUNT_ID, CREDIT_ACCOUNT_ID,
                REPAY_AMOUNT, BRANCH_XID, NOW.plusSeconds(1));

        // Confirm 后：扣减 == 还款金额
        AccountBalance afterConfirm = accountRepo.findBalance(ACCOUNT_ID).orElseThrow();
        assertThat(afterConfirm.getTotalFen()).isEqualTo(INITIAL_BALANCE - REPAY_AMOUNT);

        // 信用账户恢复 == 还款金额
        CreditAccount creditAccount = creditAccountRepo.findById(CREDIT_ACCOUNT_ID).orElseThrow();
        assertThat(creditAccount.getUsedFen()).isZero();

        // 应收减少 == 还款金额
        CreditReceivable receivable = receivableRepo.findByCreditAccountId(CREDIT_ACCOUNT_ID).orElseThrow();
        assertThat(receivable.getTotalOutstandingFen()).isZero();
    }

    // ========== 完整生命周期测试 ==========

    @Test
    @DisplayName("Try → Cancel → Try（新交易）余额恢复后可再次使用")
    void tryCancelThenNewTry() {
        participant.tryRepay("tx-1", ACCOUNT_ID, CREDIT_ACCOUNT_ID,
                REPAY_AMOUNT, "xid-1", NOW);
        participant.cancelRepay(
                "tx-1", ACCOUNT_ID, CREDIT_ACCOUNT_ID,
                REPAY_AMOUNT, "xid-1", NOW.plusSeconds(1));

        // 取消后余额恢复，可以发起新还款
        participant.tryRepay("tx-2", ACCOUNT_ID, CREDIT_ACCOUNT_ID,
                REPAY_AMOUNT, "xid-2", NOW.plusSeconds(2));

        AccountBalance balance = accountRepo.findBalance(ACCOUNT_ID).orElseThrow();
        assertThat(balance.getAvailableFen()).isEqualTo(INITIAL_BALANCE - REPAY_AMOUNT);
        assertThat(balance.getFrozenFen()).isEqualTo(REPAY_AMOUNT);
    }

    // ========== 内存仓储实现 ==========

    static class TestAccountRepository implements AccountRepository {
        final Map<String, Account> accounts = new HashMap<>();
        final Map<String, AccountBalance> balances = new HashMap<>();

        @Override
        public Optional<Account> findByRegistrationId(String registrationId) {
            return accounts.values().stream()
                    .filter(a -> a.getRegistrationId().equals(registrationId)).findFirst();
        }

        @Override
        public Optional<Account> findByUserId(String userId) {
            return accounts.values().stream()
                    .filter(a -> a.getUserId().equals(userId)).findFirst();
        }

        @Override
        public Optional<Account> findById(String accountId) {
            return Optional.ofNullable(accounts.get(accountId));
        }

        @Override
        public Optional<AccountBalance> findBalance(String accountId) {
            return Optional.ofNullable(balances.get(accountId));
        }

        @Override
        public void create(Account account, AccountBalance balance) {
            accounts.put(account.getAccountId(), account);
            balances.put(balance.getAccountId(), balance);
        }

        @Override
        public boolean updateBalance(AccountBalance balance, long expectedVersion) {
            AccountBalance current = balances.get(balance.getAccountId());
            if (current == null || current.getVersion() != expectedVersion) return false;
            balance.updateVersion(expectedVersion + 1);
            balances.put(balance.getAccountId(), balance);
            return true;
        }

        @Override
        public boolean updateBalanceForActiveAccount(AccountBalance balance, long expectedVersion) {
            Account account = accounts.get(balance.getAccountId());
            return account != null && account.getStatus() == AccountStatus.ACTIVE
                    && updateBalance(balance, expectedVersion);
        }
    }

    static class TestFreezeRecordRepository implements FreezeRecordRepository {
        final Map<String, FreezeRecord> records = new HashMap<>();

        private String key(String txId, String accId, FreezePurpose purpose) {
            return txId + ':' + accId + ':' + purpose;
        }

        @Override
        public Optional<FreezeRecord> find(String txId, String accId, FreezePurpose purpose) {
            return Optional.ofNullable(records.get(key(txId, accId, purpose)));
        }

        @Override
        public Optional<FreezeRecord> findForUpdate(String txId, String accId, FreezePurpose purpose) {
            return find(txId, accId, purpose);
        }

        @Override
        public void create(FreezeRecord record) {
            records.put(key(record.getTransactionId(), record.getAccountId(), record.getPurpose()), record);
        }

        @Override
        public boolean update(FreezeRecord record, long expectedVersion) {
            if (record.getVersion() != expectedVersion) return false;
            record.updateVersion(expectedVersion + 1);
            records.put(key(record.getTransactionId(), record.getAccountId(), record.getPurpose()), record);
            return true;
        }
    }

    static class TestCreditAccountRepository implements CreditAccountRepository {
        final Map<String, CreditAccount> accounts = new HashMap<>();

        @Override
        public Optional<CreditAccount> findByUserId(String userId) {
            return accounts.values().stream()
                    .filter(a -> a.getUserId().equals(userId)).findFirst();
        }

        @Override
        public Optional<CreditAccount> findById(String creditAccountId) {
            return Optional.ofNullable(accounts.get(creditAccountId));
        }

        @Override
        public List<CreditAccount> findByStatus(CreditAccountStatus status) {
            return accounts.values().stream()
                    .filter(a -> a.getStatus() == status)
                    .toList();
        }

        @Override
        public void save(CreditAccount account) {
            accounts.put(account.getCreditAccountId(), account);
            account.updateVersion(account.getVersion() + 1);
        }
    }

    static class TestCreditReceivableRepository implements CreditReceivableRepository {
        final Map<String, CreditReceivable> receivables = new HashMap<>();

        @Override
        public Optional<CreditReceivable> findByCreditAccountId(String creditAccountId) {
            return Optional.ofNullable(receivables.get(creditAccountId));
        }

        @Override
        public void save(CreditReceivable receivable) {
            receivables.put(receivable.getCreditAccountId(), receivable);
            receivable.updateVersion(receivable.getVersion() + 1);
        }
    }

    static class TestCreditRepaymentRepository implements CreditRepaymentRepository {
        final Map<String, CreditRepayment> repayments = new HashMap<>();

        @Override
        public Optional<CreditRepayment> findById(String repaymentId) {
            return Optional.ofNullable(repayments.get(repaymentId));
        }

        @Override
        public Optional<CreditRepayment> findByRepaymentDraftId(String repaymentDraftId) {
            return repayments.values().stream()
                    .filter(r -> r.getRepaymentDraftId().equals(repaymentDraftId))
                    .findFirst();
        }

        @Override
        public Optional<CreditRepayment> findByTransactionId(String transactionId) {
            return repayments.values().stream()
                    .filter(r -> r.getTransactionId().equals(transactionId))
                    .findFirst();
        }

        @Override
        public void save(CreditRepayment repayment) {
            repayments.put(repayment.getRepaymentId(), repayment);
        }
    }
}
