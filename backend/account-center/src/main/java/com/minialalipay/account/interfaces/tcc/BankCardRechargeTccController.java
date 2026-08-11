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
 * 仅供 business-center 调用的银行卡充值 TCC 内部接口，不经前端网关暴露。
 *
 * <p>充值流程（银行卡给账户充钱）：银行卡余额减少，账户余额增加（由 business-center 协调账户侧 TCC）</p>
 */
@RestController
@Validated
@RequestMapping("/internal/v1/tcc/bank-card-recharge")
public class BankCardRechargeTccController {

    private final BankCardBalanceTccApplicationService service;

    public BankCardRechargeTccController(BankCardBalanceTccApplicationService service) {
        this.service = service;
    }

    /** 执行充值 Try、Confirm 或 Cancel，重复请求必须携带同一组业务标识。 */
    @PostMapping("/{action}")
    public ResponseEntity<Result> execute(@PathVariable @Pattern(regexp = "^(try|confirm|cancel)$") String action,
                                          @Valid @RequestBody Request request) {
        Command command = new Command(request.xid(), request.transactionId(),
                request.userId(), request.cardId(), request.amountFen());
        var branch = switch (action) {
            case "try" -> service.tryRecharge(command, Instant.now());
            case "confirm" -> service.confirmRecharge(command, Instant.now());
            default -> service.cancelRecharge(command, Instant.now());
        };
        return ResponseEntity.ok(new Result(branch.getStatus().name()));
    }

    /**
     * 银行卡充值 TCC 命令参数。
     *
     * <p>xid、transactionId 与 cardId 共同定位幂等屏障，Try/Confirm/Cancel 重试时
     * 必须保持完全一致，否则会被识别为不同请求。金额单位为分。</p>
     *
     * @param xid 业务屏障标识（由交易 ID 派生）
     * @param transactionId 统一交易 ID，26 位
     * @param userId 发起充值用户 ID，用于校验银行卡归属
     * @param cardId 银行卡 ID
     * @param amountFen 充值金额（分），必须为正数
     */
    public record Request(@NotBlank @Size(max = 128) String xid,
                          @NotBlank @Size(min = 26, max = 26) String transactionId,
                          @NotBlank @Size(min = 26, max = 26) String userId,
                          @NotBlank @Size(min = 26, max = 26) String cardId,
                          @Positive long amountFen) { }

    /** TCC 动作执行结果；status 为分支当前状态（INIT/TRIED/CONFIRMED/CANCELLED/MANUAL_REVIEW，空回滚以 CANCELLED 记录）。 */
    public record Result(String status) { }
}
