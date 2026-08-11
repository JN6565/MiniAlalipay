package com.minialalipay.account.interfaces.credit;

import com.minialalipay.account.application.credit.CreditQueryService;
import com.minialalipay.account.application.credit.CreditOpeningService;
import com.minialalipay.account.application.credit.CreditRepaymentService;
import com.minialalipay.account.application.credit.dto.CreditBillDetailDTO;
import com.minialalipay.account.application.credit.dto.CreditBillListDTO;
import com.minialalipay.account.application.credit.dto.CreditPurchaseDTO;
import com.minialalipay.account.application.credit.dto.CreditSummaryDTO;
import com.minialalipay.account.application.credit.dto.RepaymentDTO;
import com.minialalipay.account.application.credit.dto.RepaymentDraftDTO;
import com.minialalipay.account.interfaces.credit.dto.CreateRepaymentDraftRequest;
import com.minialalipay.account.interfaces.credit.dto.SubmitRepaymentRequest;
import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.common.idempotency.IdempotencyKeyValidator;
import com.minialalipay.common.trace.RequestIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端信用接口 Controller。
 *
 * <p>实现 P0 接口目录中 account-center 拥有的 7 个 C 端信用端点。
 * 所有接口经网关访问，禁止直连 8083 端口。</p>
 *
 * <p>接口清单：
 * <ul>
 *   <li>GET /api/v1/credit/me — 查询本人额度摘要</li>
 *   <li>GET /api/v1/credit/purchases — 查询消费明细</li>
 *   <li>GET /api/v1/credit/bills — 查询账单列表</li>
 *   <li>GET /api/v1/credit/bills/{id} — 查询账单详情</li>
 *   <li>POST /api/v1/credit/repayment-drafts — 创建还款草稿</li>
 *   <li>POST /api/v1/credit/repayments — 提交还款</li>
 *   <li>GET /api/v1/credit/repayments/{id} — 查询还款状态</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/v1/credit")
public class CreditController {

    private final CreditQueryService creditQueryService;
    private final CreditOpeningService creditOpeningService;
    private final CreditRepaymentService creditRepaymentService;
    private final IdempotencyKeyValidator idempotencyKeyValidator;
    private final RequestIdGenerator requestIdGenerator;

    /**
     * 注入依赖。
     */
    public CreditController(
            CreditQueryService creditQueryService,
            CreditOpeningService creditOpeningService,
            CreditRepaymentService creditRepaymentService,
            IdempotencyKeyValidator idempotencyKeyValidator,
            RequestIdGenerator requestIdGenerator
    ) {
        this.creditQueryService = creditQueryService;
        this.creditOpeningService = creditOpeningService;
        this.creditRepaymentService = creditRepaymentService;
        this.idempotencyKeyValidator = idempotencyKeyValidator;
        this.requestIdGenerator = requestIdGenerator;
    }

    /**
     * 显式开通当前用户的 Mini 花呗。
     *
     * <p>幂等键必填；重复提交只返回同一已开通账户事实，不会重复创建额度、应收或账本科目。</p>
     *
     * @param userId 用户 ID
     * @param idempotencyKey 幂等键
     * @param httpRequest HTTP 请求
     * @return 开通后的额度摘要
     */
    @PostMapping("/open")
    public ResponseEntity<ApiResponse<CreditSummaryDTO>> openCredit(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest
    ) {
        validateIdempotencyKey(idempotencyKey);
        String requestId = requestIdGenerator.resolve(httpRequest.getHeader("X-Request-Id"));
        String traceId = httpRequest.getHeader("X-Trace-Id");
        CreditSummaryDTO data = creditOpeningService.open(userId, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.success(data, requestId, traceId));
    }

    /**
     * 查询本人额度摘要。
     *
     * @param userId 用户 ID（由网关从会话令牌解析后透传）
     * @param httpRequest HTTP 请求
     * @return 额度摘要
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CreditSummaryDTO>> getMyCredit(
            @RequestHeader("X-User-Id") String userId,
            HttpServletRequest httpRequest
    ) {
        String requestId = requestIdGenerator.resolve(httpRequest.getHeader("X-Request-Id"));
        String traceId = httpRequest.getHeader("X-Trace-Id");
        CreditSummaryDTO data = creditQueryService.getMyCredit(userId);
        return ResponseEntity.ok(ApiResponse.success(data, requestId, traceId));
    }

    /**
     * 查询信用消费明细列表。
     *
     * @param userId 用户 ID
     * @param billingStatus 出账状态筛选（可选）
     * @param httpRequest HTTP 请求
     * @return 消费明细列表
     */
    @GetMapping("/purchases")
    public ResponseEntity<ApiResponse<List<CreditPurchaseDTO>>> listCreditPurchases(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(value = "billingStatus", required = false) String billingStatus,
            HttpServletRequest httpRequest
    ) {
        String requestId = requestIdGenerator.resolve(httpRequest.getHeader("X-Request-Id"));
        String traceId = httpRequest.getHeader("X-Trace-Id");
        List<CreditPurchaseDTO> data = creditQueryService.listCreditPurchases(userId, billingStatus);
        return ResponseEntity.ok(ApiResponse.success(data, requestId, traceId));
    }

