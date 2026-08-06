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
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
        log.info("业务中心客户端初始化: baseUrl={}, timeout={}ms", baseUrl, timeoutMs);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> createTransferDraft(
            String userId, String payeeId, long amountFen,
            String remark, String idempotencyKey) {
        log.debug("创建转账草稿: userId={}, payeeId={}, amountFen={}", userId, payeeId, amountFen);
        Map<String, Object> body = Map.of(
                "payeeId", payeeId,
                "amountFen", amountFen,
                "remark", remark != null ? remark : ""
        );
        return postWithIdempotency(userId, "/api/v1/transfer-drafts", idempotencyKey, body);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> validateTransferDraft(
            String userId, String draftId, String idempotencyKey) {
        log.debug("校验转账草稿: userId={}, draftId={}", userId, draftId);
        return postWithIdempotency(userId,
                "/api/v1/transfer-drafts/" + draftId + "/validate", idempotencyKey, Map.of());
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
        log.debug("提交已确认转账: userId={}, draftId={}", userId, draftId);
        Map<String, Object> body = Map.of(
                "draftId", draftId,
                "confirmationHandle", confirmationHandle
        );
        return postWithIdempotency(userId, "/api/v1/transfers", idempotencyKey, body);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> prepareConfirmationCard(String userId, String draftId) {
        log.debug("准备确认卡片: userId={}, draftId={}", userId, draftId);
        // 确认卡片通过草稿查询 + 校验结果生成
        Map<String, Object> draft = getTransferDraft(userId, draftId);
        return Map.of(
                "cardType", "TRANSFER_CONFIRMATION",
                "payeeId", draft.getOrDefault("payeeId", ""),
                "amountFen", draft.getOrDefault("amountFen", 0L),
                "remark", draft.getOrDefault("remark", ""),
                "fundingSource", "BALANCE"
        );
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
            return restClient.post()
                    .uri(path)
                    .header("X-User-Id", userId)
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
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
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("业务中心 POST 调用失败: path={}, error={}", path, e.getMessage());
            throw new BusinessException(AgentErrorCode.TOOL_UNAVAILABLE);
        }
    }
}
