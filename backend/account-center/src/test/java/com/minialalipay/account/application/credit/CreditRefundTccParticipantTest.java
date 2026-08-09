package com.minialalipay.account.application.credit;

import com.minialalipay.account.domain.credit.CreditPurchase;
import com.minialalipay.account.domain.credit.CreditPurchaseBillingStatus;
import com.minialalipay.account.domain.credit.CreditPurchaseRepository;
import com.minialalipay.account.domain.credit.CreditReceivable;
import com.minialalipay.account.domain.credit.CreditReceivableRepository;
import com.minialalipay.account.domain.tcc.RollbackType;
import com.minialalipay.account.domain.tcc.TccBranch;
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
 * CreditRefundTccParticipant（信用支付退款冲正分支）单元测试。
 *
 * <p>退款冲正只针对原信用消费全额且尚未还款的场景，覆盖幂等、空回滚、防悬挂、金额守恒与消费置为终态。</p>
 */
@DisplayName("信用支付退款冲正 TCC 分支参与者测试")
class CreditRefundTccParticipantTest {

    private static final Instant NOW = Instant.parse("2026-08-06T08:00:00Z");
    private static final String ORIGINAL_TRANSACTION_ID = "tx-orig-credit-pay";
    private static final String REFUND_TRANSACTION_ID = "tx-refund";
    private static final String CREDIT_ACCOUNT_ID = "credit-acc-1";
    private static final String MERCHANT_ACCOUNT_ID = "merchant-acc-1";
    private static final long AMOUNT_FEN = 50_000L;

    private InMemoryCreditPurchaseRepository purchaseRepo;
    private InMemoryCreditReceivableRepository receivableRepo;
    private InMemoryTccBranchRepository branchRepo;
    private CreditRefundTccParticipant participant;

    @BeforeEach
    void setUp() {
        purchaseRepo = new InMemoryCreditPurchaseRepository();
        receivableRepo = new InMemoryCreditReceivableRepository();
        branchRepo = new InMemoryTccBranchRepository();
        participant = new CreditRefundTccParticipant(purchaseRepo, receivableRepo, branchRepo);

        CreditPurchase purchase = new CreditPurchase("purchase-1", ORIGINAL_TRANSACTION_ID, CREDIT_ACCOUNT_ID,
                "qr-order-1", MERCHANT_ACCOUNT_ID, AMOUNT_FEN, NOW);
        purchaseRepo.save(purchase);
        CreditReceivable receivable = new CreditReceivable(CREDIT_ACCOUNT_ID, AMOUNT_FEN, 0L, 0L, 0L, NOW);
        receivableRepo.save(receivable);
    }

    // ========== Try 阶段测试 ==========

    @Test
    @DisplayName("Try 成功：只校验不占用资源，返回已锁定分支")
    void tryRefundSuccess() {
        TccBranch result = participant.tryRefund(REFUND_TRANSACTION_ID, ORIGINAL_TRANSACTION_ID,
                MERCHANT_ACCOUNT_ID, AMOUNT_FEN, "xid-refund-1", NOW);

        assertThat(result.getStatus()).isEqualTo(TccBranchStatus.TRIED);
        assertThat(result.getBranchType()).isEqualTo(TccBranchType.REFUND);

        // Try 阶段不得提前置消费为冲正终态
        CreditPurchase purchase = purchaseRepo.findByCreditTransactionId(ORIGINAL_TRANSACTION_ID).orElseThrow();
        assertThat(purchase.getBillingStatus()).isEqualTo(CreditPurchaseBillingStatus.UNBILLED);
        assertThat(purchase.getRefundedFen()).isZero();
    }

