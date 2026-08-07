package com.minialalipay.account.application.tcc;

import io.seata.rm.tcc.api.BusinessActionContext;
import io.seata.rm.tcc.api.BusinessActionContextParameter;
import io.seata.rm.tcc.api.LocalTCC;
import io.seata.rm.tcc.api.TwoPhaseBusinessAction;
import org.springframework.stereotype.Service;

import java.time.Instant;

/** 转账复式账本的 Seata TCC 参与者，负责预制、过账和取消完整凭证。 */
@LocalTCC
@Service
public class SeataLedgerTccParticipant {
    private final LedgerTccApplicationService ledgerService;

    public SeataLedgerTccParticipant(LedgerTccApplicationService ledgerService) {
        this.ledgerService = ledgerService;
    }

    /** Try 创建平衡的 PREPARED 凭证，并注册账本分支。 */
    @TwoPhaseBusinessAction(name = "transferLedgerTcc", commitMethod = "confirmLedger",
            rollbackMethod = "cancelLedger", useTCCFence = false)
    public boolean tryLedger(BusinessActionContext context,
                             @BusinessActionContextParameter(paramName = "businessXid") String businessXid,
                             @BusinessActionContextParameter(paramName = "transactionId") String transactionId,
                             @BusinessActionContextParameter(paramName = "payerAccountId") String payerAccountId,
                             @BusinessActionContextParameter(paramName = "payeeAccountId") String payeeAccountId,
                             @BusinessActionContextParameter(paramName = "amountFen") long amountFen,
                             @BusinessActionContextParameter(paramName = "voucherId") String voucherId,
                             @BusinessActionContextParameter(paramName = "debitEntryId") long debitEntryId,
                             @BusinessActionContextParameter(paramName = "creditEntryId") long creditEntryId,
                             @BusinessActionContextParameter(paramName = "eventId") String eventId,
                             @BusinessActionContextParameter(paramName = "traceId") String traceId) {
        ledgerService.tryLedger(command(businessXid, transactionId, payerAccountId, payeeAccountId, amountFen,
                voucherId, debitEntryId, creditEntryId, eventId, traceId), Instant.now());
        return true;
    }

    /** TC 提交回调；过账前由既有账本服务再次校验借贷平衡。 */
    public boolean confirmLedger(BusinessActionContext context) {
        ledgerService.confirmLedger(command(context), Instant.now());
        return true;
    }

    /** TC 回滚回调；取消预制凭证并保留审计事实。 */
    public boolean cancelLedger(BusinessActionContext context) {
        ledgerService.cancelLedger(command(context), Instant.now());
        return true;
    }

    private static LedgerTccApplicationService.LedgerCommand command(BusinessActionContext context) {
        return command(text(context, "businessXid"), text(context, "transactionId"),
                text(context, "payerAccountId"), text(context, "payeeAccountId"), number(context, "amountFen"),
                text(context, "voucherId"), number(context, "debitEntryId"), number(context, "creditEntryId"),
                text(context, "eventId"), text(context, "traceId"));
    }

    private static LedgerTccApplicationService.LedgerCommand command(
            String businessXid, String transactionId, String payerAccountId, String payeeAccountId,
            long amountFen, String voucherId, long debitEntryId, long creditEntryId,
            String eventId, String traceId) {
        return new LedgerTccApplicationService.LedgerCommand(businessXid, transactionId, payerAccountId,
                payeeAccountId, amountFen, voucherId, debitEntryId, creditEntryId, eventId, traceId);
    }

    private static String text(BusinessActionContext context, String key) {
        Object value = context.getActionContext(key);
        if (value == null) {
            throw new IllegalStateException("Seata TCC 回调缺少参数: " + key);
        }
        return value.toString();
    }

    private static long number(BusinessActionContext context, String key) {
        Object value = context.getActionContext(key);
        return value instanceof Number number ? number.longValue() : Long.parseLong(text(context, key));
    }
}
