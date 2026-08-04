package com.minialalipay.ai.infrastructure.persistence;

import com.minialalipay.ai.domain.agent.AgentSession;
import com.minialalipay.ai.domain.agent.AgentSessionRepository;
import com.minialalipay.ai.domain.agent.AgentSessionStatus;
import com.minialalipay.ai.infrastructure.persistence.mapper.AgentSessionMapper;
import com.minialalipay.ai.infrastructure.persistence.po.AgentSessionPO;
import org.springframework.stereotype.Repository;

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

    private final AgentSessionMapper agentSessionMapper;

    public MyBatisAgentSessionRepository(AgentSessionMapper agentSessionMapper) {
        this.agentSessionMapper = agentSessionMapper;
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
                throw new IllegalStateException(
                        "会话乐观锁冲突或状态不允许修改，sessionId=" + session.getSessionId());
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
     * 简易 JSON 解析——将数据库 JSON 字符串转为 Map。
     * 生产环境应使用 Jackson ObjectMapper。
     */
    private Map<String, Object> parseSlotsJson(String json) {
        // 阶段三骨架：预留 JSON 解析位置，当前返回空 Map
        return new java.util.HashMap<>();
    }

    /**
     * 简易 JSON 序列化——将槽位 Map 转为 JSON 字符串。
     * 生产环境应使用 Jackson ObjectMapper。
     */
    private String toSlotsJson(Map<String, Object> slots) {
        // 阶段三骨架：预留 JSON 序列化位置，当前返回空 JSON 对象
        if (slots == null || slots.isEmpty()) {
            return null;
        }
        return "{}";
    }
}
