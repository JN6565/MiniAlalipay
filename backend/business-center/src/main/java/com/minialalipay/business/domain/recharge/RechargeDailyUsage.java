package com.minialalipay.business.domain.recharge;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 单用户单业务日充值额度使用情况。
 *
 * <p>创建订单先将额度占入处理中；渠道拒绝或受理前取消必须释放，占用未知结果不能释放，
 * 防止同一日累计额度在并发重试下被绕过。仓储更新必须带 {@code version} CAS。</p>
 */
public final class RechargeDailyUsage {
    private final String userId;
    private final LocalDate businessDate;
    private long processingFen;
    private long successFen;
    private int processingCount;
    private int successCount;
    private long version;
    private Instant updatedAt;

    /** 创建当天零用量记录。 */
    public static RechargeDailyUsage empty(String userId, LocalDate businessDate, Instant now) {
        return new RechargeDailyUsage(userId, businessDate, 0L, 0L, 0, 0, 0L, now);
    }

    /** 从持久化事实重建日额度聚合。 */
    public RechargeDailyUsage(String userId, LocalDate businessDate, long processingFen, long successFen,
                              int processingCount, int successCount, long version, Instant updatedAt) {
        this.userId = required(userId, "用户 ID");
        this.businessDate = Objects.requireNonNull(businessDate, "业务日期不能为空");
        if (processingFen < 0 || successFen < 0 || processingCount < 0 || successCount < 0 || version < 0) {
            throw new IllegalArgumentException("充值日额度状态不合法");
        }
        this.processingFen = processingFen;
        this.successFen = successFen;
        this.processingCount = processingCount;
        this.successCount = successCount;
        this.version = version;
        this.updatedAt = Objects.requireNonNull(updatedAt, "更新时间不能为空");
    }

    /** 为新充值订单预占金额和次数。 */
    public void reserve(long expectedVersion, long amountFen, RechargePolicy policy, Instant now) {
        checkVersion(expectedVersion);
        Objects.requireNonNull(policy, "充值策略不能为空").validateAmount(amountFen);
        if (wouldExceedAmount(amountFen, policy) || wouldExceedCount(policy)) {
            throw new IllegalStateException("模拟充值超过日限额");
        }
        processingFen += amountFen;
        processingCount++;
        advance(now);
    }

    /** 渠道拒绝、受理前取消时释放此前预占。 */
    public void release(long expectedVersion, long amountFen, Instant now) {
        checkVersion(expectedVersion);
        if (amountFen < 1 || processingFen < amountFen || processingCount < 1) {
            throw new IllegalStateException("充值日额度不存在可释放的预占");
        }
        processingFen -= amountFen;
        processingCount--;
        advance(now);
    }

    /** 充值成功终态把在途占用结算为当日成功，累加成功次数并释放处理中次数。 */
    public void settleSuccess(long expectedVersion, long amountFen, Instant now) {
        checkVersion(expectedVersion);
        if (amountFen < 1 || processingFen < amountFen || processingCount < 1) {
            throw new IllegalStateException("充值日额度不存在可结算的预占");
        }
        processingFen -= amountFen;
        successFen += amountFen;
        processingCount--;
        successCount++;
        advance(now);
    }

    private boolean wouldExceedAmount(long amountFen, RechargePolicy policy) {
        return successFen > policy.getDailyLimitFen() - processingFen
                || amountFen > policy.getDailyLimitFen() - successFen - processingFen;
    }
    private boolean wouldExceedCount(RechargePolicy policy) {
        return successCount >= policy.getDailyCountLimit() - processingCount;
    }
    private void checkVersion(long expectedVersion) {
        if (version != expectedVersion) throw new IllegalStateException("充值日额度版本已经变化");
    }
    private void advance(Instant now) { version++; updatedAt = now; }
    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + "不能为空");
        return value;
    }

    public String getUserId() { return userId; }
    public LocalDate getBusinessDate() { return businessDate; }
    public long getProcessingFen() { return processingFen; }
    public long getSuccessFen() { return successFen; }
    public int getProcessingCount() { return processingCount; }
    public int getSuccessCount() { return successCount; }
    public long getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }
}
