package com.minialalipay.account.application.credit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minialalipay.account.application.credit.dto.RepaymentDTO;
import com.minialalipay.account.application.credit.dto.RepaymentDraftDTO;
import com.minialalipay.account.domain.account.Account;
import com.minialalipay.account.domain.account.AccountRepository;
import com.minialalipay.account.domain.bill.CreditBill;
import com.minialalipay.account.domain.bill.CreditBillItem;
import com.minialalipay.account.domain.bill.CreditBillItemRepository;
import com.minialalipay.account.domain.bill.CreditBillRepository;
import com.minialalipay.account.domain.credit.CreditAccount;
import com.minialalipay.account.domain.credit.CreditAccountRepository;
import com.minialalipay.account.domain.credit.CreditAccountStatus;
import com.minialalipay.account.domain.credit.CreditErrorCode;
import com.minialalipay.account.domain.credit.CreditPurchase;
import com.minialalipay.account.domain.credit.CreditPurchaseBillingStatus;
import com.minialalipay.account.domain.credit.CreditPurchaseRepository;
import com.minialalipay.account.domain.credit.CreditReceivable;
import com.minialalipay.account.domain.credit.CreditReceivableRepository;
import com.minialalipay.account.domain.credit.RepaymentAllocationType;
import com.minialalipay.account.domain.repayment.CreditRepayment;
import com.minialalipay.account.domain.repayment.CreditRepaymentAllocation;
import com.minialalipay.account.domain.repayment.CreditRepaymentAllocationDetail;
import com.minialalipay.account.domain.repayment.CreditRepaymentAllocationRepository;
import com.minialalipay.account.domain.repayment.CreditRepaymentDraft;
import com.minialalipay.account.domain.repayment.CreditRepaymentDraftRepository;
import com.minialalipay.account.domain.repayment.CreditRepaymentDraftStatus;
import com.minialalipay.account.domain.repayment.CreditRepaymentRepository;
import com.minialalipay.account.domain.repayment.CreditRepaymentStatus;
import com.minialalipay.common.error.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 信用还款应用服务。
 *
 * <p>提供还款草稿创建（含分配预览）、还款提交和还款查询能力。</p>
 *
 * <p>分配顺序（固定规则，Try 阶段固化）：
 * <ol>
 *   <li>逾期账单（OVERDUE_BILL）→ 按最早到期时间</li>
 *   <li>已出账账单（BILL）→ 按最早出账时间</li>
 *   <li>未出账消费（UNBILLED_PURCHASE）→ 按最早发生时间</li>
 * </ol>
 * </p>
 */
@Service
public class CreditRepaymentService {

    private static final Logger log = LoggerFactory.getLogger(CreditRepaymentService.class);

    private static final long DRAFT_TTL_MINUTES = 10L;
    private static final long MAX_REPAYMENT_FEN = 5_000_000L;

    private final CreditAccountRepository creditAccountRepository;
    private final CreditReceivableRepository creditReceivableRepository;
    private final CreditPurchaseRepository creditPurchaseRepository;
    private final CreditBillRepository creditBillRepository;
    private final CreditBillItemRepository creditBillItemRepository;
    private final CreditRepaymentDraftRepository draftRepository;
    private final CreditRepaymentRepository repaymentRepository;
    private final CreditRepaymentAllocationRepository allocationRepository;
    private final AccountRepository accountRepository;
    private final CreditRepayTccParticipant creditRepayTccParticipant;
    private final PaymentProofPort paymentProofPort;
    private final ObjectMapper objectMapper;

