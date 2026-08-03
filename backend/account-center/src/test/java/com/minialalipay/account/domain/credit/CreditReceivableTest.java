package com.minialalipay.account.domain.credit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CreditReceivable} 领域模型单元测试。
 */
@DisplayName("CreditReceivable 信用应收汇总")
class CreditReceivableTest {

    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");
    private static final String CREDIT_ACCOUNT_ID = "credit-acc-01";

    @Nested
    @DisplayName("开户")
    class Open {

        @Test
        @DisplayName("开户后 unbilled=0, billed=0, overdue=0")
        void shouldCreateZeroReceivable() {
            CreditReceivable receivable = new CreditReceivable(CREDIT_ACCOUNT_ID, NOW);

            assertThat(receivable.getUnbilledFen()).isZero();
            assertThat(receivable.getBilledFen()).isZero();
            assertThat(receivable.getOverdueFen()).isZero();
            assertThat(receivable.getTotalOutstandingFen()).isZero();
            assertThat(receivable.getCreditAccountId()).isEqualTo(CREDIT_ACCOUNT_ID);
        }
    }

    @Nested
    @DisplayName("increaseUnbilled 增加未出账应收")
    class IncreaseUnbilled {

        @Test
        @DisplayName("增加未出账应收")
        void shouldIncreaseUnbilled() {
            CreditReceivable receivable = new CreditReceivable(CREDIT_ACCOUNT_ID, NOW);

            receivable.increaseUnbilled(500L, NOW);

            assertThat(receivable.getUnbilledFen()).isEqualTo(500L);
            assertThat(receivable.getTotalOutstandingFen()).isEqualTo(500L);
        }

