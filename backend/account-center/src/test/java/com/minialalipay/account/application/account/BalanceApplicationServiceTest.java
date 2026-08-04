package com.minialalipay.account.application.account;

import com.minialalipay.account.domain.account.Account;
import com.minialalipay.account.domain.account.AccountBalance;
import com.minialalipay.account.domain.account.FreezePurpose;
import com.minialalipay.account.domain.account.FreezeRecord;
import com.minialalipay.account.domain.account.FreezeRecordRepository;
import com.minialalipay.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BalanceApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-04T08:00:00Z");

    @Test
    void repeatedFreezeConfirmAndCancelDoNotChangeBalanceTwice() {
        var accounts = new AccountApplicationServiceTest.InMemoryAccountRepository();
        accounts.create(Account.open("account", "user", "registration", NOW),
                new AccountBalance("account", 1_000L, 0L, 0L, NOW));
        var freezes = new InMemoryFreezeRepository();
        BalanceApplicationService service = new BalanceApplicationService(accounts, freezes);

        service.freeze("freeze", "transaction", "account", FreezePurpose.TRANSFER_OUT,
                300L, "xid", NOW);
        service.freeze("another-id", "transaction", "account", FreezePurpose.TRANSFER_OUT,
                300L, "xid", NOW.plusSeconds(1));
        service.confirm("transaction", "account", FreezePurpose.TRANSFER_OUT, NOW.plusSeconds(2));
        service.confirm("transaction", "account", FreezePurpose.TRANSFER_OUT, NOW.plusSeconds(3));

        AccountBalance balance = accounts.findBalance("account").orElseThrow();
        assertThat(balance.getAvailableFen()).isEqualTo(700L);
        assertThat(balance.getFrozenFen()).isZero();
        assertThat(balance.getVersion()).isEqualTo(2L);
    }

    @Test
    void mapsBalanceCasFailureToVersionConflict() {
        var accounts = new AccountApplicationServiceTest.InMemoryAccountRepository() {
            @Override public boolean updateBalanceForActiveAccount(AccountBalance balance, long expectedVersion) {
                return false;
            }
        };
        accounts.create(Account.open("account", "user", "registration", NOW),
                new AccountBalance("account", 1_000L, 0L, 0L, NOW));
        BalanceApplicationService service = new BalanceApplicationService(accounts, new InMemoryFreezeRepository());

        assertThatThrownBy(() -> service.freeze("freeze", "transaction", "account",
                FreezePurpose.TRANSFER_OUT, 300L, "xid", NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode().code())
                .isEqualTo("VERSION_CONFLICT");
    }

    static final class InMemoryFreezeRepository implements FreezeRecordRepository {
        private final Map<String, FreezeRecord> records = new HashMap<>();
        private String key(String transactionId, String accountId, FreezePurpose purpose) {
            return transactionId + ':' + accountId + ':' + purpose;
        }
        @Override public Optional<FreezeRecord> find(String transactionId, String accountId, FreezePurpose purpose) {
            return Optional.ofNullable(records.get(key(transactionId, accountId, purpose)));
        }
        @Override public Optional<FreezeRecord> findForUpdate(String transactionId, String accountId,
                                                              FreezePurpose purpose) {
            return find(transactionId, accountId, purpose);
        }
        @Override public void create(FreezeRecord record) {
            records.put(key(record.getTransactionId(), record.getAccountId(), record.getPurpose()), record);
        }
        @Override public boolean update(FreezeRecord record, long expectedVersion) {
            if (record.getVersion() != expectedVersion) return false;
            record.updateVersion(expectedVersion + 1);
            return true;
        }
    }
}
