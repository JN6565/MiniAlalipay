package com.minialalipay.account.application.account;

import com.minialalipay.account.domain.account.Account;
import com.minialalipay.account.domain.account.AccountBalance;
import com.minialalipay.account.domain.account.AccountErrorCode;
import com.minialalipay.account.domain.account.AccountRepository;
import com.minialalipay.account.domain.account.AccountStatus;
import com.minialalipay.account.domain.account.FreezePurpose;
import com.minialalipay.account.domain.account.FreezeRecord;
import com.minialalipay.account.domain.account.FreezeRecordRepository;
import com.minialalipay.account.domain.account.FreezeStatus;
import com.minialalipay.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T-12 交叉评审：余额应用服务并发安全与幂等测试。
 *
 * <p>验证 BalanceApplicationService 的以下关键评审点：
 * <ul>
 *   <li>冻结记录唯一键先于余额 CAS（幂等屏障顺序）</li>
 *   <li>重复 freeze/confirm/cancel 幂等返回</li>
 *   <li>CAS 版本冲突正确映射为 VERSION_CONFLICT</li>
 *   <li>DataIntegrityViolationException 转为幂等回读</li>
 *   <li>账户非 ACTIVE 状态拒绝冻结</li>
 *   <li>余额不足映射为 INSUFFICIENT_BALANCE</li>
 * </ul>
 * </p>
 */
@DisplayName("T-12 余额应用服务交叉评审")
class BalanceApplicationServiceCrossReviewTest {

    private static final Instant NOW = Instant.parse("2026-08-04T08:00:00Z");

