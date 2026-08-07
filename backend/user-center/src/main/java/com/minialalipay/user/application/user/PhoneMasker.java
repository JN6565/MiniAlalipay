package com.minialalipay.user.application.user;

/**
 * 手机号脱敏工具。
 *
 * <p>完整手机号属于敏感信息，禁止下发给前端或写入日志；
 * 所有面向展示的手机号必须在服务边界内统一脱敏后输出。
 * 用户中心内所有需要脱敏手机号的查询场景共用本工具，避免规则不一致。</p>
 */
public final class PhoneMasker {

    private PhoneMasker() {
        // 纯静态工具类，禁止实例化
    }

    /**
     * 生成脱敏手机号：保留前 3 位和后 4 位，中间统一替换为 4 个星号（如 138****9150）。
     *
     * @param phoneNumber 完整手机号
     * @return 脱敏手机号；号码为空或长度不足 7 位时返回 null
     */
    public static String mask(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 7) {
            return null;
        }
        return phoneNumber.substring(0, 3) + "****" + phoneNumber.substring(phoneNumber.length() - 4);
    }
}
