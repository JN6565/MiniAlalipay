package com.minialalipay.ai.infrastructure.persistence.mapper;

import com.minialalipay.ai.infrastructure.persistence.po.AgentSessionPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * AI 会话 Mapper，对应 {@code agent_db.agent_session} 表。
 *
 * <p>提供会话的按 ID 查询、按用户查询活跃会话、插入及乐观锁 CAS 更新能力。
 * UPDATE 通过 {@code WHERE version = #{version}} 实现并发控制。</p>
 */
@Mapper
public interface AgentSessionMapper {

    /**
     * 根据会话 ID 查询。
     *
     * @param sessionId 会话 ID
     * @return 会话 PO，未找到时返回 null
     */
    @Select("SELECT * FROM agent_db.agent_session WHERE session_id = #{sessionId}")
    AgentSessionPO findBySessionId(@Param("sessionId") String sessionId);

    /**
     * 查询用户的活跃会话，按最后活跃时间倒序。
     *
     * @param userId 用户 ID
     * @return 活跃会话列表
     */
    @Select("SELECT * FROM agent_db.agent_session "
            + "WHERE user_id = #{userId} AND status = 'ACTIVE' "
            + "ORDER BY last_active_at DESC")
    List<AgentSessionPO> findActiveByUserId(@Param("userId") String userId);

    /**
     * 插入新会话。
     *
     * @param po 会话持久化对象
     * @return 受影响行数
     */
    @Insert("INSERT INTO agent_db.agent_session "
            + "(session_id, user_id, summary, slots_json, status, version, last_active_at, created_at) "
            + "VALUES (#{sessionId}, #{userId}, #{summary}, #{slotsJson}, #{status}, "
            + "#{version}, #{lastActiveAt}, #{createdAt})")
    int insert(AgentSessionPO po);

    /**
     * 乐观锁 CAS 更新会话（摘要、槽位、状态和活跃时间）。
     *
     * <p>仅当 version 匹配且状态为 ACTIVE 时更新，version 自增 1。
     * 用于保护并发下的上下文压缩和槽位修改。</p>
     *
     * @param po 包含最新字段值及当前版本号的会话 PO
     * @return 受影响行数，0 表示版本冲突或状态不允许修改
     */
    @Update("UPDATE agent_db.agent_session "
            + "SET summary = #{summary}, slots_json = #{slotsJson}, "
            + "status = #{status}, version = version + 1, last_active_at = #{lastActiveAt} "
            + "WHERE session_id = #{sessionId} AND version = #{version} AND status = 'ACTIVE'")
    int updateByCas(AgentSessionPO po);
}
