package com.minialalipay.account.infrastructure.credit;

import com.minialalipay.account.domain.credit.CreditFreeze;
import com.minialalipay.account.domain.credit.CreditFreezeRepository;
import com.minialalipay.account.domain.credit.CreditFreezeStatus;
import com.minialalipay.account.infrastructure.credit.mapper.CreditFreezeMapper;
import com.minialalipay.account.infrastructure.credit.po.CreditFreezePO;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 信用额度冻结记录仓储实现类。
 *
 * <p>基于 {@link CreditFreezeMapper} 实现冻结记录的持久化操作，
 * 负责领域对象 {@link CreditFreeze} 与 {@link CreditFreezePO} 之间的转换。</p>
 */
@Repository
public class CreditFreezeRepositoryImpl implements CreditFreezeRepository {

    private final CreditFreezeMapper creditFreezeMapper;

    public CreditFreezeRepositoryImpl(CreditFreezeMapper creditFreezeMapper) {
        this.creditFreezeMapper = creditFreezeMapper;
    }

    @Override
    public Optional<CreditFreeze> findByTransactionIdAndAccountId(String transactionId, String creditAccountId) {
        CreditFreezePO po = creditFreezeMapper.findByTransactionIdAndAccountId(transactionId, creditAccountId);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public Optional<CreditFreeze> findByTransactionId(String transactionId) {
        CreditFreezePO po = creditFreezeMapper.findByTransactionId(transactionId);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public void save(CreditFreeze freeze) {
        CreditFreezePO existing = creditFreezeMapper.findByTransactionIdAndAccountId(
                freeze.getTransactionId(), freeze.getCreditAccountId());
        if (existing == null) {
            creditFreezeMapper.insert(toPO(freeze));
        } else {
            int updated = creditFreezeMapper.updateStatus(
                    freeze.getCreditFreezeId(),
                    freeze.getStatus().name(),
                    freeze.getUpdatedAt(),
                    freeze.getVersion()
            );
            if (updated == 0) {
                throw new IllegalStateException("冻结记录乐观锁冲突，creditFreezeId=" + freeze.getCreditFreezeId());
            }
            freeze.updateVersion(freeze.getVersion() + 1);
        }
    }

    /**
     * 将持久化对象转换为领域对象。
     */
    private CreditFreeze toDomain(CreditFreezePO po) {
        return new CreditFreeze(
                po.getCreditFreezeId(),
                po.getTransactionId(),
                po.getCreditAccountId(),
                po.getAmountFen(),
                CreditFreezeStatus.valueOf(po.getStatus()),
                po.getBranchXid(),
                po.getVersion(),
                po.getCreatedAt(),
                po.getUpdatedAt()
        );
    }

    /**
     * 将领域对象转换为持久化对象。
     */
    private CreditFreezePO toPO(CreditFreeze freeze) {
        CreditFreezePO po = new CreditFreezePO();
        po.setCreditFreezeId(freeze.getCreditFreezeId());
        po.setTransactionId(freeze.getTransactionId());
        po.setCreditAccountId(freeze.getCreditAccountId());
        po.setAmountFen(freeze.getAmountFen());
        po.setStatus(freeze.getStatus().name());
        po.setBranchXid(freeze.getBranchXid());
        po.setVersion(freeze.getVersion());
        po.setCreatedAt(freeze.getCreatedAt());
        po.setUpdatedAt(freeze.getUpdatedAt());
        return po;
    }
}
