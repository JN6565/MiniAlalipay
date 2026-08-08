package com.minialalipay.ai.infrastructure.persistence.mapper;

import com.minialalipay.ai.infrastructure.persistence.po.AgentMessagePO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * AI 消息 Mapper，对应 {@code agent_db.agent_message} 表。
 *
 * <p>消息不可变，只提供 INSERT 和 SELECT 操作。
 * 同一 {@code (session_id, client_message_id, role)} 的重复插入由数据库唯一约束阻止。</p>
 */
@Mapper
public interface AgentMessageMapper {

    /**
     * 根据消息 ID 查询。
     *
     * @param messageId 消息 ID
     * @return 消息 PO，未找到时返回 null
     */
    @Select("SELECT * FROM agent_db.agent_message WHERE message_id = #{messageId}")
    AgentMessagePO findByMessageId(@Param("messageId") String messageId);

    /**
     * 根据会话 ID 和客户端消息 ID 查询已有消息（用于幂等检查）。
     *
     * @param sessionId 会话 ID
     * @param clientMessageId 客户端消息幂等键
     * @return 已存在消息列表
     */
    @Select("SELECT * FROM agent_db.agent_message "
            + "WHERE session_id = #{sessionId} AND client_message_id = #{clientMessageId}")
    List<AgentMessagePO> findByClientMessageId(
            @Param("sessionId") String sessionId,
            @Param("clientMessageId") String clientMessageId);

    /**
     * 查询会话内消息，按创建时间正序（用于恢复对话上下文）。
     *
     * <p>注意：此方法返回最早 N 条，不适合"最近 N 轮"场景。
     * 获取最近消息请使用 {@link #findRecentBySessionId}。</p>
     *
     * @param sessionId 会话 ID
     * @param limit 最大返回数
     * @return 消息列表（正序）
     */
    @Select("SELECT * FROM agent_db.agent_message "
            + "WHERE session_id = #{sessionId} "
            + "ORDER BY created_at ASC, message_id ASC LIMIT #{limit}")
    List<AgentMessagePO> findBySessionId(
            @Param("sessionId") String sessionId,
            @Param("limit") int limit);

    /**
     * 查询会话内最近 N 条消息，按创建时间倒序。
     *
     * <p>调用方自行反转以获得正序上下文。使用 {@code message_id}
     * 作为第二排序键保证同毫秒消息的稳定排序。</p>
     *
     * @param sessionId 会话 ID
     * @param limit 最大返回数
     * @return 最近消息列表（倒序）
     */
    @Select("SELECT * FROM agent_db.agent_message "
            + "WHERE session_id = #{sessionId} "
            + "ORDER BY created_at DESC, message_id DESC LIMIT #{limit}")
    List<AgentMessagePO> findRecentBySessionId(
            @Param("sessionId") String sessionId,
            @Param("limit") int limit);

    /**
     * 插入新消息。
     *
     * @param po 消息持久化对象
     * @return 受影响行数
     */
    @Insert("INSERT INTO agent_db.agent_message "
            + "(message_id, session_id, client_message_id, role, content_redacted, "
            + "token_count, created_at, kind, tool_name) "
            + "VALUES (#{messageId}, #{sessionId}, #{clientMessageId}, #{role}, "
            + "#{contentRedacted}, #{tokenCount}, #{createdAt}, #{kind}, #{toolName})")
    int insert(AgentMessagePO po);
}
