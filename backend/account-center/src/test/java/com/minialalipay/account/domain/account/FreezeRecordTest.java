package com.minialalipay.account.domain.account;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FreezeRecordTest {

    private static final Instant NOW = Instant.parse("2026-08-04T08:00:00Z");

    @Test
    void frozenRecordCanBeConfirmedOnlyOnce() {
        FreezeRecord record = FreezeRecord.create("freeze", "transaction", "account",
                FreezePurpose.TRANSFER_OUT, 300L, "xid-001", NOW);

        record.confirm(NOW.plusSeconds(1));
        record.confirm(NOW.plusSeconds(2));

        assertThat(record.getStatus()).isEqualTo(FreezeStatus.CONFIRMED);
        assertThat(record.getUpdatedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void cancelledRecordCannotBeConfirmed() {
        FreezeRecord record = FreezeRecord.create("freeze", "transaction", "account",
                FreezePurpose.TRANSFER_OUT, 300L, "xid-001", NOW);
        record.cancel(NOW.plusSeconds(1));

        assertThatThrownBy(() -> record.confirm(NOW.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("已释放的冻结记录不能确认");
    }
}
