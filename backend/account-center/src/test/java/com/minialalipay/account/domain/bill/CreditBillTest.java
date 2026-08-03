package com.minialalipay.account.domain.bill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CreditBill} 领域模型单元测试。
 */
@DisplayName("CreditBill 月度信用账单")
class CreditBillTest {

    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");
    private static final String BILL_ID = "bill-01";
    private static final String CREDIT_ACCOUNT_ID = "credit-acc-01";
    private static final String PERIOD = "2026-07";
    private static final LocalDate STATEMENT_DATE = LocalDate.parse("2026-07-01");
    private static final Instant DUE_AT = Instant.parse("2026-07-10T23:59:59Z");

    @Nested
    @DisplayName("创建账单")
    class Create {

        @Test
        @DisplayName("创建后 total>0, paid=0, outstanding=total, status=OPEN")
        void shouldCreateOpenBill() {
            CreditBill bill = new CreditBill(
                    BILL_ID, CREDIT_ACCOUNT_ID, PERIOD,
                    STATEMENT_DATE, DUE_AT, 100_000L, NOW
            );

            assertThat(bill.getTotalFen()).isEqualTo(100_000L);
            assertThat(bill.getPaidFen()).isZero();
            assertThat(bill.getOutstandingFen()).isEqualTo(100_000L);
            assertThat(bill.getReversedFen()).isZero();
            assertThat(bill.getStatus()).isEqualTo(CreditBillStatus.OPEN);
            assertThat(bill.getBillId()).isEqualTo(BILL_ID);
            assertThat(bill.getCreditAccountId()).isEqualTo(CREDIT_ACCOUNT_ID);
            assertThat(bill.getPeriod()).isEqualTo(PERIOD);
            assertThat(bill.getStatementDate()).isEqualTo(STATEMENT_DATE);
            assertThat(bill.getDueAt()).isEqualTo(DUE_AT);
            assertThat(bill.getVersion()).isZero();
            assertThat(bill.getCreatedAt()).isEqualTo(NOW);
            assertThat(bill.getUpdatedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("total<=0 抛异常")
        void shouldThrowWhenTotalNotPositive() {
            assertThatThrownBy(() -> new CreditBill(
                    BILL_ID, CREDIT_ACCOUNT_ID, PERIOD,
                    STATEMENT_DATE, DUE_AT, 0L, NOW
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("账单总额必须为正");

            assertThatThrownBy(() -> new CreditBill(
                    BILL_ID, CREDIT_ACCOUNT_ID, PERIOD,
                    STATEMENT_DATE, DUE_AT, -1L, NOW
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("账单总额必须为正");
        }
    }

    @Nested
    @DisplayName("applyRepayment 应用还款")
    class ApplyRepayment {

        @Test
        @DisplayName("部分还款：OPEN→PARTIALLY_PAID，paid 增加 outstanding 减少")
        void shouldTransitionToPartiallyPaid() {
            CreditBill bill = new CreditBill(
                    BILL_ID, CREDIT_ACCOUNT_ID, PERIOD,
                    STATEMENT_DATE, DUE_AT, 100_000L, NOW
            );

            bill.applyRepayment(40_000L, NOW);

            assertThat(bill.getStatus()).isEqualTo(CreditBillStatus.PARTIALLY_PAID);
            assertThat(bill.getPaidFen()).isEqualTo(40_000L);
            assertThat(bill.getOutstandingFen()).isEqualTo(60_000L);
        }

        @Test
        @DisplayName("全额还款：→PAID，outstanding=0")
        void shouldTransitionToPaidWhenFullyRepaid() {
            CreditBill bill = new CreditBill(
                    BILL_ID, CREDIT_ACCOUNT_ID, PERIOD,
                    STATEMENT_DATE, DUE_AT, 100_000L, NOW
            );

            bill.applyRepayment(100_000L, NOW);

            assertThat(bill.getStatus()).isEqualTo(CreditBillStatus.PAID);
            assertThat(bill.getPaidFen()).isEqualTo(100_000L);
            assertThat(bill.getOutstandingFen()).isZero();
            assertThat(bill.isPaid()).isTrue();
        }

        @Test
        @DisplayName("PAID 状态再还款抛异常")
        void shouldThrowWhenRepayPaidBill() {
            CreditBill bill = new CreditBill(
                    BILL_ID, CREDIT_ACCOUNT_ID, PERIOD,
                    STATEMENT_DATE, DUE_AT, 100_000L, NOW
            );
            bill.applyRepayment(100_000L, NOW);

            assertThatThrownBy(() -> bill.applyRepayment(1L, NOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("已全额还清的账单不可再还款");
        }

        @Test
        @DisplayName("超过未还余额抛异常")
        void shouldThrowWhenAmountExceedsOutstanding() {
            CreditBill bill = new CreditBill(
                    BILL_ID, CREDIT_ACCOUNT_ID, PERIOD,
                    STATEMENT_DATE, DUE_AT, 100_000L, NOW
            );
            bill.applyRepayment(60_000L, NOW);

            assertThatThrownBy(() -> bill.applyRepayment(41_000L, NOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("还款金额超过账单未还余额");
        }
    }

    @Nested
    @DisplayName("markOverdue 标记逾期")
    class MarkOverdue {

        @Test
        @DisplayName("OPEN→OVERDUE")
        void shouldTransitionFromOpenToOverdue() {
            CreditBill bill = new CreditBill(
                    BILL_ID, CREDIT_ACCOUNT_ID, PERIOD,
                    STATEMENT_DATE, DUE_AT, 100_000L, NOW
            );

            bill.markOverdue(NOW);

            assertThat(bill.getStatus()).isEqualTo(CreditBillStatus.OVERDUE);
            assertThat(bill.isOverdue()).isTrue();
        }

        @Test
        @DisplayName("PARTIALLY_PAID→OVERDUE")
        void shouldTransitionFromPartiallyPaidToOverdue() {
            CreditBill bill = new CreditBill(
                    BILL_ID, CREDIT_ACCOUNT_ID, PERIOD,
                    STATEMENT_DATE, DUE_AT, 100_000L, NOW
            );
            bill.applyRepayment(40_000L, NOW);

            bill.markOverdue(NOW);

            assertThat(bill.getStatus()).isEqualTo(CreditBillStatus.OVERDUE);
        }

        @Test
        @DisplayName("PAID 状态标记逾期抛异常")
        void shouldThrowWhenMarkOverdueOnPaidBill() {
            CreditBill bill = new CreditBill(
                    BILL_ID, CREDIT_ACCOUNT_ID, PERIOD,
                    STATEMENT_DATE, DUE_AT, 100_000L, NOW
            );
            bill.applyRepayment(100_000L, NOW);

            assertThatThrownBy(() -> bill.markOverdue(NOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("仅 OPEN 和 PARTIALLY_PAID 状态可标记逾期");
        }
    }

    @Nested
    @DisplayName("不变量校验")
    class Invariant {

        @Test
        @DisplayName("total = paid + reversed + outstanding 始终成立")
        void shouldMaintainBalanceInvariant() {
            CreditBill bill = new CreditBill(
                    BILL_ID, CREDIT_ACCOUNT_ID, PERIOD,
                    STATEMENT_DATE, DUE_AT, 100_000L, NOW
            );

            bill.applyRepayment(30_000L, NOW);
            assertThat(bill.getTotalFen())
                    .isEqualTo(bill.getPaidFen() + bill.getReversedFen() + bill.getOutstandingFen());

            bill.applyRepayment(50_000L, NOW);
            assertThat(bill.getTotalFen())
                    .isEqualTo(bill.getPaidFen() + bill.getReversedFen() + bill.getOutstandingFen());
        }
    }

    @Nested
    @DisplayName("isPaid / isOverdue 判断方法")
    class StatusCheck {

        @Test
        @DisplayName("isPaid 仅 PAID 状态返回 true")
        void shouldReturnTrueOnlyForPaid() {
            CreditBill bill = new CreditBill(
                    BILL_ID, CREDIT_ACCOUNT_ID, PERIOD,
                    STATEMENT_DATE, DUE_AT, 100_000L, NOW
            );

            assertThat(bill.isPaid()).isFalse();

            bill.applyRepayment(100_000L, NOW);
            assertThat(bill.isPaid()).isTrue();
        }

        @Test
        @DisplayName("isOverdue 仅 OVERDUE 状态返回 true")
        void shouldReturnTrueOnlyForOverdue() {
            CreditBill bill = new CreditBill(
                    BILL_ID, CREDIT_ACCOUNT_ID, PERIOD,
                    STATEMENT_DATE, DUE_AT, 100_000L, NOW
            );

            assertThat(bill.isOverdue()).isFalse();

            bill.markOverdue(NOW);
            assertThat(bill.isOverdue()).isTrue();
        }
    }
}
