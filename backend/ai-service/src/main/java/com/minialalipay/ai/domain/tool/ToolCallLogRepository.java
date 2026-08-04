package com.minialalipay.ai.domain.tool;

import java.util.List;
import java.util.Optional;

/**
 * 工具调用日志仓储接口。
 *
 * <p>工具调用日志只增不改，不提供 update 方法。
 * 按 Trace ID 和会话 ID 提供查询入口。</p>
 */
public interface ToolCallLogRepository {

    /**
     * 根据工具调用 ID 查找日志。
     *
     * @param toolCallId 工具调用 ID
     * @return 工具调用日志，不存在时返回 {@link Optional#empty()}
     */
    Optional<ToolCallLog> findById(String toolCallId);

    /**
     * 查询会话内的全部工具调用，按发生时间倒序。
     *
     * @param sessionId 会话 ID
     * @return 工具调用日志列表
     */
    List<ToolCallLog> findBySessionId(String sessionId);

    /**
     * 新增工具调用日志。
     *
     * @param log 工具调用日志
     */
    void insert(ToolCallLog log);
}
