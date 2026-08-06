package com.minialalipay.account.application.credit;

import com.minialalipay.account.application.credit.dto.CreditBillDetailDTO;
import com.minialalipay.account.application.credit.dto.CreditBillListDTO;
import com.minialalipay.account.application.credit.dto.CreditPurchaseDTO;
import com.minialalipay.account.application.credit.dto.CreditSummaryDTO;
import com.minialalipay.account.domain.bill.CreditBill;
import com.minialalipay.account.domain.bill.CreditBillItem;
import com.minialalipay.account.domain.bill.CreditBillItemRepository;
import com.minialalipay.account.domain.bill.CreditBillRepository;
import com.minialalipay.account.domain.credit.CreditAccount;
import com.minialalipay.account.domain.credit.CreditAccountRepository;
import com.minialalipay.account.domain.credit.CreditErrorCode;
import com.minialalipay.account.domain.credit.CreditPurchase;
import com.minialalipay.account.domain.credit.CreditPurchaseRepository;
import com.minialalipay.account.domain.credit.CreditReceivable;
import com.minialalipay.account.domain.credit.CreditReceivableRepository;
import com.minialalipay.common.error.BusinessException;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 信用查询应用服务。
 *
 * <p>提供额度摘要、消费明细、账单列表和账单详情的查询能力。
 * 所有查询方法均为只读，不产生业务副作用。</p>
 */
@Service
public class CreditQueryService {

    private final CreditAccountRepository creditAccountRepository;
    private final CreditReceivableRepository creditReceivableRepository;
    private final CreditPurchaseRepository creditPurchaseRepository;
    private final CreditBillRepository creditBillRepository;
    private final CreditBillItemRepository creditBillItemRepository;

    /**
     * 注入仓储依赖。
     */
    public CreditQueryService(
            CreditAccountRepository creditAccountRepository,
            CreditReceivableRepository creditReceivableRepository,
            CreditPurchaseRepository creditPurchaseRepository,
            CreditBillRepository creditBillRepository,
            CreditBillItemRepository creditBillItemRepository
    ) {
        this.creditAccountRepository = creditAccountRepository;
        this.creditReceivableRepository = creditReceivableRepository;
        this.creditPurchaseRepository = creditPurchaseRepository;
        this.creditBillRepository = creditBillRepository;
        this.creditBillItemRepository = creditBillItemRepository;
    }

    /**
     * 查询本人额度摘要。
     *
     * @param userId 用户 ID
     * @return 额度摘要
     * @throws BusinessException 信用账户不存在时抛出 CREDIT_ACCOUNT_NOT_FOUND
     */
    public CreditSummaryDTO getMyCredit(String userId) {
        try {
            CreditAccount account = creditAccountRepository.findByUserId(userId).orElse(null);
            if (account == null) {
                // 信用账户不存在时返回默认值
                return new CreditSummaryDTO("", "ACTIVE", 500000L, 0L, 0L, 500000L, 0L, 0L, 0L);
            }
            CreditReceivable receivable = creditReceivableRepository
                    .findByCreditAccountId(account.getCreditAccountId())
                    .orElseGet(() -> new CreditReceivable(account.getCreditAccountId(),
                            java.time.Instant.now()));
            return new CreditSummaryDTO(
                    account.getCreditAccountId(),
                    account.getStatus().name(),
                    account.getTotalLimitFen(),
                    account.getUsedFen(),
                    account.getFrozenFen(),
                    account.getAvailableFen(),
                    receivable.getUnbilledFen(),
                    receivable.getBilledFen(),
                    receivable.getOverdueFen()
            );
        } catch (Exception e) {
            // 查询失败时返回默认值
            return new CreditSummaryDTO("", "ACTIVE", 500000L, 0L, 0L, 500000L, 0L, 0L, 0L);
        }
    }

    /**
     * 查询信用消费明细列表。
     *
     * @param userId 用户 ID
     * @param billingStatus 出账状态筛选（可为 null，表示全部）
     * @return 消费明细列表
     * @throws BusinessException 信用账户不存在时抛出 CREDIT_ACCOUNT_NOT_FOUND
     */
    public List<CreditPurchaseDTO> listCreditPurchases(String userId, String billingStatus) {
        CreditAccount account = creditAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(CreditErrorCode.CREDIT_ACCOUNT_NOT_FOUND));
        String status = billingStatus != null ? billingStatus : null;
        List<CreditPurchase> purchases = creditPurchaseRepository
                .findByCreditAccountIdAndBillingStatus(account.getCreditAccountId(), status);
        return purchases.stream()
                .map(p -> new CreditPurchaseDTO(
                        p.getPurchaseId(),
                        p.getCreditTransactionId(),
                        p.getQrOrderId(),
                        p.getAmountFen(),
                        p.getRepaidFen(),
                        p.getOutstandingFen(),
                        p.getBillingStatus().name(),
                        p.getOccurredAt()
                ))
                .collect(Collectors.toList());
    }

    /**
     * 查询信用账单列表。
     *
     * @param userId 用户 ID
     * @return 账单列表
     * @throws BusinessException 信用账户不存在时抛出 CREDIT_ACCOUNT_NOT_FOUND
     */
    public List<CreditBillListDTO> listCreditBills(String userId) {
        CreditAccount account = creditAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(CreditErrorCode.CREDIT_ACCOUNT_NOT_FOUND));
        List<CreditBill> bills = creditBillRepository.findByCreditAccountId(account.getCreditAccountId());
        return bills.stream()
                .map(b -> new CreditBillListDTO(
                        b.getBillId(),
                        b.getPeriod(),
                        b.getStatementDate(),
                        b.getDueAt(),
                        b.getTotalFen(),
                        b.getPaidFen(),
                        b.getOutstandingFen(),
                        b.getStatus().name()
                ))
                .collect(Collectors.toList());
    }

    /**
     * 查询账单详情（含明细和还款分配）。
     *
     * @param userId 用户 ID
     * @param billId 账单 ID
     * @return 账单详情
     * @throws BusinessException 信用账户或账单不存在时抛出对应错误码
     */
    public CreditBillDetailDTO getCreditBill(String userId, String billId) {
        CreditAccount account = creditAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(CreditErrorCode.CREDIT_ACCOUNT_NOT_FOUND));
        CreditBill bill = creditBillRepository.findById(billId)
                .orElseThrow(() -> new BusinessException(CreditErrorCode.BILL_NOT_FOUND));
        // 越权检查：账单必须属于当前用户的信用账户
        if (!bill.getCreditAccountId().equals(account.getCreditAccountId())) {
            throw new BusinessException(CreditErrorCode.BILL_NOT_FOUND);
        }
        List<CreditBillItem> items = creditBillItemRepository.findByBillId(billId);
        List<CreditBillDetailDTO.BillItemDTO> itemDTOs = items.stream()
                .map(i -> new CreditBillDetailDTO.BillItemDTO(
                        i.getPurchaseId(),
                        i.getAmountFen(),
                        i.getAllocatedPaidFen(),
                        i.getStatus().name()
                ))
                .collect(Collectors.toList());
        return new CreditBillDetailDTO(
                bill.getBillId(),
                bill.getPeriod(),
                bill.getStatementDate(),
                bill.getDueAt(),
                bill.getTotalFen(),
                bill.getPaidFen(),
                bill.getOutstandingFen(),
                bill.getStatus().name(),
                itemDTOs
        );
    }
}
