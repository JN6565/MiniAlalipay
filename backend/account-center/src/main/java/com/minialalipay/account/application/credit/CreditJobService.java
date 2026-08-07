package com.minialalipay.account.application.credit;

import com.minialalipay.account.application.credit.dto.CreditJobRunDTO;
import com.minialalipay.account.domain.bill.CreditBill;
import com.minialalipay.account.domain.bill.CreditBillItem;
import com.minialalipay.account.domain.bill.CreditBillItemRepository;
import com.minialalipay.account.domain.bill.CreditBillItemStatus;
import com.minialalipay.account.domain.bill.CreditBillRepository;
import com.minialalipay.account.domain.bill.CreditBillStatus;
import com.minialalipay.account.domain.credit.CreditAccount;
import com.minialalipay.account.domain.credit.CreditAccountRepository;
import com.minialalipay.account.domain.credit.CreditAccountStatus;
import com.minialalipay.account.domain.credit.CreditErrorCode;
import com.minialalipay.account.domain.credit.CreditJobRun;
import com.minialalipay.account.domain.credit.CreditJobRunRepository;
import com.minialalipay.account.domain.credit.CreditJobStatus;
import com.minialalipay.account.domain.credit.CreditJobTriggerType;
import com.minialalipay.account.domain.credit.CreditJobType;
import com.minialalipay.account.domain.credit.CreditPurchase;
import com.minialalipay.account.domain.credit.CreditPurchaseBillingStatus;
import com.minialalipay.account.domain.credit.CreditPurchaseRepository;
import com.minialalipay.account.domain.credit.CreditReceivable;
import com.minialalipay.account.domain.credit.CreditReceivableRepository;
import com.minialalipay.common.error.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 信用批处理应用服务。
 *
 * <p>提供月度出账和到期检查两个批处理任务的触发能力。
 * 任务以 (jobType, businessDate) 幂等，重复运行不重复建账单或重复改变金额。</p>
 *
 * <p>出账逻辑：
 * <ul>
 *   <li>每月 1 日生成上月账单</li>
 *   <li>将 UNBILLED 消费汇总到账单</li>
 *   <li>更新信用应收（unbilled → billed）</li>
 * </ul>
 * </p>
 *
 * <p>到期检查逻辑：
 * <ul>
 *   <li>到期未还清的账单标记为 OVERDUE</li>
 *   <li>信用应收中已出账非逾期转为逾期</li>
 *   <li>存在逾期账单的信用账户标记为 SUSPENDED</li>
 *   <li>所有逾期账单还清后自动恢复 ACTIVE</li>
 * </ul>
 * </p>
 */
@Service
public class CreditJobService {

    /** 账单到期日：每月 10 日 23:59:59。 */
    private static final int BILL_DUE_DAY = 10;

    private final CreditJobRunRepository jobRunRepository;
    private final CreditAccountRepository creditAccountRepository;
    private final CreditReceivableRepository creditReceivableRepository;
    private final CreditPurchaseRepository creditPurchaseRepository;
    private final CreditBillRepository creditBillRepository;
    private final CreditBillItemRepository creditBillItemRepository;

    /**
     * 注入仓储依赖。
     */
    public CreditJobService(
            CreditJobRunRepository jobRunRepository,
            CreditAccountRepository creditAccountRepository,
            CreditReceivableRepository creditReceivableRepository,
            CreditPurchaseRepository creditPurchaseRepository,
            CreditBillRepository creditBillRepository,
            CreditBillItemRepository creditBillItemRepository
    ) {
        this.jobRunRepository = jobRunRepository;
        this.creditAccountRepository = creditAccountRepository;
        this.creditReceivableRepository = creditReceivableRepository;
        this.creditPurchaseRepository = creditPurchaseRepository;
        this.creditBillRepository = creditBillRepository;
        this.creditBillItemRepository = creditBillItemRepository;
    }

