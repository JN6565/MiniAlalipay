package com.minialalipay.account.infrastructure.credit;

import com.minialalipay.account.domain.credit.CreditJobRun;
import com.minialalipay.account.domain.credit.CreditJobRunRepository;
import com.minialalipay.account.domain.credit.CreditJobStatus;
import com.minialalipay.account.domain.credit.CreditJobTriggerType;
import com.minialalipay.account.domain.credit.CreditJobType;
import com.minialalipay.account.infrastructure.credit.mapper.CreditJobRunMapper;
import com.minialalipay.account.infrastructure.credit.po.CreditJobRunPO;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 信用定时任务执行记录仓储实现类。
 *
 * <p>基于 {@link CreditJobRunMapper} 实现任务执行记录的持久化操作，
 * 负责领域对象 {@link CreditJobRun} 与 {@link CreditJobRunPO} 之间的转换。</p>
 *
 * <p>领域对象 {@link CreditJobRun} 与 {@link CreditJobRunPO} 同名同构，
 * 15 个字段一一映射，无字段丢弃或错位。</p>
 */
@Repository
public class CreditJobRunRepositoryImpl implements CreditJobRunRepository {

    private final CreditJobRunMapper creditJobRunMapper;

    public CreditJobRunRepositoryImpl(CreditJobRunMapper creditJobRunMapper) {
        this.creditJobRunMapper = creditJobRunMapper;
    }

    @Override
    public Optional<CreditJobRun> findByJobTypeAndBusinessDate(String jobType, LocalDate businessDate) {
        CreditJobRunPO po = creditJobRunMapper.findByJobTypeAndBusinessDate(jobType, businessDate);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public void save(CreditJobRun jobRun) {
        CreditJobRunPO existing = creditJobRunMapper.findByJobTypeAndBusinessDate(
                jobRun.getJobType().name(), jobRun.getBusinessDate());
        if (existing == null) {
            creditJobRunMapper.insert(toPO(jobRun));
        } else {
            int updated = creditJobRunMapper.updateByCas(toPO(jobRun));
            if (updated == 0) {
                throw new IllegalStateException("任务执行记录乐观锁冲突，runId=" + jobRun.getRunId());
            }
            jobRun.updateVersion(jobRun.getVersion() + 1);
        }
    }

    /**
     * 将持久化对象转换为领域对象。
     */
    private CreditJobRun toDomain(CreditJobRunPO po) {
        return new CreditJobRun(
                po.getRunId(),
                CreditJobType.valueOf(po.getJobType()),
                po.getBusinessDate(),
                CreditJobStatus.valueOf(po.getStatus()),
                po.getCursorCreditAccountId(),
                CreditJobTriggerType.valueOf(po.getTriggerType()),
                po.getTriggeredByUserId(),
                po.getRequestDigest(),
                po.getRetryCount() == null ? 0 : po.getRetryCount(),
                po.getVersion() == null ? 0L : po.getVersion(),
                po.getStartedAt(),
                po.getCompletedAt(),
                po.getCreatedAt(),
                po.getUpdatedAt(),
                po.getErrorCode()
        );
    }

    /**
     * 将领域对象转换为持久化对象。
     */
    private CreditJobRunPO toPO(CreditJobRun jobRun) {
        CreditJobRunPO po = new CreditJobRunPO();
        po.setRunId(jobRun.getRunId());
        po.setJobType(jobRun.getJobType().name());
        po.setBusinessDate(jobRun.getBusinessDate());
        po.setStatus(jobRun.getStatus().name());
        po.setCursorCreditAccountId(jobRun.getCursorCreditAccountId());
        po.setTriggerType(jobRun.getTriggerType().name());
        po.setTriggeredByUserId(jobRun.getTriggeredByUserId());
        po.setRequestDigest(jobRun.getRequestDigest());
        po.setRetryCount(jobRun.getRetryCount());
        po.setErrorCode(jobRun.getErrorCode());
        po.setVersion(jobRun.getVersion());
        po.setStartedAt(jobRun.getStartedAt());
        po.setCompletedAt(jobRun.getCompletedAt());
        po.setCreatedAt(jobRun.getCreatedAt());
        po.setUpdatedAt(jobRun.getUpdatedAt());
        return po;
    }
}
