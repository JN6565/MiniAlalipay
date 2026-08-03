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
 * <p>字段映射说明：
 * <ul>
 *   <li>领域对象 {@code runId} ↔ PO {@code jobRunId}</li>
 *   <li>领域对象 {@code completedAt} ↔ PO {@code finishedAt}</li>
 *   <li>领域对象 {@code errorCode} ↔ PO {@code errorMessage}</li>
 *   <li>领域对象 {@code cursorCreditAccountId}、{@code triggerType}、{@code triggeredByUserId}
 *       在当前 PO 中无对应字段，持久化时不写入</li>
 * </ul>
 * </p>
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
                po.getJobRunId(),
                CreditJobType.valueOf(po.getJobType()),
                po.getBusinessDate(),
                CreditJobStatus.valueOf(po.getStatus()),
                null,
                null,
                null,
                po.getVersion(),
                po.getStartedAt(),
                po.getFinishedAt(),
                po.getCreatedAt(),
                po.getUpdatedAt(),
                po.getErrorMessage()
        );
    }

    /**
     * 将领域对象转换为持久化对象。
     */
    private CreditJobRunPO toPO(CreditJobRun jobRun) {
        CreditJobRunPO po = new CreditJobRunPO();
        po.setJobRunId(jobRun.getRunId());
        po.setJobType(jobRun.getJobType().name());
        po.setBusinessDate(jobRun.getBusinessDate());
        po.setStatus(jobRun.getStatus().name());
        po.setStartedAt(jobRun.getStartedAt());
        po.setFinishedAt(jobRun.getCompletedAt());
        po.setErrorMessage(jobRun.getErrorCode());
        po.setVersion(jobRun.getVersion());
        po.setCreatedAt(jobRun.getCreatedAt());
        po.setUpdatedAt(jobRun.getUpdatedAt());
        return po;
    }
}
