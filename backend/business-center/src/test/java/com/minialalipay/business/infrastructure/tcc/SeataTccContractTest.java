package com.minialalipay.business.infrastructure.tcc;

import io.seata.spring.annotation.GlobalTransactional;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 验证转账 Seata TCC 入口和参与者注解未被误删。 */
class SeataTccContractTest {
    @Test
    void 全局入口必须声明GlobalTransactional() throws Exception {
        Method method = SeataGlobalTransactionExecutor.class
                .getMethod("execute", SeataGlobalTransactionExecutor.TransferTccRequest.class);
        assertNotNull(method.getAnnotation(GlobalTransactional.class));
    }

}
