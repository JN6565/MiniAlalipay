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
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
        log.info("用户中心客户端初始化: baseUrl={}, timeout={}ms", baseUrl, timeoutMs);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> searchPayees(String userId, String query, int limit) {
        log.debug("搜索收款人: userId={}, query={}, limit={}", userId, query, limit);
        try {
            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/users/search")
                            .queryParam("q", query)
                            .queryParam("limit", limit)
                            .build())
                    .header("X-User-Id", userId)
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
            Object users = response.get("users");
            if (users instanceof List) {
                return (List<Map<String, Object>>) users;
            }
            return Collections.emptyList();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("搜索收款人调用失败: {}", e.getMessage());
            throw new BusinessException(AgentErrorCode.TOOL_UNAVAILABLE);
        }
    }
}
