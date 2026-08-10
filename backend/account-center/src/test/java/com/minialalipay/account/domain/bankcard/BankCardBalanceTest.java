package com.minialalipay.account.domain.bankcard;

import com.minialalipay.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 银行卡虚拟余额领域行为测试：覆盖余额增加（recharge）、余额减少（withdraw）、
 * 支付扣减、余额不足、状态约束（UNBOUND 禁止操作）、零/负金额拒绝、
 * 解绑前余额未清零拒绝等不变量。
 *
 * <p>注意：业务层“充值”从卡出资金，调用 card.withdraw()；“提现”向卡入账，
 * 调用 card.recharge()。本节测试仅验证域方法本身的余额变更逻辑。</p>
 */
class BankCardBalanceTest {

    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");

    /** 构造一张余额为给定值的 ACTIVE 银行卡。 */
    private BankCard cardWithBalance(long balanceFen) {
        return new BankCard("CARD_001", "USER_001", "ACC_001", "ICBC", "中国工商银行",
                BankCardType.DEBIT, "621226", "1234", "张*", "3301**********1234",
                "138****5678", balanceFen, false, BankCardStatus.ACTIVE, null,
                0L, NOW, NOW);
    }

    /** 构造一张 UNBOUND 状态的银行卡，用于测试状态约束。 */
    private BankCard unboundCard() {
        return new BankCard("CARD_002", "USER_001", "ACC_001", "ICBC", "中国工商银行",
                BankCardType.DEBIT, "621226", "1234", "张*", "3301**********1234",
                "138****5678", 0L, false, BankCardStatus.UNBOUND, NOW,
                0L, NOW, NOW);
    }

    // ─── 余额增加（recharge，业务层“提现”调用此方法） ───

    @Test
    void rechargeIncreasesBalance() {
        BankCard card = cardWithBalance(10000L);
        card.recharge(5000L, NOW);
        assertThat(card.getBalanceFen()).isEqualTo(15000L);
    }

    @Test
    void rechargeFromZeroBalance() {
        BankCard card = cardWithBalance(0L);
        card.recharge(1L, NOW);
        assertThat(card.getBalanceFen()).isEqualTo(1L);
    }

    @Test
    void rechargeZeroAmountRejected() {
        BankCard card = cardWithBalance(0L);
        assertThatThrownBy(() -> card.recharge(0L, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechargeNegativeAmountRejected() {
        BankCard card = cardWithBalance(0L);
        assertThatThrownBy(() -> card.recharge(-100L, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechargeOnUnboundCardRejected() {
        BankCard card = unboundCard();
        assertThatThrownBy(() -> card.recharge(100L, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(BankCardErrorCode.BANK_CARD_ALREADY_UNBOUND);
    }

    // ─── 余额减少（withdraw，业务层“充值”调用此方法） ───

    @Test
    void withdrawDecreasesBalance() {
        BankCard card = cardWithBalance(10000L);
        card.withdraw(3000L, NOW);
        assertThat(card.getBalanceFen()).isEqualTo(7000L);
    }

    @Test
    void withdrawExactBalanceToZero() {
        BankCard card = cardWithBalance(5000L);
        card.withdraw(5000L, NOW);
        assertThat(card.getBalanceFen()).isEqualTo(0L);
    }

    @Test
    void withdrawExceedingBalanceRejected() {
        BankCard card = cardWithBalance(1000L);
        assertThatThrownBy(() -> card.withdraw(1001L, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(BankCardErrorCode.BANK_CARD_INSUFFICIENT_BALANCE);
    }

    @Test
    void withdrawFromZeroBalanceRejected() {
        BankCard card = cardWithBalance(0L);
        assertThatThrownBy(() -> card.withdraw(1L, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(BankCardErrorCode.BANK_CARD_INSUFFICIENT_BALANCE);
    }

    @Test
    void withdrawZeroAmountRejected() {
        BankCard card = cardWithBalance(1000L);
        assertThatThrownBy(() -> card.withdraw(0L, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withdrawOnUnboundCardRejected() {
        BankCard card = unboundCard();
        assertThatThrownBy(() -> card.withdraw(100L, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(BankCardErrorCode.BANK_CARD_ALREADY_UNBOUND);
    }

    // ─── 支付扣减（复用 withdraw 逻辑） ───

    @Test
    void deductForPaymentDecreasesBalance() {
        BankCard card = cardWithBalance(20000L);
        card.deductForPayment(8000L, NOW);
        assertThat(card.getBalanceFen()).isEqualTo(12000L);
    }

    @Test
    void deductForPaymentInsufficientBalanceRejected() {
        BankCard card = cardWithBalance(500L);
        assertThatThrownBy(() -> card.deductForPayment(501L, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(BankCardErrorCode.BANK_CARD_INSUFFICIENT_BALANCE);
    }

    // ─── 解绑前余额约束 ───

    @Test
    void unbindCardWithBalanceThrowsHasBalanceError() {
        // 余额未清零的卡解绑时必须拒绝，防止资金丢失
        BankCard card = cardWithBalance(100L);
        assertThatThrownBy(() -> card.unbind(NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(BankCardErrorCode.BANK_CARD_HAS_BALANCE);
    }

    @Test
    void unbindCardWithZeroBalanceAllowed() {
        // 余额已清零的卡允许解绑
        BankCard card = cardWithBalance(0L);
        card.unbind(NOW);
        assertThat(card.getStatus()).isEqualTo(BankCardStatus.UNBOUND);
    }

    // ─── 连续操作 ───

    @Test
    void multipleRechargeAndWithdrawAccumulateCorrectly() {
        BankCard card = cardWithBalance(0L);
        card.recharge(10000L, NOW);
        card.recharge(5000L, NOW);
        assertThat(card.getBalanceFen()).isEqualTo(15000L);

        card.withdraw(3000L, NOW);
        assertThat(card.getBalanceFen()).isEqualTo(12000L);

        card.deductForPayment(2000L, NOW);
        assertThat(card.getBalanceFen()).isEqualTo(10000L);
    }
}
