package com.minialalipay.account.domain.bankcard;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 身份证号校验器测试（account-center 侧实现）。
 *
 * <p>覆盖格式正例/反例、出生日期非法（13 月、未来日期、1900 前）反例与边界日期正例，
 * 与 user-center 及前端 validateIdCard 保持同口径。</p>
 */
class IdCardValidatorTest {

    @Test
    void validIdCardReturnsNull() {
        assertThat(IdCardValidator.validate("330106199001011234")).isNull();
    }

    @Test
    void lowercaseXSuffixIsValid() {
        assertThat(IdCardValidator.validate("11010119900307851x")).isNull();
    }

    @Test
    void uppercaseXSuffixIsValid() {
        assertThat(IdCardValidator.validate("11010119900307851X")).isNull();
    }

    @Test
    void boundaryBirthDate1900IsValid() {
        assertThat(IdCardValidator.validate("330106190001011234")).isNull();
    }

    @Test
    void todayBirthDateIsValid() {
        String today = LocalDate.now().toString().replace("-", "");
        assertThat(IdCardValidator.validate("330106" + today + "1234")).isNull();
    }

    @Test
    void blankIdCardRejected() {
        assertThat(IdCardValidator.validate(null)).isNotNull();
        assertThat(IdCardValidator.validate("  ")).isNotNull();
    }

    @Test
    void wrongLengthRejected() {
        assertThat(IdCardValidator.validate("3301061990010112")).isNotNull();
        assertThat(IdCardValidator.validate("3301061990010112345")).isNotNull();
    }

    @Test
    void nonDigitBodyRejected() {
        assertThat(IdCardValidator.validate("33010A199001011234")).isNotNull();
    }

    @Test
    void invalidMonthRejected() {
        assertThat(IdCardValidator.validate("330106199013011234")).isNotNull();
    }

    @Test
    void invalidDayRejected() {
        assertThat(IdCardValidator.validate("330106199001321234")).isNotNull();
    }

    @Test
    void nonexistentDateRejected() {
        // 1990-02-30 不存在
        assertThat(IdCardValidator.validate("330106199002301234")).isNotNull();
    }

    @Test
    void futureBirthDateRejected() {
        assertThat(IdCardValidator.validate("330106203001011234")).isNotNull();
    }

    @Test
    void before1900BirthDateRejected() {
        assertThat(IdCardValidator.validate("330106189912311234")).isNotNull();
    }
}