    /**
     * 查询信用账单列表。
     *
     * @param userId 用户 ID
     * @param httpRequest HTTP 请求
     * @return 账单列表
     */
    @GetMapping("/bills")
    public ResponseEntity<ApiResponse<List<CreditBillListDTO>>> listCreditBills(
            @RequestHeader("X-User-Id") String userId,
            HttpServletRequest httpRequest
    ) {
        String requestId = requestIdGenerator.resolve(httpRequest.getHeader("X-Request-Id"));
        String traceId = httpRequest.getHeader("X-Trace-Id");
        List<CreditBillListDTO> data = creditQueryService.listCreditBills(userId);
        return ResponseEntity.ok(ApiResponse.success(data, requestId, traceId));
    }

    /**
     * 查询账单详情（含明细）。
     *
     * @param userId 用户 ID
     * @param billId 账单 ID
     * @param httpRequest HTTP 请求
     * @return 账单详情
     */
    @GetMapping("/bills/{id}")
    public ResponseEntity<ApiResponse<CreditBillDetailDTO>> getCreditBill(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable("id") String billId,
            HttpServletRequest httpRequest
    ) {
        String requestId = requestIdGenerator.resolve(httpRequest.getHeader("X-Request-Id"));
        String traceId = httpRequest.getHeader("X-Trace-Id");
        CreditBillDetailDTO data = creditQueryService.getCreditBill(userId, billId);
        return ResponseEntity.ok(ApiResponse.success(data, requestId, traceId));
    }

    /**
     * 创建还款草稿和分配预览。
     *
     * <p>幂等键 Idempotency-Key 必填。</p>
     *
     * @param userId 用户 ID
     * @param request 创建草稿请求
     * @param idempotencyKey 幂等键
     * @param httpRequest HTTP 请求
     * @return 还款草稿及分配预览
     */
    @PostMapping("/repayment-drafts")
    public ResponseEntity<ApiResponse<RepaymentDraftDTO>> createCreditRepaymentDraft(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody CreateRepaymentDraftRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest
    ) {
        validateIdempotencyKey(idempotencyKey);
        String requestId = requestIdGenerator.resolve(httpRequest.getHeader("X-Request-Id"));
        String traceId = httpRequest.getHeader("X-Trace-Id");
        RepaymentDraftDTO data = creditRepaymentService.createRepaymentDraft(
                userId, request.amountFen(), idempotencyKey);
        return ResponseEntity.ok(ApiResponse.success(data, requestId, traceId));
    }

    /**
     * 提交还款。
     *
     * <p>幂等键 Idempotency-Key 必填。
     * 支付密码证明不得写入日志、URL 或浏览器存储。</p>
     *
     * @param userId 用户 ID
     * @param request 提交还款请求
     * @param idempotencyKey 幂等键
     * @param httpRequest HTTP 请求
     * @return 还款记录
     */
    @PostMapping("/repayments")
    public ResponseEntity<ApiResponse<RepaymentDTO>> submitCreditRepayment(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody SubmitRepaymentRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest
    ) {
        validateIdempotencyKey(idempotencyKey);
        String requestId = requestIdGenerator.resolve(httpRequest.getHeader("X-Request-Id"));
        String traceId = httpRequest.getHeader("X-Trace-Id");
        RepaymentDTO data = creditRepaymentService.submitRepayment(
                userId, request.repaymentDraftId(), request.paymentProofToken(), idempotencyKey);
        return ResponseEntity.ok(ApiResponse.success(data, requestId, traceId));
    }

    /**
     * 查询还款状态。
     *
     * @param userId 用户 ID
     * @param repaymentId 还款 ID
     * @param httpRequest HTTP 请求
     * @return 还款记录
     */
    @GetMapping("/repayments/{id}")
    public ResponseEntity<ApiResponse<RepaymentDTO>> getCreditRepayment(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable("id") String repaymentId,
            HttpServletRequest httpRequest
    ) {
        String requestId = requestIdGenerator.resolve(httpRequest.getHeader("X-Request-Id"));
        String traceId = httpRequest.getHeader("X-Trace-Id");
        RepaymentDTO data = creditRepaymentService.getRepayment(userId, repaymentId);
        return ResponseEntity.ok(ApiResponse.success(data, requestId, traceId));
    }

    /**
     * 校验幂等键格式。
     */
    private void validateIdempotencyKey(String key) {
        if (!idempotencyKeyValidator.isValid(key)) {
            throw new com.minialalipay.common.error.BusinessException(
                    com.minialalipay.common.error.CommonErrorCode.INVALID_REQUEST);
        }
    }
}