    /**
     * 触发月度出账任务。
     *
     * <p>按 businessDate 对应月份的前一个月生成账单。
     * 将所有 UNBILLED 消费汇总到账单，更新消费状态为 BILLED 和应收分布。</p>
     *
     * @param operatorUserId 操作人用户 ID
     * @param businessDate 业务日期
     * @return 任务运行记录
     */
    public CreditJobRunDTO runStatement(String operatorUserId, LocalDate businessDate) {
        // 幂等检查
        CreditJobRun existing = jobRunRepository
                .findByJobTypeAndBusinessDate(CreditJobType.STATEMENT.name(), businessDate)
                .orElse(null);
        if (existing != null && existing.getStatus() == CreditJobStatus.SUCCESS) {
            return toDTO(existing);
        }

        // 创建或更新任务记录：已失败/中断的旧记录允许重跑，成功后幂等返回原结果。
        Instant now = Instant.now();
        CreditJobRun jobRun;
        if (existing != null) {
            jobRun = existing;
            jobRun.restart(now);
        } else {
            jobRun = new CreditJobRun(
                    generateId(), CreditJobType.STATEMENT, businessDate,
                    CreditJobTriggerType.MANUAL, operatorUserId,
                    requestDigestOf(CreditJobType.STATEMENT, businessDate,
                            CreditJobTriggerType.MANUAL, operatorUserId), now
            );
            jobRun.start(now);
        }
        jobRunRepository.save(jobRun);

        try {
            // 计算账期：businessDate 的前一个月
            LocalDate periodDate = businessDate.minusMonths(1).withDayOfMonth(1);
            LocalDate statementDate = businessDate.withDayOfMonth(1);
            String period = String.format("%04d-%02d", periodDate.getYear(), periodDate.getMonthValue());
            // 上月消费在本月 1 日出账，本月 10 日到期，不能误用上月日期导致新账单立即逾期。
            Instant dueAt = statementDate.withDayOfMonth(BILL_DUE_DAY)
                    .atTime(23, 59, 59)
                    .atZone(ZoneId.of("Asia/Shanghai"))
                    .toInstant();

            // 遍历所有信用账户进行出账
            // 注意：此处简化处理，实际应分页遍历
            doStatementForAllAccounts(period, statementDate, dueAt, now);

            jobRun.succeed(null, Instant.now());
            jobRunRepository.save(jobRun);
        } catch (Exception e) {
            jobRun.fail("INTERNAL_ERROR", Instant.now());
            jobRunRepository.save(jobRun);
            throw e;
        }

        return toDTO(jobRun);
    }

    /**
     * 触发到期检查任务。
     *
     * <p>检查所有已到到期时间且未还清的账单，标记为 OVERDUE。
     * 存在逾期账单的信用账户标记为 SUSPENDED。</p>
     *
     * @param operatorUserId 操作人用户 ID
     * @param businessDate 业务日期
     * @return 任务运行记录
     */
    public CreditJobRunDTO runDueCheck(String operatorUserId, LocalDate businessDate) {
        // 幂等检查
        CreditJobRun existing = jobRunRepository
                .findByJobTypeAndBusinessDate(CreditJobType.DUE_CHECK.name(), businessDate)
                .orElse(null);
        if (existing != null && existing.getStatus() == CreditJobStatus.SUCCESS) {
            return toDTO(existing);
        }

        Instant now = Instant.now();
        CreditJobRun jobRun;
        if (existing != null) {
            jobRun = existing;
            jobRun.restart(now);
        } else {
            jobRun = new CreditJobRun(
                    generateId(), CreditJobType.DUE_CHECK, businessDate,
                    CreditJobTriggerType.MANUAL, operatorUserId,
                    requestDigestOf(CreditJobType.DUE_CHECK, businessDate,
                            CreditJobTriggerType.MANUAL, operatorUserId), now
            );
            jobRun.start(now);
        }
        jobRunRepository.save(jobRun);

        try {
            doDueCheckForAllAccounts(businessDate, now);
            jobRun.succeed(null, Instant.now());
            jobRunRepository.save(jobRun);
        } catch (Exception e) {
            jobRun.fail("INTERNAL_ERROR", Instant.now());
            jobRunRepository.save(jobRun);
            throw e;
        }

        return toDTO(jobRun);
    }

