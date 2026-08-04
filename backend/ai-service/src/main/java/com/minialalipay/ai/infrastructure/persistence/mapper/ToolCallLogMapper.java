package com.minialalipay.ai.infrastructure.persistence.mapper;

import com.minialalipay.ai.infrastructure.persistence.po.ToolCallLogPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 工具调用日志 Mapper，对应 {@code agent_db.tool_call_log} 表。
 *
 * <p>工具调用日志只增不删不改，仅提供 INSERT 和 SELECT 操作。
 * 通过 {@code trace_id} 和 {@code session_id} 提供查询入口。</p>
 */
@Mapper
public interface ToolCallLogMapper {

    /**
     * 根据工具调用 ID 查询。
     *
     * @param toolCallId 工具调用 ID
     * @return 工具调用日志 PO，未找到时返回 null
     */
    @Select("SELECT * FROM agent_db.tool_call_log WHERE tool_call_id = #{toolCallId}")
    ToolCallLogPO findByToolCallId(@Param("toolCallId") String toolCallId);

    /**
     * 查询会话内的全部工具调用，按发生时间倒序。
     *
     * @param sessionId 会话 ID
     * @return 工具调用日志列表
     */
    @Select("SELECT * FROM agent_db.tool_call_log "
            + "WHERE session_id = #{sessionId} "
            + "ORDER BY occurred_at DESC")
    List<ToolCallLogPO> findBySessionId(@Param("sessionId") String sessionId);

    /**
     * 新增工具调用日志。
     *
     * @param po 工具调用日志 PO
     * @return 受影响行数
     */
    @Insert("INSERT INTO agent_db.tool_call_log "
            + "(tool_call_id, session_id, tool_name, request_digest, "
            + "result_code, duration_ms, trace_id, occurred_at) "
            + "VALUES (#{toolCallId}, #{sessionId}, #{toolName}, #{requestDigest}, "
            + "#{resultCode}, #{durationMs}, #{traceId}, #{occurredAt})")
    int insert(ToolCallLogPO po);
}
