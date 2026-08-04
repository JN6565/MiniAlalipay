package com.minialalipay.account.interfaces.tcc;

import com.minialalipay.account.application.tcc.LedgerTccApplicationService;
import com.minialalipay.account.application.tcc.LedgerTccApplicationService.LedgerCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** 仅供业务中心调用的版本化复式账本 TCC 内部接口。 */
@RestController
@RequestMapping("/internal/v1/tcc/ledger")
public class LedgerTccController {
    private final LedgerTccApplicationService service;
    public LedgerTccController(LedgerTccApplicationService service) { this.service = service; }
    /** 执行账本 Try、Confirm 或 Cancel，所有动作按 xid 和凭证 ID 幂等。 */
    @PostMapping("/{action}")
    public ResponseEntity<Result> execute(@PathVariable String action, @Valid @RequestBody Request body) {
        LedgerCommand c = new LedgerCommand(body.xid(), body.transactionId(), body.payerAccountId(), body.payeeAccountId(),
                body.amountFen(), body.voucherId(), body.debitEntryId(), body.creditEntryId(), body.eventId(), body.traceId());
        var branch = switch (action) {
            case "try" -> service.tryLedger(c, Instant.now());
            case "confirm" -> service.confirmLedger(c, Instant.now());
            case "cancel" -> service.cancelLedger(c, Instant.now());
            default -> throw new IllegalArgumentException("不支持的账本 TCC 动作");
        };
        return ResponseEntity.ok(new Result(branch.getStatus().name()));
    }
    /** 账本 TCC 请求。 */
    public record Request(@NotBlank String xid, @NotBlank String transactionId,
            @NotBlank String payerAccountId, @NotBlank String payeeAccountId, @Positive long amountFen,
            @NotBlank String voucherId, @Positive long debitEntryId, @Positive long creditEntryId,
            @NotBlank String eventId, @NotBlank String traceId) { }
    /** 账本分支响应。 */
    public record Result(String status) { }
}
