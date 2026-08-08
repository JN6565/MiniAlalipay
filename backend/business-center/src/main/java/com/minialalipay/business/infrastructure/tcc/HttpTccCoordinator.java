package com.minialalipay.business.infrastructure.tcc;

import com.minialalipay.business.application.port.BusinessStore;
import com.minialalipay.business.application.port.TccCoordinatorPort;
import com.minialalipay.business.application.port.SecurityMaterialPort;
import com.minialalipay.business.domain.transaction.FundTransaction;
import com.minialalipay.business.domain.transaction.TransactionStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;

/**
 * 普通转账 TCC 全局协调器。
 *
 * <p>全局事务先落库再调用三个参与者。Try 失败按逆序 Cancel；Confirm 调用失败不做反向猜测，
 * 保持 CONFIRMING 供恢复扫描继续正向完成。</p>
 */
@Component
public class HttpTccCoordinator implements TccCoordinatorPort {
    private final BusinessStore store;
    private final SecurityMaterialPort secure;
    private final RestClient accountClient;

    public HttpTccCoordinator(BusinessStore store, SecurityMaterialPort secure, RestClient.Builder builder,
                              @Value("${minialalipay.internal.account-center-url}") String accountBaseUrl) {
        this.store = store; this.secure = secure; this.accountClient = builder.baseUrl(accountBaseUrl).build();
    }

    /** 启动或接管一笔 PROCESSING/COMPENSATING 转账；分支技术键均由交易 ID 稳定派生。 */
    @Override
    public void startOrResume(FundTransaction supplied) {
        FundTransaction transaction = store.findTransaction(supplied.getTransactionId())
                .map(BusinessStore.FundTransactionRecord::transaction).orElse(supplied);
        if (transaction.getStatus().hasDefinitiveOutcome() || transaction.getStatus() == TransactionStatus.MANUAL_REVIEW) return;
        Artifacts a = artifacts(transaction);
        Instant now = Instant.now();
        store.createTccGlobal(a.xid(), transaction.getTransactionId(), now);
        if (transaction.getBusinessType() == com.minialalipay.business.domain.transaction.TransactionType.RECHARGE) {
            recharge(transaction, a);
            return;
        }
        if (transaction.getBusinessType() == com.minialalipay.business.domain.transaction.TransactionType.CREDIT_PAY) {
            creditPay(transaction, a);
            return;
        }
        if (transaction.getStatus() == TransactionStatus.COMPENSATING) {
            cancelAndFinalize(transaction, a);
            return;
        }
        try {
            balance("payer", "try", transaction, a);
            balance("payee", "try", transaction, a);
            ledger("try", transaction, a);
        } catch (RuntimeException tryFailure) {
            startCompensating(transaction);
            cancelAndFinalize(transaction, a);
            return;
        }
        store.updateTccGlobal(a.xid(), "COMMITTING", "{\"phase\":\"CONFIRM\"}", now, now);
        try {
            balance("payer", "confirm", transaction, a);
            balance("payee", "confirm", transaction, a);
            ledger("confirm", transaction, a);
        } catch (RuntimeException confirmTimeoutOrFailure) {
            // Confirm 可能已在下游成功，不能贸然 Cancel；恢复扫描会使用相同分支键安全重试。
            store.updateTccGlobal(a.xid(), "COMMITTING", "{\"result\":\"UNKNOWN\"}",
                    Instant.now().plusSeconds(10), Instant.now());
            return;
        }
        store.updateTccGlobal(a.xid(), "COMMITTING", "{\"result\":\"CONFIRMED\"}", null, Instant.now());
        finalizeSuccess(transaction, a);
    }

