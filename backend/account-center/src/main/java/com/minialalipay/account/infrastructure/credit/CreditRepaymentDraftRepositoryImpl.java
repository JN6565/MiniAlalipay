package com.minialalipay.account.infrastructure.credit;

import com.minialalipay.account.domain.repayment.CreditRepaymentDraft;
import com.minialalipay.account.domain.repayment.CreditRepaymentDraftRepository;
import com.minialalipay.account.domain.repayment.CreditRepaymentDraftStatus;
import com.minialalipay.account.infrastructure.credit.mapper.CreditRepaymentDraftMapper;
import com.minialalipay.account.infrastructure.credit.po.CreditRepaymentDraftPO;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 信用还款草稿仓储实现类。
 *
 * <p>基于 {@link CreditRepaymentDraftMapper} 实现还款草稿的持久化操作，
 * 负责领域对象 {@link CreditRepaymentDraft} 与 {@link CreditRepaymentDraftPO} 之间的转换。</p>
 */
@Repository
public class CreditRepaymentDraftRepositoryImpl implements CreditRepaymentDraftRepository {

    private final CreditRepaymentDraftMapper creditRepaymentDraftMapper;

    public CreditRepaymentDraftRepositoryImpl(CreditRepaymentDraftMapper creditRepaymentDraftMapper) {
        this.creditRepaymentDraftMapper = creditRepaymentDraftMapper;
    }

    @Override
    public Optional<CreditRepaymentDraft> findById(String repaymentDraftId) {
        CreditRepaymentDraftPO po = creditRepaymentDraftMapper.findById(repaymentDraftId);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public void save(CreditRepaymentDraft draft) {
        CreditRepaymentDraftPO existing = creditRepaymentDraftMapper.findById(draft.getRepaymentDraftId());
        if (existing == null) {
            creditRepaymentDraftMapper.insert(toPO(draft));
        } else {
            int updated = creditRepaymentDraftMapper.updateStatus(
                    draft.getRepaymentDraftId(),
                    draft.getStatus().name(),
                    draft.getUpdatedAt(),
                    draft.getVersion()
            );
            if (updated == 0) {
                throw new IllegalStateException("还款草稿乐观锁冲突，repaymentDraftId=" + draft.getRepaymentDraftId());
            }
            draft.updateVersion(draft.getVersion() + 1);
        }
    }

    /**
     * 将持久化对象转换为领域对象。
     */
    private CreditRepaymentDraft toDomain(CreditRepaymentDraftPO po) {
        return new CreditRepaymentDraft(
                po.getRepaymentDraftId(),
                po.getUserId(),
                po.getCreditAccountId(),
                po.getPayerAccountId(),
                po.getAmountFen(),
                po.getAllocationSnapshot(),
                po.getAllocationHash(),
                CreditRepaymentDraftStatus.valueOf(po.getStatus()),
                po.getVersion(),
                po.getExpiresAt(),
                po.getCreatedAt(),
                po.getUpdatedAt()
        );
    }

    /**
     * 将领域对象转换为持久化对象。
     */
    private CreditRepaymentDraftPO toPO(CreditRepaymentDraft draft) {
        CreditRepaymentDraftPO po = new CreditRepaymentDraftPO();
        po.setRepaymentDraftId(draft.getRepaymentDraftId());
        po.setUserId(draft.getUserId());
        po.setCreditAccountId(draft.getCreditAccountId());
        po.setPayerAccountId(draft.getPayerAccountId());
        po.setAmountFen(draft.getAmountFen());
        po.setAllocationSnapshot(draft.getAllocationSnapshot());
        po.setAllocationHash(draft.getAllocationHash());
        po.setStatus(draft.getStatus().name());
        po.setVersion(draft.getVersion());
        po.setExpiresAt(draft.getExpiresAt());
        po.setCreatedAt(draft.getCreatedAt());
        po.setUpdatedAt(draft.getUpdatedAt());
        return po;
    }
}
