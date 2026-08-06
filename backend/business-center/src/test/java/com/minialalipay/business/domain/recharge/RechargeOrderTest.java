package com.minialalipay.business.domain.recharge;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RechargeOrderTest {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");

    @Test
    void 充值订单固定策略快照并可由统一交易受理推进到处理中() {
        RechargePolicy policy = RechargePolicy.defaultActive("policy-1", NOW);
        RechargeOrder order = RechargeOrder.create("recharge-1", "user-1", "account-1", 100L,
                LocalDate.of(2026, 8, 5), policy, NOW);

        assertEquals(RechargeOrderStatus.PENDING_CHANNEL, order.getStatus());
        assertEquals("policy-1", order.getPolicyId());
        assertEquals(0L, order.getVersion());

        order.acceptFundTransaction(0L, "tx-1", NOW.plusSeconds(1));

        assertEquals(RechargeOrderStatus.PROCESSING, order.getStatus());
        assertEquals("tx-1", order.getTransactionId());
        assertEquals(1L, order.getVersion());
        assertThrows(IllegalStateException.class,
                () -> order.acceptFundTransaction(1L, "tx-2", NOW.plusSeconds(2)));
    }

    @Test
    void 受理前渠道拒绝为终态且重复拒绝幂等() {
        RechargePolicy policy = RechargePolicy.defaultActive("policy-1", NOW);
        RechargeOrder order = RechargeOrder.create("recharge-1", "user-1", "account-1", 100L,
                LocalDate.of(2026, 8, 5), policy, NOW);

        order.rejectByChannel(0L, "CHANNEL_REJECTED", NOW.plusSeconds(1));
        order.rejectByChannel(1L, "CHANNEL_REJECTED", NOW.plusSeconds(2));

        assertEquals(RechargeOrderStatus.REJECTED, order.getStatus());
        assertEquals("CHANNEL_REJECTED", order.getRejectReasonCode());
        assertEquals(1L, order.getVersion());
    }
}
