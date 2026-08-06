package com.minialalipay.ai.application.service;

import com.minialalipay.ai.application.port.StreamCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * SSE 流式消息处理服务。
 *
 * <p>复用 {@link AgentMessageService} 的核心处理逻辑，通过 {@link StreamCallback}
 * 在每个阶段发射 SSE 事件。作为流式端点背后的应用层编排入口。</p>
 *
 * <p>此类不包含业务逻辑，只负责将 AgentMessageService 的执行阶段映射为
 * 有序的 SSE 事件序列。所有的注入检测、幂等、上下文管理、工具执行仍由
 * AgentMessageService 负责。</p>
 */
@Service
public class AgentStreamService {

    private static final Logger log = LoggerFactory.getLogger(AgentStreamService.class);

    private final AgentMessageService messageService;
    private final Clock clock;

    public AgentStreamService(AgentMessageService messageService, Clock clock) {
        this.messageService = messageService;
        this.clock = clock;
    }

    /**
     * 通过流式回调处理消息。
     *
     * @param userId          用户 ID（由网关注入）
     * @param clientMessageId 客户端消息幂等键
     * @param sessionId       会话 ID（可空）
     * @param rawContent      用户输入
     * @param callback        流式回调（不可为 null）
     */
    public void processStream(
            String userId,
            String clientMessageId,
            String sessionId,
            String rawContent,
            StreamCallback callback
    ) {
        try {
            log.debug("开始流式处理: userId={}, clientMessageId={}", userId, clientMessageId);
            AgentMessageService.SendMessageResult result = messageService.processMessageWithStream(
                    userId, clientMessageId, sessionId, rawContent, clock.instant(), callback);
            log.debug("流式处理完成: sessionId={}, messageId={}",
                    result.sessionId(), result.messageId());
        } catch (Exception e) {
            log.error("流式处理异常: {}", e.getMessage(), e);
            callback.onError(new com.minialalipay.ai.application.port.SseEvent.ErrorPayload(
                    "INTERNAL_ERROR",
                    "处理请求时发生异常，请稍后重试: " + e.getMessage()));
        }
    }
}
