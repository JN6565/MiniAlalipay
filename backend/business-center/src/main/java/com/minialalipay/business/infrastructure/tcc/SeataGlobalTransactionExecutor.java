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
 * <p>该类只负责在全局事务内请求账户中心注册 TCC 分支。业务主单已经在调用前提交，
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
     * 注册转账 TCC 分支。
     *
     * <p>余额出资：注册付款余额冻结、收款余额预占和复式账本三个分支（组合端点一次完成）。
     * 银行卡出资：资金方向与充值相同（银行卡扣款 → 收款账户入账），复用充值组合端点
     * 注册银行卡扣减分支（BANK_CARD_RECHARGE，Confirm 时扣减卡虚拟余额）、收款入账分支
     * （PAYEE_BALANCE，Confirm 时增加收款方余额）和外部出资账本分支。账本分支只给收款方
     * 生成余额贷方明细，付款方银行卡扣款由银行卡流水进入全局账单。不得改用提现分支：
     * 其 Confirm 向卡入账，方向相反且没有收款分支，会导致收款方永远收不到钱。</p>
     *
     * @param request 稳定业务分支参数；重试时不得改变
     */
    @GlobalTransactional(name = "minialalipay-transfer-tcc", timeoutMills = 30000, rollbackFor = Exception.class)
    public void execute(TransferTccRequest request) {
        String xid = RootContext.getXID();
        if (xid == null || xid.isBlank()) {
            throw new IllegalStateException("Seata 全局事务 XID 未建立");
        }
        // 银行卡出资转账/扫码支付：注册银行卡扣减 + 收款入账两个分支
        if (request.bankCardId() != null && !request.bankCardId().isBlank()) {
            accountClient.post()
                    .uri("/internal/v1/seata-tcc/bank-card-recharge/try")
                    .header(RootContext.KEY_XID, xid)
                    .body(new BankCardRechargeRequest(request.businessXid(), request.transactionId(),
                            request.payerUserId(), request.payeeAccountId(), request.bankCardId(),
                            request.amountFen(), request.payeeReservationId(), request.voucherId(),
                            request.debitEntryId(), request.creditEntryId(), request.ledgerEventId(),
                            request.traceId()))
                    .retrieve()
                    .toBodilessEntity();
        } else {
            // 普通余额转账：冻结付款方账户余额
            accountClient.post()
                    .uri("/internal/v1/seata-tcc/transfer/try")
                    .header(RootContext.KEY_XID, xid)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        }
    }

    /**
     * 注册银行卡充值组合 TCC 分支：银行卡余额扣减 + 收款方账户余额入账。
     *
     * @param request 稳定业务分支参数；重试时不得改变
     */
    @GlobalTransactional(name = "minialalipay-bank-card-recharge-tcc", timeoutMills = 30000, rollbackFor = Exception.class)
    public void executeBankCardRecharge(BankCardRechargeRequest request) {
        String xid = RootContext.getXID();
        if (xid == null || xid.isBlank()) {
            throw new IllegalStateException("Seata 全局事务 XID 未建立");
        }
        // 组合端点：同时注册银行卡扣减分支和收款账户入账分支
        accountClient.post()
                .uri("/internal/v1/seata-tcc/bank-card-recharge/try")
                .header(RootContext.KEY_XID, xid)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * 注册银行卡提现组合 TCC 分支：付款方账户余额冻结 + 银行卡余额增加。
     *
     * @param request 稳定业务分支参数；重试时不得改变
     */
    @GlobalTransactional(name = "minialalipay-bank-card-withdraw-tcc", timeoutMills = 30000, rollbackFor = Exception.class)
    public void executeBankCardWithdraw(BankCardWithdrawRequest request) {
        String xid = RootContext.getXID();
        if (xid == null || xid.isBlank()) {
            throw new IllegalStateException("Seata 全局事务 XID 未建立");
        }
        // 组合端点：同时注册付款账户冻结分支和银行卡入账分支
        accountClient.post()
                .uri("/internal/v1/seata-tcc/bank-card-withdraw/try")
                .header(RootContext.KEY_XID, xid)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    /** 转账 TCC 的完整稳定参数，技术 XID 不进入该对象。bankCardId 可选，非空时走银行卡扣减分支。 */
    public record TransferTccRequest(String businessXid, String transactionId,
                                     String payerAccountId, String payeeAccountId, long amountFen,
                                     String payerFreezeId, String payeeReservationId,
                                     String voucherId, long debitEntryId, long creditEntryId,
                                     String ledgerEventId, String traceId,
                                     String payerUserId, String bankCardId) { }

    /** 银行卡充值 TCC 的完整稳定参数；账本字段仅银行卡出资转账/扫码使用，普通充值为空。 */
    public record BankCardRechargeRequest(String businessXid, String transactionId,
                                          String userId, String accountId, String cardId,
                                          long amountFen, String reservationId,
                                          String voucherId, long debitEntryId, long creditEntryId,
                                          String ledgerEventId, String traceId) { }

    /** 银行卡提现 TCC 的完整稳定参数，包含账户冻结所需标识。 */
    public record BankCardWithdrawRequest(String businessXid, String transactionId,
                                          String userId, String accountId, String cardId,
                                          long amountFen, String freezeId) { }
}
