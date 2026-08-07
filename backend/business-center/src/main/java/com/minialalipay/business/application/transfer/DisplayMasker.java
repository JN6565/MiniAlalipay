package com.minialalipay.business.application.transfer;

/**
 * 转账展示脱敏工具。
 *
 * <p>真实姓名与完整系统账户号属于个人敏感信息，禁止明文下发给前端；
 * 转账确认页与转账结果页需要的展示信息必须在服务边界统一脱敏后输出。
 * 规则与用户中心 NameMasker、H5 前端历史脱敏实现保持一致，保证各页面观感统一。</p>
 */
public final class DisplayMasker {

    private DisplayMasker() {
        // 纯静态工具类，禁止实例化
    }

    /**
     * 姓名脱敏：保留首字符，其余字符统一替换为星号（如 吕布 → 吕*，欧阳锋 → 欧**）。
     *
     * @param name 真实姓名或昵称
     * @return 脱敏姓名；为空时返回 null，单字符时补充两个星号
     */
    public static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String trimmed = name.trim();
        if (trimmed.length() <= 1) {
            return trimmed + "**";
        }
        return trimmed.charAt(0) + "*".repeat(trimmed.length() - 1);
    }

    /**
     * 账号脱敏：保留前 4 位和后 4 位，中间统一替换为 4 个星号（如 6296****3228）。
     *
     * @param accountNumber 完整系统账户号
     * @return 脱敏账号；为空时返回 null，长度不超过 8 位时原样展示
     */
    public static String maskAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return null;
        }
        if (accountNumber.length() <= 8) {
            return accountNumber;
        }
        return accountNumber.substring(0, 4) + "****" + accountNumber.substring(accountNumber.length() - 4);
    }
}
