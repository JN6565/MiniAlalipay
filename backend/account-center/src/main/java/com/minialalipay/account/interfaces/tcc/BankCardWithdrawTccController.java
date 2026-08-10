package com.minialalipay.account.interfaces.tcc;

import com.minialalipay.account.application.tcc.BankCardBalanceTccApplicationService;
import com.minialalipay.account.application.tcc.BankCardBalanceTccApplicationService.Command;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;

/**
 * 仅供 business-center 调用的银行卡提现 TCC 内部接口，不经前端网关暴露。
 *
 * <p>提现流程（账户给银行卡充钱）：账户余额减少，银行卡余额增加（由 business-center 协调账户侧 TCC）</p>
 */
@RestController
@Validated
@RequestMapping("/internal/v1/tcc/bank-card-withdraw")
public class BankCardWithdrawTccController {

    private final BankCardBalanceTccApplicationService service;

    public BankCardWithdrawTccController(BankCardBalanceTccApplicationService service) {
        this.service = service;
    }

    /** 执行提现 Try、Confirm 或 Cancel，重复请求必须携带同一组业务标识。 */
    @PostMapping("/{action}")
    public ResponseEntity<Result> execute(@PathVariable @Pattern(regexp = "^(try|confirm|cancel)$") String action,
                                          @Valid @RequestBody Request request) {
        Command command = new Command(request.xid(), request.transactionId(),
                request.userId(), request.cardId(), request.amountFen());
        var branch = switch (action) {
            case "try" -> service.tryWithdraw(command, Instant.now());
            case "confirm" -> service.confirmWithdraw(command, Instant.now());
            default -> service.cancelWithdraw(command, Instant.now());
        };
        return ResponseEntity.ok(new Result(branch.getStatus().name()));
    }

    public record Request(@NotBlank @Size(max = 128) String xid,
                          @NotBlank @Size(min = 26, max = 26) String transactionId,
                          @NotBlank @Size(min = 26, max = 26) String userId,
                          @NotBlank @Size(min = 26, max = 26) String cardId,
                          @Positive long amountFen) { }

    public record Result(String status) { }
}
