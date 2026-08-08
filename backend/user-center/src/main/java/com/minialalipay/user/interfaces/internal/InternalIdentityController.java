package com.minialalipay.user.interfaces.internal;

import com.minialalipay.user.application.identity.IdentityApplicationService;
import com.minialalipay.user.interfaces.dto.internal.VerifyIdentityRequest;
import com.minialalipay.user.interfaces.dto.internal.VerifyIdentityResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部身份校验接口，供 account-center 绑卡时调用。
 *
 * <p>三要素交叉比对：持卡人姓名、身份证号哈希、手机号与用户存储信息完全一致才返回 matched=true。
 * 本接口仅限内部服务调用，网关不对外暴露。</p>
 */
@RestController
@RequestMapping("/internal/v1/identity")
public class InternalIdentityController {

    private final IdentityApplicationService identityApplicationService;

    public InternalIdentityController(IdentityApplicationService identityApplicationService) {
        this.identityApplicationService = identityApplicationService;
    }

    /**
     * 三要素交叉校验。
     *
     * @param body 校验请求（userId + 持卡人姓名 + 身份证明文 + 手机号明文）
     * @return 校验结果
     */
    @PostMapping("/verify")
    public ResponseEntity<VerifyIdentityResponse> verifyIdentity(
            @Valid @RequestBody VerifyIdentityRequest body) {
        boolean matched = identityApplicationService.verifyThreeElements(
                body.userId(), body.holderName(), body.idCard(), body.phone());

        // 无论匹配与否都返回用户的真实姓名和手机号，方便调用方调试
        // 但只在 matched=true 时返回有意义的数据
        VerifyIdentityResponse response = matched
                ? new VerifyIdentityResponse(true, body.holderName(), body.phone())
                : new VerifyIdentityResponse(false, null, null);
        return ResponseEntity.ok(response);
    }
}
