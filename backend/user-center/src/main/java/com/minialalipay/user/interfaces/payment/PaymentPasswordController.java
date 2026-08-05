package com.minialalipay.user.interfaces.payment;

import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.common.idempotency.IdempotencyKeyValidator;
import com.minialalipay.common.trace.RequestIdGenerator;
import com.minialalipay.user.application.payment.PaymentPasswordService;
import com.minialalipay.user.application.payment.PaymentProofService;
import com.minialalipay.user.interfaces.dto.payment.ChangePaymentPasswordRequestDTO;
import com.minialalipay.user.interfaces.dto.payment.IssuePaymentProofRequestDTO;
import com.minialalipay.user.interfaces.dto.payment.IssuePaymentProofResponseDTO;
import com.minialalipay.user.interfaces.dto.payment.SetPaymentPasswordRequestDTO;
import com.minialalipay.user.interfaces.dto.payment.VerifyPaymentPasswordRequestDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付密码 Controller。
 *
 * <p>实现 P0 接口目录中 user-center 拥有的支付密码端点。
 * 所有接口经网关访问，禁止直连 8081 端口。</p>
 *
 * <p>接口清单：
 * <ul>
 *   <li>PUT /api/v1/payment-password - 设置支付密码</li>
 *   <li>PATCH /api/v1/payment-password - 修改支付密码</li>
 *   <li>POST /api/v1/payment-password/verify - 验证支付密码</li>
 * </ul>
 * </p>
 *
 * <p>请求头规范：
 * <ul>
 *   <li>{@code X-User-Id} - 由网关解析会话后透传</li>
 *   <li>{@code X-Request-Id} - 请求编号，由 {@link RequestIdGenerator} 解析/生成</li>
 *   <li>{@code X-Trace-Id} - 链路编号，用于关联跨服务 Trace</li>
 *   <li>{@code Idempotency-Key} - 幂等键（写接口必须）</li>
 * </ul>
 * </p>
 *
 * @see PaymentPasswordService 支付密码应用服务
 */
@RestController
@RequestMapping("/api/v1/payment-password")
public class PaymentPasswordController {

    private final PaymentPasswordService paymentPasswordService;
    private final PaymentProofService paymentProofService;
    private final IdempotencyKeyValidator idempotencyKeyValidator;
    private final RequestIdGenerator requestIdGenerator;

    /**
     * 构造函数注入依赖。
     *
     * @param paymentPasswordService  支付密码应用服务
     * @param paymentProofService     支付证明应用服务
     * @param idempotencyKeyValidator 幂等键校验器
     * @param requestIdGenerator      请求编号生成器
     */
    public PaymentPasswordController(
            PaymentPasswordService paymentPasswordService,
            PaymentProofService paymentProofService,
            IdempotencyKeyValidator idempotencyKeyValidator,
            RequestIdGenerator requestIdGenerator
    ) {
        this.paymentPasswordService = paymentPasswordService;
        this.paymentProofService = paymentProofService;
        this.idempotencyKeyValidator = idempotencyKeyValidator;
        this.requestIdGenerator = requestIdGenerator;
    }

    /**
     * 设置支付密码。
     *
     * <p>设置流程：
     * <ol>
     *   <li>校验请求参数（使用 {@code @Valid} 注解）</li>
     *   <li>校验幂等键格式</li>
     *   <li>从请求头获取用户 ID</li>
     *   <li>调用 {@link PaymentPasswordService#setPaymentPassword} 完成设置</li>
     *   <li>返回成功响应</li>
     * </ol>
     * </p>
     *
     * <p>业务规则：
     * <ul>
     *   <li>首次设置只允许 {@code payment_password_hash IS NULL}</li>
     *   <li>设置后版本号递增为 1</li>
     *   <li>设置成功后可以签发支付证明</li>
     * </ul>
     * </p>
     *
     * @param requestDTO     设置支付密码请求 DTO
     * @param userId         用户 ID（由网关从会话令牌解析后透传）
     * @param idempotencyKey 幂等键（写接口必须）
     * @param httpRequest    HTTP 请求（用于提取 X-Request-Id 和 X-Trace-Id）
     * @return 成功响应
     */
    @PutMapping
    public ResponseEntity<ApiResponse<Void>> setPaymentPassword(
            @Valid @RequestBody SetPaymentPasswordRequestDTO requestDTO,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest
    ) {
        // 1. 解析请求编号
        String requestId = requestIdGenerator.resolve(httpRequest.getHeader("X-Request-Id"));
        String traceId = httpRequest.getHeader("X-Trace-Id");

        // 2. 校验幂等键格式
        if (!idempotencyKeyValidator.isValid(idempotencyKey)) {
            throw new IllegalArgumentException("幂等键格式不合法");
        }

        // 3. 调用应用服务完成设置
        paymentPasswordService.setPaymentPassword(userId, requestDTO.paymentPassword());

        // 4. 返回成功响应
        return ResponseEntity.ok(ApiResponse.success(null, requestId, traceId));
    }

