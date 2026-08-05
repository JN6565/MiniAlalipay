package com.minialalipay.account.interfaces.account;

import com.minialalipay.account.application.account.AccountApplicationService;
import com.minialalipay.account.application.account.dto.AccountSummaryDTO;
import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.common.trace.RequestIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * 账户开户接口。
 *
 * <p>提供用户注册时的账户开户能力，由用户中心调用。
 * 开户成功后返回账户摘要，包含账户 ID 和初始余额（0）。</p>
 *
 * <p>接口清单：
 * <ul>
 *   <li>POST /api/v1/accounts/open - 开户</li>
 * </ul>
 * </p>
 *
 * @see AccountApplicationService 账户应用服务
 */
@Validated
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountOpeningController {

    private final AccountApplicationService accountApplicationService;
    private final RequestIdGenerator requestIdGenerator;

    public AccountOpeningController(AccountApplicationService accountApplicationService,
                                    RequestIdGenerator requestIdGenerator) {
        this.accountApplicationService = accountApplicationService;
        this.requestIdGenerator = requestIdGenerator;
    }

    /**
     * 用户开户。
     *
     * <p>开户流程：
     * <ol>
     *   <li>校验请求参数</li>
     *   <li>调用账户应用服务完成开户</li>
     *   <li>返回账户摘要</li>
     * </ol>
     * </p>
     *
     * <p>业务规则：
     * <ul>
     *   <li>使用 registrationId 作为幂等键，重复调用返回已有账户</li>
     *   <li>初始余额为 0</li>
     *   <li>同时创建账本科目</li>
     * </ul>
     * </p>
     *
     * @param requestDTO  开户请求 DTO
     * @param httpRequest HTTP 请求
     * @return 账户摘要
     */
    @PostMapping("/open")
    public ResponseEntity<ApiResponse<AccountSummaryDTO>> openAccount(
            @RequestBody OpenAccountRequestDTO requestDTO,
            HttpServletRequest httpRequest
    ) {
        String requestId = requestIdGenerator.resolve(httpRequest.getHeader("X-Request-Id"));
        String traceId = httpRequest.getHeader("X-Trace-Id");

        // 生成账户 ID
        String accountId = generateAccountId();

        AccountSummaryDTO result = accountApplicationService.openAccount(
                accountId,
                requestDTO.userId(),
                requestDTO.registrationId(),
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
     * @param registrationId 注册幂等键
     */
    public record OpenAccountRequestDTO(
            @NotBlank(message = "用户 ID 不能为空")
            String userId,

            @NotBlank(message = "注册幂等键不能为空")
            String registrationId
    ) {
    }
}
