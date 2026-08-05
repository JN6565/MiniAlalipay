package com.minialalipay.ai.infrastructure.persistence;

import com.minialalipay.ai.infrastructure.persistence.mapper.AuditLogMapper;
import com.minialalipay.ai.infrastructure.persistence.po.AuditLogPO;
import org.springframework.stereotype.Repository;

import java.util.concurrent.atomic.AtomicLong;

/**
 * MyBatis 实现的 AI 审计日志仓储。
 *
 * <p>{@code audit_id} 使用原子递增计数器生成，单实例足够。
 * 多实例部署时应改用数据库序列或分布式 ID。</p>
 */
@Repository
public class MyBatisAuditLogRepository {

    private final AuditLogMapper mapper;
    private final AtomicLong idSequence = new AtomicLong(System.currentTimeMillis());

    public MyBatisAuditLogRepository(AuditLogMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 写入脱敏审计记录。
     *
     * @param actorType  主体类型
     * @param actorId    脱敏主体标识
     * @param action     动作名
     * @param targetType 目标类型
     * @param targetId   目标标识
     * @param resultCode 标准结果码
     * @param traceId    链路追踪 ID
     * @param detailJson 脱敏详情 JSON
     * @param occurredAt 发生时间
     */
    public void insert(String actorType, String actorId, String action,
                       String targetType, String targetId, String resultCode,
                       String traceId, String detailJson, java.time.Instant occurredAt) {
        AuditLogPO po = new AuditLogPO();
        po.setAuditId(idSequence.incrementAndGet());
        po.setActorType(actorType);
        po.setActorId(actorId);
        po.setAction(action);
        po.setTargetType(targetType);
        po.setTargetId(targetId);
        po.setResultCode(resultCode);
        po.setTraceId(traceId);
        po.setDetailJson(detailJson);
        po.setOccurredAt(occurredAt);
        mapper.insert(po);
    }
}