    /**
     * 修改支付密码。
     *
     * <p>修改流程：
     * <ol>
     *   <li>校验请求参数（使用 {@code @Valid} 注解）</li>
     *   <li>校验幂等键格式</li>
     *   <li>从请求头获取用户 ID</li>
     *   <li>调用 {@link PaymentPasswordService#changePaymentPassword} 完成修改</li>
     *   <li>返回成功响应</li>
     * </ol>
     * </p>
     *
     * <p>业务规则：
     * <ul>
     *   <li>修改前必须验证当前支付密码</li>
     *   <li>修改后版本号递增</li>
     *   <li>修改后立即废弃所有已签发的支付证明</li>
     * </ul>
     * </p>
     *
     * @param requestDTO     修改支付密码请求 DTO
     * @param userId         用户 ID（由网关从会话令牌解析后透传）
     * @param idempotencyKey 幂等键（写接口必须）
     * @param httpRequest    HTTP 请求（用于提取 X-Request-Id 和 X-Trace-Id）
     * @return 成功响应
     */
    @PatchMapping
    public ResponseEntity<ApiResponse<Void>> changePaymentPassword(
            @Valid @RequestBody ChangePaymentPasswordRequestDTO requestDTO,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest
    ) {
        // 1. 解析请求编号
        String requestId = requestIdGenerator.resolve(httpRequest.getHeader("X-Request-Id"));
        String traceId = httpRequest.getHeader("X-Trace-Id");

        // 2. 校验幂等键格式
        if (!idempotencyKeyValidator.isValid(idempotencyKey)) {
            throw new IllegalArgumentException("幂等键格式不合法");
        }

        // 3. 调用应用服务完成修改
        paymentPasswordService.changePaymentPassword(
                userId,
                requestDTO.currentPassword(),
                requestDTO.newPassword()
        );

        // 4. 返回成功响应
        return ResponseEntity.ok(ApiResponse.success(null, requestId, traceId));
    }

    /**
     * 验证支付密码。
     *
     * <p>验证流程：
     * <ol>
     *   <li>校验请求参数（使用 {@code @Valid} 注解）</li>
     *   <li>校验幂等键格式</li>
     *   <li>从请求头获取用户 ID</li>
     *   <li>调用 {@link PaymentPasswordService#verifyPaymentPassword} 完成验证</li>
     *   <li>返回成功响应</li>
     * </ol>
     * </p>
     *
     * <p>业务规则：
     * <ul>
     *   <li>连续失败 5 次后锁定 30 分钟</li>
     *   <li>锁定期间的验证请求直接返回 {@link com.minialalipay.user.domain.auth.UserErrorCode#PAYMENT_LOCKED}</li>
     *   <li>验证成功后重置失败计数</li>
     * </ul>
     * </p>
     *
     * @param requestDTO     验证支付密码请求 DTO
     * @param userId         用户 ID（由网关从会话令牌解析后透传）
     * @param idempotencyKey 幂等键（写接口必须）
     * @param httpRequest    HTTP 请求（用于提取 X-Request-Id 和 X-Trace-Id）
     * @return 成功响应
     */
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<Void>> verifyPaymentPassword(
            @Valid @RequestBody VerifyPaymentPasswordRequestDTO requestDTO,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest
    ) {
        // 1. 解析请求编号
        String requestId = requestIdGenerator.resolve(httpRequest.getHeader("X-Request-Id"));
        String traceId = httpRequest.getHeader("X-Trace-Id");

        // 2. 校验幂等键格式
        if (!idempotencyKeyValidator.isValid(idempotencyKey)) {
            throw new IllegalArgumentException("幂等键格式不合法");
        }

        // 3. 调用应用服务完成验证
        paymentPasswordService.verifyPaymentPassword(userId, requestDTO.paymentPassword());

        // 4. 返回成功响应
        return ResponseEntity.ok(ApiResponse.success(null, requestId, traceId));
    }

    /**
     * 验证支付密码并签发证明。
     *
     * <p>签发流程：
     * <ol>
     *   <li>校验请求参数（使用 {@code @Valid} 注解）</li>
     *   <li>校验幂等键格式</li>
     *   <li>从请求头获取用户 ID</li>
     *   <li>调用 {@link PaymentProofService#verifyAndIssueProof} 完成验证和签发</li>
     *   <li>返回原始令牌</li>
     * </ol>
     * </p>
     *
     * <p>业务规则：
     * <ul>
     *   <li>原始令牌只返回一次，客户端需要保存</li>
     *   <li>证明有效期为 2 分钟</li>
     *   <li>连续失败 5 次后锁定 30 分钟</li>
     * </ul>
     * </p>
     *
     * @param requestDTO     签发证明请求 DTO
     * @param userId         用户 ID（由网关从会话令牌解析后透传）
     * @param idempotencyKey 幂等键（写接口必须）
     * @param httpRequest    HTTP 请求（用于提取 X-Request-Id 和 X-Trace-Id）
     * @return 原始令牌
     */
    @PostMapping("/proof")
    public ResponseEntity<ApiResponse<IssuePaymentProofResponseDTO>> issuePaymentProof(
            @Valid @RequestBody IssuePaymentProofRequestDTO requestDTO,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest
    ) {
        // 1. 解析请求编号
        String requestId = requestIdGenerator.resolve(httpRequest.getHeader("X-Request-Id"));
        String traceId = httpRequest.getHeader("X-Trace-Id");

        // 2. 校验幂等键格式
        if (!idempotencyKeyValidator.isValid(idempotencyKey)) {
            throw new IllegalArgumentException("幂等键格式不合法");
        }

        // 3. 调用应用服务完成验证和签发
        String rawToken = paymentProofService.verifyAndIssueProof(
                userId,
                requestDTO.paymentPassword(),
                requestDTO.purpose()
        );

        // 4. 返回原始令牌
        IssuePaymentProofResponseDTO responseDTO = new IssuePaymentProofResponseDTO(rawToken);
        return ResponseEntity.ok(ApiResponse.success(responseDTO, requestId, traceId));
    }
}
