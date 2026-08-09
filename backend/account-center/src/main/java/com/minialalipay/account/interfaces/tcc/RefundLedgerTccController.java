package com.minialalipay.account.interfaces.tcc;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.minialalipay.account.application.tcc.RefundLedgerTccApplicationService;
import com.minialalipay.account.application.tcc.RefundLedgerTccApplicationService.RefundLedgerCommand;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
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

/**
 * 退款专用账本 TCC 的内部接口。
 *
 * <p>仅供业务中心服务身份调用，不经网关暴露。请求不允许传入付款余额账户之外的资金事实；
 * 余额退款由 {@code payerAccountId} 指定贷方余额科目，信用退款由 {@code creditAccountId}
 * 派生信用应收资产科目。</p>
 */
@RestController
@Validated
@RequestMapping("/internal/v1/tcc/refund-ledger")
public class RefundLedgerTccController {

    private final RefundLedgerTccApplicationService service;

    public RefundLedgerTccController(RefundLedgerTccApplicationService service) {
        this.service = service;
    }

    /**
     * 执行退款账本 Try、Confirm 或 Cancel。
     *
     * <p>重复调用必须复用全部稳定技术键。Try 仅创建预记账凭证，Confirm 才会过账并写入 Outbox；
     * Cancel 只允许取消未过账凭证。</p>
     */
    @PostMapping("/{action}")
    public ResponseEntity<Result> execute(
            @PathVariable @Pattern(regexp = "^(try|confirm|cancel)$") String action,
            @Valid @RequestBody Request body) {
        RefundLedgerCommand command = new RefundLedgerCommand(body.xid(), body.transactionId(),
                body.merchantAccountId(), body.payerAccountId(), body.creditAccountId(), body.amountFen(),
                body.voucherId(), body.debitEntryId(), body.creditEntryId(), body.eventId(), body.traceId());
        var branch = switch (action) {
            case "try" -> service.tryLedger(command, Instant.now());
            case "confirm" -> service.confirmLedger(command, Instant.now());
            case "cancel" -> service.cancelLedger(command, Instant.now());
            default -> throw new IllegalArgumentException("不支持的退款账本 TCC 动作");
        };
        return ResponseEntity.ok(new Result(branch.getStatus().name()));
    }

    /** 退款账本 TCC 请求，金额单位为分。原付款人余额账户与信用账户二选一指定贷方。 */
    public record Request(@NotBlank @Size(max = 128) String xid,
                          @NotBlank @Size(min = 26, max = 26)
                          @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String transactionId,
                          @NotBlank @Size(min = 26, max = 26)
                          @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String merchantAccountId,
                          @Size(min = 26, max = 26)
                          @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String payerAccountId,
                          @Size(min = 26, max = 26) String creditAccountId,
                          @Positive long amountFen,
                          @NotBlank @Size(min = 26, max = 26)
                          @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String voucherId,
                          @Positive long debitEntryId,
                          @Positive long creditEntryId,
                          @NotBlank @Size(min = 26, max = 26)
                          @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String eventId,
                          @NotBlank @Pattern(regexp = "^[a-fA-F0-9]{32}$") String traceId) {
        /**
         * 拒绝未在内部契约声明的字段，防止调用方伪造资金事实。
         */
        @JsonAnySetter
        void rejectUnknownField(String field, Object ignored) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    /** TCC 分支同步执行结果。 */
    public record Result(String status) { }
}
