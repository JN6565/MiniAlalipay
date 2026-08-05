package com.minialalipay.ai.infrastructure.persistence.mapper;

import com.minialalipay.ai.infrastructure.persistence.po.AuditLogPO;
import org.apache.ibatis.annotations.*;

/**
 * {@code agent_db.audit_log} MyBatis Mapper。
 */
@Mapper
public interface AuditLogMapper {

    @Insert("INSERT INTO agent_db.audit_log "
            + "(audit_id, actor_type, actor_id, action, target_type, target_id, "
            + " result_code, trace_id, detail_json, occurred_at) "
            + "VALUES (#{auditId}, #{actorType}, #{actorId}, #{action}, "
            + " #{targetType}, #{targetId}, #{resultCode}, #{traceId}, "
            + " #{detailJson}, #{occurredAt})")
    void insert(AuditLogPO po);
}
