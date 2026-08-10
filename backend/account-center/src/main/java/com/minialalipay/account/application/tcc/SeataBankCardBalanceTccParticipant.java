package com.minialalipay.account.application.tcc;

import io.seata.rm.tcc.api.BusinessActionContext;
import io.seata.rm.tcc.api.BusinessActionContextParameter;
import io.seata.rm.tcc.api.LocalTCC;
import io.seata.rm.tcc.api.TwoPhaseBusinessAction;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 银行卡充值/提现 Seata TCC 参与者。
 *
 * <p>技术 XID 只用于 Seata 注册和回调；业务屏障始终使用由交易 ID 派生的 businessXid，
 * 从而保证 TC 重试、恢复扫描和人工接管使用同一幂等键。项目已有 tcc_branch 屏障，
 * 因此不启用 Seata TCC Fence，避免维护两套互相竞争的业务状态。</p>
 *
 * <p>充值分支（银行卡给账户充钱）：Try 注册分支并校验余额充足，Confirm 扣减银行卡虚拟余额；
 * 提现分支（账户给银行卡充钱）：Try 注册分支并校验银行卡归属，Confirm 增加银行卡虚拟余额。</p>
 */
@LocalTCC
@Service
public class SeataBankCardBalanceTccParticipant {
    private final BankCardBalanceTccApplicationService balanceService;

    public SeataBankCardBalanceTccParticipant(BankCardBalanceTccApplicationService balanceService) {
        this.balanceService = balanceService;
    }

    /** Try 注册充值分支：校验银行卡存在且属于当前用户。 */
    @TwoPhaseBusinessAction(name = "bankCardRechargeTcc", commitMethod = "confirmRecharge",
            rollbackMethod = "cancelRecharge", useTCCFence = false)
    public boolean tryRecharge(BusinessActionContext context,
                               @BusinessActionContextParameter(paramName = "businessXid") String businessXid,
                               @BusinessActionContextParameter(paramName = "transactionId") String transactionId,
                               @BusinessActionContextParameter(paramName = "userId") String userId,
                               @BusinessActionContextParameter(paramName = "cardId") String cardId,
                               @BusinessActionContextParameter(paramName = "amountFen") long amountFen) {
        balanceService.tryRecharge(command(businessXid, transactionId, userId, cardId, amountFen), Instant.now());
        return true;
    }

    /** TC 提交回调：扣减银行卡虚拟余额（充值从卡出资金），相同业务屏障重复调用只扣减一次。 */
    public boolean confirmRecharge(BusinessActionContext context) {
        balanceService.confirmRecharge(command(context), Instant.now());
        return true;
    }

    /** TC 回滚回调：释放充值分支。 */
    public boolean cancelRecharge(BusinessActionContext context) {
        balanceService.cancelRecharge(command(context), Instant.now());
        return true;
    }

    /** Try 注册提现分支：校验银行卡存在、归属正确且余额充足。 */
    @TwoPhaseBusinessAction(name = "bankCardWithdrawTcc", commitMethod = "confirmWithdraw",
            rollbackMethod = "cancelWithdraw", useTCCFence = false)
    public boolean tryWithdraw(BusinessActionContext context,
                               @BusinessActionContextParameter(paramName = "businessXid") String businessXid,
                               @BusinessActionContextParameter(paramName = "transactionId") String transactionId,
                               @BusinessActionContextParameter(paramName = "userId") String userId,
                               @BusinessActionContextParameter(paramName = "cardId") String cardId,
                               @BusinessActionContextParameter(paramName = "amountFen") long amountFen) {
        balanceService.tryWithdraw(command(businessXid, transactionId, userId, cardId, amountFen), Instant.now());
        return true;
    }

    /** TC 提交回调：增加银行卡虚拟余额（提现向卡入账），相同业务屏障重复调用只充值一次。 */
    public boolean confirmWithdraw(BusinessActionContext context) {
        balanceService.confirmWithdraw(command(context), Instant.now());
        return true;
    }

    /** TC 回滚回调：释放提现分支。 */
    public boolean cancelWithdraw(BusinessActionContext context) {
        balanceService.cancelWithdraw(command(context), Instant.now());
        return true;
    }

    private static BankCardBalanceTccApplicationService.Command command(BusinessActionContext context) {
        return command(text(context, "businessXid"), text(context, "transactionId"),
                text(context, "userId"), text(context, "cardId"), number(context, "amountFen"));
    }

    private static BankCardBalanceTccApplicationService.Command command(String businessXid, String transactionId,
                                                                        String userId, String cardId, long amountFen) {
        return new BankCardBalanceTccApplicationService.Command(businessXid, transactionId, userId, cardId, amountFen);
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
