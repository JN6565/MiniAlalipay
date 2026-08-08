package com.minialalipay.account.domain.bankcard;

/**
 * 敏感信息掩码化规则（无状态工具类）。
 *
 * <p>绑卡四要素（持卡人姓名、身份证号、预留手机号）明文只在绑卡请求处理期间存在，
 * 落库前必须经过本类掩码化；系统任何地方不得保存或输出明文。
 * 掩码规则与支付宝展示习惯一致：保留可辨识的首尾字符，中间以星号替代。</p>
 */
public final class SensitiveMask {

    /** 掩码字符。 */
    private static final char MASK_CHAR = '*';

    private SensitiveMask() {
        // 工具类禁止实例化
    }

    /**
     * 姓名掩码：保留首字符与尾字符，中间全部掩码。
     *
     * <p>两个字符只保留首字符（如 张三 → 张*）；三个及以上字符保留首尾
     * （如 张三丰 → 张*丰）。输入为空时原样返回。</p>
     *
     * @param name 持卡人姓名明文
     * @return 掩码后的姓名
     */
    public static String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        if (name.length() == 1) {
            return String.valueOf(MASK_CHAR);
        }
        if (name.length() == 2) {
            return name.charAt(0) + String.valueOf(MASK_CHAR);
        }
        return name.charAt(0) + String.valueOf(MASK_CHAR).repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }

    /**
     * 身份证号掩码：保留前 4 位与后 4 位，中间 10 位掩码
     * （如 3301**********1234）。
     *
     * @param idCard 身份证号明文（18 位）
     * @return 掩码后的证件号；长度不足 8 位时整体掩码，避免泄露短输入
     */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.isEmpty()) {
            return idCard;
        }
        if (idCard.length() < 8) {
            return String.valueOf(MASK_CHAR).repeat(idCard.length());
        }
        return idCard.substring(0, 4) + String.valueOf(MASK_CHAR).repeat(idCard.length() - 8) + idCard.substring(idCard.length() - 4);
    }

    /**
     * 手机号掩码：保留前 3 位与后 4 位，中间 4 位掩码（如 138****5678）。
     *
     * @param phone 手机号明文（11 位）
     * @return 掩码后的手机号；长度不足 7 位时整体掩码
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return phone;
        }
        if (phone.length() < 7) {
            return String.valueOf(MASK_CHAR).repeat(phone.length());
        }
        return phone.substring(0, 3) + String.valueOf(MASK_CHAR).repeat(phone.length() - 7) + phone.substring(phone.length() - 4);
    }
}