    /**
     * 注入仓储和 TCC 参与者依赖。
     */
    public CreditRepaymentService(
            CreditAccountRepository creditAccountRepository,
            CreditReceivableRepository creditReceivableRepository,
            CreditPurchaseRepository creditPurchaseRepository,
            CreditBillRepository creditBillRepository,
            CreditBillItemRepository creditBillItemRepository,
            CreditRepaymentDraftRepository draftRepository,
            CreditRepaymentRepository repaymentRepository,
            CreditRepaymentAllocationRepository allocationRepository,
            AccountRepository accountRepository,
            CreditRepayTccParticipant creditRepayTccParticipant,
            PaymentProofPort paymentProofPort,
            ObjectMapper objectMapper
    ) {
        this.creditAccountRepository = creditAccountRepository;
        this.creditReceivableRepository = creditReceivableRepository;
        this.creditPurchaseRepository = creditPurchaseRepository;
        this.creditBillRepository = creditBillRepository;
        this.creditBillItemRepository = creditBillItemRepository;
        this.draftRepository = draftRepository;
        this.repaymentRepository = repaymentRepository;
        this.allocationRepository = allocationRepository;
        this.accountRepository = accountRepository;
        this.creditRepayTccParticipant = creditRepayTccParticipant;
        this.paymentProofPort = paymentProofPort;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建还款草稿和分配预览。
     *
     * <p>按固定分配顺序计算分配计划，生成分配快照和哈希。
     * 草稿有效期为 10 分钟。</p>
     *
     * @param userId 用户 ID
     * @param amountFen 还款金额（分），必须在 1~5000000 范围内
     * @param idempotencyKey 客户端幂等键，同一用户和幂等键只能形成一个草稿
     * @return 还款草稿及分配预览
     * @throws BusinessException 信用账户不存在、金额不合法或应收不足时抛出对应错误码
     */
    @Transactional
    public RepaymentDraftDTO createRepaymentDraft(String userId, long amountFen, String idempotencyKey) {
        // 校验金额
        if (amountFen < 1 || amountFen > MAX_REPAYMENT_FEN) {
            throw new BusinessException(CreditErrorCode.REPAYMENT_AMOUNT_INVALID);
        }

        String draftId = deterministicId("repayment-draft", userId, idempotencyKey);
        CreditRepaymentDraft existingDraft = draftRepository.findById(draftId).orElse(null);
        if (existingDraft != null) {
            if (!existingDraft.getUserId().equals(userId) || existingDraft.getAmountFen() != amountFen) {
                throw new BusinessException(CreditErrorCode.REPAYMENT_AMOUNT_INVALID);
            }
            return toDraftDTO(existingDraft);
        }

        CreditAccount account = creditAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(CreditErrorCode.CREDIT_ACCOUNT_NOT_FOUND));

        // 查询用户余额账户，用于还款时扣减余额
        Account payerAccount = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(CreditErrorCode.CREDIT_ACCOUNT_NOT_FOUND));

        CreditReceivable receivable = creditReceivableRepository
                .findByCreditAccountId(account.getCreditAccountId())
                .orElseThrow(() -> new BusinessException(CreditErrorCode.CREDIT_ACCOUNT_NOT_FOUND));

        // 还款金额不得超过信用应收总额
        if (amountFen > receivable.getTotalOutstandingFen()) {
            throw new BusinessException(CreditErrorCode.REPAYMENT_AMOUNT_INVALID);
        }

        // 计算分配计划
        List<AllocationPlan> plans = calculateAllocation(account.getCreditAccountId(), amountFen);

        // 生成分配快照 JSON
        String snapshot = buildAllocationSnapshot(plans);

        // 生成分配哈希
        byte[] hash = computeSha256(snapshot);

        Instant now = Instant.now();
        Instant expiresAt = now.plus(DRAFT_TTL_MINUTES, ChronoUnit.MINUTES);

        CreditRepaymentDraft draft = new CreditRepaymentDraft(
                draftId, userId, account.getCreditAccountId(),
                payerAccount.getAccountId(),
                amountFen, snapshot, hash, expiresAt, now
        );
        draftRepository.save(draft);

