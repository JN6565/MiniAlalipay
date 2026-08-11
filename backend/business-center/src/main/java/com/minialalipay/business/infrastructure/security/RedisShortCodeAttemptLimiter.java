package com.minialalipay.business.infrastructure.security;

import com.minialalipay.business.application.port.ShortCodeAttemptLimiter;
import com.minialalipay.business.domain.transaction.BusinessErrorCode;
import com.minialalipay.common.error.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 短码兑换尝试限流的 Redis 实现。
 *
 * <p>10 分钟内失败 5 次锁定 30 分钟；Redis 不可用时放行请求并尽力计数，
 * 避免限流基础设施故障阻断正常兑换。</p>
 */
@Component
public class RedisShortCodeAttemptLimiter implements ShortCodeAttemptLimiter {
    private static final int MAX_FAILURES = 5;
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(10);
    private static final Duration LOCK_DURATION = Duration.ofMinutes(30);

    private final StringRedisTemplate redis;

    /** 创建短码尝试限流器。 */
    public RedisShortCodeAttemptLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void requireAllowed(String principal) {
        try {
            if (Boolean.TRUE.equals(redis.hasKey(lockKey(principal)))) {
                throw new BusinessException(BusinessErrorCode.SHORT_CODE_RATE_LIMITED);
            }
        } catch (BusinessException limited) {
            throw limited;
        } catch (RuntimeException unavailable) {
            // Redis 不可用时放行，限流基础设施故障不应阻断正常兑换
        }
    }

    @Override
    public void recordFailure(String principal) {
        try {
            String key = failureKey(principal);
            Long failures = redis.opsForValue().increment(key);
            if (failures != null && failures == 1L) {
                redis.expire(key, FAILURE_WINDOW);
            }
            if (failures != null && failures >= MAX_FAILURES) {
                redis.opsForValue().set(lockKey(principal), "LOCKED", LOCK_DURATION);
                redis.delete(key);
            }
        } catch (RuntimeException unavailable) {
            // 计数尽力而为，限流基础设施故障不应阻断业务流程
        }
    }

    @Override
    public void reset(String principal) {
        try {
            redis.delete(failureKey(principal));
        } catch (RuntimeException unavailable) {
            // 清零失败静默忽略，失败计数会随窗口自然过期
        }
    }

    private static String failureKey(String principal) {
        return "short_code:fail:" + principal;
    }

    private static String lockKey(String principal) {
        return "short_code:lock:" + principal;
    }
}
