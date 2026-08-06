package com.minialalipay.ai.infrastructure.client;

import com.minialalipay.ai.application.port.AccountCenterPort;
import com.minialalipay.ai.domain.agent.AgentErrorCode;
import com.minialalipay.common.error.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 账户中心真实 HTTP 客户端。
 *
 * <p>通过 RestClient 调用账户中心的公开 API，统一处理超时、重试和错误映射。
 * 只读查询，不产生资金副作用。超时后最多重试一次。</p>
 */
@Component
@ConditionalOnProperty(name = "ai.client.mock-mode", havingValue = "false")
public class HttpAccountCenterClient implements AccountCenterPort {

    private static final Logger log = LoggerFactory.getLogger(HttpAccountCenterClient.class);

    private final RestClient restClient;

    public HttpAccountCenterClient(
            @Value("${ai.client.account-center.base-url:http://localhost:8083}") String baseUrl,
            @Value("${ai.client.timeout-ms:3000}") int timeoutMs
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
        log.info("账户中心客户端初始化: baseUrl={}, timeout={}ms", baseUrl, timeoutMs);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getAccountSummary(String userId) {
        log.debug("查询账户摘要: userId={}", userId);
        return get(userId, "/api/v1/accounts/me");
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getBalance(String userId) {
        log.debug("查询余额: userId={}", userId);
        Map<String, Object> account = get(userId, "/api/v1/accounts/me");
        return Map.of(
                "availableFen", account.getOrDefault("availableFen", 0L),
                "frozenFen", account.getOrDefault("frozenFen", 0L)
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> listTransactions(String userId, int limit) {
        log.debug("查询交易明细: userId={}, limit={}", userId, limit);
        return get(userId, "/api/v1/accounts/me/entries?limit=" + limit);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getCreditSummary(String userId) {
        log.debug("查询花呗额度: userId={}", userId);
        return get(userId, "/api/v1/credit/me");
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> listCreditBills(String userId, int limit) {
        log.debug("查询花呗账单: userId={}, limit={}", userId, limit);
        return get(userId, "/api/v1/credit/bills?limit=" + limit);
    }

    // ---- 内部方法 ----

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(String userId, String path) {
        try {
            return restClient.get()
                    .uri(path)
                    .header("X-User-Id", userId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        log.warn("账户中心客户端错误: status={}, path={}", res.getStatusCode(), path);
                        throw new BusinessException(AgentErrorCode.TOOL_UNAVAILABLE);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        log.warn("账户中心服务端错误: status={}, path={}", res.getStatusCode(), path);
                        throw new BusinessException(AgentErrorCode.TOOL_UNAVAILABLE);
                    })
                    .body(Map.class);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("账户中心调用失败: path={}, error={}", path, e.getMessage());
            throw new BusinessException(AgentErrorCode.TOOL_UNAVAILABLE);
        }
    }
}
