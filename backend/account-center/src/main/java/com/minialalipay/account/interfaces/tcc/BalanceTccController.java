package com.minialalipay.account.interfaces.tcc;

import com.minialalipay.account.application.tcc.BalanceTccApplicationService;
import com.minialalipay.account.application.tcc.BalanceTccApplicationService.TccCommand;
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

/** 仅供业务中心调用的版本化余额 TCC 内部接口，不经前端网关暴露。 */
@RestController
@RequestMapping("/internal/v1/tcc/balance")
public class BalanceTccController {
    private final BalanceTccApplicationService service;
    public BalanceTccController(BalanceTccApplicationService service) { this.service = service; }

    /** 执行付款或收款分支的 Try、Confirm、Cancel；重复同参请求幂等。 */
    @PostMapping("/{role}/{action}")
    public ResponseEntity<BranchResponse> execute(@PathVariable String role, @PathVariable String action,
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
    public record BranchRequest(@NotBlank String xid, @NotBlank String transactionId,
                                @NotBlank String accountId, @Positive long amountFen,
                                @NotBlank String freezeId) { }
    /** 账户 TCC 分支结果。 */
    public record BranchResponse(String status, String rollbackType) { }
}
