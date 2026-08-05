package com.minialalipay.ai.application.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IOSanitizerTest {

    private final IOSanitizer sanitizer = new IOSanitizer();

    @Test
    void shouldSanitizePhoneNumber() {
        String result = sanitizer.sanitizeContent("我的手机是13812345678");
        assertThat(result).doesNotContain("13812345678").contains("****5678");
    }

    @Test
    void shouldPreserveNonPhoneContent() {
        String input = "我想转账 100 元给张三";
        assertThat(sanitizer.sanitizeContent(input)).isEqualTo(input);
    }

    @Test
    void shouldSanitizePhoneTail4() {
        assertThat(sanitizer.sanitizePhoneTail4("13812345678")).isEqualTo("****5678");
    }

    @Test
    void shouldHandleShortPhone() {
        assertThat(sanitizer.sanitizePhoneTail4("123")).isEqualTo("****");
    }

    @Test
    void shouldSanitizeAccountFirstLast4() {
        assertThat(sanitizer.sanitizeAccountFirstLast4("01J5Q000000000000000000001"))
                .isEqualTo("01J5****0001");
    }
}
