package com.minialalipay.business.infrastructure.tcc;

import io.seata.core.context.RootContext;
import io.seata.spring.annotation.GlobalTransactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Seata 全局事务执行器。
 *
 * <p>该类只负责在全局事务内请求账户中心注册转账 TCC 分支。业务主单已经在调用前提交，
 * 不属于 Seata 分支；只有本方法正常返回时 TC 才会发起 Confirm，异常则发起 Cancel。</p>
 */
@Component
public class SeataGlobalTransactionExecutor {
    private final RestClient accountClient;

    public SeataGlobalTransactionExecutor(@LoadBalanced RestClient.Builder builder,
                                          @Value("${minialalipay.internal.account-center-url}") String accountBaseUrl) {
        this.accountClient = builder.baseUrl(accountBaseUrl).build();
    }

    /**
     * 注册付款余额、收款余额和复式账本三个 TCC 分支。
     *
     * @param request 稳定业务分支参数；重试时不得改变
     */
    @GlobalTransactional(name = "minialalipay-transfer-tcc", timeoutMills = 30000, rollbackFor = Exception.class)
    public void execute(TransferTccRequest request) {
        String xid = RootContext.getXID();
        if (xid == null || xid.isBlank()) {
            throw new IllegalStateException("Seata 全局事务 XID 未建立");
        }
        accountClient.post()
                .uri("/internal/v1/seata-tcc/transfer/try")
                .header(RootContext.KEY_XID, xid)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    /** 转账 TCC 的完整稳定参数，技术 XID 不进入该对象。 */
    public record TransferTccRequest(String businessXid, String transactionId,
                                     String payerAccountId, String payeeAccountId, long amountFen,
                                     String payerFreezeId, String payeeReservationId,
                                     String voucherId, long debitEntryId, long creditEntryId,
                                     String ledgerEventId, String traceId) { }
}
