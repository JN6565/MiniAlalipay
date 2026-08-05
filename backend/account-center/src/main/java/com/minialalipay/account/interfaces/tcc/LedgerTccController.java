package com.minialalipay.account.interfaces.tcc;

import com.minialalipay.account.application.tcc.LedgerTccApplicationService;
import com.minialalipay.account.application.tcc.LedgerTccApplicationService.LedgerCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** 仅供业务中心调用的版本化复式账本 TCC 内部接口。 */
@RestController
@Validated
@RequestMapping("/internal/v1/tcc/ledger")
public class LedgerTccController {
    private final LedgerTccApplicationService service;
    public LedgerTccController(LedgerTccApplicationService service) { this.service = service; }
    /**
     * 执行账本 Try、Confirm 或 Cancel。
     *
     * <p>仅限业务中心服务身份。应用服务在 ledger_db 本地事务中写入分支屏障、平衡凭证、分录和 Outbox；
     * 重试必须复用 xid、凭证、分录和事件 ID。非法动作或参数返回 400，事实不存在返回 404，
     * 幂等载荷或状态冲突返回 409。</p>
     */
    @PostMapping("/{action}")
    public ResponseEntity<Result> execute(
            @PathVariable @Pattern(regexp = "^(try|confirm|cancel)$") String action,
            @Valid @RequestBody Request body) {
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
    public record Request(@NotBlank @Size(max = 128) String xid,
            @NotBlank @Size(min = 26, max = 26) @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String transactionId,
            @NotBlank @Size(min = 26, max = 26) @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String payerAccountId,
            @NotBlank @Size(min = 26, max = 26) @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String payeeAccountId,
            @Positive long amountFen,
            @NotBlank @Size(min = 26, max = 26) @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String voucherId,
            @Positive long debitEntryId, @Positive long creditEntryId,
            @NotBlank @Size(min = 26, max = 26) @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String eventId,
            @NotBlank @Pattern(regexp = "^[a-fA-F0-9]{32}$") String traceId) { }
    /** 账本分支响应。 */
    public record Result(String status) { }
}
