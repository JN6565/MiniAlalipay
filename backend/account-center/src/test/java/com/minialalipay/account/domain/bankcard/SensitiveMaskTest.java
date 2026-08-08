package com.minialalipay.account.domain.bankcard;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 敏感信息掩码化测试：掩码规则是隐私保护的最后一道防线，
 * 必须保证明文中间段不出现在掩码结果中。
 */
class SensitiveMaskTest {

    @Test
    void maskNameKeepsHeadAndTail() {
        assertThat(SensitiveMask.maskName("张三")).isEqualTo("张*");
        assertThat(SensitiveMask.maskName("张三丰")).isEqualTo("张*丰");
        assertThat(SensitiveMask.maskName("欧阳修文")).isEqualTo("欧**文");
    }

    @Test
    void maskNameEdgeCases() {
        assertThat(SensitiveMask.maskName("张")).isEqualTo("*");
        assertThat(SensitiveMask.maskName("")).isEmpty();
        assertThat(SensitiveMask.maskName(null)).isNull();
    }

    @Test
    void maskIdCardKeepsFirst4AndLast4() {
        assertThat(SensitiveMask.maskIdCard("330106199001011234")).isEqualTo("3301**********1234");
    }

    @Test
    void maskIdCardShortInputFullyMasked() {
        assertThat(SensitiveMask.maskIdCard("12345")).isEqualTo("*****");
    }

    @Test
    void maskPhoneKeepsFirst3AndLast4() {
        assertThat(SensitiveMask.maskPhone("13812345678")).isEqualTo("138****5678");
    }

    @Test
    void maskPhoneShortInputFullyMasked() {
        assertThat(SensitiveMask.maskPhone("138")).isEqualTo("***");
    }
}
