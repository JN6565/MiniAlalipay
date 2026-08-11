package com.minialalipay.business.infrastructure.tcc;

import io.seata.core.context.RootContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Seata 全局事务执行器的分支注册路由契约测试。
 *
 * <p>银行卡出资转账/扫码支付必须注册“银行卡扣减 + 收款入账”组合分支（复用充值组合端点，
 * 资金方向一致）；余额出资转账维持原三分支组合端点。路由接错端点会造成收款方不入账
 * 或资金方向相反，属于资金安全问题，用契约测试固化。</p>
 */
class SeataGlobalTransactionExecutorTest {

    @AfterEach
    void unbindXid() {
        RootContext.unbind();
    }

    @Test
    void 银行卡出资转账注册卡扣减与收款入账组合分支() {
        RootContext.bind("mock-xid");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SeataGlobalTransactionExecutor executor = new SeataGlobalTransactionExecutor(builder, "http://account");

        // 收款账户、银行卡与收款预占键必须透传给充值组合端点，事实核验才能按充值规则集判定终态
        server.expect(once(), requestTo("http://account/internal/v1/seata-tcc/bank-card-recharge/try"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(RootContext.KEY_XID, "mock-xid"))
                .andExpect(jsonPath("$.userId").value("user-1"))
                .andExpect(jsonPath("$.accountId").value("payee-account"))
                .andExpect(jsonPath("$.cardId").value("card-1"))
                .andExpect(jsonPath("$.reservationId").value("payee-reservation"))
                .andExpect(jsonPath("$.amountFen").value(8800))
                .andRespond(withSuccess());

        executor.execute(new SeataGlobalTransactionExecutor.TransferTccRequest(
                "tcc:tx-1", "tx-1", null, "payee-account", 8800L,
                "payer-freeze", "payee-reservation", "voucher-1", 11L, 12L,
                "ledger-event-1", "0123456789abcdef0123456789abcdef", "user-1", "card-1"));
        server.verify();
    }

    @Test
    void 余额出资转账仍走付款冻结收款预占账本三分支端点() {
        RootContext.bind("mock-xid");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SeataGlobalTransactionExecutor executor = new SeataGlobalTransactionExecutor(builder, "http://account");

        server.expect(once(), requestTo("http://account/internal/v1/seata-tcc/transfer/try"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(RootContext.KEY_XID, "mock-xid"))
                .andExpect(jsonPath("$.payerAccountId").value("payer-account"))
                .andRespond(withSuccess());

        executor.execute(new SeataGlobalTransactionExecutor.TransferTccRequest(
                "tcc:tx-2", "tx-2", "payer-account", "payee-account", 8800L,
                "payer-freeze", "payee-reservation", "voucher-1", 11L, 12L,
                "ledger-event-1", "0123456789abcdef0123456789abcdef", "user-1", null));
        server.verify();
    }
}
