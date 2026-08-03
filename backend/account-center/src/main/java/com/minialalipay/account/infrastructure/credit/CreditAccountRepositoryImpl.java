package com.minialalipay.account.infrastructure.credit;

import com.minialalipay.account.domain.credit.CreditAccount;
import com.minialalipay.account.domain.credit.CreditAccountRepository;
import com.minialalipay.account.domain.credit.CreditAccountStatus;
import com.minialalipay.account.infrastructure.credit.mapper.CreditAccountMapper;
import com.minialalipay.account.infrastructure.credit.po.CreditAccountPO;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 信用额度账户仓储实现类。
 *
 * <p>基于 {@link CreditAccountMapper} 实现信用账户的持久化操作，
 * 负责领域对象 {@link CreditAccount} 与 {@link CreditAccountPO} 之间的转换。</p>
 */
@Repository
public class CreditAccountRepositoryImpl implements CreditAccountRepository {

    private final CreditAccountMapper creditAccountMapper;

    public CreditAccountRepositoryImpl(CreditAccountMapper creditAccountMapper) {
        this.creditAccountMapper = creditAccountMapper;
    }

    @Override
    public Optional<CreditAccount> findByUserId(String userId) {
        CreditAccountPO po = creditAccountMapper.findByUserId(userId);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public Optional<CreditAccount> findById(String creditAccountId) {
        CreditAccountPO po = creditAccountMapper.findByCreditAccountId(creditAccountId);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public void save(CreditAccount account) {
        CreditAccountPO existing = creditAccountMapper.findByCreditAccountId(account.getCreditAccountId());
        if (existing == null) {
            creditAccountMapper.insert(toPO(account));
        } else {
            int updated = creditAccountMapper.updateByCas(toPO(account));
            if (updated == 0) {
                throw new IllegalStateException("信用账户乐观锁冲突，creditAccountId=" + account.getCreditAccountId());
            }
            account.updateVersion(account.getVersion() + 1);
        }
    }

    /**
     * 将持久化对象转换为领域对象。
     */
    private CreditAccount toDomain(CreditAccountPO po) {
        return new CreditAccount(
                po.getCreditAccountId(),
                po.getUserId(),
                po.getTotalLimitFen(),
                po.getUsedFen(),
                po.getFrozenFen(),
                CreditAccountStatus.valueOf(po.getStatus()),
                po.getSuspendReason(),
                po.getVersion(),
                po.getCreatedAt(),
                po.getUpdatedAt()
        );
    }

    /**
     * 将领域对象转换为持久化对象。
     */
    private CreditAccountPO toPO(CreditAccount account) {
        CreditAccountPO po = new CreditAccountPO();
        po.setCreditAccountId(account.getCreditAccountId());
        po.setUserId(account.getUserId());
        po.setTotalLimitFen(account.getTotalLimitFen());
        po.setUsedFen(account.getUsedFen());
        po.setFrozenFen(account.getFrozenFen());
        po.setStatus(account.getStatus().name());
        po.setSuspendReason(account.getSuspendReason());
        po.setVersion(account.getVersion());
        po.setCreatedAt(account.getCreatedAt());
        po.setUpdatedAt(account.getUpdatedAt());
        return po;
    }
}
