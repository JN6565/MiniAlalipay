package com.minialalipay.account.interfaces.tcc;

import com.minialalipay.account.application.credit.CreditRefundTccParticipant;
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
 * 信用支付退款冲正的版本化内部 TCC 接口。
 *
 * <p>仅供业务中心通过服务间鉴权调用，不经前端网关暴露。退款只针对原信用消费全额且尚未还款的冲正，
 * 消费明细、应收与分支屏障在同一账户中心本地事务内变更。</p>
 */
@RestController
@Validated
@RequestMapping("/internal/v1/tcc/credit-refund")
public class CreditRefundTccController {

    private final CreditRefundTccParticipant participant;

    public CreditRefundTccController(CreditRefundTccParticipant participant) {
        this.participant = participant;
    }

    /**
     * 执行信用退款冲正 Try、Confirm 或 Cancel。
     *
     * <p>重复调用必须复用全部稳定技术键。收款方余额冻结与退款账本由其余分支负责。</p>
     */
    @PostMapping("/{action}")
    public ResponseEntity<Result> execute(
            @PathVariable @Pattern(regexp = "^(try|confirm|cancel)$") String action,
            @Valid @RequestBody Request body) {
        var branch = switch (action) {
            case "try" -> participant.tryRefund(body.transactionId(), body.originalTransactionId(),
                    body.merchantAccountId(), body.amountFen(), body.xid(), Instant.now());
            case "confirm" -> participant.confirmRefund(body.transactionId(), body.originalTransactionId(),
                    body.merchantAccountId(), body.amountFen(), body.xid(), Instant.now());
            case "cancel" -> participant.cancelRefund(body.transactionId(), body.originalTransactionId(),
                    body.merchantAccountId(), body.amountFen(), body.xid(), Instant.now());
            default -> throw new IllegalArgumentException("不支持的信用退款 TCC 动作");
        };
        return ResponseEntity.ok(new Result(branch.getStatus().name()));
    }

    /** 信用退款冲正 TCC 请求，金额单位为分。 */
    public record Request(@NotBlank @Size(max = 128) String xid,
                          @NotBlank @Size(min = 26, max = 26)
                          @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String transactionId,
                          @NotBlank @Size(min = 26, max = 26)
                          @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String originalTransactionId,
                          @NotBlank @Size(min = 26, max = 26)
                          @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String merchantAccountId,
                          @Positive long amountFen) { }

    /** TCC 分支同步执行结果。 */
    public record Result(String status) { }
}
