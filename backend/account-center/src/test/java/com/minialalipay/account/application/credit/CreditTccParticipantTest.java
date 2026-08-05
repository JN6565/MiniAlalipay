package com.minialalipay.account.application.credit;

import com.minialalipay.account.domain.credit.CreditAccount;
import com.minialalipay.account.domain.credit.CreditAccountRepository;
import com.minialalipay.account.domain.credit.CreditAccountStatus;
import com.minialalipay.account.domain.credit.CreditFreeze;
import com.minialalipay.account.domain.credit.CreditFreezeRepository;
import com.minialalipay.account.domain.credit.CreditFreezeStatus;
import com.minialalipay.account.domain.credit.CreditPurchase;
import com.minialalipay.account.domain.credit.CreditPurchaseBillingStatus;
import com.minialalipay.account.domain.credit.CreditPurchaseRepository;
import com.minialalipay.account.domain.credit.CreditReceivable;
import com.minialalipay.account.domain.credit.CreditReceivableRepository;
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
 * CreditTccParticipant（CREDIT_PAY 分支）单元测试。
 *
 * <p>覆盖幂等、空回滚、防悬挂、并发安全和金额守恒等 TCC 核心场景。</p>
 */
@DisplayName("CREDIT_PAY TCC 分支参与者测试")
class CreditTccParticipantTest {

    private static final Instant NOW = Instant.parse("2026-08-04T08:00:00Z");
    private static final String CREDIT_ACCOUNT_ID = "credit-acc-1";
    private static final String TRANSACTION_ID = "tx-001";
    private static final String BRANCH_XID = "branch-001";
    private static final long AMOUNT_FEN = 50_000L;

    private InMemoryCreditAccountRepository accountRepo;
    private InMemoryCreditFreezeRepository freezeRepo;
    private InMemoryCreditPurchaseRepository purchaseRepo;
    private InMemoryCreditReceivableRepository receivableRepo;
    private InMemoryTccBranchRepository branchRepo;
    private CreditTccParticipant participant;

    @BeforeEach
    void setUp() {
        accountRepo = new InMemoryCreditAccountRepository();
        freezeRepo = new InMemoryCreditFreezeRepository();
        purchaseRepo = new InMemoryCreditPurchaseRepository();
        receivableRepo = new InMemoryCreditReceivableRepository();
        branchRepo = new InMemoryTccBranchRepository();
        participant = new CreditTccParticipant(
                accountRepo, freezeRepo, purchaseRepo, receivableRepo, branchRepo);

        // 初始化信用账户和应收
        CreditAccount account = new CreditAccount(CREDIT_ACCOUNT_ID, "user-1", NOW);
        accountRepo.save(account);
        CreditReceivable receivable = new CreditReceivable(CREDIT_ACCOUNT_ID, NOW);
        receivableRepo.save(receivable);
    }

    // ========== Try 阶段测试 ==========

    @Test
    @DisplayName("Try 成功：冻结额度，创建冻结记录")
    void tryFreezeSuccess() {
        CreditFreeze result = participant.tryFreeze(
                TRANSACTION_ID, CREDIT_ACCOUNT_ID, AMOUNT_FEN, BRANCH_XID, NOW
        );

        assertThat(result.getStatus()).isEqualTo(CreditFreezeStatus.FROZEN);
        assertThat(result.getAmountFen()).isEqualTo(AMOUNT_FEN);

        CreditAccount account = accountRepo.findById(CREDIT_ACCOUNT_ID).orElseThrow();
        assertThat(account.getFrozenFen()).isEqualTo(AMOUNT_FEN);
        assertThat(account.getUsedFen()).isZero();
        assertThat(account.getAvailableFen()).isEqualTo(CreditAccount.FIXED_TOTAL_LIMIT_FEN - AMOUNT_FEN);
    }

