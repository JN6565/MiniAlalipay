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

    /**
     * 充值 TCC 命令参数。
     *
     * <p>除屏障标识外还携带复式账本凭证与分录的稳定标识，
     * Try/Confirm/Cancel 重试时必须保持完全一致，否则会被识别为不同请求。金额单位为分。</p>
     *
     * @param xid 业务屏障标识（由交易 ID 派生）
     * @param transactionId 统一交易 ID，26 位
     * @param targetAccountId 充值入账的目标账户 ID，26 位
     * @param amountFen 充值金额（分），必须为正数
     * @param voucherId 复式账本凭证 ID，26 位
     * @param debitEntryId 借方分录稳定标识
     * @param creditEntryId 贷方分录稳定标识
     * @param eventId 账本事件幂等标识，26 位
     * @param traceId 链路追踪标识，32 位十六进制
     */
    public record Request(@NotBlank @Size(max = 128) String xid,
                          @NotBlank @Size(min = 26, max = 26) String transactionId,
                          @NotBlank @Size(min = 26, max = 26) String targetAccountId,
                          @Positive long amountFen,
                          @NotBlank @Size(min = 26, max = 26) String voucherId,
                          @Positive long debitEntryId, @Positive long creditEntryId,
                          @NotBlank @Size(min = 26, max = 26) String eventId,
                          @NotBlank @Pattern(regexp = "^[a-fA-F0-9]{32}$") String traceId) { }
    /** TCC 动作执行结果；status 为分支当前状态（INIT/TRIED/CONFIRMED/CANCELLED/MANUAL_REVIEW，空回滚以 CANCELLED 记录）。 */
    public record Result(String status) { }
}