        return toDraftDTO(draft);
    }

    /**
     * 提交还款。
     *
     * <p>确认草稿后发起 CREDIT_REPAY TCC 事务：
     * <ol>
     *   <li>Try：冻结用户虚拟余额，预占还款资金</li>
     *   <li>Confirm：扣减余额，减少信用应收/已用额度，恢复可用额度，标记还款成功</li>
     * </ol>
     * 若 Try 阶段失败（如余额不足），事务整体回滚，不产生还款记录。
     * Seata 全局协调器引入后，Try/Confirm/Cancel 将由协调器编排，此方法只需发起 Try。</p>
     *
     * @param userId 用户 ID
     * @param repaymentDraftId 还款草稿 ID
     * @param paymentProofToken 一次性支付密码证明，成功校验后由用户中心原子消费
     * @param idempotencyKey 提交幂等键
     * @return 还款记录
     * @throws BusinessException 草稿不存在、已过期、余额不足或状态不允许时抛出对应错误码
     */
    @Transactional
    public RepaymentDTO submitRepayment(
            String userId, String repaymentDraftId,
            String paymentProofToken, String idempotencyKey
    ) {
        CreditRepayment existingRepayment = repaymentRepository
                .findByRepaymentDraftId(repaymentDraftId).orElse(null);
        if (existingRepayment != null) {
            return ownedRepayment(userId, existingRepayment);
        }

        CreditRepaymentDraft draft = draftRepository.findById(repaymentDraftId)
                .orElseThrow(() -> new BusinessException(CreditErrorCode.REPAYMENT_NOT_FOUND));

        // 校验草稿属于当前用户
        if (!draft.getUserId().equals(userId)) {
            throw new BusinessException(CreditErrorCode.REPAYMENT_NOT_FOUND);
        }

        // 校验草稿状态
        if (draft.getStatus() != CreditRepaymentDraftStatus.DRAFT) {
            throw new BusinessException(CreditErrorCode.REPAYMENT_NOT_FOUND);
        }

        // 校验未过期
        if (draft.isExpired(Instant.now())) {
            draft.expire(Instant.now());
            draftRepository.save(draft);
            throw new BusinessException(CreditErrorCode.REPAYMENT_NOT_FOUND);
        }

        // 支付证明只用于本次 CREDIT_REPAY，原始值不落库、不记录日志。
        paymentProofPort.verify(userId, paymentProofToken, "CREDIT_REPAY");

        // 确认草稿
        Instant now = Instant.now();
        draft.confirm(now);
        draftRepository.save(draft);

        // 创建还款记录（PROCESSING 状态）
        String repaymentId = deterministicId("repayment", userId, idempotencyKey);
        String transactionId = deterministicId("credit-repay-transaction", userId, idempotencyKey);
        String branchXid = "credit-repay:" + transactionId;
        CreditRepayment repayment = new CreditRepayment(
                repaymentId, repaymentDraftId, transactionId,
                draft.getCreditAccountId(), draft.getAmountFen(), now
        );
        repaymentRepository.save(repayment);

        List<CreditRepaymentAllocation> allocations = materializeAllocations(
                repaymentId, draft.getCreditAccountId(), draft.getAmountFen(),
                parsePlans(draft.getAllocationSnapshot()), now);
        allocationRepository.saveAll(repaymentId, allocations);

        // 发起 CREDIT_REPAY TCC 事务（同步 Try → Confirm）
        // 金额守恒：Try 冻结金额 == Confirm 扣减金额 == 还款金额
        creditRepayTccParticipant.tryRepay(
                transactionId, draft.getPayerAccountId(), draft.getCreditAccountId(),
                draft.getAmountFen(), branchXid, now
        );
        creditRepayTccParticipant.confirmRepay(
                transactionId, draft.getPayerAccountId(), draft.getCreditAccountId(),
                draft.getAmountFen(), branchXid, now
        );

        // Confirm 必须应用 Try 阶段固化的分配计划，确保账单、消费、应收和额度同步减少。
        applyAllocations(draft.getCreditAccountId(), allocations, now);

        log.info("信用还款成功: repaymentId={}, transactionId={}, amountFen={}",
                repaymentId, transactionId, draft.getAmountFen());

        // 消费草稿
        draft.consume(now);
        draftRepository.save(draft);

        // 重新读取还款记录以获取 Confirm 更新后的最终状态
        CreditRepayment finalized = repaymentRepository.findById(repaymentId)
                .orElseThrow(() -> new IllegalStateException("还款记录在提交后消失，repaymentId=" + repaymentId));

        return toRepaymentDTO(finalized);
    }

    /**
     * 查询还款状态。
     *
     * @param userId 用户 ID
     * @param repaymentId 还款 ID
     * @return 还款记录
     * @throws BusinessException 还款不存在或不属于当前用户时抛出 REPAYMENT_NOT_FOUND
     */
    public RepaymentDTO getRepayment(String userId, String repaymentId) {
        CreditRepayment repayment = repaymentRepository.findById(repaymentId)
                .orElseThrow(() -> new BusinessException(CreditErrorCode.REPAYMENT_NOT_FOUND));

        // 越权检查：通过信用账户验证所有权
        CreditAccount account = creditAccountRepository.findById(repayment.getCreditAccountId())
                .orElseThrow(() -> new BusinessException(CreditErrorCode.REPAYMENT_NOT_FOUND));
        if (!account.getUserId().equals(userId)) {
            throw new BusinessException(CreditErrorCode.REPAYMENT_NOT_FOUND);
        }

        return toRepaymentDTO(repayment);
    }

    /** 将持久化草稿恢复为接口 DTO，确保幂等重试返回首次创建的分配预览。 */
    private RepaymentDraftDTO toDraftDTO(CreditRepaymentDraft draft) {
        List<RepaymentDraftDTO.AllocationPreviewDTO> allocations = parsePlans(draft.getAllocationSnapshot())
                .stream()
                .map(plan -> new RepaymentDraftDTO.AllocationPreviewDTO(
                        plan.sequenceNo(), plan.targetType().name(), plan.targetId(), plan.amountFen()))
                .collect(Collectors.toList());
        return new RepaymentDraftDTO(
                draft.getRepaymentDraftId(), draft.getAmountFen(),
                bytesToHex(draft.getAllocationHash()), draft.getExpiresAt(), allocations);
    }

    /** 对幂等返回执行对象级归属校验。 */
    private RepaymentDTO ownedRepayment(String userId, CreditRepayment repayment) {
        CreditAccount account = creditAccountRepository.findById(repayment.getCreditAccountId())
                .orElseThrow(() -> new BusinessException(CreditErrorCode.REPAYMENT_NOT_FOUND));
        if (!account.getUserId().equals(userId)) {
            throw new BusinessException(CreditErrorCode.REPAYMENT_NOT_FOUND);
        }
        return toRepaymentDTO(repayment);
    }

    private RepaymentDTO toRepaymentDTO(CreditRepayment repayment) {
        return new RepaymentDTO(
                repayment.getRepaymentId(), repayment.getAmountFen(), repayment.getStatus().name(),
                repayment.getCreatedAt(), repayment.getUpdatedAt());
    }

    /**
     * 将草稿中不可变的分配快照物化为还款分配及逐笔消费明细。
     * 父分配与明细金额必须严格相等，否则中止还款，防止只减汇总应收而遗漏账单事实。
     */
    private List<CreditRepaymentAllocation> materializeAllocations(
            String repaymentId, String creditAccountId, long repaymentAmountFen,
            List<AllocationPlan> plans, Instant now
    ) {
        List<CreditRepaymentAllocation> allocations = new ArrayList<>();
        long totalAllocatedFen = 0L;
        for (AllocationPlan plan : plans) {
            CreditRepaymentAllocation allocation = new CreditRepaymentAllocation(
                    repaymentId, plan.sequenceNo(), plan.targetType(),
                    plan.targetId(), plan.amountFen(), now);
            if (plan.targetType() == RepaymentAllocationType.UNBILLED_PURCHASE) {
                CreditPurchase purchase = requiredPurchase(plan.targetId(), creditAccountId);
                if (purchase.getBillingStatus() != CreditPurchaseBillingStatus.UNBILLED
                        || plan.amountFen() > purchase.getOutstandingFen()) {
                    throw new BusinessException(CreditErrorCode.REPAYMENT_AMOUNT_INVALID);
                }
                allocation.addDetail(new CreditRepaymentAllocationDetail(
                        repaymentId, plan.sequenceNo(), 1, purchase.getPurchaseId(),
                        null, plan.amountFen(), now));
            } else {
                CreditBill bill = creditBillRepository.findById(plan.targetId())
                        .filter(candidate -> candidate.getCreditAccountId().equals(creditAccountId))
                        .orElseThrow(() -> new BusinessException(CreditErrorCode.BILL_NOT_FOUND));
                if (plan.amountFen() > bill.getOutstandingFen()) {
                    throw new BusinessException(CreditErrorCode.REPAYMENT_AMOUNT_INVALID);
                }
                long remaining = plan.amountFen();
                int detailNo = 1;
                List<CreditBillItem> items = creditBillItemRepository.findByBillId(bill.getBillId())
                        .stream()
                        .sorted((left, right) -> requiredPurchase(left.getPurchaseId(), creditAccountId)
                                .getOccurredAt().compareTo(requiredPurchase(right.getPurchaseId(), creditAccountId)
                                        .getOccurredAt()))
                        .toList();
                for (CreditBillItem item : items) {
                    if (remaining == 0) {
                        break;
                    }
                    long itemOutstanding = item.getAmountFen()
                            - item.getAllocatedPaidFen() - item.getReversedFen();
                    long detailAmount = Math.min(remaining, itemOutstanding);
                    if (detailAmount > 0) {
                        allocation.addDetail(new CreditRepaymentAllocationDetail(
                                repaymentId, plan.sequenceNo(), detailNo++, item.getPurchaseId(),
                                bill.getBillId(), detailAmount, now));
                        remaining -= detailAmount;
                    }
                }
                if (remaining != 0) {
                    throw new IllegalStateException("账单明细未还金额与账单汇总不一致");
                }
            }
            long detailTotal = allocation.getDetails().stream()
                    .mapToLong(CreditRepaymentAllocationDetail::getAmountFen).sum();
            if (detailTotal != allocation.getAmountFen()) {
                throw new IllegalStateException("还款父分配金额与明细合计不一致");
            }
            allocations.add(allocation);
            totalAllocatedFen += allocation.getAmountFen();
        }
        if (totalAllocatedFen != repaymentAmountFen) {
            throw new IllegalStateException("还款分配合计与还款金额不一致");
        }
        return allocations;
    }

    /** 应用分配事实并在逾期全部清偿后立即恢复信用账户。 */
    private void applyAllocations(
            String creditAccountId, List<CreditRepaymentAllocation> allocations, Instant now
    ) {
        for (CreditRepaymentAllocation allocation : allocations) {
            if (allocation.getTargetType() != RepaymentAllocationType.UNBILLED_PURCHASE) {
                CreditBill bill = creditBillRepository.findById(allocation.getTargetId())
                        .orElseThrow(() -> new BusinessException(CreditErrorCode.BILL_NOT_FOUND));
                bill.applyRepayment(allocation.getAmountFen(), now);
                creditBillRepository.save(bill);
            }
            for (CreditRepaymentAllocationDetail detail : allocation.getDetails()) {
                CreditPurchase purchase = requiredPurchase(detail.getPurchaseId(), creditAccountId);
                purchase.applyRepayment(detail.getAmountFen(), now);
                creditPurchaseRepository.save(purchase);
                if (detail.getBillId() != null) {
                    CreditBillItem item = creditBillItemRepository.findByPurchaseId(detail.getPurchaseId())
                            .orElseThrow(() -> new IllegalStateException("账单消费明细不存在"));
                    item.applyRepayment(detail.getAmountFen(), now);
                    creditBillItemRepository.save(item);
                }
            }
        }

        CreditAccount account = creditAccountRepository.findById(creditAccountId)
                .orElseThrow(() -> new BusinessException(CreditErrorCode.CREDIT_ACCOUNT_NOT_FOUND));
        boolean hasOverdue = creditBillRepository.findByCreditAccountId(creditAccountId).stream()
                .anyMatch(bill -> bill.getStatus() == com.minialalipay.account.domain.bill.CreditBillStatus.OVERDUE
                        && bill.getOutstandingFen() > 0);
        if (account.getStatus() == CreditAccountStatus.SUSPENDED && !hasOverdue) {
            account.activate(now);
            creditAccountRepository.save(account);
        }
    }

    private CreditPurchase requiredPurchase(String purchaseId, String creditAccountId) {
        return creditPurchaseRepository.findById(purchaseId)
                .filter(purchase -> purchase.getCreditAccountId().equals(creditAccountId))
                .orElseThrow(() -> new BusinessException(CreditErrorCode.REPAYMENT_AMOUNT_INVALID));
    }

    private List<AllocationPlan> parsePlans(String snapshot) {
        try {
            List<AllocationSnapshot> values = objectMapper.readValue(
                    snapshot, new TypeReference<List<AllocationSnapshot>>() { });
            return values.stream()
                    .map(value -> new AllocationPlan(
                            value.seq(), RepaymentAllocationType.valueOf(value.type()),
                            value.target(), value.amount()))
                    .toList();
        } catch (JsonProcessingException | IllegalArgumentException malformed) {
            throw new IllegalStateException("还款分配快照无法解析", malformed);
        }
    }

    /**
     * 按固定顺序计算还款分配计划。
     *
     * <p>分配顺序：逾期账单 → 已出账账单 → 未出账消费。</p>
     */
    private List<AllocationPlan> calculateAllocation(String creditAccountId, long amountFen) {
        List<AllocationPlan> plans = new ArrayList<>();
        long remaining = amountFen;
        int sequenceNo = 1;

        // 1. 逾期账单
        List<CreditBill> overdueBills = creditBillRepository.findByCreditAccountId(creditAccountId)
                .stream()
                .filter(b -> b.getStatus() == com.minialalipay.account.domain.bill.CreditBillStatus.OVERDUE)
                .sorted((a, b) -> a.getDueAt().compareTo(b.getDueAt()))
                .toList();
        for (CreditBill bill : overdueBills) {
            if (remaining <= 0) break;
            long allocate = Math.min(remaining, bill.getOutstandingFen());
            if (allocate > 0) {
                plans.add(new AllocationPlan(sequenceNo++,
                        RepaymentAllocationType.OVERDUE_BILL, bill.getBillId(), allocate));
                remaining -= allocate;
            }
        }

        // 2. 已出账账单（非逾期）
        List<CreditBill> billedBills = creditBillRepository.findByCreditAccountId(creditAccountId)
                .stream()
                .filter(b -> b.getStatus() == com.minialalipay.account.domain.bill.CreditBillStatus.OPEN
                        || b.getStatus() == com.minialalipay.account.domain.bill.CreditBillStatus.PARTIALLY_PAID)
                .sorted((a, b) -> a.getStatementDate().compareTo(b.getStatementDate()))
                .toList();
        for (CreditBill bill : billedBills) {
            if (remaining <= 0) break;
            long allocate = Math.min(remaining, bill.getOutstandingFen());
            if (allocate > 0) {
                plans.add(new AllocationPlan(sequenceNo++,
                        RepaymentAllocationType.BILL, bill.getBillId(), allocate));
                remaining -= allocate;
            }
        }

        // 3. 未出账消费
        if (remaining > 0) {
            List<CreditPurchase> unbilledPurchases = creditPurchaseRepository
                    .findByCreditAccountIdAndBillingStatus(creditAccountId,
                            CreditPurchaseBillingStatus.UNBILLED.name())
                    .stream()
                    .sorted((a, b) -> a.getOccurredAt().compareTo(b.getOccurredAt()))
                    .toList();
            for (CreditPurchase purchase : unbilledPurchases) {
                if (remaining <= 0) break;
                long allocate = Math.min(remaining, purchase.getOutstandingFen());
                if (allocate > 0) {
                    plans.add(new AllocationPlan(sequenceNo++,
                            RepaymentAllocationType.UNBILLED_PURCHASE, purchase.getPurchaseId(), allocate));
                    remaining -= allocate;
                }
            }
        }

        return plans;
    }

    private String buildAllocationSnapshot(List<AllocationPlan> plans) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < plans.size(); i++) {
            AllocationPlan p = plans.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"seq\":").append(p.sequenceNo)
              .append(",\"type\":\"").append(p.targetType.name()).append("\"")
              .append(",\"target\":\"").append(p.targetId).append("\"")
              .append(",\"amount\":").append(p.amountFen).append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private byte[] computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String generateId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 26);
    }

    /** 由业务维度和幂等键生成稳定的 26 位标识，避免同键重复创建资金事实。 */
    private String deterministicId(String scope, String userId, String idempotencyKey) {
        return bytesToHex(computeSha256(scope + ":" + userId + ":" + idempotencyKey)).substring(0, 26);
    }

    /** 分配快照 JSON 的内部反序列化结构。 */
    private record AllocationSnapshot(int seq, String type, String target, long amount) { }

    /** 分配计划内部表示。 */
    private record AllocationPlan(
            int sequenceNo,
            RepaymentAllocationType targetType,
            String targetId,
            long amountFen
    ) {}
}
