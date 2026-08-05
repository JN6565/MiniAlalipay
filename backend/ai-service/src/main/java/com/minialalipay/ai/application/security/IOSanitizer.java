package com.minialalipay.ai.application.security;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 输入输出脱敏器。
 *
 * <p>在消息持久化前脱敏手机号、账号 ID 和支付密码上下文，
 * 确保原始敏感信息不进入数据库、日志或 Trace。</p>
 */
@Component
public class IOSanitizer {

    /** 11 位中国手机号 */
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("1[3-9]\\d{9}");

    /** 26 字符 ULID */
    private static final Pattern ULID_PATTERN =
            Pattern.compile("[0-9A-HJ-NP-TV-Za-hj-np-tv-z]{26}");

    /** 6 位数字（上下文中的支付密码模式） */
    private static final Pattern PAY_PASSWORD_PATTERN =
            Pattern.compile("(支付密码|密码)[\\s:：是]*(\\d{6})");

    /**
     * 脱敏消息内容。
     *
     * @param rawContent 原始输入
     * @return 脱敏后内容（手机号→****尾4位，账号→首4位****尾4位，密码→****）
     */
    public String sanitizeContent(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return rawContent;
        }
        String sanitized = rawContent;

        // 手机号脱敏：保留尾号 4 位
        sanitized = PHONE_PATTERN.matcher(sanitized).replaceAll(mr -> {
            String phone = mr.group();
            return "****" + phone.substring(phone.length() - 4);
        });

        // 账号脱敏：保留首尾各 4 位
        sanitized = ULID_PATTERN.matcher(sanitized).replaceAll(mr -> {
            String id = mr.group();
            if (id.length() < 8) return "****";
            return id.substring(0, 4) + "****" + id.substring(id.length() - 4);
        });

        // 支付密码脱敏
        sanitized = PAY_PASSWORD_PATTERN.matcher(sanitized).replaceAll("$1=******");

        return sanitized;
    }

    /**
     * 手机号脱敏（仅保留尾号 4 位）。
     */
    public String sanitizePhoneTail4(String phone) {
        if (phone == null || phone.length() < 4) return "****";
        return "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 账号脱敏（保留首尾各 4 位）。
     */
    public String sanitizeAccountFirstLast4(String accountId) {
        if (accountId == null || accountId.length() < 8) return "****";
        return accountId.substring(0, 4) + "****" + accountId.substring(accountId.length() - 4);
    }
}
