package com.minialalipay.user.interfaces.user;

import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import com.minialalipay.common.trace.RequestIdGenerator;
import com.minialalipay.user.application.user.AdminUserService;
import com.minialalipay.user.domain.user.User;
import com.minialalipay.user.domain.user.UserAdminView;
import com.minialalipay.user.domain.user.UserStatus;
import com.minialalipay.user.interfaces.security.AdminAccessGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * B 端用户管理 API。
 *
 * <p>仅系统管理员可访问（由网关角色门禁与 {@link AdminAccessGuard} 双重校验）：
 * <ul>
 *   <li>GET /api/v1/admin/users - 用户只读分页列表（脱敏登录名）</li>
 *   <li>POST /api/v1/admin/users/{userId}/freeze - 管理冻结（ACTIVE -&gt; DISABLED）</li>
 *   <li>POST /api/v1/admin/users/{userId}/unfreeze - 管理解冻（DISABLED -&gt; ACTIVE）</li>
 * </ul>
 * 操作者来自网关注入的可信 {@code X-User-Id}，冻结理由持久化用于审计。接口不修改资金或账户余额。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserService service;
    private final AdminAccessGuard access;
    private final RequestIdGenerator requestIdGenerator;

    /** 构造函数注入依赖。 */
    public AdminUserController(AdminUserService service, AdminAccessGuard access, RequestIdGenerator requestIdGenerator) {
        this.service = service;
        this.access = access;
        this.requestIdGenerator = requestIdGenerator;
    }

    /** 用户只读分页列表；按状态过滤 + 稳定 ID 游标分页，登录名脱敏展示。 */
    @GetMapping
    public ResponseEntity<ApiResponse<AdminUserPage>> list(
            @RequestHeader("X-User-Roles") String roles,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            HttpServletRequest request) {
        access.requireAdmin(roles);
        UserStatus statusEnum = parseStatus(status);
        List<UserAdminView> views = service.list(statusEnum, cursor, limit);
        List<AdminUserResponse> items = views.stream().map(AdminUserResponse::from).toList();
        String nextCursor = items.size() == limit ? views.getLast().user().getUserId() : null;
        return ResponseEntity.ok(success(new AdminUserPage(items, nextCursor), request));
    }

    /** 管理冻结用户；CAS 版本保护，记录操作者与理由。 */
    @PostMapping("/{userId}/freeze")
    public ResponseEntity<ApiResponse<AdminUserResponse>> freeze(
            @RequestHeader("X-User-Id") String operatorId,
            @RequestHeader("X-User-Roles") String roles,
            @PathVariable String userId,
            @Valid @RequestBody FreezeUserRequest body,
            HttpServletRequest request) {
        access.requireAdmin(roles);
        AdminUserService.UserUpdateResult result = service.freeze(userId, body.version(), operatorId, body.reason());
        return ResponseEntity.ok(success(AdminUserResponse.from(result.user(), result.version()), request));
    }

    /** 管理解冻用户；CAS 版本保护，解冻后恢复登录。 */
    @PostMapping("/{userId}/unfreeze")
    public ResponseEntity<ApiResponse<AdminUserResponse>> unfreeze(
            @RequestHeader("X-User-Id") String operatorId,
            @RequestHeader("X-User-Roles") String roles,
            @PathVariable String userId,
            @Valid @RequestBody UnfreezeUserRequest body,
            HttpServletRequest request) {
        access.requireAdmin(roles);
        AdminUserService.UserUpdateResult result = service.unfreeze(userId, body.version(), operatorId);
        return ResponseEntity.ok(success(AdminUserResponse.from(result.user(), result.version()), request));
    }

    private UserStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UserStatus.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    private <T> ApiResponse<T> success(T data, HttpServletRequest request) {
        return ApiResponse.success(data, requestIdGenerator.resolve(request.getHeader("X-Request-Id")),
                request.getHeader("X-Trace-Id"));
    }

    /** 冻结请求：版本号（CAS）与冻结理由必填。 */
    public record FreezeUserRequest(@NotNull @Min(0) long version,
                                    @NotBlank @Size(max = 200) String reason) { }

    /** 解冻请求：仅需版本号（CAS）。 */
    public record UnfreezeUserRequest(@NotNull @Min(0) long version) { }

    /** 用户分页响应；基于稳定 ID 游标。 */
    public record AdminUserPage(List<AdminUserResponse> items, String nextCursor) { }

    /** B 端用户只读 DTO；登录名脱敏，不暴露手机号或任何密码。 */
    public record AdminUserResponse(String userId, String loginNameMasked, String nickname, String status,
                                    Instant loginLockedUntil, String disabledBy, String disabledReason,
                                    long version, Instant createdAt, Instant updatedAt) {

        /** 从列表投影映射；携带登录锁定时间。 */
        static AdminUserResponse from(UserAdminView view) {
            User user = view.user();
            return new AdminUserResponse(user.getUserId(), maskLoginName(user.getAccountNumber()), user.getNickname(),
                    user.getStatus().name(), view.loginLockedUntil(), user.getDisabledBy(), user.getDisabledReason(),
                    user.getVersion(), user.getCreatedAt(), user.getUpdatedAt());
        }

        /** 从冻结/解冻结果映射；版本号为变更后的新版本，登录锁定时间留空由列表刷新。 */
        static AdminUserResponse from(User user, long version) {
            return new AdminUserResponse(user.getUserId(), maskLoginName(user.getAccountNumber()), user.getNickname(),
                    user.getStatus().name(), null, user.getDisabledBy(), user.getDisabledReason(),
                    version, user.getCreatedAt(), user.getUpdatedAt());
        }

        /** 登录名脱敏：16 位账户号/11 位手机号保留前 3 后 4，其余保留首尾各 1。 */
        static String maskLoginName(String value) {
            if (value == null || value.isBlank()) return "";
            if (value.length() >= 8) {
                return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
            }
            if (value.length() >= 4) {
                return value.substring(0, 1) + "***" + value.substring(value.length() - 1);
            }
            return value.substring(0, 1) + "***";
        }
    }
}
