package com.minialalipay.account.interfaces.tcc;

import com.minialalipay.account.application.credit.CreditRepayTccParticipant;
import com.minialalipay.account.application.credit.CreditTccParticipant;
import com.minialalipay.account.domain.credit.CreditFreezeStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * 账户中心信用资金的版本化内部 TCC 接口。
 *
 * <p>该接口只允许业务中心通过服务间鉴权调用，不经过前端网关暴露。每次调用均在参与者的
 * 本地事务内完成额度、余额、应收与分支屏障变更；同一 {@code xid + branchType + resourceId}
 * 重试必须保持所有业务参数一致。</p>
 */
@RestController
@RequestMapping("/internal/v1/tcc")
public class CreditTccController {

    private final CreditTccParticipant creditTccParticipant;
    private final CreditRepayTccParticipant creditRepayTccParticipant;

    public CreditTccController(
            CreditTccParticipant creditTccParticipant,
            CreditRepayTccParticipant creditRepayTccParticipant
    ) {
        this.creditTccParticipant = creditTccParticipant;
        this.creditRepayTccParticipant = creditRepayTccParticipant;
    }

    /**
     * 冻结信用额度并建立 CREDIT_PAY Try 屏障。
     *
     * @param request 信用支付分支的稳定标识、信用账户和金额
     * @return 已完成 Try 的分支状态；重复请求返回相同业务结果
     */
    @PostMapping("/credit-pay/try")
    public ResponseEntity<BranchResponse> tryCreditPay(
            @Valid @RequestBody CreditPayBranchRequest request
    ) {
        var freeze = creditTccParticipant.tryFreeze(
                request.transactionId(), request.creditAccountId(), request.amountFen(),
                request.xid(), Instant.now());
        String status = freeze.getStatus() == CreditFreezeStatus.CONFIRMED
                ? "CONFIRMED"
                : "TRIED";
        return branchResponse(status);
    }

    /**
     * 确认信用额度占用，并在同一账户中心本地事务内增加消费明细和信用应收。
     *
     * @param request Try 阶段原始参数以及扫码订单、收款账户引用
     * @return 已确认的分支状态；重复确认不会重复增加应收
     */
    @PostMapping("/credit-pay/confirm")
    public ResponseEntity<BranchResponse> confirmCreditPay(
            @Valid @RequestBody CreditPayConfirmRequest request
    ) {
        creditTccParticipant.confirmFreeze(
                request.transactionId(), request.creditAccountId(), request.amountFen(), request.xid(),
                request.qrOrderId(), request.merchantAccountId(), Instant.now());
        return branchResponse("CONFIRMED");
    }

    /**
     * 取消信用额度冻结；Try 尚未到达时也必须持久化空回滚屏障以拒绝晚到 Try。
     *
     * @param request 信用支付分支的稳定标识、信用账户和金额
     * @return 已取消的分支状态；重复取消不会重复释放额度
     */
    @PostMapping("/credit-pay/cancel")
    public ResponseEntity<BranchResponse> cancelCreditPay(
            @Valid @RequestBody CreditPayBranchRequest request
    ) {
        creditTccParticipant.cancelFreeze(
                request.transactionId(), request.creditAccountId(), request.amountFen(),
                request.xid(), Instant.now());
        return branchResponse("CANCELLED");
    }

    /**
     * 冻结还款人的余额并建立 CREDIT_REPAY Try 屏障。
     *
     * @param request 还款分支的稳定标识、余额账户、信用账户和金额
     * @return 已完成 Try 的分支状态；重复请求不重复冻结余额
     */
    @PostMapping("/credit-repay/try")
    public ResponseEntity<BranchResponse> tryCreditRepay(
            @Valid @RequestBody CreditRepayBranchRequest request
    ) {
        var status = creditRepayTccParticipant.tryRepay(
                request.transactionId(), request.accountId(), request.creditAccountId(),
                request.amountFen(), request.xid(), Instant.now());
        return branchResponse(status.name());
    }

    /**
     * 确认还款余额扣减，同时减少信用应收、恢复可用额度并完成还款事实。
     *
     * @param request 必须与 Try 阶段完全一致的还款分支参数
     * @return 已确认的分支状态；重复确认不重复扣款或恢复额度
     */
    @PostMapping("/credit-repay/confirm")
    public ResponseEntity<BranchResponse> confirmCreditRepay(
            @Valid @RequestBody CreditRepayBranchRequest request
    ) {
        creditRepayTccParticipant.confirmRepay(
                request.transactionId(), request.accountId(), request.creditAccountId(),
                request.amountFen(), request.xid(), Instant.now());
        return branchResponse("CONFIRMED");
    }

    /**
     * 取消还款余额冻结；空回滚会持久化屏障，防止晚到 Try 再占用资金。
     *
     * @param request 必须与 Try 阶段完全一致的还款分支参数
     * @return 已取消的分支状态；重复取消不重复释放余额
     */
    @PostMapping("/credit-repay/cancel")
    public ResponseEntity<BranchResponse> cancelCreditRepay(
            @Valid @RequestBody CreditRepayBranchRequest request
    ) {
        creditRepayTccParticipant.cancelRepay(
                request.transactionId(), request.accountId(), request.creditAccountId(),
                request.amountFen(), request.xid(), Instant.now());
        return branchResponse("CANCELLED");
    }

    private ResponseEntity<BranchResponse> branchResponse(String status) {
        return ResponseEntity.ok(new BranchResponse(status));
    }

    /**
     * 信用支付 Try/Cancel 请求。金额单位为分，同一分支重试时字段不得变化。
     */
    public record CreditPayBranchRequest(
            @NotBlank @Size(max = 128) String xid,
            @NotBlank @Size(max = 26) String transactionId,
            @NotBlank @Size(max = 26) String creditAccountId,
            @Positive long amountFen
    ) { }

    /**
     * 信用支付 Confirm 请求，扫码订单和商户账户只作为消费事实引用，不允许由前端提交。
     */
    public record CreditPayConfirmRequest(
            @NotBlank @Size(max = 128) String xid,
            @NotBlank @Size(max = 26) String transactionId,
            @NotBlank @Size(max = 26) String creditAccountId,
            @Positive long amountFen,
            @NotBlank @Size(max = 26) String qrOrderId,
            @NotBlank @Size(max = 26) String merchantAccountId
    ) { }

    /**
     * 信用还款 Try/Confirm/Cancel 请求。金额单位为分，同一分支重试时字段不得变化。
     */
    public record CreditRepayBranchRequest(
            @NotBlank @Size(max = 128) String xid,
            @NotBlank @Size(max = 26) String transactionId,
            @NotBlank @Size(max = 26) String accountId,
            @NotBlank @Size(max = 26) String creditAccountId,
            @Positive long amountFen
    ) { }

    /**
     * TCC 分支同步执行结果，状态取值为 TRIED、CONFIRMED 或 CANCELLED。
     */
    public record BranchResponse(String status) { }
}
