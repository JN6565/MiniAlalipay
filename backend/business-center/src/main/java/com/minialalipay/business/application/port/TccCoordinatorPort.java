package com.minialalipay.business.application.port;

import com.minialalipay.business.domain.transaction.FundTransaction;

/** 普通转账 TCC 全局协调端口。 */
public interface TccCoordinatorPort {
    /** 受理事务提交后启动或接管该交易的全局事务。 */
    void startOrResume(FundTransaction transaction);
}
