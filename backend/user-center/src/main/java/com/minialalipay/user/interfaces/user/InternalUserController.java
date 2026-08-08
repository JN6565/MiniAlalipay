package com.minialalipay.user.interfaces.user;

import com.minialalipay.user.application.user.UserQueryService;
import com.minialalipay.user.application.user.PhoneMasker;
import com.minialalipay.user.domain.user.User;
import com.minialalipay.user.domain.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * 内部用户信息接口。
 *
 * <p>仅供其他微服务内部调用，不经过前端网关公开路由。</p>
 */
@RestController
@RequestMapping("/internal/v1/users")
public class InternalUserController {

    private final UserRepository userRepository;
    private final String serviceToken;

    public InternalUserController(
            UserRepository userRepository,
            @Value("${internal.auth.service-token:local-internal-token}") String serviceToken
    ) {
        this.userRepository = userRepository;
        this.serviceToken = serviceToken;
    }

    /**
     * 根据用户 ID 获取用户基本信息。
     *
     * @param userId         用户 ID
     * @param serviceToken   服务间认证令牌
     * @return 用户基本信息（realName, nickname, accountNumber）
     */
    @GetMapping("/{userId}")
    public ResponseEntity<Map<String, String>> getUserInfo(
            @PathVariable String userId,
            @RequestHeader("X-Internal-Service-Token") String suppliedServiceToken) {
        if (!serviceToken.equals(suppliedServiceToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Optional<User> user = userRepository.findById(userId);
        if (user.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        User u = user.get();
        // accountNumber 供转账展示页脱敏后展示，与 realName 属同级内部展示字段
        // maskedPhone 供账本分录展示交易对方信息
        return ResponseEntity.ok(Map.of(
                "userId", u.getUserId(),
                "realName", u.getRealName() != null ? u.getRealName() : "",
                "nickname", u.getNickname() != null ? u.getNickname() : "",
                "accountNumber", u.getAccountNumber() != null ? u.getAccountNumber() : "",
                "maskedPhone", PhoneMasker.mask(u.getPhoneNumber())
        ));
    }
}
