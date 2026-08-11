package com.minialalipay.account.application.tcc;

import io.seata.rm.tcc.api.BusinessActionContext;
import io.seata.rm.tcc.api.BusinessActionContextParameter;
import io.seata.rm.tcc.api.LocalTCC;
import io.seata.rm.tcc.api.TwoPhaseBusinessAction;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 银行卡等外部资金出资的转账账本 Seata TCC 参与者。
 *
 * <p>该参与者只用于“银行卡扣款给他人余额入账”的资金路径。付款方不发生账户余额变化，
 * 因此账本不生成付款用户余额分录；收款方余额发生变化，必须生成可查询的贷方分录，
 * 并用系统发行权益科目作为清算借方保持复式记账平衡。</p>
 */
@LocalTCC
@Service
public class SeataExternalFundingLedgerTccParticipant {
    private final LedgerTccApplicationService ledgerService;

    public SeataExternalFundingLedgerTccParticipant(LedgerTccApplicationService ledgerService) {
        this.ledgerService = ledgerService;
    }

    /** Try 创建银行卡出资转账/扫码的 PREPARED 平衡凭证。 */
    @TwoPhaseBusinessAction(name = "externalFundingTransferLedgerTcc", commitMethod = "confirmLedger",
            rollbackMethod = "cancelLedger", useTCCFence = false)
    public boolean tryLedger(BusinessActionContext context,
                             @BusinessActionContextParameter(paramName = "businessXid") String businessXid,
                             @BusinessActionContextParameter(paramName = "transactionId") String transactionId,
                             @BusinessActionContextParameter(paramName = "payeeAccountId") String payeeAccountId,
                             @BusinessActionContextParameter(paramName = "amountFen") long amountFen,
                             @BusinessActionContextParameter(paramName = "voucherId") String voucherId,
                             @BusinessActionContextParameter(paramName = "debitEntryId") long debitEntryId,
                             @BusinessActionContextParameter(paramName = "creditEntryId") long creditEntryId,
                             @BusinessActionContextParameter(paramName = "eventId") String eventId,
                             @BusinessActionContextParameter(paramName = "traceId") String traceId) {
        ledgerService.tryExternalFundingLedger(command(businessXid, transactionId, payeeAccountId, amountFen,
                voucherId, debitEntryId, creditEntryId, eventId, traceId), Instant.now());
        return true;
    }

    /** TC 提交回调；过账后收款方余额明细才能稳定查询到本次收款。 */
    public boolean confirmLedger(BusinessActionContext context) {
        ledgerService.confirmExternalFundingLedger(command(context), Instant.now());
        return true;
    }

    /** TC 回滚回调；取消未过账凭证，避免留下不完整收款明细。 */
    public boolean cancelLedger(BusinessActionContext context) {
        ledgerService.cancelExternalFundingLedger(command(context), Instant.now());
        return true;
    }

    private static LedgerTccApplicationService.ExternalFundingLedgerCommand command(BusinessActionContext context) {
        return command(text(context, "businessXid"), text(context, "transactionId"),
                text(context, "payeeAccountId"), number(context, "amountFen"), text(context, "voucherId"),
                number(context, "debitEntryId"), number(context, "creditEntryId"),
                text(context, "eventId"), text(context, "traceId"));
    }

    private static LedgerTccApplicationService.ExternalFundingLedgerCommand command(
            String businessXid, String transactionId, String payeeAccountId, long amountFen,
            String voucherId, long debitEntryId, long creditEntryId, String eventId, String traceId) {
        return new LedgerTccApplicationService.ExternalFundingLedgerCommand(businessXid, transactionId,
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