    private void recharge(FundTransaction transaction, Artifacts a) {
        try {
            if (transaction.getStatus() == TransactionStatus.COMPENSATING) {
                rechargeCall("cancel", transaction, a);
                long version = transaction.getVersion();
                transaction.publishCancelled(true, Instant.now());
                store.finalizeTransaction(transaction, version, a.xid(), "CANCELLED", secure.newId(), Instant.now());
                return;
            }
            rechargeCall("try", transaction, a);
            store.updateTccGlobal(a.xid(), "COMMITTING", "{\"phase\":\"CONFIRM\"}", Instant.now(), Instant.now());
            rechargeCall("confirm", transaction, a);
            store.updateTccGlobal(a.xid(), "COMMITTING", "{\"result\":\"CONFIRMED\"}", null, Instant.now());
            long version = transaction.getVersion();
            transaction.publishSuccess(true, Instant.now());
            store.finalizeTransaction(transaction, version, a.xid(), "SUCCESS", secure.newId(), Instant.now());
        } catch (RuntimeException failure) {
            try {
                rechargeCall("cancel", transaction, a);
                transaction.startCompensating(Instant.now());
                long version = transaction.getVersion();
                transaction.publishCancelled(true, Instant.now());
                store.finalizeTransaction(transaction, version, a.xid(), "CANCELLED", secure.newId(), Instant.now());
            } catch (RuntimeException cancelFailure) {
                store.updateTccGlobal(a.xid(), "ROLLING_BACK", "{\"result\":\"UNKNOWN\"}", Instant.now().plusSeconds(10), Instant.now());
            }
        }
    }

    /**
     * 执行动态扫码信用支付。
     *
     * <p>额度、收款余额和信用账本使用同一稳定 XID；Confirm 任一步结果未知时只正向恢复，
     * 不能反向取消已经确认的额度或收款事实。</p>
     */
    private void creditPay(FundTransaction transaction, Artifacts a) {
        CreditAccountReference credit = resolveCreditAccount(transaction.getInitiatorUserId());
        if (transaction.getStatus() == TransactionStatus.COMPENSATING) {
            cancelCreditAndFinalize(transaction, a, credit.creditAccountId());
            return;
        }
        try {
            credit("try", transaction, a, credit.creditAccountId());
            balance("payee", "try", transaction, a);
            creditLedger("try", transaction, a, credit.creditAccountId());
        } catch (RuntimeException tryFailure) {
            startCompensating(transaction);
            cancelCreditAndFinalize(transaction, a, credit.creditAccountId());
            return;
        }
        store.updateTccGlobal(a.xid(), "COMMITTING", "{\"phase\":\"CONFIRM\"}", Instant.now(), Instant.now());
        try {
            credit("confirm", transaction, a, credit.creditAccountId());
            balance("payee", "confirm", transaction, a);
            creditLedger("confirm", transaction, a, credit.creditAccountId());
        } catch (RuntimeException confirmTimeoutOrFailure) {
            store.updateTccGlobal(a.xid(), "COMMITTING", "{\"result\":\"UNKNOWN\"}",
                    Instant.now().plusSeconds(10), Instant.now());
            return;
        }
        store.updateTccGlobal(a.xid(), "COMMITTING", "{\"result\":\"CONFIRMED\"}", null, Instant.now());
        finalizeSuccess(transaction, a);
    }

    private void cancelCreditAndFinalize(FundTransaction transaction, Artifacts a, String creditAccountId) {
        store.updateTccGlobal(a.xid(), "ROLLING_BACK", "{\"phase\":\"CANCEL\"}", Instant.now(), Instant.now());
        try {
            creditLedger("cancel", transaction, a, creditAccountId);
            balance("payee", "cancel", transaction, a);
            credit("cancel", transaction, a, creditAccountId);
        } catch (RuntimeException cancelFailure) {
            store.updateTccGlobal(a.xid(), "ROLLING_BACK", "{\"result\":\"UNKNOWN\"}",
                    Instant.now().plusSeconds(10), Instant.now());
            return;
        }
        Facts facts = facts(transaction.getTransactionId());
        if (!facts.cancelConsistent()) {
            requireManualReview(transaction, a, "CANCEL_FACT_MISMATCH", facts);
            return;
        }
        long version = transaction.getVersion();
        transaction.publishCancelled(true, Instant.now());
        store.finalizeTransaction(transaction, version, a.xid(), "CANCELLED", secure.newId(), Instant.now());
    }

    private void rechargeCall(String action, FundTransaction t, Artifacts a) {
        accountClient.post().uri("/internal/v1/tcc/recharge/{action}", action)
                .body(new RechargeCommand(a.xid(), t.getTransactionId(), t.getPayeeAccountId(), t.getAmountFen(),
                        a.voucherId(), a.debitEntryId(), a.creditEntryId(), a.ledgerEventId(), t.getTraceId()))
                .retrieve().toBodilessEntity();
    }

