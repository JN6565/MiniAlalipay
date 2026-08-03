package com.minialalipay.account.domain.repayment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CreditRepaymentDraft} 领域模型单元测试。
 */
@DisplayName("CreditRepaymentDraft 信用还款草稿")
class CreditRepaymentDraftTest {

    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");
    private static final Instant FUTURE = Instant.parse("2026-08-01T12:00:00Z");
    private static final Instant PAST = Instant.parse("2026-08-01T08:00:00Z");
    private static final String REPAYMENT_DRAFT_ID = "draft-01";
    private static final String USER_ID = "user-01";
    private static final String CREDIT_ACCOUNT_ID = "credit-acc-01";
    private static final String PAYER_ACCOUNT_ID = "payer-acc-01";
    private static final String ALLOCATION_SNAPSHOT = "{}";
    private static final byte[] ALLOCATION_HASH = new byte[32];

    @Nested
    @DisplayName("创建还款草稿")
    class Create {

        @Test
        @DisplayName("创建后 status=DRAFT")
        void shouldCreateDraft() {
            CreditRepaymentDraft draft = new CreditRepaymentDraft(
                    REPAYMENT_DRAFT_ID, USER_ID, CREDIT_ACCOUNT_ID,
                    PAYER_ACCOUNT_ID, 100_000L,
                    ALLOCATION_SNAPSHOT, ALLOCATION_HASH,
                    FUTURE, NOW
            );

            assertThat(draft.getStatus()).isEqualTo(CreditRepaymentDraftStatus.DRAFT);
            assertThat(draft.getAmountFen()).isEqualTo(100_000L);
            assertThat(draft.getRepaymentDraftId()).isEqualTo(REPAYMENT_DRAFT_ID);
            assertThat(draft.getUserId()).isEqualTo(USER_ID);
            assertThat(draft.getCreditAccountId()).isEqualTo(CREDIT_ACCOUNT_ID);
            assertThat(draft.getPayerAccountId()).isEqualTo(PAYER_ACCOUNT_ID);
            assertThat(draft.getAllocationSnapshot()).isEqualTo(ALLOCATION_SNAPSHOT);
            assertThat(draft.getAllocationHash()).isEqualTo(ALLOCATION_HASH);
            assertThat(draft.getExpiresAt()).isEqualTo(FUTURE);
            assertThat(draft.getVersion()).isZero();
            assertThat(draft.getCreatedAt()).isEqualTo(NOW);
            assertThat(draft.getUpdatedAt()).isEqualTo(NOW);
        }
    }

    @Nested
    @DisplayName("金额范围校验")
    class AmountValidation {

