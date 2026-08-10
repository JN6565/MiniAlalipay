package com.minialalipay.account.interfaces.tcc;

import com.minialalipay.account.application.tcc.BalanceTccApplicationService;
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
 * 银行卡充值 Seata 全局事务 Try 端点：组合银行卡余额扣减与账户余额入账。
 *
 * <p>充值（银行卡扣钱给账户加钱）需要在同一个 Seata 全局事务内注册两个 TCC 分支：
 * <ol>
 *     <li>银行卡虚拟余额扣减（BANK_CARD_RECHARGE）— Confirm 时扣减</li>
 *     <li>收款方账户余额入账（PAYEE_BALANCE）— Confirm 时增加可用余额</li>
 * </ol>
 * TC 提交后分别回调各自参与者的 Confirm；回滚时分别回调 Cancel。</p>
 */
@RestController
@Validated
@RequestMapping("/internal/v1/seata-tcc/bank-card-recharge")
public class SeataBankCardRechargeTccController {
    private final SeataBankCardBalanceTccParticipant bankCardBalance;
    private final SeataTransferTccParticipant balance;

    public SeataBankCardRechargeTccController(SeataBankCardBalanceTccParticipant bankCardBalance,
                                               SeataTransferTccParticipant balance) {
        this.bankCardBalance = bankCardBalance;
        this.balance = balance;
    }

    /**
     * 组合注册银行卡扣减与收款余额入账两个 TCC 分支；任一 Try 失败由 TC 统一回滚。
     *
     * @param request 包含业务屏障标识、银行卡、用户账户和金额
     */
    @PostMapping("/try")
    public ResponseEntity<Void> tryBankCardRecharge(@Valid @RequestBody Request request) {
        // 1. 注册银行卡充值分支（Confirm 时扣减银行卡虚拟余额）
        bankCardBalance.tryRecharge(null, request.businessXid(), request.transactionId(),
                request.userId(), request.cardId(), request.amountFen());
        // 2. 收款方账户余额入账（Confirm 时增加可用余额）
        balance.tryPayee(null, request.businessXid(), request.transactionId(), request.accountId(),
                request.amountFen(), request.reservationId());
        return ResponseEntity.ok().build();
    }

    /** 银行卡充值组合 Try 参数；金额单位为分，所有标识在重试期间必须保持稳定。 */
    public record Request(@NotBlank @Size(max = 128) String businessXid,
                          @NotBlank @Size(max = 128) String transactionId,
                          @NotBlank @Size(max = 128) String userId,
                          @NotBlank @Size(max = 128) String accountId,
                          @NotBlank @Size(max = 128) String cardId,
                          @Positive long amountFen,
                          @NotBlank @Size(max = 128) String reservationId) { }
}
