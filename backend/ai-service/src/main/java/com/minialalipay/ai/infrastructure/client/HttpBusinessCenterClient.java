package com.minialalipay.ai.infrastructure.client;

import com.minialalipay.ai.application.port.BusinessCenterPort;
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
import java.util.Map;

/**
 * 业务中心真实 HTTP 客户端。
 *
 * <p>通过 RestClient 调用业务中心的公开 API，统一处理超时、重试和错误映射。
 * 写操作使用幂等键重试，超时后查询原资源状态而非盲重试。</p>
 */
@Component
@ConditionalOnProperty(name = "ai.client.mock-mode", havingValue = "false")
public class HttpBusinessCenterClient implements BusinessCenterPort {

    private static final Logger log = LoggerFactory.getLogger(HttpBusinessCenterClient.class);

    private final RestClient restClient;
    private final int timeoutMs;

    public HttpBusinessCenterClient(
            @Value("${ai.client.business-center.base-url:http://localhost:8082}") String baseUrl,
            @Value("${ai.client.timeout-ms:3000}") int timeoutMs
    ) {
        this.timeoutMs = timeoutMs;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Content-Type", "application/json")
                .build();
        log.info("业务中心客户端初始化: baseUrl={}, timeout={}ms", baseUrl, timeoutMs);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> createTransferDraft(
            String userId, String payeeId, long amountFen,
            String remark, String idempotencyKey) {
        // 防御性校验：payeeId 必须非空，否则 LLM 未正确解析收款人
        if (payeeId == null || payeeId.isBlank()) {
            throw new BusinessException(AgentErrorCode.INTENT_LOW_CONFIDENCE);
        }
        log.debug("创建转账草稿: userId={}, payeeId={}, amountFen={}", userId, payeeId, amountFen);
        // 使用 HashMap 而非 Map.of()，因为 JDK 的 Map.of() 不允许 null value
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("payeeUserId", payeeId);
        body.put("amountFen", amountFen);
        body.put("remark", remark != null ? remark : "");
        return postWithIdempotency(userId, "/api/v1/transfer-drafts", idempotencyKey, body);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> validateTransferDraft(
            String userId, String draftId, long version, String idempotencyKey) {
        log.debug("校验转账草稿: userId={}, draftId={}, version={}", userId, draftId, version);
        // 下游 TransferController.validateDraft() 要求请求体包含 version（CAS 版本号）
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("version", version);
        return postWithIdempotency(userId,
                "/api/v1/transfer-drafts/" + draftId + "/validate", idempotencyKey, body);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getTransferDraft(String userId, String draftId) {
        log.debug("查询草稿: userId={}, draftId={}", userId, draftId);
        return get(userId, "/api/v1/transfer-drafts/" + draftId);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getTransferStatus(String userId, String transactionId) {
        log.debug("查询交易状态: userId={}, transactionId={}", userId, transactionId);
        return get(userId, "/api/v1/transfers/" + transactionId);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> submitConfirmedTransfer(
            String userId, String draftId, String confirmationHandle, String idempotencyKey) {
        // 防御性校验：避免 Map.of(null) 的 NPE
        if (draftId == null || draftId.isBlank()) {
            throw new BusinessException(AgentErrorCode.INTENT_LOW_CONFIDENCE);
        }
        if (confirmationHandle == null || confirmationHandle.isBlank()) {
            throw new BusinessException(AgentErrorCode.TOOL_FORBIDDEN);
        }
        log.debug("提交已确认转账: userId={}, draftId={}", userId, draftId);
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("draftId", draftId);
        body.put("confirmationToken", confirmationHandle);
        return postWithIdempotency(userId, "/api/v1/transfers", idempotencyKey, body);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> prepareConfirmationCard(String userId, String draftId) {
        log.debug("准备确认卡片: userId={}, draftId={}", userId, draftId);
        // 确认卡片通过草稿查询生成；business-center 暂未提供专用卡片 API，
        // 此处从草稿数据中提取可用字段，前端根据 payeeId 补充收款人展示信息
        Map<String, Object> draft = getTransferDraft(userId, draftId);
        java.util.Map<String, Object> card = new java.util.HashMap<>();
        card.put("cardType", "TRANSFER_CONFIRMATION");
        card.put("draftId", draftId);
        // 下游 DraftResponse 返回 payeeUserId，需对齐字段名
        card.put("payeeId", draft.getOrDefault("payeeUserId", ""));
        card.put("amountFen", draft.getOrDefault("amountFen", 0L));
        card.put("remark", draft.getOrDefault("remark", ""));
        // 资金来源默认 BALANCE，待 business-center 提供专用 API 后替换
        card.put("fundingSource", draft.getOrDefault("fundingSource", "BALANCE"));
        // 草稿版本号，前端确认转账时需要传给 issueConfirmation 接口
        card.put("version", draft.getOrDefault("version", 0L));
        return card;
    }

    // ---- 内部方法 ----

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(String userId, String path) {
        try {
            var spec = restClient.get()
                    .uri(path)
                    .header("X-User-Id", userId);
            Map<String, Object> response = withAuth(spec).retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        int status = res.getStatusCode().value();
                        if (status == 404) {
                            throw new BusinessException(AgentErrorCode.SESSION_NOT_FOUND);
                        }
                        if (status == 409) {
                            throw new BusinessException(AgentErrorCode.VERSION_CONFLICT);
                        }
                        throw new BusinessException(AgentErrorCode.TOOL_UNAVAILABLE);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) ->
                            { throw new BusinessException(AgentErrorCode.TOOL_UNAVAILABLE); })
                    .body(Map.class);
            // 下游 Controller 统一包装在 ApiResponse 信封中，需提取 data 字段
            return unwrapEnvelope(response);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("业务中心 GET 调用失败: path={}, error={}", path, e.getMessage());
            throw new BusinessException(AgentErrorCode.TOOL_UNAVAILABLE);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postWithIdempotency(
            String userId, String path, String idempotencyKey, Map<String, Object> body) {
        try {
            var spec = restClient.post()
                    .uri(path)
                    .header("X-User-Id", userId)
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);
            Map<String, Object> response = withAuth(spec).retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        int status = res.getStatusCode().value();
                        if (status == 409) {
                            throw new BusinessException(AgentErrorCode.VERSION_CONFLICT);
                        }
                        throw new BusinessException(AgentErrorCode.TOOL_UNAVAILABLE);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) ->
                            { throw new BusinessException(AgentErrorCode.TOOL_UNAVAILABLE); })
                    .body(Map.class);
            // 下游 Controller 统一包装在 ApiResponse 信封中，需提取 data 字段
            return unwrapEnvelope(response);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("业务中心 POST 调用失败: path={}, error={}", path, e.getMessage());
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