        @Test
        @DisplayName("金额<1 抛异常")
        void shouldThrowWhenAmountLessThanOne() {
            assertThatThrownBy(() -> new CreditRepaymentDraft(
                    REPAYMENT_DRAFT_ID, USER_ID, CREDIT_ACCOUNT_ID,
                    PAYER_ACCOUNT_ID, 0L,
                    ALLOCATION_SNAPSHOT, ALLOCATION_HASH,
                    FUTURE, NOW
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("还款金额必须在 1~5000000 分范围内");
        }

        @Test
        @DisplayName("金额>5000000 抛异常")
        void shouldThrowWhenAmountExceedsMax() {
            assertThatThrownBy(() -> new CreditRepaymentDraft(
                    REPAYMENT_DRAFT_ID, USER_ID, CREDIT_ACCOUNT_ID,
                    PAYER_ACCOUNT_ID, 5_000_001L,
                    ALLOCATION_SNAPSHOT, ALLOCATION_HASH,
                    FUTURE, NOW
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("还款金额必须在 1~5000000 分范围内");
        }
    }

    @Nested
    @DisplayName("confirm 确认草稿")
    class Confirm {

        @Test
        @DisplayName("DRAFT→CONFIRMED")
        void shouldTransitionToConfirmed() {
            CreditRepaymentDraft draft = new CreditRepaymentDraft(
                    REPAYMENT_DRAFT_ID, USER_ID, CREDIT_ACCOUNT_ID,
                    PAYER_ACCOUNT_ID, 100_000L,
                    ALLOCATION_SNAPSHOT, ALLOCATION_HASH,
                    FUTURE, NOW
            );

            draft.confirm(NOW);

            assertThat(draft.getStatus()).isEqualTo(CreditRepaymentDraftStatus.CONFIRMED);
        }

        @Test
        @DisplayName("非 DRAFT 状态确认抛异常")
        void shouldThrowWhenNotDraft() {
            CreditRepaymentDraft draft = new CreditRepaymentDraft(
                    REPAYMENT_DRAFT_ID, USER_ID, CREDIT_ACCOUNT_ID,
                    PAYER_ACCOUNT_ID, 100_000L,
                    ALLOCATION_SNAPSHOT, ALLOCATION_HASH,
                    FUTURE, NOW
            );
            draft.confirm(NOW);

            assertThatThrownBy(() -> draft.confirm(NOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("仅 DRAFT 状态可确认");
        }

        @Test
        @DisplayName("已过期抛异常")
        void shouldThrowWhenExpired() {
            CreditRepaymentDraft draft = new CreditRepaymentDraft(
                    REPAYMENT_DRAFT_ID, USER_ID, CREDIT_ACCOUNT_ID,
                    PAYER_ACCOUNT_ID, 100_000L,
                    ALLOCATION_SNAPSHOT, ALLOCATION_HASH,
                    PAST, NOW
            );

            assertThatThrownBy(() -> draft.confirm(NOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("草稿已过期，不可确认");
        }
    }

    @Nested
    @DisplayName("consume 消费草稿")
    class Consume {

        @Test
        @DisplayName("CONFIRMED→CONSUMED")
        void shouldTransitionToConsumed() {
            CreditRepaymentDraft draft = new CreditRepaymentDraft(
                    REPAYMENT_DRAFT_ID, USER_ID, CREDIT_ACCOUNT_ID,
                    PAYER_ACCOUNT_ID, 100_000L,
                    ALLOCATION_SNAPSHOT, ALLOCATION_HASH,
                    FUTURE, NOW
            );
            draft.confirm(NOW);

            draft.consume(NOW);

            assertThat(draft.getStatus()).isEqualTo(CreditRepaymentDraftStatus.CONSUMED);
        }

        @Test
        @DisplayName("非 CONFIRMED 状态消费抛异常")
        void shouldThrowWhenNotConfirmed() {
            CreditRepaymentDraft draft = new CreditRepaymentDraft(
                    REPAYMENT_DRAFT_ID, USER_ID, CREDIT_ACCOUNT_ID,
                    PAYER_ACCOUNT_ID, 100_000L,
                    ALLOCATION_SNAPSHOT, ALLOCATION_HASH,
                    FUTURE, NOW
            );

            assertThatThrownBy(() -> draft.consume(NOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("仅 CONFIRMED 状态可消费");
        }
    }

    @Nested
    @DisplayName("expire 过期")
    class Expire {

        @Test
        @DisplayName("DRAFT→EXPIRED")
        void shouldTransitionToExpired() {
            CreditRepaymentDraft draft = new CreditRepaymentDraft(
                    REPAYMENT_DRAFT_ID, USER_ID, CREDIT_ACCOUNT_ID,
                    PAYER_ACCOUNT_ID, 100_000L,
                    ALLOCATION_SNAPSHOT, ALLOCATION_HASH,
                    FUTURE, NOW
            );

            draft.expire(NOW);

            assertThat(draft.getStatus()).isEqualTo(CreditRepaymentDraftStatus.EXPIRED);
        }

        @Test
        @DisplayName("CONSUMED 状态不改变")
        void shouldNotChangeConsumedStatus() {
            CreditRepaymentDraft draft = new CreditRepaymentDraft(
                    REPAYMENT_DRAFT_ID, USER_ID, CREDIT_ACCOUNT_ID,
                    PAYER_ACCOUNT_ID, 100_000L,
                    ALLOCATION_SNAPSHOT, ALLOCATION_HASH,
                    FUTURE, NOW
            );
            draft.confirm(NOW);
            draft.consume(NOW);

            draft.expire(NOW);

            assertThat(draft.getStatus()).isEqualTo(CreditRepaymentDraftStatus.CONSUMED);
        }
    }

    @Nested
    @DisplayName("isExpired 过期判断")
    class IsExpired {

        @Test
        @DisplayName("当前时间超过过期时间返回 true")
        void shouldReturnTrueWhenAfterExpiresAt() {
            CreditRepaymentDraft draft = new CreditRepaymentDraft(
                    REPAYMENT_DRAFT_ID, USER_ID, CREDIT_ACCOUNT_ID,
                    PAYER_ACCOUNT_ID, 100_000L,
                    ALLOCATION_SNAPSHOT, ALLOCATION_HASH,
                    PAST, NOW
            );

            assertThat(draft.isExpired(NOW)).isTrue();
        }

        @Test
        @DisplayName("当前时间未超过过期时间返回 false")
        void shouldReturnFalseWhenBeforeExpiresAt() {
            CreditRepaymentDraft draft = new CreditRepaymentDraft(
                    REPAYMENT_DRAFT_ID, USER_ID, CREDIT_ACCOUNT_ID,
                    PAYER_ACCOUNT_ID, 100_000L,
                    ALLOCATION_SNAPSHOT, ALLOCATION_HASH,
                    FUTURE, NOW
            );

            assertThat(draft.isExpired(NOW)).isFalse();
        }
    }
}
