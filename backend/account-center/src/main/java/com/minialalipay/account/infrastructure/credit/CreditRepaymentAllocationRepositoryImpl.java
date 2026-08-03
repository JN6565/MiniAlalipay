package com.minialalipay.account.infrastructure.credit;

import com.minialalipay.account.domain.credit.RepaymentAllocationType;
import com.minialalipay.account.domain.repayment.CreditRepaymentAllocation;
import com.minialalipay.account.domain.repayment.CreditRepaymentAllocationDetail;
import com.minialalipay.account.domain.repayment.CreditRepaymentAllocationRepository;
import com.minialalipay.account.infrastructure.credit.mapper.CreditRepaymentAllocationDetailMapper;
import com.minialalipay.account.infrastructure.credit.mapper.CreditRepaymentAllocationMapper;
import com.minialalipay.account.infrastructure.credit.po.CreditRepaymentAllocationDetailPO;
import com.minialalipay.account.infrastructure.credit.po.CreditRepaymentAllocationPO;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * 信用还款分配计划仓储实现类。
 *
 * <p>基于 {@link CreditRepaymentAllocationMapper} 和 {@link CreditRepaymentAllocationDetailMapper}
 * 实现还款分配计划及其明细的持久化操作。查询时同时读取分配主记录和明细记录，
 * 按 sequenceNo 关联组装为完整的领域对象 {@link CreditRepaymentAllocation}。</p>
 */
@Repository
public class CreditRepaymentAllocationRepositoryImpl implements CreditRepaymentAllocationRepository {

    private final CreditRepaymentAllocationMapper creditRepaymentAllocationMapper;
    private final CreditRepaymentAllocationDetailMapper creditRepaymentAllocationDetailMapper;

    public CreditRepaymentAllocationRepositoryImpl(
            CreditRepaymentAllocationMapper creditRepaymentAllocationMapper,
            CreditRepaymentAllocationDetailMapper creditRepaymentAllocationDetailMapper) {
        this.creditRepaymentAllocationMapper = creditRepaymentAllocationMapper;
        this.creditRepaymentAllocationDetailMapper = creditRepaymentAllocationDetailMapper;
    }

    @Override
    public List<CreditRepaymentAllocation> findByRepaymentId(String repaymentId) {
        List<CreditRepaymentAllocationPO> allocationPOs = creditRepaymentAllocationMapper.findByRepaymentId(repaymentId);
        if (allocationPOs.isEmpty()) {
            return List.of();
        }
        List<CreditRepaymentAllocationDetailPO> detailPOs =
                creditRepaymentAllocationDetailMapper.findByRepaymentId(repaymentId);

        List<CreditRepaymentAllocation> result = new ArrayList<>(allocationPOs.size());
        for (CreditRepaymentAllocationPO apo : allocationPOs) {
            CreditRepaymentAllocation allocation = toDomain(apo);
            for (CreditRepaymentAllocationDetailPO dpo : detailPOs) {
                if (dpo.getSequenceNo().equals(apo.getSequenceNo())) {
                    allocation.addDetail(toDetailDomain(dpo));
                }
            }
            result.add(allocation);
        }
        return result;
    }

    @Override
    public void saveAll(String repaymentId, List<CreditRepaymentAllocation> allocations) {
        for (CreditRepaymentAllocation allocation : allocations) {
            creditRepaymentAllocationMapper.insert(toPO(allocation));
            for (CreditRepaymentAllocationDetail detail : allocation.getDetails()) {
                creditRepaymentAllocationDetailMapper.insert(toDetailPO(detail));
            }
        }
    }

    /**
     * 将分配持久化对象转换为领域对象。
     */
    private CreditRepaymentAllocation toDomain(CreditRepaymentAllocationPO po) {
        return new CreditRepaymentAllocation(
                po.getRepaymentId(),
                po.getSequenceNo(),
                RepaymentAllocationType.valueOf(po.getTargetType()),
                po.getTargetId(),
                po.getAmountFen(),
                po.getCreatedAt()
        );
    }

    /**
     * 将分配明细持久化对象转换为领域对象。
     */
    private CreditRepaymentAllocationDetail toDetailDomain(CreditRepaymentAllocationDetailPO po) {
        return new CreditRepaymentAllocationDetail(
                po.getRepaymentId(),
                po.getSequenceNo(),
                po.getDetailNo(),
                po.getPurchaseId(),
                po.getBillId(),
                po.getAmountFen(),
                po.getCreatedAt()
        );
    }

    /**
     * 将分配领域对象转换为持久化对象。
     */
    private CreditRepaymentAllocationPO toPO(CreditRepaymentAllocation allocation) {
        CreditRepaymentAllocationPO po = new CreditRepaymentAllocationPO();
        po.setRepaymentId(allocation.getRepaymentId());
        po.setSequenceNo(allocation.getSequenceNo());
        po.setTargetType(allocation.getTargetType().name());
        po.setTargetId(allocation.getTargetId());
        po.setAmountFen(allocation.getAmountFen());
        po.setCreatedAt(allocation.getCreatedAt());
        return po;
    }

    /**
     * 将分配明细领域对象转换为持久化对象。
     */
    private CreditRepaymentAllocationDetailPO toDetailPO(CreditRepaymentAllocationDetail detail) {
        CreditRepaymentAllocationDetailPO po = new CreditRepaymentAllocationDetailPO();
        po.setRepaymentId(detail.getRepaymentId());
        po.setSequenceNo(detail.getSequenceNo());
        po.setDetailNo(detail.getDetailNo());
        po.setPurchaseId(detail.getPurchaseId());
        po.setBillId(detail.getBillId());
        po.setAmountFen(detail.getAmountFen());
        po.setCreatedAt(detail.getCreatedAt());
        return po;
    }
}
