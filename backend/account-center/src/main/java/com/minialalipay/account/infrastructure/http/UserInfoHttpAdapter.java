package com.minialalipay.account.infrastructure.http;

import com.minialalipay.account.application.port.UserInfoPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 用户信息 HTTP 适配器。
 *
 * <p>调用用户中心内部接口获取用户基本信息（如昵称、脱敏手机号），用于账本分录展示交易对方。</p>
 */
@Component
public class UserInfoHttpAdapter implements UserInfoPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserInfoHttpAdapter.class);

    private final RestClient client;
    private final String serviceToken;

    public UserInfoHttpAdapter(
            RestClient.Builder builder,
            @Value("${minialalipay.internal.user-center-url:http://localhost:8081}") String baseUrl,
            @Value("${minialalipay.internal.service-token:}") String serviceToken
    ) {
        this.client = builder.baseUrl(baseUrl).build();
        this.serviceToken = serviceToken;
    }

    /** 调用用户中心获取最小展示投影，查询失败时降级为空信息。 */
    @Override
    public UserInfo findUserInfo(String userId) {
        try {
            UserInfoPayload result = client.get()
                    .uri("/internal/v1/users/{userId}", userId)
                    .header("X-Internal-Service-Token", serviceToken)
                    .retrieve()
                    .body(UserInfoPayload.class);
            if (result == null) return null;
            return new UserInfo(result.userId(), result.realName(), result.nickname(), result.maskedPhone());
        } catch (RestClientResponseException exception) {
            LOGGER.warn("获取用户信息失败：userId={}, status={}", userId, exception.getStatusCode());
            return null;
        } catch (RuntimeException exception) {
            LOGGER.warn("获取用户信息异常：userId={}, cause={}", userId, exception.getMessage());
            return null;
        }
    }

    /** 用户中心内部接口的最小响应结构。 */
    private record UserInfoPayload(String userId, String realName, String nickname, String maskedPhone) { }
}
