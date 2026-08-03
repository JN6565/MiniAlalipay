package com.minialalipay.account.domain.repayment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CreditRepayment} 领域模型单元测试。
 */
@DisplayName("CreditRepayment 信用还款记录")
class CreditRepaymentTest {

    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");
    private static final String REPAYMENT_ID = "repayment-01";
    private static final String REPAYMENT_DRAFT_ID = "draft-01";
    private static final String TRANSACTION_ID = "tx-01";
    private static final String CREDIT_ACCOUNT_ID = "credit-acc-01";

    @Nested
    @DisplayName("创建还款记录")
    class Create {

        @Test
        @DisplayName("创建后 status=PROCESSING")
        void shouldCreateProcessingRepayment() {
            CreditRepayment repayment = new CreditRepayment(
                    REPAYMENT_ID, REPAYMENT_DRAFT_ID, TRANSACTION_ID,
                    CREDIT_ACCOUNT_ID, 100_000L, NOW
            );

            assertThat(repayment.getStatus()).isEqualTo(CreditRepaymentStatus.PROCESSING);
            assertThat(repayment.getAmountFen()).isEqualTo(100_000L);
            assertThat(repayment.getRepaymentId()).isEqualTo(REPAYMENT_ID);
            assertThat(repayment.getRepaymentDraftId()).isEqualTo(REPAYMENT_DRAFT_ID);
            assertThat(repayment.getTransactionId()).isEqualTo(TRANSACTION_ID);
            assertThat(repayment.getCreditAccountId()).isEqualTo(CREDIT_ACCOUNT_ID);
            assertThat(repayment.getCreatedAt()).isEqualTo(NOW);
            assertThat(repayment.getUpdatedAt()).isEqualTo(NOW);
        }
    }

    @Nested
    @DisplayName("金额范围校验")
    class AmountValidation {

        @Test
        @DisplayName("金额<1 抛异常")
        void shouldThrowWhenAmountLessThanOne() {
            assertThatThrownBy(() -> new CreditRepayment(
                    REPAYMENT_ID, REPAYMENT_DRAFT_ID, TRANSACTION_ID,
                    CREDIT_ACCOUNT_ID, 0L, NOW
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("还款金额必须在 1~5000000 分范围内");
        }

        @Test
        @DisplayName("金额>5000000 抛异常")
        void shouldThrowWhenAmountExceedsMax() {
            assertThatThrownBy(() -> new CreditRepayment(
                    REPAYMENT_ID, REPAYMENT_DRAFT_ID, TRANSACTION_ID,
                    CREDIT_ACCOUNT_ID, 5_000_001L, NOW
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("还款金额必须在 1~5000000 分范围内");
        }
    }

    @Nested
    @DisplayName("markSuccess 标记成功")
    class MarkSuccess {

        @Test
        @DisplayName("PROCESSING→SUCCESS")
        void shouldTransitionToSuccess() {
            CreditRepayment repayment = new CreditRepayment(
                    REPAYMENT_ID, REPAYMENT_DRAFT_ID, TRANSACTION_ID,
                    CREDIT_ACCOUNT_ID, 100_000L, NOW
            );

            repayment.markSuccess(NOW);

            assertThat(repayment.getStatus()).isEqualTo(CreditRepaymentStatus.SUCCESS);
        }

        @Test
        @DisplayName("非 PROCESSING 状态标记成功抛异常")
        void shouldThrowWhenNotProcessing() {
            CreditRepayment repayment = new CreditRepayment(
                    REPAYMENT_ID, REPAYMENT_DRAFT_ID, TRANSACTION_ID,
                    CREDIT_ACCOUNT_ID, 100_000L, NOW
            );
            repayment.markSuccess(NOW);

            assertThatThrownBy(() -> repayment.markSuccess(NOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("仅 PROCESSING 状态可标记成功");
        }
    }

    @Nested
    @DisplayName("markCancelled 标记取消")
    class MarkCancelled {

        @Test
        @DisplayName("PROCESSING→CANCELLED")
        void shouldTransitionToCancelled() {
            CreditRepayment repayment = new CreditRepayment(
                    REPAYMENT_ID, REPAYMENT_DRAFT_ID, TRANSACTION_ID,
                    CREDIT_ACCOUNT_ID, 100_000L, NOW
            );

            repayment.markCancelled(NOW);

            assertThat(repayment.getStatus()).isEqualTo(CreditRepaymentStatus.CANCELLED);
        }

        @Test
        @DisplayName("非 PROCESSING 状态标记取消抛异常")
        void shouldThrowWhenNotProcessing() {
            CreditRepayment repayment = new CreditRepayment(
                    REPAYMENT_ID, REPAYMENT_DRAFT_ID, TRANSACTION_ID,
                    CREDIT_ACCOUNT_ID, 100_000L, NOW
            );
            repayment.markCancelled(NOW);

            assertThatThrownBy(() -> repayment.markCancelled(NOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("仅 PROCESSING 状态可标记取消");
        }
    }

    @Nested
    @DisplayName("终态不可回退")
    class TerminalState {

        @Test
        @DisplayName("SUCCESS 后 markSuccess 抛异常")
        void shouldThrowWhenMarkSuccessOnSuccess() {
            CreditRepayment repayment = new CreditRepayment(
                    REPAYMENT_ID, REPAYMENT_DRAFT_ID, TRANSACTION_ID,
                    CREDIT_ACCOUNT_ID, 100_000L, NOW
            );
            repayment.markSuccess(NOW);

            assertThatThrownBy(() -> repayment.markSuccess(NOW))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("SUCCESS 后 markCancelled 抛异常")
        void shouldThrowWhenMarkCancelledOnSuccess() {
            CreditRepayment repayment = new CreditRepayment(
                    REPAYMENT_ID, REPAYMENT_DRAFT_ID, TRANSACTION_ID,
                    CREDIT_ACCOUNT_ID, 100_000L, NOW
            );
            repayment.markSuccess(NOW);

            assertThatThrownBy(() -> repayment.markCancelled(NOW))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("CANCELLED 后 markSuccess 抛异常")
        void shouldThrowWhenMarkSuccessOnCancelled() {
            CreditRepayment repayment = new CreditRepayment(
                    REPAYMENT_ID, REPAYMENT_DRAFT_ID, TRANSACTION_ID,
                    CREDIT_ACCOUNT_ID, 100_000L, NOW
            );
            repayment.markCancelled(NOW);

            assertThatThrownBy(() -> repayment.markSuccess(NOW))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("CANCELLED 后 markCancelled 抛异常")
        void shouldThrowWhenMarkCancelledOnCancelled() {
            CreditRepayment repayment = new CreditRepayment(
                    REPAYMENT_ID, REPAYMENT_DRAFT_ID, TRANSACTION_ID,
                    CREDIT_ACCOUNT_ID, 100_000L, NOW
            );
            repayment.markCancelled(NOW);

            assertThatThrownBy(() -> repayment.markCancelled(NOW))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