        @Test
        @DisplayName("金额<=0 抛异常")
        void shouldThrowWhenAmountNotPositive() {
            CreditReceivable receivable = new CreditReceivable(CREDIT_ACCOUNT_ID, NOW);

            assertThatThrownBy(() -> receivable.increaseUnbilled(0L, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("增加金额必须为正");

            assertThatThrownBy(() -> receivable.increaseUnbilled(-1L, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("增加金额必须为正");
        }
    }

    @Nested
    @DisplayName("transferToBilled 未出账转已出账")
    class TransferToBilled {

        @Test
        @DisplayName("未出账转已出账")
        void shouldTransferUnbilledToBilled() {
            CreditReceivable receivable = new CreditReceivable(CREDIT_ACCOUNT_ID, NOW);
            receivable.increaseUnbilled(500L, NOW);

            receivable.transferToBilled(300L, NOW);

            assertThat(receivable.getUnbilledFen()).isEqualTo(200L);
            assertThat(receivable.getBilledFen()).isEqualTo(300L);
            assertThat(receivable.getTotalOutstandingFen()).isEqualTo(500L);
        }

        @Test
        @DisplayName("超过未出账金额抛异常")
        void shouldThrowWhenAmountExceedsUnbilled() {
            CreditReceivable receivable = new CreditReceivable(CREDIT_ACCOUNT_ID, NOW);
            receivable.increaseUnbilled(300L, NOW);

            assertThatThrownBy(() -> receivable.transferToBilled(301L, NOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("未出账应收不足以出账");
        }
    }

    @Nested
    @DisplayName("markOverdue 标记逾期")
    class MarkOverdue {

        @Test
        @DisplayName("已出账转逾期")
        void shouldMarkOverdue() {
            CreditReceivable receivable = new CreditReceivable(CREDIT_ACCOUNT_ID, NOW);
            receivable.increaseUnbilled(500L, NOW);
            receivable.transferToBilled(300L, NOW);

            receivable.markOverdue(100L, NOW);

            assertThat(receivable.getOverdueFen()).isEqualTo(100L);
            assertThat(receivable.getBilledFen()).isEqualTo(300L);
        }

        @Test
        @DisplayName("超过非逾期已出账金额抛异常")
        void shouldThrowWhenAmountExceedsNonOverdueBilled() {
            CreditReceivable receivable = new CreditReceivable(CREDIT_ACCOUNT_ID, NOW);
            receivable.increaseUnbilled(500L, NOW);
            receivable.transferToBilled(300L, NOW);
            receivable.markOverdue(200L, NOW);

            // 非逾期已出账 = 300 - 200 = 100，超过 100 应抛异常
            assertThatThrownBy(() -> receivable.markOverdue(101L, NOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("已出账非逾期应收不足以标记逾期");
        }
    }

    @Nested
    @DisplayName("不变量校验")
    class Invariant {

        @Test
        @DisplayName("overdue <= billed 始终成立")
        void shouldMaintainOverdueNotExceedBilled() {
            CreditReceivable receivable = new CreditReceivable(CREDIT_ACCOUNT_ID, NOW);
            receivable.increaseUnbilled(500L, NOW);
            receivable.transferToBilled(300L, NOW);
            receivable.markOverdue(200L, NOW);

            assertThat(receivable.getOverdueFen()).isLessThanOrEqualTo(receivable.getBilledFen());
        }
    }

    @Nested
    @DisplayName("decreaseByRepayment 还款扣减")
    class DecreaseByRepayment {

        @Test
        @DisplayName("先扣逾期→再扣已出账→最后扣未出账")
        void shouldDeductInOrder() {
            CreditReceivable receivable = new CreditReceivable(CREDIT_ACCOUNT_ID, NOW);
            receivable.increaseUnbilled(300L, NOW);
            receivable.transferToBilled(200L, NOW);
            receivable.markOverdue(100L, NOW);
            // unbilled=100, billed=200, overdue=100

            receivable.decreaseByRepayment(200L, NOW);

            // 先扣逾期100，再扣已出账非逾期100
            assertThat(receivable.getOverdueFen()).isZero();
            assertThat(receivable.getBilledFen()).isEqualTo(100L);
            assertThat(receivable.getUnbilledFen()).isEqualTo(100L);
        }

        @Test
        @DisplayName("超过应收总额抛异常")
        void shouldThrowWhenAmountExceedsTotal() {
            CreditReceivable receivable = new CreditReceivable(CREDIT_ACCOUNT_ID, NOW);
            receivable.increaseUnbilled(300L, NOW);
            receivable.transferToBilled(200L, NOW);
            // unbilled=100, billed=200, overdue=0, total=300

            assertThatThrownBy(() -> receivable.decreaseByRepayment(301L, NOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("应收不足以扣减还款金额");
        }
    }

    @Nested
    @DisplayName("完整流程")
    class FullFlow {

        @Test
        @DisplayName("increaseUnbilled→transferToBilled→markOverdue→decreaseByRepayment")
        void shouldCompleteFullFlow() {
            CreditReceivable receivable = new CreditReceivable(CREDIT_ACCOUNT_ID, NOW);

            // increaseUnbilled
            receivable.increaseUnbilled(500L, NOW);
            assertThat(receivable.getUnbilledFen()).isEqualTo(500L);
            assertThat(receivable.getBilledFen()).isZero();
            assertThat(receivable.getOverdueFen()).isZero();

            // transferToBilled
            receivable.transferToBilled(300L, NOW);
            assertThat(receivable.getUnbilledFen()).isEqualTo(200L);
            assertThat(receivable.getBilledFen()).isEqualTo(300L);

            // markOverdue
            receivable.markOverdue(100L, NOW);
            assertThat(receivable.getOverdueFen()).isEqualTo(100L);

            // decreaseByRepayment: 先扣逾期100→再扣已出账300→剩余0
            // 注意：overdue 扣减后 overdueFen=0，billedNonOverdue=billedFen(300)-0=300
            receivable.decreaseByRepayment(400L, NOW);
            assertThat(receivable.getOverdueFen()).isZero();
            assertThat(receivable.getBilledFen()).isZero();
            assertThat(receivable.getUnbilledFen()).isEqualTo(200L);
        }
    }
}
