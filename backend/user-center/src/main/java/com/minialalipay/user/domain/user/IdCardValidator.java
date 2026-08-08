package com.minialalipay.user.domain.user;

import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * 本项目统一的身份证号校验器。
 *
 * <p>校验口径为「18 位格式 + 出生日期真实存在」，是全系统身份证号的唯一校验标准：
 * <ul>
 *   <li>基础格式：17 位数字加 1 位数字或 X/x（末位 X 不区分大小写）</li>
 *   <li>出生日期：第 7-14 位必须为真实存在的日期，且介于 1900-01-01 至今</li>
 * </ul>
 * 按产品决策不执行 GB 11643-1999 的 MOD 11-2 校验位验证，
 * 便于演示与测试使用编造号码；前端 {@code validateIdCard} 与本类保持同口径。
 * account-center 因仓库边界约束在本服务之外维护同规则实现，两侧口径必须一致。</p>
 */
public final class IdCardValidator {

    /** 身份证号基础格式：17 位数字加 1 位数字或 X/x。 */
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("^\\d{17}[\\dXx]$");

    /** 允许的最早出生年份，与前端校验口径一致。 */
    private static final int MIN_BIRTH_YEAR = 1900;

    private IdCardValidator() {
    }

    /**
     * 校验身份证号合规性。
     *
     * @param idCard 身份证号明文（允许首尾空格，末位 X 不区分大小写）
     * @return 不合规时返回中文错误文案，合规则返回 null
     */
    public static String validate(String idCard) {
        if (idCard == null || idCard.isBlank()) {
            return "身份证号不能为空";
        }
        String value = idCard.trim().toUpperCase();
        if (!ID_CARD_PATTERN.matcher(value).matches()) {
            return "身份证号格式不正确";
        }

        // 出生日期：第 7-14 位，必须为真实存在的日期且介于 1900-01-01 至今
        int year = Integer.parseInt(value.substring(6, 10));
        int month = Integer.parseInt(value.substring(10, 12));
        int day = Integer.parseInt(value.substring(12, 14));
        LocalDate birth;
        try {
            birth = LocalDate.of(year, month, day);
        } catch (Exception e) {
            return "身份证号出生日期不正确";
        }
        if (year < MIN_BIRTH_YEAR || birth.isAfter(LocalDate.now())) {
            return "身份证号出生日期不正确";
        }
        return null;
    }
}
