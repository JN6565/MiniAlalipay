package com.minialalipay.account.interfaces.tcc;

import com.minialalipay.account.application.tcc.SeataLedgerTccParticipant;
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

/** 业务中心在 Seata 全局事务内调用的转账 TCC Try 入口。Confirm/Cancel 不经过 HTTP。 */
@RestController
@Validated
@RequestMapping("/internal/v1/seata-tcc/transfer")
public class SeataTransferTccController {
    private final SeataTransferTccParticipant balance;
    private final SeataLedgerTccParticipant ledger;

    public SeataTransferTccController(SeataTransferTccParticipant balance, SeataLedgerTccParticipant ledger) {
        this.balance = balance;
        this.ledger = ledger;
    }

    /** 依次注册付款余额、收款余额和账本三个分支；任一 Try 失败由 TC 统一回滚。 */
    @PostMapping("/try")
    public ResponseEntity<Void> tryTransfer(@Valid @RequestBody Request request) {
        balance.tryPayer(null, request.businessXid(), request.transactionId(), request.payerAccountId(),
                request.amountFen(), request.payerFreezeId());
        balance.tryPayee(null, request.businessXid(), request.transactionId(), request.payeeAccountId(),
                request.amountFen(), request.payeeReservationId());
        ledger.tryLedger(null, request.businessXid(), request.transactionId(), request.payerAccountId(),
                request.payeeAccountId(), request.amountFen(), request.voucherId(), request.debitEntryId(),
                request.creditEntryId(), request.ledgerEventId(), request.traceId());
        return ResponseEntity.ok().build();
    }

    /** Seata TCC Try 参数；金额单位为分，所有标识在重试期间必须保持稳定。 */
    public record Request(@NotBlank @Size(max = 128) String businessXid,
                          @NotBlank @Size(min = 26, max = 26) String transactionId,
                          @NotBlank @Size(min = 26, max = 26) String payerAccountId,
                          @NotBlank @Size(min = 26, max = 26) String payeeAccountId,
                          @Positive long amountFen,
                          @NotBlank @Size(min = 26, max = 26) String payerFreezeId,
                          @NotBlank @Size(min = 26, max = 26) String payeeReservationId,
                          @NotBlank @Size(min = 26, max = 26) String voucherId,
                          @Positive long debitEntryId, @Positive long creditEntryId,
                          @NotBlank @Size(min = 26, max = 26) String ledgerEventId,
                          @NotBlank @Size(min = 32, max = 32) String traceId) { }
}
