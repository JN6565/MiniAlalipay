package com.minialalipay.business.application.port;

import com.minialalipay.business.domain.risk.RiskDecision;

import java.util.Optional;

/**
 * 前置风控决策的 business_db 仓储端口。
 *
 * <p>决策发生在资金交易创建之前，只能作为受理前拦截；实现不得通过本端口修改余额、冻结或账本事实。</p>
 */
public interface RiskDecisionStore {
    /** 按主体读取最新一条风控决策。 */
    Optional<RiskDecision> findLatestBySubject(String subjectType, String subjectId);

    /** 保存一条不可变的风控决策。 */
    boolean save(RiskDecision decision);
}
