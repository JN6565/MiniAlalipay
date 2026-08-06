package com.minialalipay.business.domain.recharge;

import java.time.Instant;
import java.util.Objects;

/**
 * 受控模拟充值的限额策略快照。
 *
 * <p>当前产品规则固定为单笔不超过 5,000,000 分、单日累计不超过 25,000,000 分、
 * 单日最多 5 次。订单创建时必须保存策略 ID 和版本，历史订单不能随策略变更重算。</p>
 */
public final class RechargePolicy {
    /** 单笔充值上限，单位为分。 */
    public static final long DEFAULT_SINGLE_LIMIT_FEN = 5_000_000L;
    /** 单用户单日累计上限，单位为分。 */
    public static final long DEFAULT_DAILY_LIMIT_FEN = 25_000_000L;
    /** 单用户单日充值次数上限。 */
    public static final int DEFAULT_DAILY_COUNT_LIMIT = 5;

    private final String policyId;
    private final long singleLimitFen;
    private final long dailyLimitFen;
    private final int dailyCountLimit;
    private final RechargePolicyStatus status;
    private final long version;
    private final Instant effectiveAt;

    /** 创建当前产品冻结的活动充值策略。 */
    public static RechargePolicy defaultActive(String policyId, Instant now) {
        return active(policyId, DEFAULT_SINGLE_LIMIT_FEN, DEFAULT_DAILY_LIMIT_FEN, DEFAULT_DAILY_COUNT_LIMIT, now);
    }

    /** 创建活动充值策略版本。 */
    public static RechargePolicy active(String policyId, long singleLimitFen, long dailyLimitFen,
                                        int dailyCountLimit, Instant effectiveAt) {
        return new RechargePolicy(policyId, singleLimitFen, dailyLimitFen, dailyCountLimit,
                RechargePolicyStatus.ACTIVE, 0L, effectiveAt);
    }

    /** 从持久化策略快照重建。 */
    public RechargePolicy(String policyId, long singleLimitFen, long dailyLimitFen, int dailyCountLimit,
                          RechargePolicyStatus status, long version, Instant effectiveAt) {
        this.policyId = required(policyId, "充值策略 ID");
        if (singleLimitFen < 1 || dailyLimitFen < singleLimitFen) {
            throw new IllegalArgumentException("充值金额限额不合法");
        }
        if (dailyCountLimit < 1) throw new IllegalArgumentException("充值日次数上限必须大于零");
        this.singleLimitFen = singleLimitFen;
        this.dailyLimitFen = dailyLimitFen;
        this.dailyCountLimit = dailyCountLimit;
        this.status = Objects.requireNonNull(status, "充值策略状态不能为空");
        if (version < 0) throw new IllegalArgumentException("充值策略版本不得为负数");
        this.version = version;
        this.effectiveAt = Objects.requireNonNull(effectiveAt, "充值策略生效时间不能为空");
    }

    /** 校验当前策略可以接收指定金额的充值订单。 */
    public void validateAmount(long amountFen) {
        if (status != RechargePolicyStatus.ACTIVE) throw new IllegalStateException("充值策略当前未生效");
        if (amountFen < 1 || amountFen > singleLimitFen) {
            throw new IllegalStateException("模拟充值超过单笔限额");
        }
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + "不能为空");
        return value;
    }

    public String getPolicyId() { return policyId; }
    public long getSingleLimitFen() { return singleLimitFen; }
    public long getDailyLimitFen() { return dailyLimitFen; }
    public int getDailyCountLimit() { return dailyCountLimit; }
    public RechargePolicyStatus getStatus() { return status; }
    public long getVersion() { return version; }
    public Instant getEffectiveAt() { return effectiveAt; }
}
