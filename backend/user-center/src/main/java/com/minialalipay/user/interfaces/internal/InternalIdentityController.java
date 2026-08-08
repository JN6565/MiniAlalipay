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
 * 内部身份校验接口，供 account-center 注册银行卡与绑卡时调用。
 *
 * <p>三要素交叉比对：持卡人姓名、身份证号哈希、手机号与用户存储信息完全一致才返回 matched=true。
 * 本接口仅限内部服务调用，网关不对外暴露；按项目决策不校验服务令牌。</p>
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
     * <p>响应中 {@code identityBound} 无论匹配与否都返回，
     * 供调用方区分「未绑定身份」与「已绑定但不匹配」两类失败。
     * 本接口不校验服务令牌，仅限内部网络调用，网关不对外暴露。</p>
     *
     * @param body 校验请求（userId + 持卡人姓名 + 身份证明文 + 手机号明文）
     * @return 校验结果（匹配结果 + 身份绑定状态）
     */
    @PostMapping("/verify")
    public ResponseEntity<VerifyIdentityResponse> verifyIdentity(
            @Valid @RequestBody VerifyIdentityRequest body) {
        IdentityApplicationService.VerifyResult result = identityApplicationService.verifyThreeElements(
                body.userId(), body.holderName(), body.idCard(), body.phone());

        // 无论匹配与否都返回 identityBound，供调用方区分未绑定与不匹配；
        // realName/phone 只在 matched=true 时回填请求值，避免泄露用户存储信息
        VerifyIdentityResponse response = result.matched()
                ? new VerifyIdentityResponse(true, true, body.holderName(), body.phone())
                : new VerifyIdentityResponse(false, result.identityBound(), null, null);
        return ResponseEntity.ok(response);
    }
}
