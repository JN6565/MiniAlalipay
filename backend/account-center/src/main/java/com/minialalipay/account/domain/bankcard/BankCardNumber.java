package com.minialalipay.account.domain.bankcard;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * 银行卡号识别与校验值对象（无状态工具类）。
 *
 * <p>职责：卡号规范化（去空格）、Luhn 校验、按 6 位 BIN 字典识别发卡行与卡类型。
 * BIN 字典为后端兜底字典，与前端展示用字典允许存在差异；
 * 识别失败统一视为「暂不支持该银行」，不泄露任何卡号明文。</p>
 *
 * <p>安全边界：卡号明文只在绑卡请求处理期间出现在内存中，
 * 持久化时只允许保存 BIN（前 6 位）与尾号（后 4 位）。</p>
 */
public final class BankCardNumber {

    /** 银联卡号长度下限。 */
    public static final int MIN_LENGTH = 16;
    /** 银联卡号长度上限。 */
    public static final int MAX_LENGTH = 19;

    /** BIN 字典：6 位卡号前缀 → 银行编码、银行名称、卡类型。 */
    private static final Map<String, BankCardInfo> BIN_TABLE = Map.ofEntries(
            Map.entry("621226", new BankCardInfo("ICBC", "中国工商银行", BankCardType.DEBIT)),
            Map.entry("622202", new BankCardInfo("ICBC", "中国工商银行", BankCardType.DEBIT)),
            Map.entry("622208", new BankCardInfo("ICBC", "中国工商银行", BankCardType.CREDIT)),
            Map.entry("621700", new BankCardInfo("CCB", "中国建设银行", BankCardType.DEBIT)),
            Map.entry("622280", new BankCardInfo("CCB", "中国建设银行", BankCardType.CREDIT)),
            Map.entry("622848", new BankCardInfo("ABC", "中国农业银行", BankCardType.DEBIT)),
            Map.entry("621661", new BankCardInfo("BOC", "中国银行", BankCardType.DEBIT)),
            Map.entry("621483", new BankCardInfo("CMB", "招商银行", BankCardType.DEBIT)),
            Map.entry("622575", new BankCardInfo("CMB", "招商银行", BankCardType.CREDIT)),
            Map.entry("622262", new BankCardInfo("BCM", "交通银行", BankCardType.DEBIT)),
            Map.entry("621098", new BankCardInfo("PSBC", "中国邮政储蓄银行", BankCardType.DEBIT)),
            Map.entry("622188", new BankCardInfo("PSBC", "中国邮政储蓄银行", BankCardType.DEBIT))
    );

    private BankCardNumber() {
        // 工具类禁止实例化
    }

    /** 发卡行识别结果：银行编码、银行名称与卡类型。 */
    public record BankCardInfo(String bankCode, String bankName, BankCardType cardType) {
    }

    /**
     * 规范化卡号：去除空格与连字符。
     *
     * @param rawNumber 原始卡号输入，允许包含分隔符
     * @return 纯数字卡号；输入为 null 时返回空字符串
     */
    public static String normalize(String rawNumber) {
        if (rawNumber == null) {
            return "";
        }
        return rawNumber.replace(" ", "").replace("-", "");
    }

    /**
     * 校验卡号格式：必须为 16 至 19 位纯数字且通过 Luhn 校验。
     *
     * @param normalizedNumber 已规范化的纯数字卡号
     * @return 是否通过校验
     */
    public static boolean isValid(String normalizedNumber) {
        if (normalizedNumber == null) {
            return false;
        }
        int length = normalizedNumber.length();
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (!Character.isDigit(normalizedNumber.charAt(i))) {
                return false;
            }
        }
        return luhnCheck(normalizedNumber);
    }

    /**
     * 返回 BIN 字典全部条目，供注册流程按银行编码查找 BIN 使用。
     *
     * @return BIN 字典值的不可变集合
     */
    public static Collection<BankCardInfo> getAllBinEntries() {
        return BIN_TABLE.values();
    }

    /**
     * 按 6 位 BIN 识别发卡行与卡类型。
     *
     * @param normalizedNumber 已通过 {@link #isValid(String)} 的纯数字卡号
     * @return 识别结果；BIN 不在字典内时返回空，表示暂不支持该银行
     */
    public static Optional<BankCardInfo> identify(String normalizedNumber) {
        if (normalizedNumber == null || normalizedNumber.length() < 6) {
            return Optional.empty();
        }
        return Optional.ofNullable(BIN_TABLE.get(normalizedNumber.substring(0, 6)));
    }

    /**
     * Luhn（模 10）校验：从右向左偶数位数字翻倍，翻倍结果大于 9 时减 9，
     * 全部数字之和必须能被 10 整除。这是银行卡号防误输的业界标准。
     *
     * @param digits 纯数字卡号
     * @return 校验是否通过
     */
    private static boolean luhnCheck(String digits) {
        int sum = 0;
        boolean doubleNext = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = digits.charAt(i) - '0';
            if (doubleNext) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleNext = !doubleNext;
        }
        return sum % 10 == 0;
    }
}
