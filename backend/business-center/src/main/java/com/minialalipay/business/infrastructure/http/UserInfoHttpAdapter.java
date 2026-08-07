package com.minialalipay.business.infrastructure.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * 用户信息 HTTP 适配器。
 *
 * <p>调用用户中心内部接口获取用户基本信息（如真实姓名）。</p>
 */
@Component
public class UserInfoHttpAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserInfoHttpAdapter.class);

    private final RestClient client;
    private final String serviceToken;

    public UserInfoHttpAdapter(
            RestClient.Builder builder,
            @Value("${minialalipay.internal.user-center-url}") String baseUrl,
            @Value("${minialalipay.internal.service-token:}") String serviceToken
    ) {
        this.client = builder.baseUrl(baseUrl).build();
        this.serviceToken = serviceToken;
    }

    /**
     * 获取用户真实姓名。
     *
     * @param userId 用户 ID
     * @return 真实姓名，如果查询失败则返回 null
     */
    public String getRealName(String userId) {
        try {
            Map<String, String> result = client.get()
                    .uri("/internal/v1/users/{userId}", userId)
                    .header("X-Internal-Service-Token", serviceToken)
                    .retrieve()
                    .body(Map.class);
            return result != null ? result.get("realName") : null;
        } catch (RestClientResponseException exception) {
            LOGGER.warn("获取用户信息失败：userId={}, status={}", userId, exception.getStatusCode());
            return null;
        } catch (RuntimeException exception) {
            LOGGER.warn("获取用户信息异常：userId={}, cause={}", userId, exception.getMessage());
            return null;
        }
    }
}
