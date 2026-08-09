package com.minialalipay.account.infrastructure.bankcard;

import com.minialalipay.account.domain.bankcard.UserCenterIdentityPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * 用户中心身份校验 HTTP 客户端。
 *
 * <p>调用 user-center 的内部三要素校验接口 {@code POST /internal/v1/identity/verify}，
 * 用于注册银行卡与绑卡时交叉比对持卡人信息与用户绑定身份是否完全一致。
 * 该内部接口按项目决策不校验服务令牌，本客户端不携带令牌；
 * 任何网络异常或非 2xx 响应统一映射为 {@code SERVICE_UNAVAILABLE}，
 * 由应用层拒绝请求，禁止在校验无法完成时放行。</p>
 */
@Component
public class UserCenterIdentityClient implements UserCenterIdentityPort {

    private static final Logger log = LoggerFactory.getLogger(UserCenterIdentityClient.class);

    private final RestTemplate restTemplate;
    private final String userCenterUrl;

    public UserCenterIdentityClient(RestTemplate restTemplate,
                                    @Value("${minialalipay.internal.user-center-url:http://user-center}") String userCenterUrl) {
        this.restTemplate = restTemplate;
        this.userCenterUrl = userCenterUrl;
    }

    @Override
    public VerifyResult verifyThreeElements(String userId, String holderName, String idCard, String phone) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Request-Id", UUID.randomUUID().toString());

            Map<String, String> requestBody = Map.of(
                    "userId", userId,
                    "holderName", holderName,
                    "idCard", idCard,
                    "phone", phone);

            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

            String url = userCenterUrl + "/internal/v1/identity/verify";
            log.info("调用 user-center 三要素校验: userId={}", userId);

            ResponseEntity<VerifyResponse> response = restTemplate.postForEntity(
                    url, request, VerifyResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                VerifyResponse body = response.getBody();
                VerifyResult result = body.matched()
                        ? VerifyResult.MATCHED
                        : (body.identityBound() ? VerifyResult.MISMATCH : VerifyResult.IDENTITY_NOT_BOUND);
                log.info("user-center 三要素校验结果: userId={}, result={}", userId, result);
                return result;
            }
            log.error("user-center 三要素校验请求失败: status={}", response.getStatusCode());
            return VerifyResult.SERVICE_UNAVAILABLE;
        } catch (Exception e) {
            log.error("调用 user-center 三要素校验接口异常", e);
            return VerifyResult.SERVICE_UNAVAILABLE;
        }
    }

    /** user-center 内部校验接口响应；identityBound 用于区分未绑定身份与不匹配。 */
    public record VerifyResponse(boolean matched, boolean identityBound, String realName, String phone) {
    }
}
