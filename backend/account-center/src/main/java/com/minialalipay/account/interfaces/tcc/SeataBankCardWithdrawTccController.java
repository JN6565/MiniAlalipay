package com.minialalipay.account.interfaces.tcc;

import com.minialalipay.account.application.tcc.BalanceTccApplicationService;
import com.minialalipay.account.application.tcc.BankCardBalanceTccApplicationService;
import com.minialalipay.account.application.tcc.SeataBankCardBalanceTccParticipant;
import com.minialalipay.account.application.tcc.SeataTransferTccParticipant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 银行卡提现 Seata 全局事务 Try 端点：组合账户余额冻结与银行卡余额分支。
 *
 * <p>提现（账户给银行卡充钱）需要在同一个 Seata 全局事务内注册两个 TCC 分支：
 * <ol>
 *     <li>付款方账户余额冻结（PAYER_BALANCE）— Confirm 时扣减</li>
 *     <li>银行卡虚拟余额增加（BANK_CARD_WITHDRAW）— Confirm 时入账</li>
 * </ol>
 * TC 提交后分别回调各自参与者的 Confirm；回滚时分别回调 Cancel。</p>
 */
@RestController
@Validated
@RequestMapping("/internal/v1/seata-tcc/bank-card-withdraw")
public class SeataBankCardWithdrawTccController {
    private final SeataTransferTccParticipant balance;
    private final SeataBankCardBalanceTccParticipant bankCardBalance;

    public SeataBankCardWithdrawTccController(SeataTransferTccParticipant balance,
                                               SeataBankCardBalanceTccParticipant bankCardBalance) {
        this.balance = balance;
        this.bankCardBalance = bankCardBalance;
    }

    /**
     * 组合注册付款余额冻结与银行卡提现两个 TCC 分支；任一 Try 失败由 TC 统一回滚。
     *
     * @param request 包含业务屏障标识、用户账户、银行卡和金额
     */
    @PostMapping("/try")
    public ResponseEntity<Void> tryBankCardWithdraw(@Valid @RequestBody Request request) {
        // 1. 冻结付款方账户余额（Confirm 时扣减）
        balance.tryPayer(null, request.businessXid(), request.transactionId(), request.accountId(),
                request.amountFen(), request.freezeId());
        // 2. 注册银行卡提现分支（Confirm 时增加银行卡虚拟余额）
        bankCardBalance.tryWithdraw(null, request.businessXid(), request.transactionId(),
                request.userId(), request.cardId(), request.amountFen());
        return ResponseEntity.ok().build();
    }

    /** 银行卡提现组合 Try 参数；金额单位为分，所有标识在重试期间必须保持稳定。 */
    public record Request(@NotBlank @Size(max = 128) String businessXid,
                          @NotBlank @Size(max = 128) String transactionId,
                          @NotBlank @Size(max = 128) String userId,
                          @NotBlank @Size(max = 128) String accountId,
                          @NotBlank @Size(max = 128) String cardId,
                          @Positive long amountFen,
                          @NotBlank @Size(max = 128) String freezeId) { }
}
