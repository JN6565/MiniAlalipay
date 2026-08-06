package com.minialalipay.account.interfaces.tcc;

import com.minialalipay.account.application.tcc.RechargeTccApplicationService;
import com.minialalipay.account.application.tcc.RechargeTccApplicationService.Command;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;

/** 仅供 business-center 调用的充值 TCC 内部接口，不经前端网关暴露。 */
@RestController
@Validated
@RequestMapping("/internal/v1/tcc/recharge")
public class RechargeTccController {
    private final RechargeTccApplicationService service;
    public RechargeTccController(RechargeTccApplicationService service) { this.service = service; }

    /** 执行充值 Try、Confirm 或 Cancel，重复请求必须携带同一组业务标识。 */
    @PostMapping("/{action}")
    public ResponseEntity<Result> execute(@PathVariable @Pattern(regexp = "^(try|confirm|cancel)$") String action,
                                          @Valid @RequestBody Request request) {
        Command command = new Command(request.xid(), request.transactionId(), request.targetAccountId(), request.amountFen(),
                request.voucherId(), request.debitEntryId(), request.creditEntryId(), request.eventId(), request.traceId());
        var branch = switch (action) {
            case "try" -> service.tryRecharge(command, Instant.now());
            case "confirm" -> service.confirmRecharge(command, Instant.now());
            default -> service.cancelRecharge(command, Instant.now());
        };
        return ResponseEntity.ok(new Result(branch.getStatus().name()));
    }

    public record Request(@NotBlank @Size(max = 128) String xid,
                          @NotBlank @Size(min = 26, max = 26) String transactionId,
                          @NotBlank @Size(min = 26, max = 26) String targetAccountId,
                          @Positive long amountFen,
                          @NotBlank @Size(min = 26, max = 26) String voucherId,
                          @Positive long debitEntryId, @Positive long creditEntryId,
                          @NotBlank @Size(min = 26, max = 26) String eventId,
                          @NotBlank @Pattern(regexp = "^[a-fA-F0-9]{32}$") String traceId) { }
    public record Result(String status) { }
}
