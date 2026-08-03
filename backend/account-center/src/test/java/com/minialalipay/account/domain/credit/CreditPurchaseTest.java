package com.minialalipay.account.domain.credit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CreditPurchase} 领域模型单元测试。
 */
@DisplayName("CreditPurchase 信用消费明细")
class CreditPurchaseTest {

    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");
    private static final String PURCHASE_ID = "purchase-01";
    private static final String CREDIT_TRANSACTION_ID = "tx-01";
    private static final String CREDIT_ACCOUNT_ID = "credit-acc-01";
    private static final String QR_ORDER_ID = "qr-order-01";
    private static final String MERCHANT_ACCOUNT_ID = "merchant-acc-01";

    @Nested
    @DisplayName("创建消费明细")
    class Create {

        @Test
        @DisplayName("创建后状态=UNBILLED，repaid=0，refunded=0")
        void shouldCreateUnbilledPurchase() {
            CreditPurchase purchase = new CreditPurchase(
                    PURCHASE_ID, CREDIT_TRANSACTION_ID, CREDIT_ACCOUNT_ID,
                    QR_ORDER_ID, MERCHANT_ACCOUNT_ID, 100_000L, NOW
            );

            assertThat(purchase.getBillingStatus()).isEqualTo(CreditPurchaseBillingStatus.UNBILLED);
            assertThat(purchase.getRepaidFen()).isZero();
            assertThat(purchase.getRefundedFen()).isZero();
            assertThat(purchase.getAmountFen()).isEqualTo(100_000L);
            assertThat(purchase.getOutstandingFen()).isEqualTo(100_000L);
            assertThat(purchase.getPurchaseId()).isEqualTo(PURCHASE_ID);
            assertThat(purchase.getCreditTransactionId()).isEqualTo(CREDIT_TRANSACTION_ID);
            assertThat(purchase.getCreditAccountId()).isEqualTo(CREDIT_ACCOUNT_ID);
            assertThat(purchase.getQrOrderId()).isEqualTo(QR_ORDER_ID);
            assertThat(purchase.getMerchantAccountId()).isEqualTo(MERCHANT_ACCOUNT_ID);
            assertThat(purchase.getOccurredAt()).isEqualTo(NOW);
        }
    }

    @Nested
    @DisplayName("金额范围校验")
    class AmountValidation {

