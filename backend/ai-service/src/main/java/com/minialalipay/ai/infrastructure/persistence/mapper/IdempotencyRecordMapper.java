package com.minialalipay.ai.infrastructure.persistence.mapper;

import com.minialalipay.ai.infrastructure.persistence.po.IdempotencyRecordPO;
import org.apache.ibatis.annotations.*;

/**
 * {@code agent_db.idempotency_record} MyBatis Mapper。
 */
@Mapper
public interface IdempotencyRecordMapper {

    @Select("SELECT * FROM agent_db.idempotency_record "
            + "WHERE principal_key = #{principalKey} "
            + "AND api_scope = #{apiScope} "
            + "AND idempotency_key = #{idempotencyKey}")
    IdempotencyRecordPO findByScope(
            @Param("principalKey") String principalKey,
            @Param("apiScope") String apiScope,
            @Param("idempotencyKey") String idempotencyKey);

    @Insert("INSERT INTO agent_db.idempotency_record "
            + "(record_id, principal_key, api_scope, idempotency_key, "
            + " request_digest, resource_type, resource_id, response_json, "
            + " status, expires_at, created_at, updated_at) "
            + "VALUES (#{recordId}, #{principalKey}, #{apiScope}, #{idempotencyKey}, "
            + " #{requestDigest}, #{resourceType}, #{resourceId}, #{responseJson}, "
            + " #{status}, #{expiresAt}, #{createdAt}, #{updatedAt})")
    void insert(IdempotencyRecordPO po);

    @Update("UPDATE agent_db.idempotency_record "
            + "SET status = 'COMPLETED', resource_type = #{resourceType}, "
            + " resource_id = #{resourceId}, response_json = #{responseJson}, "
            + " updated_at = #{now} "
            + "WHERE record_id = #{recordId} AND status = 'PROCESSING'")
    int markCompleted(@Param("recordId") String recordId,
                      @Param("resourceType") String resourceType,
                      @Param("resourceId") String resourceId,
                      @Param("responseJson") String responseJson,
                      @Param("now") java.time.Instant now);

    @Update("UPDATE agent_db.idempotency_record "
            + "SET status = 'FAILED', updated_at = #{now} "
            + "WHERE record_id = #{recordId} AND status = 'PROCESSING'")
    int markFailed(@Param("recordId") String recordId,
                   @Param("now") java.time.Instant now);

    @Delete("DELETE FROM agent_db.idempotency_record WHERE expires_at < #{now}")
    int deleteExpired(@Param("now") java.time.Instant now);
}
