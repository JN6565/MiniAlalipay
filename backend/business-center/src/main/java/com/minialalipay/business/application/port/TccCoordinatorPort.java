package com.minialalipay.business.application.port;

import com.minialalipay.business.domain.transaction.FundTransaction;

/** 普通转账 TCC 全局协调端口。 */
public interface TccCoordinatorPort {
    /** 受理事务提交后启动或接管该交易的全局事务。 */
    void startOrResume(FundTransaction transaction);
    /**
     * 复核一笔人工态交易：转回在途态后用原稳定分支键重新驱动 TCC 并重新核验资金事实。
     *
     * <p>事实一致时直接发布终态并自动处置对应人工工单；事实仍不一致时保持人工态，
     * 不影响真实异常场景的人工处置。非人工态交易调用无副作用。</p>
     */
    void recheckManualReview(FundTransaction transaction);
}
