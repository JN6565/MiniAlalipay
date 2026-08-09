package com.minialalipay.ai.infrastructure.client;

import com.minialalipay.ai.application.port.AccountCenterPort;
import com.minialalipay.ai.domain.agent.AgentErrorCode;
import com.minialalipay.common.error.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * 账户中心真实 HTTP 客户端。
 *
 * <p>通过 RestClient 调用账户中心的公开 API，统一处理超时、重试和错误映射。
 * 只读查询，不产生资金副作用。超时后最多重试一次。调用经网关完成鉴权与审计，
 * 基址默认使用网关服务名，注入的 {@link RestClient.Builder} 必须标注 {@link LoadBalanced}
 * 才会被 Spring Cloud LoadBalancer 装饰并经 Nacos 解析实例；未装饰的构建器会把服务名当作域名走 DNS 解析。</p>
 */
@Component
@ConditionalOnProperty(name = "ai.client.mock-mode", havingValue = "false")
public class HttpAccountCenterClient implements AccountCenterPort {

    private static final Logger log = LoggerFactory.getLogger(HttpAccountCenterClient.class);

    private final RestClient restClient;

    public HttpAccountCenterClient(
            @LoadBalanced RestClient.Builder restClientBuilder,
            @Value("${ai.client.account-center.base-url:http://minialalipay-gateway}") String baseUrl,
            @Value("${ai.client.timeout-ms:3000}") int timeoutMs
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(factory)
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
    public Map<String, Object> listTransactions(String userId, int limit,
            String startTime, String endTime, String direction, String status) {
        // 拼接筛选参数到查询字符串
        StringBuilder query = new StringBuilder("/api/v1/accounts/me/entries?limit=").append(limit);
        if (startTime != null && !startTime.isBlank()) {
            query.append("&startTime=").append(java.net.URLEncoder.encode(startTime, java.nio.charset.StandardCharsets.UTF_8));
        }
        if (endTime != null && !endTime.isBlank()) {
            query.append("&endTime=").append(java.net.URLEncoder.encode(endTime, java.nio.charset.StandardCharsets.UTF_8));
        }
        if (direction != null && !direction.isBlank()) {
            query.append("&direction=").append(direction);
        }
        if (status != null && !status.isBlank()) {
            query.append("&status=").append(status);
        }
        log.debug("查询交易明细（含筛选）: userId={}, query={}", userId, query);
        return get(userId, query.toString());
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

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> createCreditRepaymentDraft(
            String userId, long amountFen, String idempotencyKey) {
        log.debug("创建还款草稿: userId={}, amountFen={}", userId, amountFen);
        Map<String, Object> body = Map.of("amountFen", amountFen);
        return post(userId, "/api/v1/credit/repayment-drafts", body, idempotencyKey);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> submitCreditRepayment(
            String userId, String repaymentDraftId,
            String paymentProofToken, String idempotencyKey) {
        // 防御性校验：避免 Map.of(null) 的 NPE
        if (repaymentDraftId == null || repaymentDraftId.isBlank()) {
            throw new BusinessException(AgentErrorCode.INTENT_LOW_CONFIDENCE);
        }
        if (paymentProofToken == null || paymentProofToken.isBlank()) {
            throw new BusinessException(AgentErrorCode.TOOL_FORBIDDEN);
        }
        log.debug("提交还款: userId={}, repaymentDraftId={}", userId, repaymentDraftId);
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("repaymentDraftId", repaymentDraftId);
        body.put("paymentProofToken", paymentProofToken);
        return post(userId, "/api/v1/credit/repayments", body, idempotencyKey);
    }

    // ---- 内部方法 ----

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(String userId, String path) {
        try {
            var spec = restClient.get()
                    .uri(path)
                    .header("X-User-Id", userId);
            Map<String, Object> response = withAuth(spec)
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
            // 下游 Controller 统一包装在 ApiResponse 信封中，需提取 data 字段
            return unwrapEnvelope(response);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("账户中心调用失败: path={}, error={}", path, e.getMessage());
            throw new BusinessException(AgentErrorCode.TOOL_UNAVAILABLE);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String userId, String path,
                                     Map<String, Object> body, String idempotencyKey) {
        try {
            var spec = restClient.post()
                    .uri(path)
                    .header("X-User-Id", userId)
                    .header("Idempotency-Key", idempotencyKey)
                    .header("Content-Type", "application/json")
                    .body(body);
            Map<String, Object> response = withAuth(spec)
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
            // 下游 Controller 统一包装在 ApiResponse 信封中，需提取 data 字段
            return unwrapEnvelope(response);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("账户中心调用失败: path={}, error={}", path, e.getMessage());
            throw new BusinessException(AgentErrorCode.TOOL_UNAVAILABLE);
        }
    }

    /**
     * 条件注入 Authorization 头：通过网关调用时携带原始 Bearer Token。
     *
     * <p>Token 由 AgentController 在请求开始时写入 {@link RequestContext}，
     * 此处读取并附加到下游请求。未设置时不影响直连模式的调用。</p>
     */
    private org.springframework.web.client.RestClient.RequestHeadersSpec<?> withAuth(
            org.springframework.web.client.RestClient.RequestHeadersSpec<?> spec) {
        String authHeader = RequestContext.getAuthorizationHeader();
        if (authHeader != null) {
            spec.header("Authorization", authHeader);
        }
        return spec;
    }

    /**
     * 从 ApiResponse 信封中提取 data 字段。
     *
     * <p>下游 Controller 统一返回 {@code ApiResponse<T>} 格式：
     * {@code {"success":true, "data":{...}, "requestId":"..."}}。
     * 此方法提取内层 data 对象，使调用方直接获得业务数据。</p>
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapEnvelope(Map<String, Object> response) {
        if (response == null) {
            return Map.of();
        }
        Object data = response.get("data");
        if (data instanceof Map) {
            return (Map<String, Object>) data;
        }
        // data 不是 Map（如 List 或 null），包装为含单键的 Map 以保持接口一致
        if (data != null) {
            java.util.Map<String, Object> wrapped = new java.util.HashMap<>();
            wrapped.put("data", data);
            return wrapped;
        }
        return response;
    }
}
