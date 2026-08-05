package com.minialalipay.business.domain.confirmation;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfirmationTest {

    private static final Instant NOW = Instant.parse("2026-08-04T08:00:00Z");

    @Test
    void 有效确认只能消费一次() {
        Confirmation confirmation = Confirmation.issue("confirmation-1", new byte[32],
                SubjectType.TRANSFER_DRAFT, "draft-1", new byte[32], "payer-user",
                "proof-1", 3L, NOW);

        confirmation.consume(NOW.plusSeconds(10));

        assertEquals(ConfirmationStatus.CONSUMED, confirmation.getStatus());
        assertThrows(IllegalStateException.class, () -> confirmation.consume(NOW.plusSeconds(11)));
    }

    @Test
    void 两分钟后确认过期() {
        Confirmation confirmation = Confirmation.issue("confirmation-1", new byte[32],
                SubjectType.TRANSFER_DRAFT, "draft-1", new byte[32], "payer-user",
                "proof-1", 3L, NOW);

        assertThrows(IllegalStateException.class, () -> confirmation.consume(NOW.plusSeconds(121)));
    }
}
