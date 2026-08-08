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
}
