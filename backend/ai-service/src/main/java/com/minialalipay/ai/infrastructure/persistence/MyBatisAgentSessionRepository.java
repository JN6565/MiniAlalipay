package com.minialalipay.ai.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minialalipay.ai.domain.agent.AgentSession;
import com.minialalipay.ai.domain.agent.AgentSessionRepository;
import com.minialalipay.ai.domain.agent.AgentSessionStatus;
import com.minialalipay.ai.infrastructure.persistence.mapper.AgentSessionMapper;
import com.minialalipay.ai.infrastructure.persistence.po.AgentSessionPO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.minialalipay.ai.domain.agent.AgentErrorCode;
import com.minialalipay.common.error.BusinessException;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * AI 会话仓储实现类。
 *
 * <p>基于 {@link AgentSessionMapper} 实现会话的持久化操作，
 * 负责领域对象 {@link AgentSession} 与 {@link AgentSessionPO} 之间的转换。
 * 槽位 JSON 由持久化层透明处理，领域层只操作 {@code Map<String, Object>}。</p>
 */
@Repository
public class MyBatisAgentSessionRepository implements AgentSessionRepository {

    private static final Logger log = LoggerFactory.getLogger(MyBatisAgentSessionRepository.class);

    private final AgentSessionMapper agentSessionMapper;
    private final ObjectMapper objectMapper;

    public MyBatisAgentSessionRepository(AgentSessionMapper agentSessionMapper, ObjectMapper objectMapper) {
        this.agentSessionMapper = agentSessionMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<AgentSession> findById(String sessionId) {
        AgentSessionPO po = agentSessionMapper.findBySessionId(sessionId);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public List<AgentSession> findActiveByUserId(String userId) {
        List<AgentSessionPO> pos = agentSessionMapper.findActiveByUserId(userId);
        return pos.stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public void save(AgentSession session) {
        AgentSessionPO existing = agentSessionMapper.findBySessionId(session.getSessionId());
        if (existing == null) {
            agentSessionMapper.insert(toPO(session));
        } else {
            int updated = agentSessionMapper.updateByCas(toPO(session));
            if (updated == 0) {
                throw new BusinessException(AgentErrorCode.VERSION_CONFLICT);
            }
            session.updateVersion(session.getVersion() + 1);
        }
    }

    /**
     * 将持久化对象转换为领域对象。
     */
    @SuppressWarnings("unchecked")
    private AgentSession toDomain(AgentSessionPO po) {
        Map<String, Object> slots = Map.of();
        if (po.getSlotsJson() != null) {
            // 使用简单的 JSON 解析，实际可使用 Jackson ObjectMapper
            slots = parseSlotsJson(po.getSlotsJson());
        }
        return new AgentSession(
                po.getSessionId(),
                po.getUserId(),
                po.getSummary(),
                slots,
                AgentSessionStatus.valueOf(po.getStatus()),
                po.getVersion(),
                po.getLastActiveAt(),
                po.getCreatedAt()
        );
    }

    /**
     * 将领域对象转换为持久化对象。
     */
    private AgentSessionPO toPO(AgentSession session) {
        AgentSessionPO po = new AgentSessionPO();
        po.setSessionId(session.getSessionId());
        po.setUserId(session.getUserId());
        po.setSummary(session.getSummary());
        po.setSlotsJson(toSlotsJson(session.getSlots()));
        po.setStatus(session.getStatus().name());
        po.setVersion(session.getVersion());
        po.setLastActiveAt(session.getLastActiveAt());
        po.setCreatedAt(session.getCreatedAt());
        return po;
    }

    /**
     * 使用 Jackson ObjectMapper 将数据库 JSON 字符串转为 Map。
     *
     * <p>JSON 损坏视为数据异常，抛出稳定业务异常而非静默返回空槽位。
     * 静默降级会导致已确认的安全上下文丢失。</p>
     */
    private Map<String, Object> parseSlotsJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("会话槽位 JSON 解析失败，数据可能已损坏: error={}", e.getMessage());
            throw new BusinessException(AgentErrorCode.SESSION_NOT_FOUND);
        }
    }

    /**
     * 使用 Jackson ObjectMapper 将槽位 Map 序列化为 JSON 字符串。
     */
    private String toSlotsJson(Map<String, Object> slots) {
        if (slots == null || slots.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(slots);
        } catch (Exception e) {
            log.warn("会话槽位 JSON 序列化失败: {}", e.getMessage());
            return "{}";
        }
    }
}