    @Test
    @DisplayName("Try 幂等：重复调用返回已有分支，不重复校验副作用")
    void tryRefundIdempotent() {
        TccBranch first = participant.tryRefund(REFUND_TRANSACTION_ID, ORIGINAL_TRANSACTION_ID,
                MERCHANT_ACCOUNT_ID, AMOUNT_FEN, "xid-refund-1", NOW);
        TccBranch second = participant.tryRefund(REFUND_TRANSACTION_ID, ORIGINAL_TRANSACTION_ID,
                MERCHANT_ACCOUNT_ID, AMOUNT_FEN, "xid-refund-1", NOW.plusSeconds(1));

        assertThat(second.getStatus()).isEqualTo(TccBranchStatus.TRIED);
        assertThat(second.getResourceId()).isEqualTo(ORIGINAL_TRANSACTION_ID);
        assertThat(branchRepo.findAccountBranchForUpdate("xid-refund-1", TccBranchType.REFUND,
                ORIGINAL_TRANSACTION_ID)).isPresent();
    }

    @Test
    @DisplayName("Try 退款发起账户与原收款账户不一致 → 拒绝")
    void tryRefundWrongMerchantThrows() {
        assertThatThrownBy(() -> participant.tryRefund(REFUND_TRANSACTION_ID, ORIGINAL_TRANSACTION_ID,
                "another-merchant-acc", AMOUNT_FEN, "xid-refund-1", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("退款发起账户与原收款账户不一致");
    }

    @Test
    @DisplayName("Try 金额与原信用消费不一致 → 拒绝")
    void tryRefundAmountMismatchThrows() {
        assertThatThrownBy(() -> participant.tryRefund(REFUND_TRANSACTION_ID, ORIGINAL_TRANSACTION_ID,
                MERCHANT_ACCOUNT_ID, AMOUNT_FEN - 1, "xid-refund-1", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("退款金额与原信用消费金额不一致");
    }

    @Test
    @DisplayName("Try 原信用消费不存在 → 拒绝")
    void tryRefundMissingPurchaseThrows() {
        assertThatThrownBy(() -> participant.tryRefund(REFUND_TRANSACTION_ID, "missing-orig-tx",
                MERCHANT_ACCOUNT_ID, AMOUNT_FEN, "xid-refund-1", NOW))
                .isInstanceOf(BusinessException.class);
    }

    // ========== Confirm 阶段测试 ==========

    @Test
    @DisplayName("Confirm 成功：消费置为冲正终态并按退款金额减少应收")
    void confirmRefundSuccess() {
        participant.tryRefund(REFUND_TRANSACTION_ID, ORIGINAL_TRANSACTION_ID,
                MERCHANT_ACCOUNT_ID, AMOUNT_FEN, "xid-refund-1", NOW);
        TccBranch result = participant.confirmRefund(REFUND_TRANSACTION_ID, ORIGINAL_TRANSACTION_ID,
                MERCHANT_ACCOUNT_ID, AMOUNT_FEN, "xid-refund-1", NOW.plusSeconds(1));

        assertThat(result.getStatus()).isEqualTo(TccBranchStatus.CONFIRMED);

        CreditPurchase purchase = purchaseRepo.findByCreditTransactionId(ORIGINAL_TRANSACTION_ID).orElseThrow();
        assertThat(purchase.getBillingStatus()).isEqualTo(CreditPurchaseBillingStatus.REVERSED);
        assertThat(purchase.getRefundedFen()).isEqualTo(AMOUNT_FEN);
        assertThat(purchase.getRefundTransactionId()).isEqualTo(REFUND_TRANSACTION_ID);
        assertThat(purchase.getOutstandingFen()).isZero();

        CreditReceivable receivable = receivableRepo.findByCreditAccountId(CREDIT_ACCOUNT_ID).orElseThrow();
        assertThat(receivable.getUnbilledFen()).isZero();
        assertThat(receivable.getTotalOutstandingFen()).isZero();
    }

    @Test
    @DisplayName("Confirm 幂等：重复调用不重复冲正")
    void confirmRefundIdempotent() {
        participant.tryRefund(REFUND_TRANSACTION_ID, ORIGINAL_TRANSACTION_ID,
                MERCHANT_ACCOUNT_ID, AMOUNT_FEN, "xid-refund-1", NOW);
        participant.confirmRefund(REFUND_TRANSACTION_ID, ORIGINAL_TRANSACTION_ID,
                MERCHANT_ACCOUNT_ID, AMOUNT_FEN, "xid-refund-1", NOW.plusSeconds(1));
        participant.confirmRefund(REFUND_TRANSACTION_ID, ORIGINAL_TRANSACTION_ID,
                MERCHANT_ACCOUNT_ID, AMOUNT_FEN, "xid-refund-1", NOW.plusSeconds(2));

        CreditPurchase purchase = purchaseRepo.findByCreditTransactionId(ORIGINAL_TRANSACTION_ID).orElseThrow();
        assertThat(purchase.getRefundedFen()).isEqualTo(AMOUNT_FEN);
        CreditReceivable receivable = receivableRepo.findByCreditAccountId(CREDIT_ACCOUNT_ID).orElseThrow();
        assertThat(receivable.getUnbilledFen()).isZero();
    }

    @Test
    @DisplayName("Confirm 未执行 Try 的分支 → 拒绝")
    void confirmWithoutTryThrows() {
        assertThatThrownBy(() -> participant.confirmRefund(REFUND_TRANSACTION_ID, ORIGINAL_TRANSACTION_ID,
                MERCHANT_ACCOUNT_ID, AMOUNT_FEN, "xid-refund-1", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("屏障不存在");
    }

    @Test
    @DisplayName("Confirm 已取消的分支 → 拒绝")
    void confirmCancelledBranchThrows() {
        participant.tryRefund(REFUND_TRANSACTION_ID, ORIGINAL_TRANSACTION_ID,
                MERCHANT_ACCOUNT_ID, AMOUNT_FEN, "xid-refund-1", NOW);
        participant.cancelRefund(REFUND_TRANSACTION_ID, ORIGINAL_TRANSACTION_ID,
                MERCHANT_ACCOUNT_ID, AMOUNT_FEN, "xid-refund-1", NOW.plusSeconds(1));

        assertThatThrownBy(() -> participant.confirmRefund(REFUND_TRANSACTION_ID, ORIGINAL_TRANSACTION_ID,
                MERCHANT_ACCOUNT_ID, AMOUNT_FEN, "xid-refund-1", NOW.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不可确认");
    }

    @Test
    @DisplayName("Confirm 已还清的信用消费 → 拒绝全额退款")
    void confirmRepaidPurchaseThrows() {
        // 独立的一笔已还清消费，避免与 setup 中的未出账消费共用同一交易号
        CreditPurchase repaid = new CreditPurchase("purchase-repaid", "tx-orig-repaid",
                CREDIT_ACCOUNT_ID, "qr-order-1", MERCHANT_ACCOUNT_ID, AMOUNT_FEN,
                0L, 0L, null, CreditPurchaseBillingStatus.REPAID, 0L, NOW, NOW);
        purchaseRepo.save(repaid);
        TccBranch branch = TccBranch.initialize("xid-refund-repaid", TccBranchType.REFUND,
                "tx-orig-repaid", REFUND_TRANSACTION_ID, AMOUNT_FEN, NOW);
        branch.markTried(NOW);
        branchRepo.createAccountBranch(branch);

        assertThatThrownBy(() -> participant.confirmRefund(REFUND_TRANSACTION_ID, "tx-orig-repaid",
                MERCHANT_ACCOUNT_ID, AMOUNT_FEN, "xid-refund-repaid", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已还清");
    }

    // ========== Cancel 阶段测试 ==========

    @Test
    @DisplayName("Cancel 空回滚：Try 未执行时建立空回滚屏障并拒绝晚到 Try")
    void cancelRefundEmptyRollback() {
        TccBranch result = participant.cancelRefund(REFUND_TRANSACTION_ID, ORIGINAL_TRANSACTION_ID,
                MERCHANT_ACCOUNT_ID, AMOUNT_FEN, "xid-refund-1", NOW);

        assertThat(result.getStatus()).isEqualTo(TccBranchStatus.CANCELLED);
        assertThat(result.getRollbackType()).isEqualTo(RollbackType.EMPTY);

        // 消费与应收未被改动
        CreditPurchase purchase = purchaseRepo.findByCreditTransactionId(ORIGINAL_TRANSACTION_ID).orElseThrow();
        assertThat(purchase.getBillingStatus()).isEqualTo(CreditPurchaseBillingStatus.UNBILLED);
        CreditReceivable receivable = receivableRepo.findByCreditAccountId(CREDIT_ACCOUNT_ID).orElseThrow();
        assertThat(receivable.getUnbilledFen()).isEqualTo(AMOUNT_FEN);

        assertThatThrownBy(() -> participant.tryRefund(REFUND_TRANSACTION_ID, ORIGINAL_TRANSACTION_ID,
                MERCHANT_ACCOUNT_ID, AMOUNT_FEN, "xid-refund-1", NOW.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("拒绝晚到 Try");
    }

    @Test
    @DisplayName("Cancel 幂等：重复调用保持取消终态")
    void cancelRefundIdempotent() {
        participant.tryRefund(REFUND_TRANSACTION_ID, ORIGINAL_TRANSACTION_ID,
                MERCHANT_ACCOUNT_ID, AMOUNT_FEN, "xid-refund-1", NOW);
        participant.cancelRefund(REFUND_TRANSACTION_ID, ORIGINAL_TRANSACTION_ID,
                MERCHANT_ACCOUNT_ID, AMOUNT_FEN, "xid-refund-1", NOW.plusSeconds(1));
        TccBranch second = participant.cancelRefund(REFUND_TRANSACTION_ID, ORIGINAL_TRANSACTION_ID,
                MERCHANT_ACCOUNT_ID, AMOUNT_FEN, "xid-refund-1", NOW.plusSeconds(2));

        assertThat(second.getStatus()).isEqualTo(TccBranchStatus.CANCELLED);
        assertThat(second.getRollbackType()).isEqualTo(RollbackType.NORMAL);
    }

    @Test
    @DisplayName("Cancel 已确认的分支 → 拒绝")
    void cancelConfirmedBranchThrows() {
        participant.tryRefund(REFUND_TRANSACTION_ID, ORIGINAL_TRANSACTION_ID,
                MERCHANT_ACCOUNT_ID, AMOUNT_FEN, "xid-refund-1", NOW);
        participant.confirmRefund(REFUND_TRANSACTION_ID, ORIGINAL_TRANSACTION_ID,
                MERCHANT_ACCOUNT_ID, AMOUNT_FEN, "xid-refund-1", NOW.plusSeconds(1));

        assertThatThrownBy(() -> participant.cancelRefund(REFUND_TRANSACTION_ID, ORIGINAL_TRANSACTION_ID,
                MERCHANT_ACCOUNT_ID, AMOUNT_FEN, "xid-refund-1", NOW.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不可取消");
    }

    // ========== 完整生命周期 ==========

    @Test
    @DisplayName("Try → Confirm 生命周期后应收与消费一致归零")
    void fullLifecycle() {
        participant.tryRefund(REFUND_TRANSACTION_ID, ORIGINAL_TRANSACTION_ID,
                MERCHANT_ACCOUNT_ID, AMOUNT_FEN, "xid-refund-1", NOW);
        participant.confirmRefund(REFUND_TRANSACTION_ID, ORIGINAL_TRANSACTION_ID,
                MERCHANT_ACCOUNT_ID, AMOUNT_FEN, "xid-refund-1", NOW.plusSeconds(1));

        CreditPurchase purchase = purchaseRepo.findByCreditTransactionId(ORIGINAL_TRANSACTION_ID).orElseThrow();
        CreditReceivable receivable = receivableRepo.findByCreditAccountId(CREDIT_ACCOUNT_ID).orElseThrow();
        assertThat(purchase.getOutstandingFen()).isZero();
        assertThat(receivable.getTotalOutstandingFen()).isZero();
        assertThat(purchase.getOutstandingFen() + receivable.getTotalOutstandingFen()).isZero();
    }

    // ========== 内存仓储实现 ==========

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
