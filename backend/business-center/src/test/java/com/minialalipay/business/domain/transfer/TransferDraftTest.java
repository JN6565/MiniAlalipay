package com.minialalipay.business.domain.transfer;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransferDraftTest {

    private static final Instant NOW = Instant.parse("2026-08-04T08:00:00Z");

    @Test
    void 修改草稿后递增版本并回到待校验状态() {
        TransferDraft draft = TransferDraft.create("draft-1", "payer-user", "payee-user",
                "payer-account", "payee-account", 100L, "原备注", NOW);
        draft.validate(0L, NOW.plusSeconds(1));

        draft.edit(1L, 200L, "新备注", NOW.plusSeconds(2));

        assertEquals(DraftStatus.DRAFT, draft.getStatus());
        assertEquals(2L, draft.getVersion());
        assertEquals(200L, draft.getAmountFen());
    }

    @Test
    void 版本不匹配时拒绝编辑() {
        TransferDraft draft = TransferDraft.create("draft-1", "payer-user", "payee-user",
                "payer-account", "payee-account", 100L, null, NOW);

        assertThrows(IllegalStateException.class,
                () -> draft.edit(1L, 200L, null, NOW.plusSeconds(1)));
    }

    @Test
    void 金额超过转账上限时拒绝创建() {
        assertThrows(IllegalArgumentException.class,
                () -> TransferDraft.create("draft-1", "payer-user", "payee-user",
                        "payer-account", "payee-account", 5_000_001L, null, NOW));
    }
}
