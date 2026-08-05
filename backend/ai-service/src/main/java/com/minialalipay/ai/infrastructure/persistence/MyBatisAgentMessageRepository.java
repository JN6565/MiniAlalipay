package com.minialalipay.ai.infrastructure.persistence;

import com.minialalipay.ai.domain.agent.AgentMessage;
import com.minialalipay.ai.domain.agent.AgentMessageRepository;
import com.minialalipay.ai.domain.agent.MessageRole;
import com.minialalipay.ai.infrastructure.persistence.mapper.AgentMessageMapper;
import com.minialalipay.ai.infrastructure.persistence.po.AgentMessagePO;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * AI 消息仓储实现类。
 *
 * <p>基于 {@link AgentMessageMapper} 实现消息持久化操作，
 * 负责领域对象 {@link AgentMessage} 与 {@link AgentMessagePO} 之间的转换。</p>
 */
@Repository
public class MyBatisAgentMessageRepository implements AgentMessageRepository {

    private final AgentMessageMapper agentMessageMapper;

    public MyBatisAgentMessageRepository(AgentMessageMapper agentMessageMapper) {
        this.agentMessageMapper = agentMessageMapper;
    }

    @Override
    public Optional<AgentMessage> findByClientMessageId(
            String sessionId, String clientMessageId, MessageRole role) {
        List<AgentMessagePO> pos = agentMessageMapper.findByClientMessageId(sessionId, clientMessageId);
        return pos.stream()
                .filter(po -> role.name().equals(po.getRole()))
                .findFirst()
                .map(this::toDomain);
    }

    @Override
    public List<AgentMessage> findBySessionId(String sessionId, int limit) {
        List<AgentMessagePO> pos = agentMessageMapper.findBySessionId(sessionId, limit);
        return pos.stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<AgentMessage> findRecentBySessionId(String sessionId, int limit) {
        List<AgentMessagePO> pos = agentMessageMapper.findRecentBySessionId(sessionId, limit);
        List<AgentMessage> messages = pos.stream()
                .map(this::toDomain)
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.reverse(messages);
        return messages;
    }

    @Override
    public void insert(AgentMessage message) {
        agentMessageMapper.insert(toPO(message));
    }

    private AgentMessage toDomain(AgentMessagePO po) {
        return new AgentMessage(
                po.getMessageId(),
                po.getSessionId(),
                po.getClientMessageId(),
                MessageRole.valueOf(po.getRole()),
                po.getContentRedacted(),
                po.getTokenCount(),
                po.getCreatedAt()
        );
    }

    private AgentMessagePO toPO(AgentMessage message) {
        AgentMessagePO po = new AgentMessagePO();
        po.setMessageId(message.getMessageId());
        po.setSessionId(message.getSessionId());
        po.setClientMessageId(message.getClientMessageId());
        po.setRole(message.getRole().name());
        po.setContentRedacted(message.getContentRedacted());
        po.setTokenCount(message.getTokenCount());
        po.setCreatedAt(message.getCreatedAt());
        return po;
    }
}