    @Test
    @DisplayName("Try 幂等：重复调用返回已有冻结记录，额度不变")
    void tryFreezeIdempotent() {
        CreditFreeze first = participant.tryFreeze(
                TRANSACTION_ID, CREDIT_ACCOUNT_ID, AMOUNT_FEN, BRANCH_XID, NOW
        );
        CreditFreeze second = participant.tryFreeze(
                TRANSACTION_ID, CREDIT_ACCOUNT_ID, AMOUNT_FEN, BRANCH_XID, NOW.plusSeconds(1)
        );

        assertThat(second.getCreditFreezeId()).isEqualTo(first.getCreditFreezeId());
        CreditAccount account = accountRepo.findById(CREDIT_ACCOUNT_ID).orElseThrow();
        assertThat(account.getFrozenFen()).isEqualTo(AMOUNT_FEN);
    }

    @Test
    @DisplayName("Try 防悬挂：已释放的冻结记录拒绝 Try")
    void tryFreezeSuspensionPrevention() {
        participant.tryFreeze(TRANSACTION_ID, CREDIT_ACCOUNT_ID, AMOUNT_FEN, BRANCH_XID, NOW);
        participant.cancelFreeze(
                TRANSACTION_ID, CREDIT_ACCOUNT_ID, AMOUNT_FEN, BRANCH_XID, NOW.plusSeconds(1));

        assertThatThrownBy(() -> participant.tryFreeze(
                TRANSACTION_ID, CREDIT_ACCOUNT_ID, AMOUNT_FEN, BRANCH_XID, NOW.plusSeconds(2)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("拒绝晚到 Try");
    }

    @Test
    @DisplayName("Try 金额不同 → 抛出异常")
    void tryFreezeWithDifferentAmountThrows() {
        participant.tryFreeze(TRANSACTION_ID, CREDIT_ACCOUNT_ID, AMOUNT_FEN, BRANCH_XID, NOW);

        assertThatThrownBy(() -> participant.tryFreeze(
                TRANSACTION_ID, CREDIT_ACCOUNT_ID, 30_000L, BRANCH_XID, NOW.plusSeconds(1)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("幂等参数不一致");
    }

    @Test
    @DisplayName("Try 额度不足 → CREDIT_LIMIT_INSUFFICIENT")
    void tryFreezeInsufficientLimitThrows() {
        assertThatThrownBy(() -> participant.tryFreeze(
                TRANSACTION_ID, CREDIT_ACCOUNT_ID,
                CreditAccount.FIXED_TOTAL_LIMIT_FEN + 1, BRANCH_XID, NOW
        )).isInstanceOf(BusinessException.class)
          .extracting(e -> ((BusinessException) e).errorCode().code())
          .isEqualTo("CREDIT_LIMIT_INSUFFICIENT");
    }

    // ========== Confirm 阶段测试 ==========

    @Test
    @DisplayName("Confirm 成功：冻结转已用，创建消费明细，增加应收")
    void confirmFreezeSuccess() {
        participant.tryFreeze(TRANSACTION_ID, CREDIT_ACCOUNT_ID, AMOUNT_FEN, BRANCH_XID, NOW);
        participant.confirmFreeze(
                TRANSACTION_ID, CREDIT_ACCOUNT_ID, AMOUNT_FEN, BRANCH_XID,
                "qr-order-1", "merchant-acc-1", NOW.plusSeconds(1)
        );

        CreditAccount account = accountRepo.findById(CREDIT_ACCOUNT_ID).orElseThrow();
        assertThat(account.getFrozenFen()).isZero();
        assertThat(account.getUsedFen()).isEqualTo(AMOUNT_FEN);

        CreditFreeze freeze = freezeRepo.findByTransactionIdAndAccountId(
                TRANSACTION_ID, CREDIT_ACCOUNT_ID).orElseThrow();
        assertThat(freeze.getStatus()).isEqualTo(CreditFreezeStatus.CONFIRMED);

        CreditPurchase purchase = purchaseRepo.findByCreditTransactionId(TRANSACTION_ID).orElseThrow();
        assertThat(purchase.getAmountFen()).isEqualTo(AMOUNT_FEN);
        assertThat(purchase.getBillingStatus()).isEqualTo(CreditPurchaseBillingStatus.UNBILLED);

        CreditReceivable receivable = receivableRepo.findByCreditAccountId(CREDIT_ACCOUNT_ID).orElseThrow();
        assertThat(receivable.getUnbilledFen()).isEqualTo(AMOUNT_FEN);
    }

    @Test
    @DisplayName("Confirm 幂等：重复调用不产生副作用")
    void confirmFreezeIdempotent() {
        participant.tryFreeze(TRANSACTION_ID, CREDIT_ACCOUNT_ID, AMOUNT_FEN, BRANCH_XID, NOW);
        participant.confirmFreeze(
                TRANSACTION_ID, CREDIT_ACCOUNT_ID, AMOUNT_FEN, BRANCH_XID,
                "qr-order-1", "merchant-acc-1", NOW.plusSeconds(1)
        );
        participant.confirmFreeze(
                TRANSACTION_ID, CREDIT_ACCOUNT_ID, AMOUNT_FEN, BRANCH_XID,
                "qr-order-1", "merchant-acc-1", NOW.plusSeconds(2)
        );

        CreditAccount account = accountRepo.findById(CREDIT_ACCOUNT_ID).orElseThrow();
        assertThat(account.getUsedFen()).isEqualTo(AMOUNT_FEN);

        // 只有一条消费明细
        assertThat(purchaseRepo.findByCreditTransactionId(TRANSACTION_ID)).isPresent();
    }

    @Test
    @DisplayName("Confirm 不存在的冻结记录 → 抛出异常")
    void confirmNonExistentFreezeThrows() {
        assertThatThrownBy(() -> participant.confirmFreeze(
                "nonexistent-tx", CREDIT_ACCOUNT_ID, AMOUNT_FEN, BRANCH_XID,
                "qr-order-1", "merchant-acc-1", NOW
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Confirm 已释放的冻结 → IllegalStateException")
    void confirmReleasedFreezeThrows() {
        participant.tryFreeze(TRANSACTION_ID, CREDIT_ACCOUNT_ID, AMOUNT_FEN, BRANCH_XID, NOW);
        participant.cancelFreeze(
                TRANSACTION_ID, CREDIT_ACCOUNT_ID, AMOUNT_FEN, BRANCH_XID, NOW.plusSeconds(1));

        assertThatThrownBy(() -> participant.confirmFreeze(
                TRANSACTION_ID, CREDIT_ACCOUNT_ID, AMOUNT_FEN, BRANCH_XID,
                "qr-order-1", "merchant-acc-1", NOW.plusSeconds(2)
        )).isInstanceOf(IllegalStateException.class);
    }

    // ========== Cancel 阶段测试 ==========

    @Test
    @DisplayName("Cancel 成功：释放冻结额度")
    void cancelFreezeSuccess() {
        participant.tryFreeze(TRANSACTION_ID, CREDIT_ACCOUNT_ID, AMOUNT_FEN, BRANCH_XID, NOW);
        participant.cancelFreeze(
                TRANSACTION_ID, CREDIT_ACCOUNT_ID, AMOUNT_FEN, BRANCH_XID, NOW.plusSeconds(1));

        CreditAccount account = accountRepo.findById(CREDIT_ACCOUNT_ID).orElseThrow();
        assertThat(account.getFrozenFen()).isZero();
        assertThat(account.getUsedFen()).isZero();
        assertThat(account.getAvailableFen()).isEqualTo(CreditAccount.FIXED_TOTAL_LIMIT_FEN);

        CreditFreeze freeze = freezeRepo.findByTransactionIdAndAccountId(
                TRANSACTION_ID, CREDIT_ACCOUNT_ID).orElseThrow();
        assertThat(freeze.getStatus()).isEqualTo(CreditFreezeStatus.RELEASED);
    }

    @Test
    @DisplayName("Cancel 幂等：重复调用不产生副作用")
    void cancelFreezeIdempotent() {
        participant.tryFreeze(TRANSACTION_ID, CREDIT_ACCOUNT_ID, AMOUNT_FEN, BRANCH_XID, NOW);
        participant.cancelFreeze(
                TRANSACTION_ID, CREDIT_ACCOUNT_ID, AMOUNT_FEN, BRANCH_XID, NOW.plusSeconds(1));
        participant.cancelFreeze(
                TRANSACTION_ID, CREDIT_ACCOUNT_ID, AMOUNT_FEN, BRANCH_XID, NOW.plusSeconds(2));

        CreditAccount account = accountRepo.findById(CREDIT_ACCOUNT_ID).orElseThrow();
        assertThat(account.getFrozenFen()).isZero();
        assertThat(account.getAvailableFen()).isEqualTo(CreditAccount.FIXED_TOTAL_LIMIT_FEN);
    }

    @Test
    @DisplayName("Cancel 空回滚：Try 未执行时快速返回")
    void cancelFreezeEmptyRollback() {
        // 不执行 Try，直接 Cancel
        participant.cancelFreeze(TRANSACTION_ID, CREDIT_ACCOUNT_ID, AMOUNT_FEN, BRANCH_XID, NOW);

        CreditAccount account = accountRepo.findById(CREDIT_ACCOUNT_ID).orElseThrow();
        assertThat(account.getFrozenFen()).isZero();
        assertThat(account.getUsedFen()).isZero();
        assertThat(account.getAvailableFen()).isEqualTo(CreditAccount.FIXED_TOTAL_LIMIT_FEN);

        var branch = branchRepo.findAccountBranchForUpdate(
                BRANCH_XID, TccBranchType.CREDIT_PAY, CREDIT_ACCOUNT_ID).orElseThrow();
        assertThat(branch.getStatus()).isEqualTo(TccBranchStatus.CANCELLED);
        assertThat(branch.getRollbackType()).isEqualTo(RollbackType.EMPTY);

        assertThatThrownBy(() -> participant.tryFreeze(
                TRANSACTION_ID, CREDIT_ACCOUNT_ID, AMOUNT_FEN, BRANCH_XID, NOW.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("拒绝晚到 Try");
    }

    @Test
    @DisplayName("Cancel 已确认的冻结 → IllegalStateException")
    void cancelConfirmedFreezeThrows() {
        participant.tryFreeze(TRANSACTION_ID, CREDIT_ACCOUNT_ID, AMOUNT_FEN, BRANCH_XID, NOW);
        participant.confirmFreeze(
                TRANSACTION_ID, CREDIT_ACCOUNT_ID, AMOUNT_FEN, BRANCH_XID,
                "qr-order-1", "merchant-acc-1", NOW.plusSeconds(1)
        );

        assertThatThrownBy(() -> participant.cancelFreeze(
                TRANSACTION_ID, CREDIT_ACCOUNT_ID, AMOUNT_FEN, BRANCH_XID, NOW.plusSeconds(2)
        )).isInstanceOf(IllegalStateException.class);
    }

    // ========== 完整生命周期测试 ==========

    @Test
    @DisplayName("完整 Try → Confirm 生命周期后额度正确")
    void fullTryConfirmLifecycle() {
        long amount1 = 30_000L;
        long amount2 = 20_000L;

        // 第一笔消费
        participant.tryFreeze("tx-1", CREDIT_ACCOUNT_ID, amount1, "xid-1", NOW);
        participant.confirmFreeze(
                "tx-1", CREDIT_ACCOUNT_ID, amount1, "xid-1",
                "qr-1", "merchant-1", NOW.plusSeconds(1));

        // 第二笔消费
        participant.tryFreeze("tx-2", CREDIT_ACCOUNT_ID, amount2, "xid-2", NOW.plusSeconds(2));
        participant.confirmFreeze(
                "tx-2", CREDIT_ACCOUNT_ID, amount2, "xid-2",
                "qr-2", "merchant-2", NOW.plusSeconds(3));

        CreditAccount account = accountRepo.findById(CREDIT_ACCOUNT_ID).orElseThrow();
        assertThat(account.getUsedFen()).isEqualTo(amount1 + amount2);
        assertThat(account.getFrozenFen()).isZero();
        assertThat(account.getAvailableFen())
                .isEqualTo(CreditAccount.FIXED_TOTAL_LIMIT_FEN - amount1 - amount2);

        CreditReceivable receivable = receivableRepo.findByCreditAccountId(CREDIT_ACCOUNT_ID).orElseThrow();
        assertThat(receivable.getUnbilledFen()).isEqualTo(amount1 + amount2);
    }

    @Test
    @DisplayName("Try → Cancel → Try（新交易）额度恢复后可再次使用")
    void tryCancelThenNewTry() {
        long amount = 30_000L;

        participant.tryFreeze("tx-1", CREDIT_ACCOUNT_ID, amount, "xid-1", NOW);
        participant.cancelFreeze(
                "tx-1", CREDIT_ACCOUNT_ID, amount, "xid-1", NOW.plusSeconds(1));

        // 取消后额度恢复，可以发起新消费
        participant.tryFreeze("tx-2", CREDIT_ACCOUNT_ID, amount, "xid-2", NOW.plusSeconds(2));
        participant.confirmFreeze(
                "tx-2", CREDIT_ACCOUNT_ID, amount, "xid-2",
                "qr-2", "merchant-2", NOW.plusSeconds(3));

        CreditAccount account = accountRepo.findById(CREDIT_ACCOUNT_ID).orElseThrow();
        assertThat(account.getUsedFen()).isEqualTo(amount);
        assertThat(account.getFrozenFen()).isZero();
    }

    // ========== 内存仓储实现 ==========

    static class InMemoryCreditAccountRepository implements CreditAccountRepository {
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

    static class InMemoryCreditFreezeRepository implements CreditFreezeRepository {
        final Map<String, CreditFreeze> records = new HashMap<>();

        @Override
        public Optional<CreditFreeze> findByTransactionIdAndAccountId(
                String transactionId, String creditAccountId) {
            return records.values().stream()
                    .filter(f -> f.getTransactionId().equals(transactionId)
                            && f.getCreditAccountId().equals(creditAccountId))
                    .findFirst();
        }

        @Override
        public void save(CreditFreeze freeze) {
            records.put(freeze.getCreditFreezeId(), freeze);
            freeze.updateVersion(freeze.getVersion() + 1);
        }
    }

    static class InMemoryCreditPurchaseRepository implements CreditPurchaseRepository {
        final Map<String, CreditPurchase> purchases = new HashMap<>();

        @Override
        public Optional<CreditPurchase> findById(String purchaseId) {
            return Optional.ofNullable(purchases.get(purchaseId));
        }

        @Override
        public Optional<CreditPurchase> findByCreditTransactionId(String creditTransactionId) {
            return purchases.values().stream()
                    .filter(p -> p.getCreditTransactionId().equals(creditTransactionId))
                    .findFirst();
        }

        @Override
        public List<CreditPurchase> findByCreditAccountIdAndBillingStatus(
                String creditAccountId, String billingStatus) {
            return purchases.values().stream()
                    .filter(p -> p.getCreditAccountId().equals(creditAccountId)
                            && p.getBillingStatus().name().equals(billingStatus))
                    .toList();
        }

        @Override
        public List<CreditPurchase> findByBillingStatus(String billingStatus) {
            return purchases.values().stream()
                    .filter(p -> p.getBillingStatus().name().equals(billingStatus))
                    .toList();
        }

        @Override
        public void save(CreditPurchase purchase) {
            purchases.put(purchase.getPurchaseId(), purchase);
            purchase.updateVersion(purchase.getVersion() + 1);
        }
    }

    static class InMemoryCreditReceivableRepository implements CreditReceivableRepository {
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
}
