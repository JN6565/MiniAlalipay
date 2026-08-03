package com.minialalipay.account.infrastructure.credit;

import com.minialalipay.account.domain.bill.CreditBillItem;
import com.minialalipay.account.domain.bill.CreditBillItemRepository;
import com.minialalipay.account.domain.bill.CreditBillItemStatus;
import com.minialalipay.account.infrastructure.credit.mapper.CreditBillItemMapper;
import com.minialalipay.account.infrastructure.credit.po.CreditBillItemPO;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 信用账单明细仓储实现类。
 *
 * <p>基于 {@link CreditBillItemMapper} 实现账单明细的持久化操作，
 * 负责领域对象 {@link CreditBillItem} 与 {@link CreditBillItemPO} 之间的转换。</p>
 */
@Repository
public class CreditBillItemRepositoryImpl implements CreditBillItemRepository {

    private final CreditBillItemMapper creditBillItemMapper;

    public CreditBillItemRepositoryImpl(CreditBillItemMapper creditBillItemMapper) {
        this.creditBillItemMapper = creditBillItemMapper;
    }

    @Override
    public List<CreditBillItem> findByBillId(String billId) {
        List<CreditBillItemPO> pos = creditBillItemMapper.findByBillId(billId);
        return pos.stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<CreditBillItem> findByPurchaseId(String purchaseId) {
        CreditBillItemPO po = creditBillItemMapper.findByPurchaseId(purchaseId);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public void save(CreditBillItem item) {
        CreditBillItemPO existing = creditBillItemMapper.findByPurchaseId(item.getPurchaseId());
        if (existing == null) {
            creditBillItemMapper.insert(toPO(item));
        } else {
            creditBillItemMapper.update(toPO(item));
        }
    }

    /**
     * 将持久化对象转换为领域对象。
     */
    private CreditBillItem toDomain(CreditBillItemPO po) {
        return new CreditBillItem(
                po.getBillId(),
                po.getPurchaseId(),
                po.getAmountFen(),
                po.getAllocatedPaidFen(),
                po.getReversedFen(),
                CreditBillItemStatus.valueOf(po.getStatus()),
                po.getCreatedAt(),
                po.getUpdatedAt()
        );
    }

    /**
     * 将领域对象转换为持久化对象。
     */
    private CreditBillItemPO toPO(CreditBillItem item) {
        CreditBillItemPO po = new CreditBillItemPO();
        po.setBillId(item.getBillId());
        po.setPurchaseId(item.getPurchaseId());
        po.setAmountFen(item.getAmountFen());
        po.setAllocatedPaidFen(item.getAllocatedPaidFen());
        po.setReversedFen(item.getReversedFen());
        po.setStatus(item.getStatus().name());
        po.setCreatedAt(item.getCreatedAt());
        po.setUpdatedAt(item.getUpdatedAt());
        return po;
    }
}
