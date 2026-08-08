package com.minialalipay.user.domain.user;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 项目统一身份证号校验器测试。
 *
 * <p>校验口径：18 位格式 + 出生日期真实存在且介于 1900-01-01 至今，
 * 不做 MOD 11-2 校验码。前端 validateIdCard 与 account-center 同规则实现必须与本类一致。</p>
 */
class IdCardValidatorTest {

    @Test
    void validIdCardReturnsNull() {
        assertThat(IdCardValidator.validate("110101199003071234")).isNull();
        assertThat(IdCardValidator.validate("330106199001011234")).isNull();
    }

    @Test
    void xSuffixIsCaseInsensitive() {
        assertThat(IdCardValidator.validate("11010119900307851x")).isNull();
        assertThat(IdCardValidator.validate("11010119900307851X")).isNull();
    }

    @Test
    void boundaryBirthDatesAreValid() {
        // 最早允许日期：1900-01-01
        assertThat(IdCardValidator.validate("330106190001011234")).isNull();
        // 最晚允许日期：今天
        String today = LocalDate.now().toString().replace("-", "");
        assertThat(IdCardValidator.validate("330106" + today + "1234")).isNull();
    }

    @Test
    void blankOrMalformedFormatRejected() {
        assertThat(IdCardValidator.validate(null)).isNotNull();
        assertThat(IdCardValidator.validate("  ")).isNotNull();
        assertThat(IdCardValidator.validate("3301061990010112")).isNotNull();
        assertThat(IdCardValidator.validate("3301061990010112345")).isNotNull();
        assertThat(IdCardValidator.validate("33010A199001011234")).isNotNull();
    }

    @Test
    void invalidBirthDateRejected() {
        // 13 月
        assertThat(IdCardValidator.validate("330106199013011234")).isNotNull();
        // 32 日
        assertThat(IdCardValidator.validate("330106199001321234")).isNotNull();
        // 1990-02-30 不存在
        assertThat(IdCardValidator.validate("330106199002301234")).isNotNull();
        // 未来日期
        assertThat(IdCardValidator.validate("330106203001011234")).isNotNull();
        // 早于 1900
        assertThat(IdCardValidator.validate("330106189912311234")).isNotNull();
    }
}
