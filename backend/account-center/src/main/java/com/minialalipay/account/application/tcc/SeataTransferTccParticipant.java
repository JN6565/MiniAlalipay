package com.minialalipay.account.application.tcc;

import io.seata.rm.tcc.api.BusinessActionContext;
import io.seata.rm.tcc.api.BusinessActionContextParameter;
import io.seata.rm.tcc.api.LocalTCC;
import io.seata.rm.tcc.api.TwoPhaseBusinessAction;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 转账余额 Seata TCC 参与者。
 *
 * <p>技术 XID 只用于 Seata 注册和回调；业务屏障始终使用由交易 ID 派生的 businessXid，
 * 从而保证 TC 重试、恢复扫描和人工接管使用同一幂等键。项目已有 tcc_branch 屏障，
 * 因此不启用 Seata TCC Fence，避免维护两套互相竞争的业务状态。</p>
 */
@LocalTCC
@Service
public class SeataTransferTccParticipant {
    private final BalanceTccApplicationService balanceService;

    public SeataTransferTccParticipant(BalanceTccApplicationService balanceService) {
        this.balanceService = balanceService;
    }

    /** Try 冻结付款方余额并注册付款分支。 */
    @TwoPhaseBusinessAction(name = "transferPayerBalanceTcc", commitMethod = "confirmPayer",
            rollbackMethod = "cancelPayer", useTCCFence = false)
    public boolean tryPayer(BusinessActionContext context,
                            @BusinessActionContextParameter(paramName = "businessXid") String businessXid,
                            @BusinessActionContextParameter(paramName = "transactionId") String transactionId,
                            @BusinessActionContextParameter(paramName = "accountId") String accountId,
                            @BusinessActionContextParameter(paramName = "amountFen") long amountFen,
                            @BusinessActionContextParameter(paramName = "freezeId") String freezeId) {
        balanceService.tryPayer(command(businessXid, transactionId, accountId, amountFen, freezeId), Instant.now());
        return true;
    }

    /** TC 提交回调；相同业务屏障重复调用只扣款一次。 */
    public boolean confirmPayer(BusinessActionContext context) {
        balanceService.confirmPayer(command(context), Instant.now());
        return true;
    }

    /** TC 回滚回调；Try 未到达时也会留下 EMPTY 空回滚屏障。 */
    public boolean cancelPayer(BusinessActionContext context) {
        balanceService.cancelPayer(command(context), Instant.now());
        return true;
    }

    /** Try 预占收款方入账资格，不提前增加余额。 */
    @TwoPhaseBusinessAction(name = "transferPayeeBalanceTcc", commitMethod = "confirmPayee",
            rollbackMethod = "cancelPayee", useTCCFence = false)
    public boolean tryPayee(BusinessActionContext context,
                            @BusinessActionContextParameter(paramName = "businessXid") String businessXid,
                            @BusinessActionContextParameter(paramName = "transactionId") String transactionId,
                            @BusinessActionContextParameter(paramName = "accountId") String accountId,
                            @BusinessActionContextParameter(paramName = "amountFen") long amountFen,
                            @BusinessActionContextParameter(paramName = "freezeId") String freezeId) {
        balanceService.tryPayee(command(businessXid, transactionId, accountId, amountFen, freezeId), Instant.now());
        return true;
    }

    /** TC 提交回调；收款方余额只增加一次。 */
    public boolean confirmPayee(BusinessActionContext context) {
        balanceService.confirmPayee(command(context), Instant.now());
        return true;
    }

    /** TC 回滚回调；撤销收款预占。 */
    public boolean cancelPayee(BusinessActionContext context) {
        balanceService.cancelPayee(command(context), Instant.now());
        return true;
    }

    private static BalanceTccApplicationService.TccCommand command(BusinessActionContext context) {
        return command(text(context, "businessXid"), text(context, "transactionId"),
                text(context, "accountId"), number(context, "amountFen"), text(context, "freezeId"));
    }

    private static BalanceTccApplicationService.TccCommand command(String businessXid, String transactionId,
                                                                    String accountId, long amountFen, String freezeId) {
        return new BalanceTccApplicationService.TccCommand(
                businessXid, transactionId, accountId, amountFen, freezeId);
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
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(text(context, key));
    }
}
