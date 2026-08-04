package com.minialalipay.business.application.recovery;

import com.minialalipay.business.application.port.BusinessStore;
import com.minialalipay.business.application.port.TccCoordinatorPort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

/** 扫描超时在途交易，使用原交易和稳定分支键接管 TCC，不创建第二笔资金交易。 */
@Service
public class TransactionRecoveryScanner {
    private final BusinessStore store;
    private final TccCoordinatorPort coordinator;
    public TransactionRecoveryScanner(BusinessStore store, TccCoordinatorPort coordinator) {
        this.store = store; this.coordinator = coordinator;
    }

    /** 每十秒扫描最多 100 笔超过 60 秒未更新的交易；重复执行无副作用。 */
    @Scheduled(fixedDelayString = "${minialalipay.recovery.fixed-delay-ms:10000}",
            initialDelayString = "${minialalipay.recovery.initial-delay-ms:10000}")
    public void recoverTimedOutTransactions() {
        for (var record : store.findRecoverable(Instant.now().minusSeconds(60), 100)) {
            coordinator.startOrResume(record.transaction());
        }
    }
}
