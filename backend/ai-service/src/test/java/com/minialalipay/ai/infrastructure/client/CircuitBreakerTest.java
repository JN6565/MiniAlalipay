package com.minialalipay.ai.infrastructure.client;

import com.minialalipay.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CircuitBreakerTest {

    @Test
    void shouldAllowCallWhenClosed() {
        var cb = new OpenAiLanguageModelAdapter.CircuitBreaker(3, 30);
        assertThatCode(cb::assertNotOpen).doesNotThrowAnyException();
    }

    @Test
    void shouldOpenAfterFailureThreshold() {
        var cb = new OpenAiLanguageModelAdapter.CircuitBreaker(3, 30);
        cb.recordFailure();
        cb.recordFailure();
        cb.recordFailure(); // 第三次 → OPEN

        assertThatThrownBy(cb::assertNotOpen)
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldRecoverAfterSuccess() {
        var cb = new OpenAiLanguageModelAdapter.CircuitBreaker(3, 30);
        cb.recordFailure();
        cb.recordFailure();
        cb.assertNotOpen(); // 仍可用
        cb.recordSuccess(); // 重置计数

        cb.recordFailure();
        cb.recordFailure();
        assertThatCode(cb::assertNotOpen).doesNotThrowAnyException(); // 未达阈值
    }

    @Test
    void shouldNotOpenBelowThreshold() {
        var cb = new OpenAiLanguageModelAdapter.CircuitBreaker(3, 30);
        cb.recordFailure();
        cb.recordFailure();
        assertThatCode(cb::assertNotOpen).doesNotThrowAnyException();
    }
}
