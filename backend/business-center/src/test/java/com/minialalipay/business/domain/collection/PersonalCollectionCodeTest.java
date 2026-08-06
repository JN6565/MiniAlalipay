package com.minialalipay.business.domain.collection;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PersonalCollectionCodeTest {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");

    @Test
    void 换码会原子停用旧码并生成新码() {
        PersonalCollectionCode code = PersonalCollectionCode.activate("code-1", "user-1", "account-1", NOW);

        code.replace(0L, NOW.plusSeconds(1));

        assertEquals(PersonalCollectionCodeStatus.REPLACED, code.getStatus());
        assertEquals(1L, code.getVersion());
        assertThrows(IllegalStateException.class, () -> code.deactivate(0L, NOW.plusSeconds(2)));
    }

    @Test
    void 已停用的个人码不能创建收款订单() {
        PersonalCollectionCode code = PersonalCollectionCode.activate("code-1", "user-1", "account-1", NOW);
        code.deactivate(0L, NOW.plusSeconds(1));

        assertThrows(IllegalStateException.class, () -> code.ensureActive());
    }
}