    /**
     * 为所有信用账户执行出账。
     */
    private void doStatementForAllAccounts(String period, LocalDate statementDate, Instant dueAt, Instant now) {
        // 查询所有未出账消费，按信用账户分组
        List<CreditPurchase> unbilledPurchases = creditPurchaseRepository
                .findByBillingStatus(CreditPurchaseBillingStatus.UNBILLED.name());

        // 按信用账户分组
        var groupedByAccount = unbilledPurchases.stream()
                .collect(java.util.stream.Collectors.groupingBy(CreditPurchase::getCreditAccountId));

        for (var entry : groupedByAccount.entrySet()) {
            String creditAccountId = entry.getKey();
            List<CreditPurchase> purchases = entry.getValue();

            // 检查是否已有该账期的账单（幂等）
            var existingBill = creditBillRepository.findByCreditAccountIdAndPeriod(creditAccountId, period);
            if (existingBill.isPresent()) {
                continue; // 已有账单，跳过
            }

            long totalFen = purchases.stream().mapToLong(CreditPurchase::getAmountFen).sum();
            if (totalFen <= 0) continue;

            // 创建账单
            String billId = generateId();
            CreditBill bill = new CreditBill(
                    billId, creditAccountId, period, statementDate, dueAt, totalFen, now
            );
            creditBillRepository.save(bill);

            // 创建账单明细并更新消费状态
            for (CreditPurchase purchase : purchases) {
                CreditBillItem item = new CreditBillItem(
                        billId, purchase.getPurchaseId(), purchase.getAmountFen(), now
                );
                creditBillItemRepository.save(item);

                purchase.markBilled(now);
                creditPurchaseRepository.save(purchase);
            }

            // 更新应收：unbilled → billed
            CreditReceivable receivable = creditReceivableRepository
                    .findByCreditAccountId(creditAccountId)
                    .orElse(null);
            if (receivable != null) {
                receivable.transferToBilled(totalFen, now);
                creditReceivableRepository.save(receivable);
            }
        }
    }

    /**
     * 为所有信用账户执行到期检查。
     *
     * <p>两阶段处理：
     * <ol>
     *   <li>标记逾期：查询到期未还账单（due_at < now 且 status IN OPEN/PARTIALLY_PAID 且 outstanding > 0），
     *       标记为 OVERDUE，增加信用应收逾期金额，暂停信用账户</li>
     *   <li>恢复激活：查询所有 SUSPENDED 账户，若无剩余逾期未还账单则恢复为 ACTIVE</li>
     * </ol>
     * </p>
     */
    private void doDueCheckForAllAccounts(LocalDate businessDate, Instant now) {
        // ---- 阶段一：标记逾期账单 ----
        List<CreditBill> overdueBills = creditBillRepository.findOverdueBills(now);

        for (CreditBill bill : overdueBills) {
            // 标记账单为逾期
            bill.markOverdue(now);
            creditBillRepository.save(bill);

            // 增加信用应收逾期金额（overdue 是 billed 的子集）
            CreditReceivable receivable = creditReceivableRepository
                    .findByCreditAccountId(bill.getCreditAccountId())
                    .orElse(null);
            if (receivable != null) {
                receivable.markOverdue(bill.getOutstandingFen(), now);
                creditReceivableRepository.save(receivable);
            }

            // 暂停信用账户（仅 ACTIVE 可暂停，SUSPENDED 幂等）
            CreditAccount account = creditAccountRepository
                    .findById(bill.getCreditAccountId())
                    .orElse(null);
            if (account != null && account.getStatus() == CreditAccountStatus.ACTIVE) {
                account.suspend("OVERDUE", now);
                creditAccountRepository.save(account);
            }
        }

        // ---- 阶段二：恢复已还清逾期的账户 ----
        List<CreditAccount> suspendedAccounts = creditAccountRepository
                .findByStatus(CreditAccountStatus.SUSPENDED);

        for (CreditAccount account : suspendedAccounts) {
            // 检查是否还有逾期未还账单
            boolean hasOverdueOutstanding = creditBillRepository
                    .findByCreditAccountId(account.getCreditAccountId())
                    .stream()
                    .anyMatch(b -> b.getStatus() == CreditBillStatus.OVERDUE
                            && b.getOutstandingFen() > 0);

            if (!hasOverdueOutstanding) {
                account.activate(now);
                creditAccountRepository.save(account);
            }
        }
    }

    private CreditJobRunDTO toDTO(CreditJobRun jobRun) {
        return new CreditJobRunDTO(
                jobRun.getRunId(),
                jobRun.getJobType().name(),
                jobRun.getBusinessDate(),
                jobRun.getStatus().name(),
                jobRun.getStartedAt(),
                jobRun.getCompletedAt(),
                jobRun.getErrorCode()
        );
    }

    /**
     * 由触发参数生成请求摘要（SHA-256，32 字节），识别同键异参。
     *
     * <p>同一 (jobType, businessDate) 键重复触发时，若触发参数不同则摘要不同，
     * 供恢复任务识别同键异参冲突；参数按固定顺序规范化拼接。</p>
     */
    private byte[] requestDigestOf(CreditJobType jobType, LocalDate businessDate,
                                   CreditJobTriggerType triggerType, String operatorUserId) {
        return computeSha256(jobType.name() + "|" + businessDate + "|"
                + triggerType.name() + "|" + operatorUserId);
    }

    private byte[] computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }

    private String generateId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 26);
    }
}
