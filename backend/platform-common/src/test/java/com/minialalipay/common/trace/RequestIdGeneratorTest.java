package com.minialalipay.common.trace;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdGeneratorTest {

    private final RequestIdGenerator generator = new RequestIdGenerator();

    @Test
    void keepsClientSuppliedRequestId() {
        assertThat(generator.resolve("client-request-42")).isEqualTo("client-request-42");
    }

    @Test
    void createsRequestIdForBlankInput() {
        String requestId = generator.resolve("  ");

        assertThat(requestId).startsWith("req_");
        assertThat(requestId).hasSizeGreaterThan("req_".length());
    }

    @Test
    void replacesUnsafeOrOversizedClientRequestId() {
        assertThat(generator.resolve("request\nforged"))
                .startsWith("req_")
                .doesNotContain("\n");
        assertThat(generator.resolve("a".repeat(129)))
                .startsWith("req_")
                .hasSizeLessThanOrEqualTo(128);
    }
}
