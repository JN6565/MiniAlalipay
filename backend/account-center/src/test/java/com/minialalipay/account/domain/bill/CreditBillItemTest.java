package com.minialalipay.account.domain.bill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CreditBillItem} 领域模型单元测试。
 */
@DisplayName("CreditBillItem 账单明细")
class CreditBillItemTest {

    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");
    private static final String BILL_ID = "bill-01";
    private static final String PURCHASE_ID = "purchase-01";

    @Nested
    @DisplayName("创建账单明细")
    class Create {

        @Test
        @DisplayName("创建后 status=ACTIVE，allocatedPaid=0")
        void shouldCreateActiveItem() {
            CreditBillItem item = new CreditBillItem(BILL_ID, PURCHASE_ID, 100_000L, NOW);

            assertThat(item.getStatus()).isEqualTo(CreditBillItemStatus.ACTIVE);
            assertThat(item.getAllocatedPaidFen()).isZero();
            assertThat(item.getAmountFen()).isEqualTo(100_000L);
            assertThat(item.getReversedFen()).isZero();
            assertThat(item.getBillId()).isEqualTo(BILL_ID);
            assertThat(item.getPurchaseId()).isEqualTo(PURCHASE_ID);
            assertThat(item.getCreatedAt()).isEqualTo(NOW);
            assertThat(item.getUpdatedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("金额<=0 抛异常")
        void shouldThrowWhenAmountNotPositive() {
            assertThatThrownBy(() -> new CreditBillItem(BILL_ID, PURCHASE_ID, 0L, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("明细金额必须为正");

            assertThatThrownBy(() -> new CreditBillItem(BILL_ID, PURCHASE_ID, -1L, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("明细金额必须为正");
        }
    }

    @Nested
    @DisplayName("applyRepayment 应用还款分配")
    class ApplyRepayment {

        @Test
        @DisplayName("部分还款：allocatedPaid 增加，status 仍 ACTIVE")
        void shouldApplyPartialRepayment() {
            CreditBillItem item = new CreditBillItem(BILL_ID, PURCHASE_ID, 100_000L, NOW);

            item.applyRepayment(40_000L, NOW);

            assertThat(item.getAllocatedPaidFen()).isEqualTo(40_000L);
            assertThat(item.getStatus()).isEqualTo(CreditBillItemStatus.ACTIVE);
        }

        @Test
        @DisplayName("全额还款：status 变 REPAID")
        void shouldTransitionToRepaidWhenFullyRepaid() {
            CreditBillItem item = new CreditBillItem(BILL_ID, PURCHASE_ID, 100_000L, NOW);

            item.applyRepayment(100_000L, NOW);

            assertThat(item.getAllocatedPaidFen()).isEqualTo(100_000L);
            assertThat(item.getStatus()).isEqualTo(CreditBillItemStatus.REPAID);
        }

        @Test
        @DisplayName("超过未还余额抛异常")
        void shouldThrowWhenAmountExceedsOutstanding() {
            CreditBillItem item = new CreditBillItem(BILL_ID, PURCHASE_ID, 100_000L, NOW);
            item.applyRepayment(60_000L, NOW);

            assertThatThrownBy(() -> item.applyRepayment(41_000L, NOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("分配金额超过明细未还余额");
        }

        @Test
        @DisplayName("金额<=0 抛异常")
        void shouldThrowWhenAmountNotPositive() {
            CreditBillItem item = new CreditBillItem(BILL_ID, PURCHASE_ID, 100_000L, NOW);

            assertThatThrownBy(() -> item.applyRepayment(0L, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("分配金额必须为正");

            assertThatThrownBy(() -> item.applyRepayment(-1L, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("分配金额必须为正");
        }
    }
}
