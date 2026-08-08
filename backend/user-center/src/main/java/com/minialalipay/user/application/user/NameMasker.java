package com.minialalipay.user.application.user;

/**
 * 姓名脱敏工具。
 *
 * <p>真实姓名属于个人敏感信息，禁止以明文形式下发给前端展示；
 * 所有面向展示的姓名必须在服务边界内统一脱敏后输出。
 * 用户中心内所有需要脱敏姓名的查询场景共用本工具，避免规则不一致。</p>
 */
public final class NameMasker {

    private NameMasker() {
        // 纯静态工具类，禁止实例化
    }

    /**
     * 生成脱敏姓名：保留首字符，其余字符统一替换为星号（如 吕布 → 吕*，欧阳锋 → 欧**）。
     *
     * <p>与 H5 前端收款结果页的历史脱敏规则保持一致，保证各页面观感统一。</p>
     *
     * @param name 真实姓名或昵称
     * @return 脱敏姓名；为空时返回 null，单字符时补充两个星号避免暴露原名长度信息不足
     */
    public static String mask(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String trimmed = name.trim();
        if (trimmed.length() <= 1) {
            return trimmed + "**";
        }
        return trimmed.charAt(0) + "*".repeat(trimmed.length() - 1);
    }
}
