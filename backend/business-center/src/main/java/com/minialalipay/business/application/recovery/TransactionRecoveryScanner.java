package com.minialalipay.business.application.recovery;

import com.minialalipay.business.application.port.BusinessStore;
import com.minialalipay.business.application.port.TccCoordinatorPort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 扫描超时在途交易，使用原交易和稳定分支键接管 TCC，不创建第二笔资金交易。
 *
 * <p>恢复扫描器带指数退避：同一笔交易恢复失败后，下次重试间隔随 retry_count 指数增长（10s × 2^count，上限 5 分钟），
 * 避免对无法恢复的交易无限重试导致 CPU 和数据库连接耗尽。</p>
 *
 * <p>除在途交易外，扫描器还定期复核人工态交易：人工态不是终态，资金事实重新核验一致后
 * 应自动收敛为终态并处置工单，避免误判交易永远停留在人工审核中。</p>
 */
@Service
public class TransactionRecoveryScanner {
    private static final int MAX_RETRIES = 10;
    private final BusinessStore store;
    private final TccCoordinatorPort coordinator;
    public TransactionRecoveryScanner(BusinessStore store, TccCoordinatorPort coordinator) {
        this.store = store; this.coordinator = coordinator;
    }

    /**
     * 每十秒扫描最多 100 笔超过 60 秒未更新且退避窗口已过的交易；
     * 同一交易最多重试 {@value MAX_RETRIES} 次，超过后跳过（需人工介入）。
     */
    @Scheduled(fixedDelayString = "${minialalipay.recovery.fixed-delay-ms:10000}",
            initialDelayString = "${minialalipay.recovery.initial-delay-ms:10000}")
    public void recoverTimedOutTransactions() {
        for (var record : store.findRecoverable(Instant.now().minusSeconds(60), 100)) {
            int retries = store.getTccRetryCount(record.transaction().getTransactionId());
            if (retries >= MAX_RETRIES) {
                continue; // 超过重试上限，跳过，需人工介入
            }
            coordinator.startOrResume(record.transaction());
        }
    }

    /**
     * 定期复核人工态交易：转回在途态后用原稳定分支键重新驱动 TCC 并重新核验资金事实。
     *
     * <p>事实一致时协调器直接发布终态并自动处置工单；事实仍不一致时保持人工态。
     * 重试上限与在途恢复一致，避免真实异常交易被无限重驱。</p>
     */
    @Scheduled(fixedDelayString = "${minialalipay.recovery.manual-review-fixed-delay-ms:30000}",
            initialDelayString = "${minialalipay.recovery.manual-review-initial-delay-ms:30000}")
    public void recheckManualReviewTransactions() {
        for (var record : store.findManualReviewRecheckable(Instant.now().minusSeconds(60), 100)) {
            int retries = store.getTccRetryCount(record.transaction().getTransactionId());
            if (retries >= MAX_RETRIES) {
                continue; // 超过重试上限，保持人工态，需人工介入
            }
            coordinator.recheckManualReview(record.transaction());
        }
    }
}
