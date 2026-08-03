package com.minialalipay.account.infrastructure.credit;

import com.minialalipay.account.domain.bill.CreditBill;
import com.minialalipay.account.domain.bill.CreditBillRepository;
import com.minialalipay.account.domain.bill.CreditBillStatus;
import com.minialalipay.account.infrastructure.credit.mapper.CreditBillMapper;
import com.minialalipay.account.infrastructure.credit.po.CreditBillPO;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 信用月度账单仓储实现类。
 *
 * <p>基于 {@link CreditBillMapper} 实现账单的持久化操作，
 * 负责领域对象 {@link CreditBill} 与 {@link CreditBillPO} 之间的转换。</p>
 */
@Repository
public class CreditBillRepositoryImpl implements CreditBillRepository {

    private final CreditBillMapper creditBillMapper;

    public CreditBillRepositoryImpl(CreditBillMapper creditBillMapper) {
        this.creditBillMapper = creditBillMapper;
    }

    @Override
    public Optional<CreditBill> findById(String billId) {
        CreditBillPO po = creditBillMapper.findById(billId);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public Optional<CreditBill> findByCreditAccountIdAndPeriod(String creditAccountId, String period) {
        CreditBillPO po = creditBillMapper.findByCreditAccountIdAndPeriod(creditAccountId, period);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public List<CreditBill> findByCreditAccountId(String creditAccountId) {
        List<CreditBillPO> pos = creditBillMapper.findByCreditAccountId(creditAccountId);
        return pos.stream().map(this::toDomain).toList();
    }

    @Override
    public void save(CreditBill bill) {
        CreditBillPO existing = creditBillMapper.findById(bill.getBillId());
        if (existing == null) {
            creditBillMapper.insert(toPO(bill));
        } else {
            int updated = creditBillMapper.updateByCas(toPO(bill));
            if (updated == 0) {
                throw new IllegalStateException("账单乐观锁冲突，billId=" + bill.getBillId());
            }
            bill.updateVersion(bill.getVersion() + 1);
        }
    }

    /**
     * 将持久化对象转换为领域对象。
     */
    private CreditBill toDomain(CreditBillPO po) {
        return new CreditBill(
                po.getBillId(),
                po.getCreditAccountId(),
                po.getPeriod(),
                po.getStatementDate(),
                po.getDueAt(),
                po.getTotalFen(),
                po.getPaidFen(),
                po.getReversedFen(),
                po.getOutstandingFen(),
                CreditBillStatus.valueOf(po.getStatus()),
                po.getVersion(),
                po.getCreatedAt(),
                po.getUpdatedAt()
        );
    }

    /**
     * 将领域对象转换为持久化对象。
     */
    private CreditBillPO toPO(CreditBill bill) {
        CreditBillPO po = new CreditBillPO();
        po.setBillId(bill.getBillId());
        po.setCreditAccountId(bill.getCreditAccountId());
        po.setPeriod(bill.getPeriod());
        po.setStatementDate(bill.getStatementDate());
        po.setDueAt(bill.getDueAt());
        po.setTotalFen(bill.getTotalFen());
        po.setPaidFen(bill.getPaidFen());
        po.setReversedFen(bill.getReversedFen());
        po.setOutstandingFen(bill.getOutstandingFen());
        po.setStatus(bill.getStatus().name());
        po.setVersion(bill.getVersion());
        po.setCreatedAt(bill.getCreatedAt());
        po.setUpdatedAt(bill.getUpdatedAt());
        return po;
    }
}
