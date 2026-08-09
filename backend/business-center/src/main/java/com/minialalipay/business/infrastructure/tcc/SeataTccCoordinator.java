package com.minialalipay.business.infrastructure.tcc;

import com.minialalipay.business.application.port.BusinessStore;
import com.minialalipay.business.application.port.SecurityMaterialPort;
import com.minialalipay.business.application.port.TccCoordinatorPort;
import com.minialalipay.business.domain.transaction.FundTransaction;
import com.minialalipay.business.domain.transaction.TransactionStatus;
import com.minialalipay.business.domain.transaction.TransactionType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;

/**
 * 基于 Seata 的转账 TCC 协调器。
 *
 * <p>转账由 Seata TC 决定 Confirm/Cancel；充值参与者尚未迁移时仍委托原 HTTP 协调器。
 * TC 返回提交成功后还必须读取账户与账本事实，不能仅凭全局事务返回值发布交易成功。</p>
 */
@Primary
@Component
@ConditionalOnProperty(name = "minialalipay.tcc.coordinator", havingValue = "seata", matchIfMissing = true)
public class SeataTccCoordinator implements TccCoordinatorPort {
    private final BusinessStore store;
    private final SecurityMaterialPort secure;
    private final SeataGlobalTransactionExecutor executor;
    private final HttpTccCoordinator httpFallback;
    private final RestClient accountClient;

    public SeataTccCoordinator(BusinessStore store, SecurityMaterialPort secure,
                               SeataGlobalTransactionExecutor executor, HttpTccCoordinator httpFallback,
                               RestClient.Builder builder,
                               @org.springframework.beans.factory.annotation.Value("${minialalipay.internal.account-center-url}")
                               String accountBaseUrl) {
        this.store = store;
        this.secure = secure;
        this.executor = executor;
        this.httpFallback = httpFallback;
        this.accountClient = builder.baseUrl(accountBaseUrl).build();
    }

    /** 启动或恢复资金事务；当前仅 TRANSFER 使用 Seata 注解式 TCC。 */
    @Override
    public void startOrResume(FundTransaction supplied) {
        FundTransaction transaction = store.findTransaction(supplied.getTransactionId())
                .map(BusinessStore.FundTransactionRecord::transaction).orElse(supplied);
        if (transaction.getBusinessType() != TransactionType.TRANSFER) {
            httpFallback.startOrResume(transaction);
            return;
        }
        if (transaction.getStatus().hasDefinitiveOutcome()) {
            return;
        }

        SeataGlobalTransactionExecutor.TransferTccRequest request = request(transaction);
        Instant now = Instant.now();
        store.createTccGlobal(request.businessXid(), transaction.getTransactionId(), now);
        try {
            executor.execute(request);
            store.updateTccGlobal(request.businessXid(), "COMMITTING", "{\"coordinator\":\"SEATA\"}", null, Instant.now());
            finalizeSuccess(transaction, request.businessXid());
        } catch (RuntimeException failure) {
            // Seata 在异常返回前通常已完成全局回滚；只有账户事实确认全部释放后才发布取消终态。
            Facts facts = readFacts(transaction.getTransactionId());
            if (facts != null && facts.cancelConsistent()) {
                long compensatingVersion = transaction.getVersion();
                transaction.startCompensating(Instant.now());
                store.updateTransaction(transaction, compensatingVersion, secure.newId(), Instant.now());
                long version = transaction.getVersion();
                transaction.publishCancelled(true, Instant.now());
                store.finalizeTransaction(transaction, version, request.businessXid(), "CANCELLED",
                        secure.newId(), Instant.now());
            } else {
                // 结果未知时保持恢复态，禁止把客户端异常当作资金失败。
                store.updateTccGlobal(request.businessXid(), "ROLLING_BACK",
                        "{\"coordinator\":\"SEATA\",\"result\":\"UNKNOWN\"}",
                        Instant.now().plusSeconds(10), Instant.now());
            }
        }
    }

    /**
     * 复核人工态交易：转回在途态后按原协调路径重新驱动并重新核验资金事实。
     *
     * <p>人工态不是终态，事实一致时应自动收敛。按工单原因选择恢复路径：
     * 取消事实不一致转补偿态，其余转处理中；事实仍不一致时由原路径再次处置，
     * TRANSFER 走 Seata 分支，其余业务类型委托 HTTP 协调器。</p>
     */
    @Override
    public void recheckManualReview(FundTransaction supplied) {
        FundTransaction transaction = store.findTransaction(supplied.getTransactionId())
                .map(BusinessStore.FundTransactionRecord::transaction).orElse(supplied);
        if (transaction.getStatus() != TransactionStatus.MANUAL_REVIEW) return;
        String reason = store.findActiveManualCase(transaction.getTransactionId())
                .map(BusinessStore.ManualCaseRecord::reasonCode).orElse("");
        long version = transaction.getVersion();
        transaction.resumeFromManualReview(reason.startsWith("CANCEL"), Instant.now());
        // CAS 落库失败说明已有其他恢复线程接管，不得重复驱动资金操作。
        if (!store.updateTransaction(transaction, version, secure.newId(), Instant.now())) return;
        startOrResume(transaction);
    }

    private void finalizeSuccess(FundTransaction transaction, String businessXid) {
        Facts facts = readFacts(transaction.getTransactionId());
        if (facts == null || !facts.successConsistent()) {
            store.updateTccGlobal(businessXid, "COMMITTING", "{\"result\":\"FACT_PENDING\"}",
                    Instant.now().plusSeconds(10), Instant.now());
            return;
        }
        long version = transaction.getVersion();
        transaction.publishSuccess(true, Instant.now());
        store.finalizeTransaction(transaction, version, businessXid, "SUCCESS", secure.newId(), Instant.now());
    }

    private Facts readFacts(String transactionId) {
        try {
            return accountClient.get().uri("/internal/v1/transaction-facts/{id}", transactionId)
                    .retrieve().body(Facts.class);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private SeataGlobalTransactionExecutor.TransferTccRequest request(FundTransaction transaction) {
        String id = transaction.getTransactionId();
        return new SeataGlobalTransactionExecutor.TransferTccRequest(
                "tcc:" + id, id, transaction.getPayerAccountId(), transaction.getPayeeAccountId(),
                transaction.getAmountFen(), secure.stableId(id + ":payer"), secure.stableId(id + ":payee"),
                secure.stableId(id + ":voucher"), positive(id + ":debit"), positive(id + ":credit"),
                secure.stableId(id + ":ledger-event"), transaction.getTraceId());
    }

    private long positive(String key) {
        return Math.max(1L, secure.stablePositiveLong(key));
    }

    private record Facts(boolean successConsistent, boolean cancelConsistent, boolean accountsConfirmed,
                         boolean ledgerConfirmed, boolean freezeConfirmed, boolean ledgerPosted,
                         boolean accountsCancelled, boolean ledgerCancelled, boolean noActiveFreeze) { }
}
