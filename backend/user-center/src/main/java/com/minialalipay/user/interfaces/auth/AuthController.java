package com.minialalipay.user.interfaces.auth;

import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.common.idempotency.IdempotencyKeyValidator;
import com.minialalipay.common.trace.RequestIdGenerator;
import com.minialalipay.user.application.auth.AuthService;
import com.minialalipay.user.application.auth.dto.AuthResult;
import com.minialalipay.user.application.auth.dto.CurrentIdentity;
import com.minialalipay.user.application.auth.dto.LoginRequest;
import com.minialalipay.user.application.auth.dto.RegisterRequest;
import com.minialalipay.user.interfaces.dto.auth.AuthResponseDTO;
import com.minialalipay.user.interfaces.dto.auth.ChangeLoginPasswordRequestDTO;
import com.minialalipay.user.interfaces.dto.auth.CurrentIdentityResponseDTO;
import com.minialalipay.user.interfaces.dto.auth.LoginRequestDTO;
import com.minialalipay.user.interfaces.dto.auth.RegisterRequestDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证 Controller。
 *
 * <p>实现 P0 接口目录中 user-center 拥有的认证端点。
 * 所有接口经网关访问，禁止直连 8081 端口。</p>
 *
 * <p>接口清单：
 * <ul>
 *   <li>POST /api/v1/auth/register - 用户注册</li>
 *   <li>POST /api/v1/auth/login - 用户登录</li>
 *   <li>POST /api/v1/auth/logout - 用户退出</li>
 * </ul>
 * </p>
 *
 * <p>请求头规范：
 * <ul>
 *   <li>{@code X-User-Id} - 由网关解析会话后透传（登录接口不需要）</li>
 *   <li>{@code X-Request-Id} - 请求编号，由 {@link RequestIdGenerator} 解析/生成</li>
 *   <li>{@code X-Trace-Id} - 链路编号，用于关联跨服务 Trace</li>
 *   <li>{@code Idempotency-Key} - 幂等键（写接口必须）</li>
 * </ul>
 * </p>
 *
 * <p>响应格式：
 * <ul>
 *   <li>成功：{@link ApiResponse#success(data, requestId, traceId)}</li>
 *   <li>失败：异常由 {@link com.minialalipay.user.interfaces.error.UserCenterExceptionHandler} 统一处理</li>
 * </ul>
 * </p>
 *
 * @see AuthService 认证应用服务
 * @see RegisterRequestDTO 注册请求 DTO（接口层）
 * @see LoginRequestDTO 登录请求 DTO（接口层）
 * @see AuthResponseDTO 认证响应 DTO（接口层）
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final IdempotencyKeyValidator idempotencyKeyValidator;
    private final RequestIdGenerator requestIdGenerator;

    /**
     * 构造函数注入依赖。
     *
     * @param authService            认证应用服务
     * @param idempotencyKeyValidator 幂等键校验器
     * @param requestIdGenerator     请求编号生成器
     */
    public AuthController(
            AuthService authService,
            IdempotencyKeyValidator idempotencyKeyValidator,
            RequestIdGenerator requestIdGenerator
    ) {
        this.authService = authService;
        this.idempotencyKeyValidator = idempotencyKeyValidator;
        this.requestIdGenerator = requestIdGenerator;
    }

    /**
     * 用户注册。
     *
     * <p>注册流程：
     * <ol>
     *   <li>校验请求参数（使用 {@code @Valid} 注解）</li>
     *   <li>校验幂等键格式</li>
     *   <li>调用 {@link AuthService#register} 完成注册</li>
     *   <li>返回认证结果</li>
     * </ol>
     * </p>
     *
     * <p>业务规则：
     * <ul>
     *   <li>登录名唯一，规范化存储（转小写、去空格）</li>
     *   <li>登录密码使用 BCrypt 强哈希</li>
     *   <li>注册时状态为 PROVISIONING，需要等账户中心开户完成后才能变为 ACTIVE</li>
     *   <li>初始余额为 0（由账户中心负责）</li>
     * </ul>
     * </p>
     *
     * @param requestDTO     注册请求 DTO
     * @param idempotencyKey 幂等键（写接口必须）
     * @param httpRequest    HTTP 请求（用于提取 X-Request-Id 和 X-Trace-Id）
     * @return 认证结果
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponseDTO>> register(
            @Valid @RequestBody RegisterRequestDTO requestDTO,
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

        // 3. 调用应用服务完成注册
        RegisterRequest request = new RegisterRequest(
                requestDTO.phoneNumber(),
                requestDTO.realName(),
                requestDTO.nickname(),
                requestDTO.loginPassword(),
                requestDTO.paymentPassword()
        );
        AuthResult result = authService.register(request);

        // 4. 转换为接口层 DTO
        AuthResponseDTO responseDTO = new AuthResponseDTO(
                result.accessToken(),
                result.userId(),
                result.accountNumber(),
                result.nickname(),
                result.status()
        );

        // 5. 返回认证结果
        return ResponseEntity.ok(ApiResponse.success(responseDTO, requestId, traceId));
    }

    /**
     * 用户登录。
     *
     * <p>登录流程：
     * <ol>
     *   <li>校验请求参数（使用 {@code @Valid} 注解）</li>
     *   <li>调用 {@link AuthService#login} 完成登录</li>
     *   <li>返回认证结果</li>
     * </ol>
     * </p>
     *
     * <p>业务规则：
     * <ul>
     *   <li>校验用户状态（不能是 DISABLED 或 PROVISIONING）</li>
     *   <li>校验登录密码哈希</li>
     *   <li>连续失败 5 次后锁定 30 分钟</li>
     *   <li>登录成功后创建会话</li>
     * </ul>
     * </p>
     *
     * @param requestDTO  登录请求 DTO
     * @param httpRequest HTTP 请求（用于提取 X-Request-Id 和 X-Trace-Id）
     * @return 认证结果
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponseDTO>> login(
            @Valid @RequestBody LoginRequestDTO requestDTO,
            HttpServletRequest httpRequest
    ) {
        // 1. 解析请求编号
        String requestId = requestIdGenerator.resolve(httpRequest.getHeader("X-Request-Id"));
        String traceId = httpRequest.getHeader("X-Trace-Id");

        // 2. 调用应用服务完成登录
        LoginRequest request = new LoginRequest(
                requestDTO.loginIdentifier(),
                requestDTO.loginPassword()
        );
        AuthResult result = authService.login(request);

        // 3. 转换为接口层 DTO
        AuthResponseDTO responseDTO = new AuthResponseDTO(
                result.accessToken(),
                result.userId(),
                result.accountNumber(),
                result.nickname(),
                result.status()
        );

        // 4. 返回认证结果
        return ResponseEntity.ok(ApiResponse.success(responseDTO, requestId, traceId));
    }

    /**
     * 用户退出登录。
     *
     * <p>退出流程：
     * <ol>
     *   <li>从请求头获取会话令牌</li>
     *   <li>调用 {@link AuthService#logout} 销毁会话</li>
     *   <li>返回成功响应</li>
     * </ol>
     * </p>
     *
     * @param authorization 授权头（包含会话令牌）
     * @param httpRequest   HTTP 请求（用于提取 X-Request-Id 和 X-Trace-Id）
     * @return 成功响应
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader("Authorization") String authorization,
            HttpServletRequest httpRequest
    ) {
        // 1. 解析请求编号
        String requestId = requestIdGenerator.resolve(httpRequest.getHeader("X-Request-Id"));
        String traceId = httpRequest.getHeader("X-Trace-Id");

        // 2. 提取会话令牌（去掉 "Bearer " 前缀）
        String token = authorization.startsWith("Bearer ") ?
                authorization.substring(7) : authorization;

        // 3. 调用应用服务销毁会话
        authService.logout(token);

        // 4. 返回成功响应
        return ResponseEntity.ok(ApiResponse.success(null, requestId, traceId));
    }

    /**
     * 修改当前用户的登录密码。请求必须经过网关认证；成功后当前会话立即失效。
     */
    /**
     * 查询当前身份（展示名 + 角色）。
     *
     * <p>B 端登录后调用，用网关注入的可信 {@code X-User-Id} 换取当前身份与角色集合，
     * 供前端权限模型填充。普通用户无角色授权时返回默认 {@code USER}。</p>
     *
     * @param userId      网关注入的可信用户 ID
     * @param httpRequest HTTP 请求（用于提取 X-Request-Id 和 X-Trace-Id）
     * @return 当前身份
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CurrentIdentityResponseDTO>> me(
            @RequestHeader("X-User-Id") String userId,
            HttpServletRequest httpRequest
    ) {
        String requestId = requestIdGenerator.resolve(httpRequest.getHeader("X-Request-Id"));
        String traceId = httpRequest.getHeader("X-Trace-Id");
        CurrentIdentity identity = authService.currentIdentity(userId);
        return ResponseEntity.ok(ApiResponse.success(
                new CurrentIdentityResponseDTO(identity.userId(), identity.displayName(), identity.roles()),
                requestId, traceId));
    }

    @PatchMapping("/login-password")
    public ResponseEntity<ApiResponse<Void>> changeLoginPassword(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody ChangeLoginPasswordRequestDTO requestDTO,
            HttpServletRequest httpRequest
    ) {
        String token = authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
        authService.changeLoginPassword(
                userId, token, requestDTO.currentPassword(), requestDTO.newPassword());
        String requestId = requestIdGenerator.resolve(httpRequest.getHeader("X-Request-Id"));
        return ResponseEntity.ok(ApiResponse.success(null, requestId, httpRequest.getHeader("X-Trace-Id")));
    }
}
