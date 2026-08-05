package com.minialalipay.ai.infrastructure.persistence.mapper;

import com.minialalipay.ai.infrastructure.persistence.po.OutboxEventPO;
import org.apache.ibatis.annotations.*;

/**
 * {@code agent_db.outbox_event} MyBatis Mapper。
 */
@Mapper
public interface OutboxEventMapper {

    @Insert("INSERT INTO agent_db.outbox_event "
            + "(event_id, aggregate_type, aggregate_id, aggregate_version, "
            + " event_type, event_version, business_type, source_type, "
            + " source_order_id, funding_source, transaction_id, producer, "
            + " account_id, merchant_account_id, user_id_hash, trace_id, "
            + " occurred_at, payload, status, retry_count, next_retry_at, "
            + " created_at, published_at) "
            + "VALUES (#{eventId}, #{aggregateType}, #{aggregateId}, #{aggregateVersion}, "
            + " #{eventType}, #{eventVersion}, #{businessType}, #{sourceType}, "
            + " #{sourceOrderId}, #{fundingSource}, #{transactionId}, #{producer}, "
            + " #{accountId}, #{merchantAccountId}, #{userIdHash}, #{traceId}, "
            + " #{occurredAt}, #{payload}, #{status}, #{retryCount}, #{nextRetryAt}, "
            + " #{createdAt}, #{publishedAt})")
    void insert(OutboxEventPO po);

    @Update("UPDATE agent_db.outbox_event "
            + "SET status = 'PUBLISHED', published_at = #{now} "
            + "WHERE event_id = #{eventId} AND status = 'PENDING'")
    int markPublished(@Param("eventId") String eventId,
                      @Param("now") java.time.Instant now);
}
