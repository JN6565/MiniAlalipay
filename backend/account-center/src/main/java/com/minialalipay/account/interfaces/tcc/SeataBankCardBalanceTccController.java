package com.minialalipay.account.interfaces.tcc;

import com.minialalipay.account.application.tcc.SeataBankCardBalanceTccParticipant;
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
 * 业务中心在 Seata 全局事务内调用的银行卡充值/提现 TCC Try 入口。
 *
 * <p>Confirm/Cancel 不经过 HTTP，由 Seata TC 直接通过 Seata RPC 回调
 * {@link SeataBankCardBalanceTccParticipant} 的 confirm/cancel 方法。</p>
 */
@RestController
@Validated
@RequestMapping("/internal/v1/seata-tcc/bank-card-balance")
public class SeataBankCardBalanceTccController {
    private final SeataBankCardBalanceTccParticipant participant;

    public SeataBankCardBalanceTccController(SeataBankCardBalanceTccParticipant participant) {
        this.participant = participant;
    }

    /** 注册充值 TCC 分支；Try 失败由 TC 统一回滚。 */
    @PostMapping("/recharge/try")
    public ResponseEntity<Void> tryRecharge(@Valid @RequestBody RechargeRequest request) {
        participant.tryRecharge(null, request.businessXid(), request.transactionId(),
                request.userId(), request.cardId(), request.amountFen());
        return ResponseEntity.ok().build();
    }

    /** 注册提现 TCC 分支；Try 失败由 TC 统一回滚。 */
    @PostMapping("/withdraw/try")
    public ResponseEntity<Void> tryWithdraw(@Valid @RequestBody WithdrawRequest request) {
        participant.tryWithdraw(null, request.businessXid(), request.transactionId(),
                request.userId(), request.cardId(), request.amountFen());
        return ResponseEntity.ok().build();
    }

    /** 银行卡充值 TCC Try 参数；金额单位为分，所有标识在重试期间必须保持稳定。 */
    public record RechargeRequest(@NotBlank @Size(max = 128) String businessXid,
                                  @NotBlank @Size(max = 128) String transactionId,
                                  @NotBlank @Size(max = 128) String userId,
                                  @NotBlank @Size(max = 128) String cardId,
                                  @Positive long amountFen) { }

    /** 银行卡提现 TCC Try 参数；金额单位为分，所有标识在重试期间必须保持稳定。 */
    public record WithdrawRequest(@NotBlank @Size(max = 128) String businessXid,
                                  @NotBlank @Size(max = 128) String transactionId,
                                  @NotBlank @Size(max = 128) String userId,
                                  @NotBlank @Size(max = 128) String cardId,
                                  @Positive long amountFen) { }
}
