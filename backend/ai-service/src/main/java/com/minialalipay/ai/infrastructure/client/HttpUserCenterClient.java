package com.minialalipay.ai.infrastructure.client;

import com.minialalipay.ai.application.port.UserCenterPort;
import com.minialalipay.ai.domain.agent.AgentErrorCode;
import com.minialalipay.common.error.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 用户中心真实 HTTP 客户端。
 *
 * <p>通过 RestClient 调用用户中心的公开 API，统一处理超时、重试和错误映射。
 * 调用用户中心接口需要经过网关或直接服务间调用，不直连数据库。</p>
 */
@Component
@ConditionalOnProperty(name = "ai.client.mock-mode", havingValue = "false")
public class HttpUserCenterClient implements UserCenterPort {

    private static final Logger log = LoggerFactory.getLogger(HttpUserCenterClient.class);

    private final RestClient restClient;

    public HttpUserCenterClient(
            @Value("${ai.client.user-center.base-url:http://localhost:8081}") String baseUrl,
            @Value("${ai.client.timeout-ms:3000}") int timeoutMs
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Content-Type", "application/json")
                .build();
        log.info("用户中心客户端初始化: baseUrl={}, timeout={}ms", baseUrl, timeoutMs);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> searchPayees(String userId, String query, int limit) {
        log.debug("搜索收款人: userId={}, query={}, limit={}", userId, query, limit);
        try {
            var spec = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/users/search")
                            .queryParam("keyword", query)
                            .queryParam("limit", limit)
                            .build())
                    .header("X-User-Id", userId);
            Map<String, Object> response = withAuth(spec)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        log.warn("搜索收款人客户端错误: status={}", res.getStatusCode());
                        throw new BusinessException(AgentErrorCode.TOOL_UNAVAILABLE);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        log.warn("搜索收款人服务端错误: status={}", res.getStatusCode());
                        throw new BusinessException(AgentErrorCode.TOOL_UNAVAILABLE);
                    })
                    .body(Map.class);

            if (response == null) {
                return Collections.emptyList();
            }
            // user-center 返回 ApiResponse<List<UserSearchResultDTO>>，
            // 数据在 "data" 字段中
            Object data = response.get("data");
            if (data instanceof List) {
                return (List<Map<String, Object>>) data;
            }
            return Collections.emptyList();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("搜索收款人调用失败: {}", e.getMessage());
            throw new BusinessException(AgentErrorCode.TOOL_UNAVAILABLE);
        }
    }

    /**
     * 条件注入 Authorization 头：通过网关调用时携带原始 Bearer Token。
     */
    private org.springframework.web.client.RestClient.RequestHeadersSpec<?> withAuth(
            org.springframework.web.client.RestClient.RequestHeadersSpec<?> spec) {
        String authHeader = RequestContext.getAuthorizationHeader();
        if (authHeader != null) {
            spec.header("Authorization", authHeader);
        }
        return spec;
    }
}
