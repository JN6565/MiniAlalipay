package com.minialalipay.common.idempotency;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyKeyValidatorTest {

    private final IdempotencyKeyValidator validator = new IdempotencyKeyValidator();

    @Test
    void acceptsContractCompliantKey() {
        assertThat(validator.isValid("01K1TX_8f5918cf-7e53")).isTrue();
    }

    @Test
    void rejectsMissingTooShortOrUnsafeKey() {
        assertThat(validator.isValid(null)).isFalse();
        assertThat(validator.isValid("short-key")).isFalse();
        assertThat(validator.isValid("0123456789abcdef 空格")).isFalse();
    }

    @Test
    void rejectsKeyLongerThanSixtyFourCharacters() {
        assertThat(validator.isValid("a".repeat(65))).isFalse();
    }
}
