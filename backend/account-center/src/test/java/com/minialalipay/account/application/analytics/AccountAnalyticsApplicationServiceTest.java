package com.minialalipay.account.application.analytics;

import com.minialalipay.account.domain.ledger.LedgerDirection;
import com.minialalipay.account.domain.ledger.LedgerEntry;
import com.minialalipay.account.domain.ledger.LedgerRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AccountAnalyticsApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-06T12:00:00Z");

    @Test
    void aggregatesPostedLedgerEntriesByDayAndDirection() {
        LedgerRepository repository = new StubRepository(List.of(
                new LedgerEntry(1L, "v1", "t1", "a", LedgerDirection.CREDIT, 800L, 1, "收入", NOW.minusSeconds(3600)),
                new LedgerEntry(2L, "v2", "t2", "a", LedgerDirection.DEBIT, 300L, 1, "支出", NOW.minusSeconds(86400))));

        var result = new AccountAnalyticsApplicationService(repository, Clock.fixed(NOW, ZoneOffset.UTC))
                .get("user-1", "7d");

        assertThat(result.incomeFen()).isEqualTo(800L);
        assertThat(result.expenseFen()).isEqualTo(300L);
        assertThat(result.trend()).hasSize(7);
        assertThat(result.trend().get(6).incomeFen()).isEqualTo(800L);
        assertThat(result.trend().get(5).expenseFen()).isEqualTo(300L);
    }

    @Test
    void rejectsUnsupportedRange() {
        var service = new AccountAnalyticsApplicationService(new StubRepository(List.of()), Clock.fixed(NOW, ZoneOffset.UTC));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.get("user-1", "year"))
                .isInstanceOf(com.minialalipay.common.error.BusinessException.class);
    }

    private record StubRepository(List<LedgerEntry> entries) implements LedgerRepository {
        @Override public java.util.Optional<com.minialalipay.account.domain.ledger.LedgerVoucher> find(String a, String b, int c) { return java.util.Optional.empty(); }
        @Override public java.util.Optional<com.minialalipay.account.domain.ledger.LedgerVoucher> findByIdForUpdate(String a) { return java.util.Optional.empty(); }
        @Override public void savePrepared(com.minialalipay.account.domain.ledger.LedgerVoucher v) { }
        @Override public LedgerTotals summarizeEntries(String a) { return new LedgerTotals(0, 0); }
        @Override public boolean postAndAppendOutbox(com.minialalipay.account.domain.ledger.LedgerVoucher v, String a, String b, Instant c) { return true; }
        @Override public java.util.List<LedgerEntry> findEntriesByUserId(String u, Instant c, long id, int limit) { return entries; }
        @Override public java.util.List<LedgerEntry> findPostedEntriesByUserId(String u, Instant since, Instant until) { return entries; }
        @Override public java.util.List<LedgerEntry.WithCounterparty> findEntriesWithCounterparty(String u, Instant c, long id, int limit) { return java.util.List.of(); }
    }
}
