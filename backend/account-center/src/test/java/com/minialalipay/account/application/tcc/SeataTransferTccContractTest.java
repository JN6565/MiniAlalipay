package com.minialalipay.account.application.tcc;

import io.seata.rm.tcc.api.TwoPhaseBusinessAction;
import io.seata.rm.tcc.api.BusinessActionContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 验证转账账户参与者已注册为 Seata TCC 分支。 */
class SeataTransferTccContractTest {
    @Test
    void 付款账户参与者必须声明TwoPhaseBusinessAction() throws Exception {
        Method method = SeataTransferTccParticipant.class
                .getMethod("tryPayer", BusinessActionContext.class, String.class, String.class, String.class,
                        long.class, String.class);
        assertNotNull(method.getAnnotation(TwoPhaseBusinessAction.class));
    }

    @Test
    void 账本参与者必须声明TwoPhaseBusinessAction() throws Exception {
        Method method = SeataLedgerTccParticipant.class.getMethod("tryLedger", BusinessActionContext.class,
                String.class, String.class, String.class, String.class, long.class, String.class,
                long.class, long.class, String.class, String.class);
        assertNotNull(method.getAnnotation(TwoPhaseBusinessAction.class));
    }
}
