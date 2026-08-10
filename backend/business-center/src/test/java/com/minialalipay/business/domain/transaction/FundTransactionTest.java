package com.minialalipay.business.domain.transaction;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 统一资金交易状态机测试。
 *
 * <p>重点覆盖人工态复核恢复语义：人工态不是终态，允许按工单原因转回在途态，
 * 供恢复任务重新驱动 TCC 并重新核验资金事实。</p>
 */
class FundTransactionTest {
    private static final Instant NOW = Instant.parse("2026-08-04T08:00:00Z");

    @Test
    void 成功事实不一致的人工态交易复核时转回处理中() {
        FundTransaction transaction = manualReviewTransaction();

        transaction.resumeFromManualReview(false, NOW.plusSeconds(120));

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.PROCESSING);
        assertThat(transaction.getVersion()).isEqualTo(2L);
    }

    @Test
    void 取消事实不一致的人工态交易复核时转回补偿中() {
        FundTransaction transaction = manualReviewTransaction();

        transaction.resumeFromManualReview(true, NOW.plusSeconds(120));

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPENSATING);
    }

    @Test
    void 非人工态交易禁止复核恢复() {
        FundTransaction transaction = transaction();

        assertThatThrownBy(() -> transaction.resumeFromManualReview(false, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 复核恢复后可按原状态机发布终态() {
        FundTransaction transaction = manualReviewTransaction();
        transaction.resumeFromManualReview(false, NOW.plusSeconds(120));

        transaction.publishSuccess(true, NOW.plusSeconds(130));

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
    }

    @Test
    void 未核验事实不能直接发布成功() {
        FundTransaction transaction = transaction();

        assertThatThrownBy(() -> transaction.publishSuccess(false, NOW.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 补偿完成后进入取消终态() {
        FundTransaction transaction = transaction();

        transaction.startCompensating(NOW.plusSeconds(1));
        transaction.publishCancelled(true, NOW.plusSeconds(2));

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.CANCELLED);
        assertThat(transaction.getVersion()).isEqualTo(2L);
    }

    @Test
    void 充值交易允许系统发行且付款账户为空() {
        FundTransaction transaction = FundTransaction.accept("01K1RCH002GH3JK4MN5PQRSTV", TransactionType.RECHARGE,
                SourceType.RECHARGE_ORDER, "01K1RCHOD2GH3JK4MN5PQRSTV", "user-1", null, "payee-account",
                FundingSource.SYSTEM_ISSUANCE, 100L, "idem-recharge", "LOW",
                "0123456789abcdef0123456789abcdef", NOW);

        assertThat(transaction.getPayerAccountId()).isNull();
        assertThat(transaction.getFundingSource()).isEqualTo(FundingSource.SYSTEM_ISSUANCE);
    }

    @Test
    void 普通交易不得缺少付款账户() {
        assertThatThrownBy(() -> FundTransaction.accept("01K1TXF002GH3JK4MN5PQRSTV", TransactionType.TRANSFER,
                SourceType.TRANSFER_DRAFT, "01K1DRF002GH3JK4MN5PQRSTV", "user-1", null, "payee-account",
                FundingSource.BALANCE, 100L, "idem-transfer", "LOW",
                "0123456789abcdef0123456789abcdef", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static FundTransaction transaction() {
        return FundTransaction.accept("01K1TX0002GH3JK4MN5PQRSTV", TransactionType.CREDIT_PAY,
                SourceType.QR_PAY_ORDER, "01K1QR0002GH3JK4MN5PQRSTV", "payer-user",
                "payer-account", "payee-account", FundingSource.MINI_CREDIT, 2200L,
                "idem-key-00000001", "LOW", "0123456789abcdef0123456789abcdef", NOW);
    }

    private static FundTransaction manualReviewTransaction() {
        FundTransaction transaction = transaction();
        transaction.requireManualReview(NOW.plusSeconds(60));
        return transaction;
    }
}
