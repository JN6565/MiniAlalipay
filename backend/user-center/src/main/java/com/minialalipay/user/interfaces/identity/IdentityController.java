package com.minialalipay.user.interfaces.identity;

import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.common.trace.RequestIdGenerator;
import com.minialalipay.user.application.identity.IdentityApplicationService;
import com.minialalipay.user.interfaces.dto.identity.BindIdentityRequest;
import com.minialalipay.user.interfaces.dto.identity.IdentityDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端身份绑定接口：绑定身份信息（真实姓名 + 身份证号）和查询身份状态。
 *
 * <p>绑定身份后才能进行银行卡绑定操作，绑卡时 account-center 会通过内部接口
 * 校验用户三要素与存储信息是否完全匹配。</p>
 */
@RestController
@RequestMapping("/api/v1/identity")
public class IdentityController {

    private final IdentityApplicationService identityApplicationService;
    private final RequestIdGenerator requestIdGenerator;

    public IdentityController(IdentityApplicationService identityApplicationService,
                              RequestIdGenerator requestIdGenerator) {
        this.identityApplicationService = identityApplicationService;
        this.requestIdGenerator = requestIdGenerator;
    }

    /**
     * 绑定身份信息：设置真实姓名和身份证号。
     *
     * @param userId 网关从会话解析的用户 ID
     * @param body 绑定身份请求（真实姓名 + 身份证号）
     * @param request HTTP 请求上下文
     * @return 绑定后的身份信息
     */
    @PostMapping("/bind")
    public ResponseEntity<ApiResponse<IdentityDTO>> bindIdentity(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody BindIdentityRequest body,
            HttpServletRequest request) {
        IdentityDTO data = identityApplicationService.bindIdentity(
                userId, body.realName(), body.idCard());
        return ResponseEntity.ok(ApiResponse.success(data,
                requestIdGenerator.resolve(request.getHeader("X-Request-Id")),
                request.getHeader("X-Trace-Id")));
    }

    /**
     * 查询当前用户身份绑定状态。
     *
     * @param userId 网关从会话解析的用户 ID
     * @param request HTTP 请求上下文
     * @return 身份信息
     */
    @GetMapping
    public ResponseEntity<ApiResponse<IdentityDTO>> getIdentity(
            @RequestHeader("X-User-Id") String userId,
            HttpServletRequest request) {
        IdentityDTO data = identityApplicationService.getIdentity(userId);
        return ResponseEntity.ok(ApiResponse.success(data,
                requestIdGenerator.resolve(request.getHeader("X-Request-Id")),
                request.getHeader("X-Trace-Id")));
    }
}
