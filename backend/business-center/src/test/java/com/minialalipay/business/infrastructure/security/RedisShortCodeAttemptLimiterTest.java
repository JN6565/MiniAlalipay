package com.minialalipay.business.infrastructure.security;

import com.minialalipay.business.domain.transaction.BusinessErrorCode;
import com.minialalipay.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 短码兑换尝试限流测试：10 分钟内失败 5 次锁定 30 分钟，Redis 故障放行。 */
class RedisShortCodeAttemptLimiterTest {

    @SuppressWarnings("unchecked")
    private static ValueOperations<String, String> valueOperations(StringRedisTemplate redis) {
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(operations);
        return operations;
    }

    @Test
    void 第五次失败触发三十分钟锁定并拒绝后续兑换() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> operations = valueOperations(redis);
        when(redis.hasKey("short_code:lock:session-1")).thenReturn(false);
        when(operations.increment("short_code:fail:session-1")).thenReturn(5L);
        RedisShortCodeAttemptLimiter limiter = new RedisShortCodeAttemptLimiter(redis);

        limiter.recordFailure("session-1");

        verify(operations).set(eq("short_code:lock:session-1"), eq("LOCKED"), eq(Duration.ofMinutes(30)));
        verify(redis).delete("short_code:fail:session-1");

        when(redis.hasKey("short_code:lock:session-1")).thenReturn(true);
        assertThatThrownBy(() -> limiter.requireAllowed("session-1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.errorCode())
                                .isEqualTo(BusinessErrorCode.SHORT_CODE_RATE_LIMITED));
    }

    @Test
    void 未达失败阈值时放行() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> operations = valueOperations(redis);
        when(redis.hasKey(any(String.class))).thenReturn(false);
        when(operations.increment(any(String.class))).thenReturn(4L);
        RedisShortCodeAttemptLimiter limiter = new RedisShortCodeAttemptLimiter(redis);

        assertThatCode(() -> limiter.requireAllowed("session-1")).doesNotThrowAnyException();
        limiter.recordFailure("session-1");
        verify(redis, org.mockito.Mockito.never()).delete(any(String.class));
    }

    @Test
    void Redis不可用时放行请求不阻断兑换() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.hasKey(any(String.class))).thenThrow(new RuntimeException("redis down"));
        RedisShortCodeAttemptLimiter limiter = new RedisShortCodeAttemptLimiter(redis);

        assertThatCode(() -> limiter.requireAllowed("session-1")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.recordFailure("session-1")).doesNotThrowAnyException();
    }
}
