package com.minialalipay.business.domain.refund;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 退款来源订单聚合测试：状态机推进、版本 CAS 与不变量。 */
class RefundOrderTest {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");

    private static RefundOrder created() {
        return RefundOrder.create("r-1", "t-original", "payee-1", "account-payee-1", "payer-1", "account-payer-1",
                "QR_PAY", "BALANCE", 100, "商品质量问题", NOW);
    }

    @Test
    void 创建订单进入待提交状态并快照原交易事实() {
        RefundOrder order = created();

        assertEquals(RefundOrderStatus.CREATED, order.getStatus());
        assertEquals("t-original", order.getOriginalTransactionId());
        assertEquals("account-payee-1", order.getMerchantAccountId());
        assertEquals("account-payer-1", order.getPayerAccountId());
        assertEquals("QR_PAY", order.getOriginalBusinessType());
        assertEquals("BALANCE", order.getFundingSource());
        assertEquals("商品质量问题", order.getReasonCode());
        assertEquals(100, order.getAmountFen());
        assertEquals(0L, order.getVersion());
    }

    @Test
    void 金额非正数拒绝创建() {
        assertThrows(IllegalArgumentException.class, () -> RefundOrder.create(
                "r-1", "t-original", "payee-1", "account-payee-1", "payer-1", "account-payer-1",
                "QR_PAY", "BALANCE", 0, "原因", NOW));
    }

    @Test
    void 提交执行绑定唯一交易并推进处理中() {
        RefundOrder order = created();

        order.submit(0L, "t-refund", NOW.plusSeconds(1));

        assertEquals(RefundOrderStatus.PROCESSING, order.getStatus());
        assertEquals("t-refund", order.getTransactionId());
        assertEquals(1L, order.getVersion());
    }

    @Test
    void 版本不匹配时提交被拒绝() {
        RefundOrder order = created();

        assertThrows(IllegalStateException.class, () -> order.submit(1L, "t-refund", NOW));
    }

    @Test
    void 已处理订单不能再次提交() {
        RefundOrder order = created();
        order.submit(0L, "t-refund", NOW);

        assertThrows(IllegalStateException.class, () -> order.submit(1L, "t-refund-2", NOW));
    }

    @Test
    void 从持久化事实重建() {
        RefundOrder rebuilt = new RefundOrder("r-1", "t-original", "payee-1", "account-payee-1",
                "payer-1", "account-payer-1", "QR_PAY", "BALANCE", 100, "商品质量问题",
                RefundOrderStatus.PROCESSING, "t-refund", 3L, NOW, NOW, null);

        assertEquals(RefundOrderStatus.PROCESSING, rebuilt.getStatus());
        assertEquals("t-refund", rebuilt.getTransactionId());
        assertEquals(3L, rebuilt.getVersion());
    }
}
