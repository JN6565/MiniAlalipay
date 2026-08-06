package com.minialalipay.user.interfaces.auth;

import com.minialalipay.user.application.auth.AuthService;
import com.minialalipay.user.interfaces.dto.auth.SessionIntrospectionRequestDTO;
import com.minialalipay.user.interfaces.dto.auth.SessionIntrospectionResponseDTO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/** 仅供网关直连的内部会话校验接口，不经过前端网关公开路由。 */
@RestController
@RequestMapping("/internal/v1/auth")
public class InternalSessionController {
    private final AuthService authService;
    private final String serviceToken;

    public InternalSessionController(AuthService authService,
                                     @Value("${internal.auth.service-token:local-internal-token}") String serviceToken) {
        this.authService = authService;
        this.serviceToken = serviceToken;
    }

    /** 校验服务身份和用户会话；无效会话返回 active=false，避免泄露额外信息。 */
    @PostMapping("/sessions/introspect")
    public ResponseEntity<SessionIntrospectionResponseDTO> introspect(
            @RequestHeader("X-Internal-Service-Token") String suppliedServiceToken,
            @Valid @RequestBody SessionIntrospectionRequestDTO request) {
        if (!serviceToken.equals(suppliedServiceToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String userId = authService.validateSession(request.accessToken());
        return userId == null
                ? ResponseEntity.ok(new SessionIntrospectionResponseDTO(false, null, Set.of()))
                : ResponseEntity.ok(new SessionIntrospectionResponseDTO(true, userId, Set.of("USER")));
    }
}
