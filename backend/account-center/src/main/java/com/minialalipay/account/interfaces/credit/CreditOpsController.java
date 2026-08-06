package com.minialalipay.account.interfaces.credit;

import com.minialalipay.account.application.credit.CreditJobService;
import com.minialalipay.account.application.credit.dto.CreditJobRunDTO;
import com.minialalipay.account.interfaces.credit.dto.RunCreditJobRequest;
import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import com.minialalipay.common.idempotency.IdempotencyKeyValidator;
import com.minialalipay.common.trace.RequestIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

/**
 * B 端信用运维接口 Controller。
 *
 * <p>实现 P0 接口目录中 account-center 拥有的 2 个 B 端运维端点。
 * 需要管理员权限和审计日志。</p>
 *
 * <p>接口清单：
 * <ul>
 *   <li>POST /api/v1/ops/credit/statement-runs — 触发出账任务</li>
 *   <li>POST /api/v1/ops/credit/due-check-runs — 触发到期检查任务</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/v1/ops/credit")
public class CreditOpsController {

    private final CreditJobService creditJobService;
    private final IdempotencyKeyValidator idempotencyKeyValidator;
    private final RequestIdGenerator requestIdGenerator;

    /**
     * 注入依赖。
     */
    public CreditOpsController(
            CreditJobService creditJobService,
            IdempotencyKeyValidator idempotencyKeyValidator,
            RequestIdGenerator requestIdGenerator
    ) {
        this.creditJobService = creditJobService;
        this.idempotencyKeyValidator = idempotencyKeyValidator;
        this.requestIdGenerator = requestIdGenerator;
    }

    /**
     * 触发月度出账任务。
     *
     * <p>幂等：以 (jobType=STATEMENT, businessDate) 为唯一键。
     * 重复触发同一业务日期的任务不会重复建账单。</p>
     *
     * @param operatorUserId 操作人用户 ID（由网关从会话令牌解析后透传）
     * @param request 触发请求
     * @param idempotencyKey 幂等键
     * @param httpRequest HTTP 请求
     * @return 任务运行记录
     */
    @PostMapping("/statement-runs")
    public ResponseEntity<ApiResponse<CreditJobRunDTO>> runCreditStatement(
            @RequestHeader("X-User-Id") String operatorUserId,
            @RequestHeader("X-User-Roles") String trustedRolesHeader,
            @Valid @RequestBody RunCreditJobRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest
    ) {
        requireAdmin(trustedRolesHeader);
        validateIdempotencyKey(idempotencyKey);
        String requestId = requestIdGenerator.resolve(httpRequest.getHeader("X-Request-Id"));
        String traceId = httpRequest.getHeader("X-Trace-Id");
        CreditJobRunDTO data = creditJobService.runStatement(operatorUserId, request.businessDate());
        return ResponseEntity.ok(ApiResponse.success(data, requestId, traceId));
    }

    /**
     * 触发到期检查任务。
     *
     * <p>幂等：以 (jobType=DUE_CHECK, businessDate) 为唯一键。
     * 重复触发同一业务日期的任务不会重复标记逾期。</p>
     *
     * @param operatorUserId 操作人用户 ID
     * @param request 触发请求
     * @param idempotencyKey 幂等键
     * @param httpRequest HTTP 请求
     * @return 任务运行记录
     */
    @PostMapping("/due-check-runs")
    public ResponseEntity<ApiResponse<CreditJobRunDTO>> runCreditDueCheck(
            @RequestHeader("X-User-Id") String operatorUserId,
            @RequestHeader("X-User-Roles") String trustedRolesHeader,
            @Valid @RequestBody RunCreditJobRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest
    ) {
        requireAdmin(trustedRolesHeader);
        validateIdempotencyKey(idempotencyKey);
        String requestId = requestIdGenerator.resolve(httpRequest.getHeader("X-Request-Id"));
        String traceId = httpRequest.getHeader("X-Trace-Id");
        CreditJobRunDTO data = creditJobService.runDueCheck(operatorUserId, request.businessDate());
        return ResponseEntity.ok(ApiResponse.success(data, requestId, traceId));
    }

    /**
     * 校验管理员权限：信用运维任务（出账/到期检查）只允许系统管理员触发（系统分析 16.7）。
     * 角色只读取网关清洗并注入的 {@code X-User-Roles}，不接受请求体或查询参数中的角色。
     */
    private void requireAdmin(String trustedRolesHeader) {
        boolean isAdmin = trustedRolesHeader != null
                && Arrays.stream(trustedRolesHeader.split(","))
                        .map(String::trim)
                        .anyMatch(role -> "ADMIN".equals(role));
        if (!isAdmin) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }

    /**
     * 校验幂等键格式。
     */
    private void validateIdempotencyKey(String key) {
        if (!idempotencyKeyValidator.isValid(key)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
    }
}
