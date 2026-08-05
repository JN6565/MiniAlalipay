package com.minialalipay.account.application.account;

import com.minialalipay.account.domain.account.Account;
import com.minialalipay.account.domain.account.AccountBalance;
import com.minialalipay.account.domain.account.AccountRepository;
import com.minialalipay.account.domain.credit.CreditAccount;
import com.minialalipay.account.domain.credit.CreditAccountRepository;
import com.minialalipay.account.domain.credit.CreditAccountStatus;
import com.minialalipay.account.domain.credit.CreditReceivable;
import com.minialalipay.account.domain.credit.CreditReceivableRepository;
import com.minialalipay.account.domain.ledger.LedgerAccount;
import com.minialalipay.account.domain.ledger.LedgerAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import com.minialalipay.common.error.BusinessException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-04T08:00:00Z");

    @Test
    void repeatedRegistrationReturnsExistingZeroBalanceAccount() {
        InMemoryAccountRepository repository = new InMemoryAccountRepository();
        InMemoryLedgerAccountRepository ledgerAccounts = new InMemoryLedgerAccountRepository();
        AccountApplicationService service = service(repository, ledgerAccounts);

        var first = service.openAccount("account-1", "user-1", "registration-1", NOW);
        var repeated = service.openAccount("account-2", "user-1", "registration-1", NOW.plusSeconds(1));

        assertThat(repeated.accountId()).isEqualTo(first.accountId()).isEqualTo("account-1");
        assertThat(repeated.availableFen()).isZero();
        assertThat(repository.createCount).isEqualTo(1);
        assertThat(ledgerAccounts.createCount).isEqualTo(1);
    }

    @Test
    void getsRealBalanceByAuthenticatedUser() {
        InMemoryAccountRepository repository = new InMemoryAccountRepository();
        repository.create(Account.open("account-1", "user-1", "registration-1", NOW),
                new AccountBalance("account-1", 800L, 200L, 4L, NOW));
        AccountApplicationService service = service(repository, new InMemoryLedgerAccountRepository());

        var result = service.getMyAccount("user-1");

        assertThat(result.availableFen()).isEqualTo(800L);
        assertThat(result.frozenFen()).isEqualTo(200L);
        assertThat(result.totalFen()).isEqualTo(1_000L);
        assertThat(result.version()).isEqualTo(4L);
    }

    @Test
    void concurrentDuplicateRegistrationReadsCommittedAccountAfterUniqueConflict() {
        RacingAccountRepository repository = new RacingAccountRepository();
        AccountApplicationService service = service(repository, new InMemoryLedgerAccountRepository());

        var result = service.openAccount("account-loser", "user-1", "registration-1", NOW);

        assertThat(result.accountId()).isEqualTo("account-winner");
        assertThat(result.totalFen()).isZero();
    }

    @Test
    void repeatedRegistrationRejectsDifferentUserBinding() {
        InMemoryAccountRepository repository = new InMemoryAccountRepository();
        repository.create(Account.open("account-1", "user-1", "registration-1", NOW),
                AccountBalance.zero("account-1", NOW));
        AccountApplicationService service = service(repository, new InMemoryLedgerAccountRepository());

        assertThatThrownBy(() -> service.openAccount("account-2", "user-2", "registration-1", NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode().code())
                .isEqualTo("IDEMPOTENCY_CONFLICT");
    }

    private static AccountApplicationService service(AccountRepository accounts,
                                                     LedgerAccountRepository ledgerAccounts) {
        return new AccountApplicationService(accounts, ledgerAccounts,
                new InMemoryCreditAccountRepository(), new InMemoryCreditReceivableRepository());
    }

    static class InMemoryAccountRepository implements AccountRepository {
        private final Map<String, Account> accounts = new HashMap<>();
        private final Map<String, AccountBalance> balances = new HashMap<>();
        int createCount;

        @Override public Optional<Account> findByRegistrationId(String registrationId) {
            return accounts.values().stream().filter(a -> a.getRegistrationId().equals(registrationId)).findFirst();
        }
        @Override public Optional<Account> findByUserId(String userId) {
            return accounts.values().stream().filter(a -> a.getUserId().equals(userId)).findFirst();
        }
        @Override public Optional<Account> findById(String accountId) { return Optional.ofNullable(accounts.get(accountId)); }
        @Override public Optional<AccountBalance> findBalance(String accountId) {
            return Optional.ofNullable(balances.get(accountId));
        }
        @Override public void create(Account account, AccountBalance balance) {
            accounts.put(account.getAccountId(), account);
            balances.put(balance.getAccountId(), balance);
            createCount++;
        }
        @Override public boolean updateBalance(AccountBalance balance, long expectedVersion) {
            AccountBalance current = balances.get(balance.getAccountId());
            if (current == null || current.getVersion() != expectedVersion) return false;
            balance.updateVersion(expectedVersion + 1);
            balances.put(balance.getAccountId(), balance);
            return true;
        }
        @Override public boolean updateBalanceForActiveAccount(AccountBalance balance, long expectedVersion) {
            Account account = accounts.get(balance.getAccountId());
            return account != null && account.getStatus().name().equals("ACTIVE")
                    && updateBalance(balance, expectedVersion);
        }
    }

    static final class InMemoryLedgerAccountRepository implements LedgerAccountRepository {
        private final Map<String, LedgerAccount> accounts = new HashMap<>();
        int createCount;
        @Override public Optional<LedgerAccount> findUserBalanceByUserId(String userId) {
            return accounts.values().stream().filter(a -> a.getOwnerId().equals(userId)).findFirst();
        }
        @Override public void create(LedgerAccount account) {
            accounts.put(account.getLedgerAccountId(), account);
            createCount++;
        }
    }

    static final class InMemoryCreditAccountRepository implements CreditAccountRepository {
        private final Map<String, CreditAccount> accounts = new HashMap<>();

        @Override public Optional<CreditAccount> findByUserId(String userId) {
            return accounts.values().stream().filter(account -> account.getUserId().equals(userId)).findFirst();
        }
        @Override public Optional<CreditAccount> findById(String creditAccountId) {
            return Optional.ofNullable(accounts.get(creditAccountId));
        }
        @Override public List<CreditAccount> findByStatus(CreditAccountStatus status) {
            return accounts.values().stream().filter(account -> account.getStatus() == status).toList();
        }
        @Override public void save(CreditAccount account) {
            accounts.put(account.getCreditAccountId(), account);
        }
    }

    static final class InMemoryCreditReceivableRepository implements CreditReceivableRepository {
        private final Map<String, CreditReceivable> receivables = new HashMap<>();

        @Override public Optional<CreditReceivable> findByCreditAccountId(String creditAccountId) {
            return Optional.ofNullable(receivables.get(creditAccountId));
        }
        @Override public void save(CreditReceivable receivable) {
            receivables.put(receivable.getCreditAccountId(), receivable);
        }
    }

    static final class RacingAccountRepository extends InMemoryAccountRepository {
        private boolean firstLookup = true;

        @Override public Optional<Account> findByRegistrationId(String registrationId) {
            if (firstLookup) {
                firstLookup = false;
                return Optional.empty();
            }
            return super.findByRegistrationId(registrationId);
        }

        @Override public void create(Account account, AccountBalance balance) {
            Account winner = Account.open("account-winner", account.getUserId(),
                    account.getRegistrationId(), account.getCreatedAt());
            super.create(winner, AccountBalance.zero(winner.getAccountId(), account.getCreatedAt()));
            throw new DuplicateKeyException("模拟并发注册唯一键冲突");
        }
    }
}
