package com.minialalipay.ai.infrastructure.persistence;

import com.minialalipay.ai.domain.agent.IdempotencyRecord;
import com.minialalipay.ai.domain.agent.IdempotencyRepository;
import com.minialalipay.ai.domain.agent.IdempotencyStatus;
import com.minialalipay.ai.domain.agent.AgentErrorCode;
import com.minialalipay.ai.infrastructure.persistence.mapper.IdempotencyRecordMapper;
import com.minialalipay.ai.infrastructure.persistence.po.IdempotencyRecordPO;
import com.minialalipay.common.error.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * MyBatis 实现的 AI 幂等记录仓储。
 */
@Repository
public class MyBatisIdempotencyRepository implements IdempotencyRepository {

    private final IdempotencyRecordMapper mapper;

    public MyBatisIdempotencyRepository(IdempotencyRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<IdempotencyRecord> findByScope(
            String principalKey, String apiScope, String idempotencyKey) {
        IdempotencyRecordPO po = mapper.findByScope(principalKey, apiScope, idempotencyKey);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public void insert(IdempotencyRecord record) {
        try {
            mapper.insert(toPo(record));
        } catch (DuplicateKeyException e) {
            throw new BusinessException(AgentErrorCode.IDEMPOTENCY_CONFLICT);
        }
    }

    @Override
    public void markCompleted(String recordId, String resourceType,
                              String resourceId, String responseJson) {
        int rows = mapper.markCompleted(recordId, resourceType, resourceId,
                responseJson, Instant.now());
        if (rows == 0) {
            throw new BusinessException(AgentErrorCode.VERSION_CONFLICT);
        }
    }

    @Override
    public void markFailed(String recordId) {
        mapper.markFailed(recordId, Instant.now());
    }

    @Override
    public int deleteExpiredRecords() {
        return mapper.deleteExpired(Instant.now());
    }

    private IdempotencyRecord toDomain(IdempotencyRecordPO po) {
        return new IdempotencyRecord(
                po.getRecordId(), po.getPrincipalKey(), po.getApiScope(),
                po.getIdempotencyKey(), po.getRequestDigest(),
                po.getResourceType(), po.getResourceId(), po.getResponseJson(),
                IdempotencyStatus.valueOf(po.getStatus()),
                po.getExpiresAt(), po.getCreatedAt(), po.getUpdatedAt());
    }

    private IdempotencyRecordPO toPo(IdempotencyRecord record) {
        IdempotencyRecordPO po = new IdempotencyRecordPO();
        po.setRecordId(record.recordId());
        po.setPrincipalKey(record.principalKey());
        po.setApiScope(record.apiScope());
        po.setIdempotencyKey(record.idempotencyKey());
        po.setRequestDigest(record.requestDigest());
        po.setResourceType(record.resourceType());
        po.setResourceId(record.resourceId());
        po.setResponseJson(record.responseJson());
        po.setStatus(record.status().name());
        po.setExpiresAt(record.expiresAt());
        po.setCreatedAt(record.createdAt());
        po.setUpdatedAt(record.updatedAt());
        return po;
    }
}
