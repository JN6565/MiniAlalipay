package com.minialalipay.account.infrastructure.credit;

import com.minialalipay.account.domain.credit.CreditReceivable;
import com.minialalipay.account.domain.credit.CreditReceivableRepository;
import com.minialalipay.account.infrastructure.credit.mapper.CreditReceivableMapper;
import com.minialalipay.account.infrastructure.credit.po.CreditReceivablePO;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 信用应收汇总仓储实现类。
 *
 * <p>基于 {@link CreditReceivableMapper} 实现应收汇总的持久化操作，
 * 负责领域对象 {@link CreditReceivable} 与 {@link CreditReceivablePO} 之间的转换。</p>
 */
@Repository
public class CreditReceivableRepositoryImpl implements CreditReceivableRepository {

    private final CreditReceivableMapper creditReceivableMapper;

    public CreditReceivableRepositoryImpl(CreditReceivableMapper creditReceivableMapper) {
        this.creditReceivableMapper = creditReceivableMapper;
    }

    @Override
    public Optional<CreditReceivable> findByCreditAccountId(String creditAccountId) {
        CreditReceivablePO po = creditReceivableMapper.findByCreditAccountId(creditAccountId);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public void save(CreditReceivable receivable) {
        CreditReceivablePO existing = creditReceivableMapper.findByCreditAccountId(receivable.getCreditAccountId());
        if (existing == null) {
            creditReceivableMapper.insert(toPO(receivable));
        } else {
            int updated = creditReceivableMapper.updateByCas(toPO(receivable));
            if (updated == 0) {
                throw new IllegalStateException("应收汇总乐观锁冲突，creditAccountId=" + receivable.getCreditAccountId());
            }
            receivable.updateVersion(receivable.getVersion() + 1);
        }
    }

    /**
     * 将持久化对象转换为领域对象。
     */
    private CreditReceivable toDomain(CreditReceivablePO po) {
        return new CreditReceivable(
                po.getCreditAccountId(),
                po.getUnbilledFen(),
                po.getBilledFen(),
                po.getOverdueFen(),
                po.getVersion(),
                po.getUpdatedAt()
        );
    }

    /**
     * 将领域对象转换为持久化对象。
     */
    private CreditReceivablePO toPO(CreditReceivable receivable) {
        CreditReceivablePO po = new CreditReceivablePO();
        po.setCreditAccountId(receivable.getCreditAccountId());
        po.setUnbilledFen(receivable.getUnbilledFen());
        po.setBilledFen(receivable.getBilledFen());
        po.setOverdueFen(receivable.getOverdueFen());
        po.setVersion(receivable.getVersion());
        po.setUpdatedAt(receivable.getUpdatedAt());
        return po;
    }
}
