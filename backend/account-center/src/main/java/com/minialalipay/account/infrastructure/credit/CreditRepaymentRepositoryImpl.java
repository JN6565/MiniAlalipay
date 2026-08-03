package com.minialalipay.account.infrastructure.credit;

import com.minialalipay.account.domain.repayment.CreditRepayment;
import com.minialalipay.account.domain.repayment.CreditRepaymentRepository;
import com.minialalipay.account.domain.repayment.CreditRepaymentStatus;
import com.minialalipay.account.infrastructure.credit.mapper.CreditRepaymentMapper;
import com.minialalipay.account.infrastructure.credit.po.CreditRepaymentPO;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 信用还款记录仓储实现类。
 *
 * <p>基于 {@link CreditRepaymentMapper} 实现还款记录的持久化操作，
 * 负责领域对象 {@link CreditRepayment} 与 {@link CreditRepaymentPO} 之间的转换。</p>
 */
@Repository
public class CreditRepaymentRepositoryImpl implements CreditRepaymentRepository {

    private final CreditRepaymentMapper creditRepaymentMapper;

    public CreditRepaymentRepositoryImpl(CreditRepaymentMapper creditRepaymentMapper) {
        this.creditRepaymentMapper = creditRepaymentMapper;
    }

    @Override
    public Optional<CreditRepayment> findById(String repaymentId) {
        CreditRepaymentPO po = creditRepaymentMapper.findById(repaymentId);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public Optional<CreditRepayment> findByRepaymentDraftId(String repaymentDraftId) {
        CreditRepaymentPO po = creditRepaymentMapper.findByRepaymentDraftId(repaymentDraftId);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public Optional<CreditRepayment> findByTransactionId(String transactionId) {
        CreditRepaymentPO po = creditRepaymentMapper.findByTransactionId(transactionId);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public void save(CreditRepayment repayment) {
        CreditRepaymentPO existing = creditRepaymentMapper.findById(repayment.getRepaymentId());
        if (existing == null) {
            creditRepaymentMapper.insert(toPO(repayment));
        } else {
            creditRepaymentMapper.updateStatus(
                    repayment.getRepaymentId(),
                    repayment.getStatus().name(),
                    repayment.getUpdatedAt()
            );
        }
    }

    /**
     * 将持久化对象转换为领域对象。
     */
    private CreditRepayment toDomain(CreditRepaymentPO po) {
        return new CreditRepayment(
                po.getRepaymentId(),
                po.getRepaymentDraftId(),
                po.getTransactionId(),
                po.getCreditAccountId(),
                po.getAmountFen(),
                CreditRepaymentStatus.valueOf(po.getStatus()),
                po.getCreatedAt(),
                po.getUpdatedAt()
        );
    }

    /**
     * 将领域对象转换为持久化对象。
     */
    private CreditRepaymentPO toPO(CreditRepayment repayment) {
        CreditRepaymentPO po = new CreditRepaymentPO();
        po.setRepaymentId(repayment.getRepaymentId());
        po.setRepaymentDraftId(repayment.getRepaymentDraftId());
        po.setTransactionId(repayment.getTransactionId());
        po.setCreditAccountId(repayment.getCreditAccountId());
        po.setAmountFen(repayment.getAmountFen());
        po.setStatus(repayment.getStatus().name());
        po.setCreatedAt(repayment.getCreatedAt());
        po.setUpdatedAt(repayment.getUpdatedAt());
        return po;
    }
}