    private void finalizeSuccess(FundTransaction transaction, Artifacts a) {
        Facts facts = facts(transaction.getTransactionId());
        if (!facts.successConsistent()) {
            requireManualReview(transaction, a, "SUCCESS_FACT_MISMATCH", facts);
            return;
        }
        long version = transaction.getVersion();
        transaction.publishSuccess(true, Instant.now());
        store.finalizeTransaction(transaction, version, a.xid(), "SUCCESS", secure.newId(), Instant.now());
    }

    private void startCompensating(FundTransaction transaction) {
        if (transaction.getStatus() == TransactionStatus.PROCESSING) {
            long version = transaction.getVersion(); transaction.startCompensating(Instant.now());
            store.updateTransaction(transaction, version, secure.newId(), Instant.now());
        }
    }

    private void cancelAndFinalize(FundTransaction transaction, Artifacts a) {
        store.updateTccGlobal(a.xid(), "ROLLING_BACK", "{\"phase\":\"CANCEL\"}", Instant.now(), Instant.now());
        try {
            ledger("cancel", transaction, a);
            balance("payee", "cancel", transaction, a);
            balance("payer", "cancel", transaction, a);
        } catch (RuntimeException cancelFailure) {
            store.updateTccGlobal(a.xid(), "ROLLING_BACK", "{\"result\":\"UNKNOWN\"}",
                    Instant.now().plusSeconds(10), Instant.now());
            return;
        }
        Facts facts = facts(transaction.getTransactionId());
        if (!facts.cancelConsistent()) {
            requireManualReview(transaction, a, "CANCEL_FACT_MISMATCH", facts);
            return;
        }
        long version = transaction.getVersion();
        transaction.publishCancelled(true, Instant.now());
        store.finalizeTransaction(transaction, version, a.xid(), "CANCELLED", secure.newId(), Instant.now());
    }

    private void requireManualReview(FundTransaction transaction, Artifacts a, String reason, Facts facts) {
        Instant now = Instant.now();
        String caseId = secure.newId();
        try {
            accountClient.post().uri("/internal/v1/reconciliation-diffs")
                    .body(new ReconciliationDiffCommand(secure.newId(), transaction.getTransactionId(), reason,
                            expectedJson(reason), factsJson(facts), caseId, transaction.getTraceId(), now))
                    .retrieve().toBodilessEntity();
        } catch (RuntimeException diffWriteFailure) {
            // 差异库暂不可用时仍先冻结到人工态，避免恢复任务继续自动推动不一致资金事实。
            reason = "DIFF_WRITE_FAILED";
        }
        long version = transaction.getVersion();
        transaction.requireManualReview(now);
        store.moveToManualReview(transaction, version, a.xid(), secure.newId(), caseId, reason, now);
    }

    private static String expectedJson(String reason) {
        return reason.startsWith("SUCCESS") ? "{\"successConsistent\":true}" : "{\"cancelConsistent\":true}";
    }

    private static String factsJson(Facts f) {
        return "{\"successConsistent\":" + f.successConsistent()
                + ",\"cancelConsistent\":" + f.cancelConsistent()
                + ",\"accountsConfirmed\":" + f.accountsConfirmed()
                + ",\"ledgerConfirmed\":" + f.ledgerConfirmed()
                + ",\"freezeConfirmed\":" + f.freezeConfirmed()
                + ",\"ledgerPosted\":" + f.ledgerPosted()
                + ",\"accountsCancelled\":" + f.accountsCancelled()
                + ",\"ledgerCancelled\":" + f.ledgerCancelled()
                + ",\"noActiveFreeze\":" + f.noActiveFreeze() + "}";
    }