        @Test
        @DisplayName("金额<1 抛异常")
        void shouldThrowWhenAmountLessThanOne() {
            assertThatThrownBy(() -> new CreditPurchase(
                    PURCHASE_ID, CREDIT_TRANSACTION_ID, CREDIT_ACCOUNT_ID,
                    QR_ORDER_ID, MERCHANT_ACCOUNT_ID, 0L, NOW
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("消费金额必须在 1~5000000 分范围内");
        }

        @Test
        @DisplayName("金额>5000000 抛异常")
        void shouldThrowWhenAmountExceedsMax() {
            assertThatThrownBy(() -> new CreditPurchase(
                    PURCHASE_ID, CREDIT_TRANSACTION_ID, CREDIT_ACCOUNT_ID,
                    QR_ORDER_ID, MERCHANT_ACCOUNT_ID, 5_000_001L, NOW
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("消费金额必须在 1~5000000 分范围内");
        }
    }

    @Nested
    @DisplayName("markBilled 出账")
    class MarkBilled {

        @Test
        @DisplayName("UNBILLED→BILLED")
        void shouldTransitionToBilled() {
            CreditPurchase purchase = new CreditPurchase(
                    PURCHASE_ID, CREDIT_TRANSACTION_ID, CREDIT_ACCOUNT_ID,
                    QR_ORDER_ID, MERCHANT_ACCOUNT_ID, 100_000L, NOW
            );

            purchase.markBilled(NOW);

            assertThat(purchase.getBillingStatus()).isEqualTo(CreditPurchaseBillingStatus.BILLED);
        }

        @Test
        @DisplayName("非 UNBILLED 状态出账抛异常")
        void shouldThrowWhenNotUnbilled() {
            CreditPurchase purchase = new CreditPurchase(
                    PURCHASE_ID, CREDIT_TRANSACTION_ID, CREDIT_ACCOUNT_ID,
                    QR_ORDER_ID, MERCHANT_ACCOUNT_ID, 100_000L, NOW
            );
            purchase.markBilled(NOW);

            assertThatThrownBy(() -> purchase.markBilled(NOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("仅未出账消费可出账");
        }
    }

    @Nested
    @DisplayName("applyRepayment 应用还款")
    class ApplyRepayment {

        @Test
        @DisplayName("部分还款，repaid 增加")
        void shouldApplyPartialRepayment() {
            CreditPurchase purchase = new CreditPurchase(
                    PURCHASE_ID, CREDIT_TRANSACTION_ID, CREDIT_ACCOUNT_ID,
                    QR_ORDER_ID, MERCHANT_ACCOUNT_ID, 100_000L, NOW
            );

            purchase.applyRepayment(40_000L, NOW);

            assertThat(purchase.getRepaidFen()).isEqualTo(40_000L);
            assertThat(purchase.getBillingStatus()).isEqualTo(CreditPurchaseBillingStatus.UNBILLED);
        }

        @Test
        @DisplayName("全额还款后状态变 REPAID")
        void shouldTransitionToRepaidWhenFullyRepaid() {
            CreditPurchase purchase = new CreditPurchase(
                    PURCHASE_ID, CREDIT_TRANSACTION_ID, CREDIT_ACCOUNT_ID,
                    QR_ORDER_ID, MERCHANT_ACCOUNT_ID, 100_000L, NOW
            );

            purchase.applyRepayment(100_000L, NOW);

            assertThat(purchase.getRepaidFen()).isEqualTo(100_000L);
            assertThat(purchase.getBillingStatus()).isEqualTo(CreditPurchaseBillingStatus.REPAID);
        }

        @Test
        @DisplayName("超过未还余额抛异常")
        void shouldThrowWhenAmountExceedsOutstanding() {
            CreditPurchase purchase = new CreditPurchase(
                    PURCHASE_ID, CREDIT_TRANSACTION_ID, CREDIT_ACCOUNT_ID,
                    QR_ORDER_ID, MERCHANT_ACCOUNT_ID, 100_000L, NOW
            );
            purchase.applyRepayment(60_000L, NOW);

            assertThatThrownBy(() -> purchase.applyRepayment(41_000L, NOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("分配金额超过消费未还余额");
        }

        @Test
        @DisplayName("金额<=0 抛异常")
        void shouldThrowWhenAmountNotPositive() {
            CreditPurchase purchase = new CreditPurchase(
                    PURCHASE_ID, CREDIT_TRANSACTION_ID, CREDIT_ACCOUNT_ID,
                    QR_ORDER_ID, MERCHANT_ACCOUNT_ID, 100_000L, NOW
            );

            assertThatThrownBy(() -> purchase.applyRepayment(0L, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("分配金额必须为正");

            assertThatThrownBy(() -> purchase.applyRepayment(-1L, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("分配金额必须为正");
        }
    }

    @Nested
    @DisplayName("getOutstandingFen 未还余额")
    class GetOutstandingFen {

        @Test
        @DisplayName("返回 amount - repaid - refunded")
        void shouldReturnOutstandingAmount() {
            CreditPurchase purchase = new CreditPurchase(
                    PURCHASE_ID, CREDIT_TRANSACTION_ID, CREDIT_ACCOUNT_ID,
                    QR_ORDER_ID, MERCHANT_ACCOUNT_ID, 100_000L, NOW
            );

            assertThat(purchase.getOutstandingFen()).isEqualTo(100_000L);

            purchase.applyRepayment(30_000L, NOW);
            assertThat(purchase.getOutstandingFen()).isEqualTo(70_000L);

            purchase.applyRepayment(30_000L, NOW);
            assertThat(purchase.getOutstandingFen()).isEqualTo(40_000L);
        }
    }
}
