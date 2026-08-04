package com.minialalipay.business.domain.transaction;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FundTransactionTest {

    private static final Instant NOW = Instant.parse("2026-08-04T08:00:00Z");

    @Test
    void 未核验事实不能直接发布成功() {
        FundTransaction transaction = transaction();

        assertThrows(IllegalStateException.class,
                () -> transaction.publishSuccess(false, NOW.plusSeconds(1)));
    }

    @Test
    void 补偿完成后进入取消终态() {
        FundTransaction transaction = transaction();

        transaction.startCompensating(NOW.plusSeconds(1));
        transaction.publishCancelled(true, NOW.plusSeconds(2));

        assertEquals(TransactionStatus.CANCELLED, transaction.getStatus());
        assertEquals(2L, transaction.getVersion());
    }

    private FundTransaction transaction() {
        return FundTransaction.accept("tx-1", TransactionType.TRANSFER, SourceType.TRANSFER_DRAFT,
                "draft-1", "payer-user", "payer-account", "payee-account", FundingSource.BALANCE,
                100L, "idem-1", "LOW", "0123456789abcdef0123456789abcdef", NOW);
    }
}