    private void balance(String role, String action, FundTransaction t, Artifacts a) {
        String accountId = "payer".equals(role) ? t.getPayerAccountId() : t.getPayeeAccountId();
        String freezeId = "payer".equals(role) ? a.payerFreezeId() : a.payeeReservationId();
        accountClient.post().uri("/internal/v1/tcc/balance/{role}/{action}", role, action)
                .body(new BalanceCommand(a.xid(), t.getTransactionId(), accountId, t.getAmountFen(), freezeId))
                .retrieve().toBodilessEntity();
    }
    private void ledger(String action, FundTransaction t, Artifacts a) {
        accountClient.post().uri("/internal/v1/tcc/ledger/{action}", action)
                .body(new LedgerCommand(a.xid(), t.getTransactionId(), t.getPayerAccountId(), t.getPayeeAccountId(),
                        t.getAmountFen(), a.voucherId(), a.debitEntryId(), a.creditEntryId(), a.ledgerEventId(), t.getTraceId()))
                .retrieve().toBodilessEntity();
    }
    private CreditAccountReference resolveCreditAccount(String userId) {
        CreditAccountReference value = accountClient.get()
                .uri("/internal/v1/credit-accounts/by-user/{id}", userId)
                .retrieve().body(CreditAccountReference.class);
        if (value == null || !"ACTIVE".equals(value.status())) {
            throw new IllegalStateException("信用账户不可用于动态扫码支付");
        }
        return value;
    }
    private void credit(String action, FundTransaction t, Artifacts a, String creditAccountId) {
        Object command = "confirm".equals(action)
                ? new CreditConfirmCommand(a.xid(), t.getTransactionId(), creditAccountId, t.getAmountFen(),
                t.getSourceOrderId(), t.getPayeeAccountId())
                : new CreditBranchCommand(a.xid(), t.getTransactionId(), creditAccountId, t.getAmountFen());
        accountClient.post().uri("/internal/v1/tcc/credit-pay/{action}", action)
                .body(command).retrieve().toBodilessEntity();
    }
    private void creditLedger(String action, FundTransaction t, Artifacts a, String creditAccountId) {
        accountClient.post().uri("/internal/v1/tcc/credit-ledger/{action}", action)
                .body(new CreditLedgerCommand(a.xid(), t.getTransactionId(), creditAccountId,
                        t.getPayeeAccountId(), t.getAmountFen(), a.voucherId(), a.debitEntryId(),
                        a.creditEntryId(), a.ledgerEventId(), t.getTraceId()))
                .retrieve().toBodilessEntity();
    }
    private Facts facts(String transactionId) {
        Facts value = accountClient.get().uri("/internal/v1/transaction-facts/{id}", transactionId)
                .retrieve().body(Facts.class);
        if (value == null) throw new IllegalStateException("账户中心未返回终态核验事实");
        return value;
    }
    private Artifacts artifacts(FundTransaction t) {
        String id = t.getTransactionId();
        return new Artifacts("tcc:" + id, secure.stableId(id + ":payer"), secure.stableId(id + ":payee"),
                secure.stableId(id + ":voucher"), positive(id + ":debit"), positive(id + ":credit"),
                secure.stableId(id + ":ledger-event"));
    }
    private long positive(String key) { return Math.max(1L, secure.stablePositiveLong(key)); }

    private record BalanceCommand(String xid, String transactionId, String accountId, long amountFen, String freezeId) { }
    private record LedgerCommand(String xid, String transactionId, String payerAccountId, String payeeAccountId,
            long amountFen, String voucherId, long debitEntryId, long creditEntryId, String eventId, String traceId) { }
    private record RechargeCommand(String xid, String transactionId, String targetAccountId, long amountFen,
            String voucherId, long debitEntryId, long creditEntryId, String eventId, String traceId) { }
    private record Facts(boolean successConsistent, boolean cancelConsistent, boolean accountsConfirmed,
            boolean ledgerConfirmed, boolean freezeConfirmed, boolean ledgerPosted, boolean accountsCancelled,
            boolean ledgerCancelled, boolean noActiveFreeze) { }
    private record ReconciliationDiffCommand(String diffId, String transactionId, String diffType,
            String expectedJson, String actualJson, String manualCaseId, String traceId, Instant detectedAt) { }
    private record CreditAccountReference(String creditAccountId, String userId, String status, long version) { }
    private record CreditBranchCommand(String xid, String transactionId, String creditAccountId, long amountFen) { }
    private record CreditConfirmCommand(String xid, String transactionId, String creditAccountId, long amountFen,
            String qrOrderId, String merchantAccountId) { }
    private record CreditLedgerCommand(String xid, String transactionId, String creditAccountId,
            String payeeAccountId, long amountFen, String voucherId, long debitEntryId,
            long creditEntryId, String eventId, String traceId) { }
    private record Artifacts(String xid, String payerFreezeId, String payeeReservationId, String voucherId,
            long debitEntryId, long creditEntryId, String ledgerEventId) { }
}
