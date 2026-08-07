package com.minialalipay.account.interfaces.account;

import com.minialalipay.account.application.account.AccountApplicationService;
import com.minialalipay.account.application.account.dto.AccountSummaryDTO;
import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.common.trace.RequestIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * 用户注册自动开户内部接口。
 *
 * <p>仅允许用户中心在注册编排和恢复任务中调用，不注册到网关。调用方必须使用用户中心持久化的
 * {@code registrationId}，重复请求返回既有账户、信用账户和账本科目，不得重复开户或初始化额度。</p>
 *
 * @see AccountApplicationService 账户应用服务
 */
@Validated
@RestController
@RequestMapping("/internal/v1/accounts/registrations")
public class AccountOpeningController {

    private final AccountApplicationService accountApplicationService;
    private final RequestIdGenerator requestIdGenerator;

    public AccountOpeningController(AccountApplicationService accountApplicationService,
                                    RequestIdGenerator requestIdGenerator) {
        this.accountApplicationService = accountApplicationService;
        this.requestIdGenerator = requestIdGenerator;
    }

    /**
     * 以注册编号幂等创建余额账户、账本科目、信用账户和信用应收。
     *
     * <p>权限仅限用户中心服务身份。Controller 不持有事务，账户应用服务负责账户体系的本地事务；
     * 相同 registrationId 与相同用户重复调用返回原账户，不同用户复用该编号返回幂等冲突。
     * 参数错误返回 400，幂等绑定冲突返回 409，持久化失败返回统一内部错误。</p>
     *
     * @param registrationId 用户中心生成并持久化的注册幂等编号
     * @param requestDTO  包含用户 ID 的开户请求
     * @param httpRequest HTTP 请求
     * @return 已创建或既有账户的零余额摘要
     */
    @PutMapping("/{registrationId}")
    public ResponseEntity<ApiResponse<AccountSummaryDTO>> openAccount(
            @PathVariable @NotBlank @Size(min = 26, max = 26)
            @Pattern(regexp = "^(REG[A-Z]{9}\\d{14}|[0-9A-HJKMNP-TV-Z]{26})$") String registrationId,
            @Valid @RequestBody OpenAccountRequestDTO requestDTO,
            HttpServletRequest httpRequest
    ) {
        String requestId = requestIdGenerator.resolve(httpRequest.getHeader("X-Request-Id"));
        String traceId = httpRequest.getHeader("X-Trace-Id");

        // 账户 ID 必须由账户中心生成，调用方不能指定或覆盖其他用户的账户。
        String accountId = generateAccountId();

        AccountSummaryDTO result = accountApplicationService.openAccount(
                accountId,
                requestDTO.userId(),
                registrationId,
                Instant.now()
        );

        return ResponseEntity.ok(ApiResponse.success(result, requestId, traceId));
    }

    /**
     * 生成账户 ID。
     *
     * @return 26 位账户 ID
     */
    private String generateAccountId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 26).toUpperCase();
    }

    /**
     * 开户请求 DTO。
     *
     * @param userId         用户 ID
     */
    public record OpenAccountRequestDTO(
            @NotBlank(message = "用户 ID 不能为空")
            @Size(min = 26, max = 26, message = "用户 ID 必须为 26 位")
            @Pattern(regexp = "^(USR[A-Z]{9}\\d{14}|[0-9A-HJKMNP-TV-Z]{26})$", message = "用户 ID 格式不正确")
            String userId
    ) {
    }
}