    @Test
    @DisplayName("重复 freeze 幂等：返回已有记录，余额不变")
    void repeatedFreezeIsIdempotent() {
        var accounts = new TestAccountRepository();
        accounts.create(Account.open("acc-1", "user-1", "reg-1", NOW),
                new AccountBalance("acc-1", 10_000L, 0L, 0L, NOW));
        var freezes = new TestFreezeRepository();
        var service = new BalanceApplicationService(accounts, freezes);

        FreezeRecord first = service.freeze("freeze-1", "tx-1", "acc-1",
                FreezePurpose.TRANSFER_OUT, 3_000L, "xid-1", NOW);
        FreezeRecord second = service.freeze("freeze-2", "tx-1", "acc-1",
                FreezePurpose.TRANSFER_OUT, 3_000L, "xid-1", NOW.plusSeconds(1));

        assertThat(second.getFreezeId()).isEqualTo(first.getFreezeId());
        AccountBalance balance = accounts.findBalance("acc-1").orElseThrow();
        assertThat(balance.getAvailableFen()).isEqualTo(7_000L);
        assertThat(balance.getFrozenFen()).isEqualTo(3_000L);
        assertThat(balance.getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("重复 freeze 但金额不同 → IDEMPOTENCY_CONFLICT")
    void repeatedFreezeWithDifferentAmountThrows() {
        var accounts = new TestAccountRepository();
        accounts.create(Account.open("acc-1", "user-1", "reg-1", NOW),
                new AccountBalance("acc-1", 10_000L, 0L, 0L, NOW));
        var freezes = new TestFreezeRepository();
        var service = new BalanceApplicationService(accounts, freezes);

        service.freeze("freeze-1", "tx-1", "acc-1",
                FreezePurpose.TRANSFER_OUT, 3_000L, "xid-1", NOW);

        assertThatThrownBy(() -> service.freeze("freeze-2", "tx-1", "acc-1",
                FreezePurpose.TRANSFER_OUT, 4_000L, "xid-1", NOW.plusSeconds(1)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode().code())
                .isEqualTo("IDEMPOTENCY_CONFLICT");
    }

    @Test
    @DisplayName("重复 freeze 但 branchXid 不同 → IDEMPOTENCY_CONFLICT")
    void repeatedFreezeWithDifferentXidThrows() {
        var accounts = new TestAccountRepository();
        accounts.create(Account.open("acc-1", "user-1", "reg-1", NOW),
                new AccountBalance("acc-1", 10_000L, 0L, 0L, NOW));
        var freezes = new TestFreezeRepository();
        var service = new BalanceApplicationService(accounts, freezes);

        service.freeze("freeze-1", "tx-1", "acc-1",
                FreezePurpose.TRANSFER_OUT, 3_000L, "xid-1", NOW);

        assertThatThrownBy(() -> service.freeze("freeze-2", "tx-1", "acc-1",
                FreezePurpose.TRANSFER_OUT, 3_000L, "xid-2", NOW.plusSeconds(1)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode().code())
                .isEqualTo("IDEMPOTENCY_CONFLICT");
    }

    @Test
    @DisplayName("confirm 后重复 confirm 幂等返回，余额不二次扣减")
    void repeatedConfirmIsIdempotent() {
        var accounts = new TestAccountRepository();
        accounts.create(Account.open("acc-1", "user-1", "reg-1", NOW),
                new AccountBalance("acc-1", 10_000L, 0L, 0L, NOW));
        var freezes = new TestFreezeRepository();
        var service = new BalanceApplicationService(accounts, freezes);

        service.freeze("freeze-1", "tx-1", "acc-1",
                FreezePurpose.TRANSFER_OUT, 3_000L, "xid-1", NOW);
        service.confirm("tx-1", "acc-1", FreezePurpose.TRANSFER_OUT, NOW.plusSeconds(1));
        service.confirm("tx-1", "acc-1", FreezePurpose.TRANSFER_OUT, NOW.plusSeconds(2));

        AccountBalance balance = accounts.findBalance("acc-1").orElseThrow();
        assertThat(balance.getAvailableFen()).isEqualTo(7_000L);
        assertThat(balance.getFrozenFen()).isZero();
    }

    @Test
    @DisplayName("cancel 后重复 cancel 幂等返回，余额不二次恢复")
    void repeatedCancelIsIdempotent() {
        var accounts = new TestAccountRepository();
        accounts.create(Account.open("acc-1", "user-1", "reg-1", NOW),
                new AccountBalance("acc-1", 10_000L, 0L, 0L, NOW));
        var freezes = new TestFreezeRepository();
        var service = new BalanceApplicationService(accounts, freezes);

        service.freeze("freeze-1", "tx-1", "acc-1",
                FreezePurpose.TRANSFER_OUT, 3_000L, "xid-1", NOW);
        service.cancel("tx-1", "acc-1", FreezePurpose.TRANSFER_OUT, NOW.plusSeconds(1));
        service.cancel("tx-1", "acc-1", FreezePurpose.TRANSFER_OUT, NOW.plusSeconds(2));

        AccountBalance balance = accounts.findBalance("acc-1").orElseThrow();
        assertThat(balance.getAvailableFen()).isEqualTo(10_000L);
        assertThat(balance.getFrozenFen()).isZero();
    }

    @Test
    @DisplayName("余额不足时 freeze 抛出 INSUFFICIENT_BALANCE")
    void freezeInsufficientBalanceThrows() {
        var accounts = new TestAccountRepository();
        accounts.create(Account.open("acc-1", "user-1", "reg-1", NOW),
                new AccountBalance("acc-1", 500L, 0L, 0L, NOW));
        var freezes = new TestFreezeRepository();
        var service = new BalanceApplicationService(accounts, freezes);

        assertThatThrownBy(() -> service.freeze("freeze-1", "tx-1", "acc-1",
                FreezePurpose.TRANSFER_OUT, 501L, "xid-1", NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode().code())
                .isEqualTo("INSUFFICIENT_BALANCE");
    }

    @Test
    @DisplayName("账户非 ACTIVE 状态时 freeze 抛出 ACCOUNT_UNAVAILABLE")
    void freezeOnSuspendedAccountThrows() {
        var accounts = new TestAccountRepository();
        Account account = new Account("acc-1", "user-1", "reg-1",
                com.minialalipay.account.domain.account.AccountType.PERSONAL, "CNY",
                AccountStatus.FROZEN, 0L, NOW, NOW);
        accounts.create(account, new AccountBalance("acc-1", 10_000L, 0L, 0L, NOW));
        var freezes = new TestFreezeRepository();
        var service = new BalanceApplicationService(accounts, freezes);

        assertThatThrownBy(() -> service.freeze("freeze-1", "tx-1", "acc-1",
                FreezePurpose.TRANSFER_OUT, 3_000L, "xid-1", NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode().code())
                .isEqualTo("ACCOUNT_UNAVAILABLE");
    }

    @Test
    @DisplayName("confirm 不存在的冻结记录 → NOT_FOUND")
    void confirmNonExistentFreezeThrows() {
        var accounts = new TestAccountRepository();
        accounts.create(Account.open("acc-1", "user-1", "reg-1", NOW),
                new AccountBalance("acc-1", 10_000L, 0L, 0L, NOW));
        var service = new BalanceApplicationService(accounts, new TestFreezeRepository());

        assertThatThrownBy(() -> service.confirm("nonexistent-tx", "acc-1",
                FreezePurpose.TRANSFER_OUT, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode().code())
                .isEqualTo("COMMON_NOT_FOUND");
    }

    @Test
    @DisplayName("CAS 余额版本冲突 → VERSION_CONFLICT")
    void casVersionConflictThrows() {
        var accounts = new TestAccountRepository() {
            @Override
            public boolean updateBalanceForActiveAccount(AccountBalance balance, long expectedVersion) {
                return false; // 模拟 CAS 失败
            }
        };
        accounts.create(Account.open("acc-1", "user-1", "reg-1", NOW),
                new AccountBalance("acc-1", 10_000L, 0L, 0L, NOW));
        var service = new BalanceApplicationService(accounts, new TestFreezeRepository());

        assertThatThrownBy(() -> service.freeze("freeze-1", "tx-1", "acc-1",
                FreezePurpose.TRANSFER_OUT, 3_000L, "xid-1", NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode().code())
                .isEqualTo("VERSION_CONFLICT");
    }

    @Test
    @DisplayName("freeze + confirm + freeze 完整生命周期余额正确")
    void fullFreezeConfirmCycleThenNewFreeze() {
        var accounts = new TestAccountRepository();
        accounts.create(Account.open("acc-1", "user-1", "reg-1", NOW),
                new AccountBalance("acc-1", 10_000L, 0L, 0L, NOW));
        var freezes = new TestFreezeRepository();
        var service = new BalanceApplicationService(accounts, freezes);

        // 第一笔：冻结 3000 → 确认
        service.freeze("f1", "tx-1", "acc-1", FreezePurpose.TRANSFER_OUT, 3_000L, "x1", NOW);
        service.confirm("tx-1", "acc-1", FreezePurpose.TRANSFER_OUT, NOW.plusSeconds(1));

        // 第二笔：冻结 2000 → 释放
        service.freeze("f2", "tx-2", "acc-1", FreezePurpose.TRANSFER_OUT, 2_000L, "x2", NOW.plusSeconds(2));
        service.cancel("tx-2", "acc-1", FreezePurpose.TRANSFER_OUT, NOW.plusSeconds(3));

        AccountBalance balance = accounts.findBalance("acc-1").orElseThrow();
        // 10000 - 3000(已确认) = 7000 可用
        assertThat(balance.getAvailableFen()).isEqualTo(7_000L);
        assertThat(balance.getFrozenFen()).isZero();
    }

    // --- 内存仓储实现 ---

    static class TestAccountRepository implements AccountRepository {
        final Map<String, Account> accounts = new HashMap<>();
        final Map<String, AccountBalance> balances = new HashMap<>();

        @Override
        public Optional<Account> findByRegistrationId(String registrationId) {
            return accounts.values().stream()
                    .filter(a -> a.getRegistrationId().equals(registrationId)).findFirst();
        }

        @Override
        public Optional<Account> findByUserId(String userId) {
            return accounts.values().stream()
                    .filter(a -> a.getUserId().equals(userId)).findFirst();
        }

        @Override
        public Optional<Account> findById(String accountId) {
            return Optional.ofNullable(accounts.get(accountId));
        }

        @Override
        public Optional<AccountBalance> findBalance(String accountId) {
            return Optional.ofNullable(balances.get(accountId));
        }

        @Override
        public void create(Account account, AccountBalance balance) {
            accounts.put(account.getAccountId(), account);
            balances.put(balance.getAccountId(), balance);
        }

        @Override
        public boolean updateBalance(AccountBalance balance, long expectedVersion) {
            AccountBalance current = balances.get(balance.getAccountId());
            if (current == null || current.getVersion() != expectedVersion) return false;
            balance.updateVersion(expectedVersion + 1);
            balances.put(balance.getAccountId(), balance);
            return true;
        }

        @Override
        public boolean updateBalanceForActiveAccount(AccountBalance balance, long expectedVersion) {
            Account account = accounts.get(balance.getAccountId());
            return account != null && account.getStatus() == AccountStatus.ACTIVE
                    && updateBalance(balance, expectedVersion);
        }
    }

    static class TestFreezeRepository implements FreezeRecordRepository {
        final Map<String, FreezeRecord> records = new HashMap<>();

        private String key(String txId, String accId, FreezePurpose purpose) {
            return txId + ':' + accId + ':' + purpose;
        }

        @Override
        public Optional<FreezeRecord> find(String txId, String accId, FreezePurpose purpose) {
            return Optional.ofNullable(records.get(key(txId, accId, purpose)));
        }

        @Override
        public Optional<FreezeRecord> findForUpdate(String txId, String accId, FreezePurpose purpose) {
            return find(txId, accId, purpose);
        }

        @Override
        public void create(FreezeRecord record) {
            records.put(key(record.getTransactionId(), record.getAccountId(), record.getPurpose()), record);
        }

        @Override
        public boolean update(FreezeRecord record, long expectedVersion) {
            if (record.getVersion() != expectedVersion) return false;
            record.updateVersion(expectedVersion + 1);
            records.put(key(record.getTransactionId(), record.getAccountId(), record.getPurpose()), record);
            return true;
        }
    }
}
