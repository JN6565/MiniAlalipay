package com.minialalipay.account.interfaces.tcc;

import com.minialalipay.account.application.tcc.BalanceTccApplicationService;
import com.minialalipay.account.application.tcc.BalanceTccApplicationService.TccCommand;
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

/** 仅供业务中心调用的版本化余额 TCC 内部接口，不经前端网关暴露。 */
@RestController
@Validated
@RequestMapping("/internal/v1/tcc/balance")
public class BalanceTccController {
    private final BalanceTccApplicationService service;
    public BalanceTccController(BalanceTccApplicationService service) { this.service = service; }

    /**
     * 执行付款或收款分支的 Try、Confirm、Cancel。
     *
     * <p>仅限业务中心服务身份。应用服务在 account_db 本地事务中持久化屏障、冻结与余额更新；
     * 相同 xid、分支类型和账户 ID 的同载荷请求幂等。非法动作或参数返回 400，账户不存在返回 404，
     * 幂等载荷、状态或并发冲突返回 409。</p>
     */
    @PostMapping("/{role}/{action}")
    public ResponseEntity<BranchResponse> execute(
                                                   @PathVariable @Pattern(regexp = "^(payer|payee)$") String role,
                                                   @PathVariable @Pattern(regexp = "^(try|confirm|cancel)$") String action,
                                                   @Valid @RequestBody BranchRequest request) {
        TccCommand command = new TccCommand(request.xid(), request.transactionId(), request.accountId(),
                request.amountFen(), request.freezeId());
        var branch = switch (role + ":" + action) {
            case "payer:try" -> service.tryPayer(command, Instant.now());
            case "payer:confirm" -> service.confirmPayer(command, Instant.now());
            case "payer:cancel" -> service.cancelPayer(command, Instant.now());
            case "payee:try" -> service.tryPayee(command, Instant.now());
            case "payee:confirm" -> service.confirmPayee(command, Instant.now());
            case "payee:cancel" -> service.cancelPayee(command, Instant.now());
            default -> throw new IllegalArgumentException("不支持的 TCC 分支动作");
        };
        return ResponseEntity.ok(new BranchResponse(branch.getStatus().name(),
                branch.getRollbackType() == null ? null : branch.getRollbackType().name()));
    }

    /** 账户 TCC 内部请求。 */
    public record BranchRequest(@NotBlank @Size(max = 128) String xid,
                                @NotBlank @Size(min = 26, max = 26)
                                @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String transactionId,
                                @NotBlank @Size(min = 26, max = 26)
                                @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String accountId,
                                @Positive long amountFen,
                                @NotBlank @Size(min = 26, max = 26)
                                @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String freezeId) { }
    /** 账户 TCC 分支结果。 */
    public record BranchResponse(String status, String rollbackType) { }
}
