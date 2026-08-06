package com.minialalipay.account.interfaces.tcc;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.minialalipay.account.application.tcc.CreditPayLedgerTccApplicationService;
import com.minialalipay.account.application.tcc.CreditPayLedgerTccApplicationService.CreditPayLedgerCommand;
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
 * 信用支付专用账本 TCC 的内部接口。
 *
 * <p>仅供业务中心服务身份调用，不经网关暴露。请求不包含付款余额账户，账户中心始终从
 * {@code creditAccountId} 派生信用应收资产科目，从 {@code payeeAccountId} 派生收款用户余额负债科目。</p>
 */
@RestController
@Validated
@RequestMapping("/internal/v1/tcc/credit-ledger")
public class CreditPayLedgerTccController {

    private final CreditPayLedgerTccApplicationService service;

    public CreditPayLedgerTccController(CreditPayLedgerTccApplicationService service) {
        this.service = service;
    }

    /**
     * 执行信用支付账本 Try、Confirm 或 Cancel。
     *
     * <p>重复调用必须复用全部稳定技术键。Try 仅创建预记账凭证，Confirm 才会过账并写入 Outbox；
     * Cancel 只允许取消未过账凭证，已确认交易必须走受控冲正。</p>
     */
    @PostMapping("/{action}")
    public ResponseEntity<Result> execute(
            @PathVariable @Pattern(regexp = "^(try|confirm|cancel)$") String action,
            @Valid @RequestBody Request body) {
        CreditPayLedgerCommand command = new CreditPayLedgerCommand(body.xid(), body.transactionId(),
                body.creditAccountId(), body.payeeAccountId(), body.amountFen(), body.voucherId(),
                body.debitEntryId(), body.creditEntryId(), body.eventId(), body.traceId());
        var branch = switch (action) {
            case "try" -> service.tryLedger(command, Instant.now());
            case "confirm" -> service.confirmLedger(command, Instant.now());
            case "cancel" -> service.cancelLedger(command, Instant.now());
            default -> throw new IllegalArgumentException("不支持的信用支付账本 TCC 动作");
        };
        return ResponseEntity.ok(new Result(branch.getStatus().name()));
    }

    /** 信用支付账本 TCC 请求，金额单位为分。 */
    public record Request(@NotBlank @Size(max = 128) String xid,
                          @NotBlank @Size(min = 26, max = 26)
                          @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String transactionId,
                          @NotBlank @Size(min = 26, max = 26)
                          String creditAccountId,
                          @NotBlank @Size(min = 26, max = 26)
                          @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String payeeAccountId,
                          @Positive long amountFen,
                          @NotBlank @Size(min = 26, max = 26)
                          @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String voucherId,
                          @Positive long debitEntryId,
                          @Positive long creditEntryId,
                          @NotBlank @Size(min = 26, max = 26)
                          @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String eventId,
                          @NotBlank @Pattern(regexp = "^[a-fA-F0-9]{32}$") String traceId) {
        /**
         * 拒绝未在内部契约声明的字段，防止调用方伪造付款余额账户等资金事实。
         *
         * <p>OpenAPI 的 {@code additionalProperties: false} 必须由运行时同步执行，不能只停留在文档层。</p>
         */
        @JsonAnySetter
        void rejectUnknownField(String field, Object ignored) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    /** TCC 分支同步执行结果。 */
    public record Result(String status) {
    }
}
