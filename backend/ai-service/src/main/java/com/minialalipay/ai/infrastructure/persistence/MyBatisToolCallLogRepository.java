package com.minialalipay.ai.infrastructure.persistence;

import com.minialalipay.ai.domain.tool.ToolCallLog;
import com.minialalipay.ai.domain.tool.ToolCallLogRepository;
import com.minialalipay.ai.infrastructure.persistence.mapper.ToolCallLogMapper;
import com.minialalipay.ai.infrastructure.persistence.po.ToolCallLogPO;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 工具调用日志仓储实现类。
 *
 * <p>基于 {@link ToolCallLogMapper} 实现工具调用日志的持久化操作，
 * 负责领域对象 {@link ToolCallLog} 与 {@link ToolCallLogPO} 之间的转换。
 * 工具调用日志只增不删不改。</p>
 */
@Repository
public class MyBatisToolCallLogRepository implements ToolCallLogRepository {

    private final ToolCallLogMapper toolCallLogMapper;

    public MyBatisToolCallLogRepository(ToolCallLogMapper toolCallLogMapper) {
        this.toolCallLogMapper = toolCallLogMapper;
    }

    @Override
    public Optional<ToolCallLog> findById(String toolCallId) {
        ToolCallLogPO po = toolCallLogMapper.findByToolCallId(toolCallId);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public List<ToolCallLog> findBySessionId(String sessionId) {
        List<ToolCallLogPO> pos = toolCallLogMapper.findBySessionId(sessionId);
        return pos.stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public void insert(ToolCallLog log) {
        toolCallLogMapper.insert(toPO(log));
    }

    private ToolCallLog toDomain(ToolCallLogPO po) {
        return new ToolCallLog(
                po.getToolCallId(),
                po.getSessionId(),
                po.getToolName(),
                po.getRequestDigest(),
                po.getResultCode(),
                po.getDurationMs(),
                po.getTraceId(),
                po.getOccurredAt()
        );
    }

    private ToolCallLogPO toPO(ToolCallLog log) {
        ToolCallLogPO po = new ToolCallLogPO();
        po.setToolCallId(log.getToolCallId());
        po.setSessionId(log.getSessionId());
        po.setToolName(log.getToolName());
        po.setRequestDigest(log.getRequestDigest());
        po.setResultCode(log.getResultCode());
        po.setDurationMs(log.getDurationMs());
        po.setTraceId(log.getTraceId());
        po.setOccurredAt(log.getOccurredAt());
        return po;
    }
}
