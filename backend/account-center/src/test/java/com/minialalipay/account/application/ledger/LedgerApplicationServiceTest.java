package com.minialalipay.account.application.ledger;

import com.minialalipay.account.domain.ledger.LedgerDirection;
import com.minialalipay.account.domain.ledger.LedgerEntry;
import com.minialalipay.account.domain.ledger.LedgerRepository;
import com.minialalipay.account.domain.ledger.LedgerVoucher;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.minialalipay.common.error.BusinessException;

class LedgerApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-04T08:00:00Z");

    @Test
    void repeatedVoucherBusinessKeyReturnsExistingPostedFact() {
        InMemoryLedgerRepository repository = new InMemoryLedgerRepository();
        LedgerApplicationService service = new LedgerApplicationService(repository);
        LedgerVoucher first = voucher("voucher-1");
        LedgerVoucher repeated = voucher("voucher-2");

        LedgerVoucher firstResult = service.post(first, "event-00000000000000000001", "trace-00000000000000000000000000",
                NOW.plusSeconds(1));
        LedgerVoucher repeatedResult = service.post(repeated, "event-00000000000000000002", "trace-00000000000000000000000000",
                NOW.plusSeconds(2));

        assertThat(firstResult.getVoucherId()).isEqualTo("voucher-1");
        assertThat(repeatedResult.getVoucherId()).isEqualTo("voucher-1");
        assertThat(repository.saved).hasSize(1);
    }

    @Test
    void repeatedVoucherBusinessKeyRejectsDifferentAmount() {
        InMemoryLedgerRepository repository = new InMemoryLedgerRepository();
        LedgerApplicationService service = new LedgerApplicationService(repository);
        service.post(voucher("voucher-1"), "event-00000000000000000001",
                "trace-00000000000000000000000000", NOW.plusSeconds(1));
        LedgerVoucher changed = LedgerVoucher.prepare("voucher-2", "transaction", "TRANSFER", 0, null,
                600L, 600L, List.of(
                        new LedgerEntry(3L, "voucher-2", "transaction", "payer", LedgerDirection.DEBIT,
                                600L, 1, null, NOW),
                        new LedgerEntry(4L, "voucher-2", "transaction", "payee", LedgerDirection.CREDIT,
                                600L, 2, null, NOW)), NOW);

        assertThatThrownBy(() -> service.post(changed, "event-00000000000000000002",
                "trace-00000000000000000000000000", NOW.plusSeconds(2)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode().code())
                .isEqualTo("IDEMPOTENCY_CONFLICT");
    }

    @Test
    void fullFinalPageDoesNotReturnNextCursor() {
        InMemoryLedgerRepository repository = new InMemoryLedgerRepository();
        repository.queryEntries = List.of(new LedgerEntry(9L, "voucher", "transaction", "ledger",
                LedgerDirection.DEBIT, 500L, 1, "付款", NOW));

        var page = new LedgerApplicationService(repository).listMyEntries("user", null, 1);

        assertThat(page.items()).hasSize(1);
        assertThat(page.nextCursor()).isNull();
        assertThat(repository.requestedLimit).isEqualTo(2);
    }

    @Test
    void nextPageUsesCreatedAtAndEntryIdFromOpaqueCursor() {
        InMemoryLedgerRepository repository = new InMemoryLedgerRepository();
        repository.queryEntries = List.of(
                new LedgerEntry(9L, "voucher", "transaction", "ledger", LedgerDirection.DEBIT,
                        500L, 1, "付款", NOW),
                new LedgerEntry(8L, "voucher", "transaction", "ledger", LedgerDirection.CREDIT,
                        500L, 2, "收款", NOW.minusMillis(1)));
        LedgerApplicationService service = new LedgerApplicationService(repository);

        var firstPage = service.listMyEntries("user", null, 1);
        repository.queryEntries = List.of();
        service.listMyEntries("user", firstPage.nextCursor(), 1);

        assertThat(repository.cursorCreatedAt).isEqualTo(NOW);
        assertThat(repository.cursorEntryId).isEqualTo(9L);
    }

    private LedgerVoucher voucher(String voucherId) {
        return LedgerVoucher.prepare(voucherId, "transaction", "TRANSFER", 0, null, 500L, 500L,
                List.of(
                        new LedgerEntry(1L, voucherId, "transaction", "payer", LedgerDirection.DEBIT,
                                500L, 1, null, NOW),
                        new LedgerEntry(2L, voucherId, "transaction", "payee", LedgerDirection.CREDIT,
                                500L, 2, null, NOW)
                ), NOW);
    }

    static final class InMemoryLedgerRepository implements LedgerRepository {
        final List<LedgerVoucher> saved = new ArrayList<>();
        List<LedgerEntry> queryEntries = List.of();
        Instant cursorCreatedAt;
        long cursorEntryId;
        int requestedLimit;
        @Override public Optional<LedgerVoucher> find(String transactionId, String voucherType, int reversalNo) {
            return saved.stream().filter(v -> v.getTransactionId().equals(transactionId)
                    && v.getVoucherType().equals(voucherType) && v.getReversalNo() == reversalNo).findFirst();
        }
        @Override public Optional<LedgerVoucher> findByIdForUpdate(String voucherId) {
            return saved.stream().filter(voucher -> voucher.getVoucherId().equals(voucherId)).findFirst();
        }
        @Override public void savePrepared(LedgerVoucher voucher) { saved.add(voucher); }
        @Override public LedgerTotals summarizeEntries(String voucherId) {
            LedgerVoucher voucher = findByIdForUpdate(voucherId).orElseThrow();
            long debit = voucher.getEntries().stream().filter(entry -> entry.direction() == LedgerDirection.DEBIT)
                    .mapToLong(LedgerEntry::amountFen).sum();
            long credit = voucher.getEntries().stream().filter(entry -> entry.direction() == LedgerDirection.CREDIT)
                    .mapToLong(LedgerEntry::amountFen).sum();
            return new LedgerTotals(debit, credit);
        }
        @Override public boolean postAndAppendOutbox(LedgerVoucher voucher, String eventId,
                                                     String traceId, Instant now) {
            return true;
        }
        @Override public List<LedgerEntry> findEntriesByUserId(String userId, Instant cursorCreatedAt,
                                                               long cursorEntryId, int limit) {
            this.cursorCreatedAt = cursorCreatedAt;
            this.cursorEntryId = cursorEntryId;
            this.requestedLimit = limit;
            return queryEntries;
        }
    }
}
