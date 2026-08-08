package com.minialalipay.account.domain.bankcard;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 银行卡注册聚合根。
 *
 * <p>注册时自动生成卡号并保存三要素哈希，绑定时与用户存储身份交叉比对。
 * 状态流转：REGISTERED → BOUND（终态），注册记录一旦绑定不可逆转。</p>
 */
public class RegisteredCard {

    private final String registrationId;
    private final String userId;
    private final String bankCode;
    private final String bankName;
    private final BankCardType cardType;
    private final String cardNumber;
    private final String cardBin;
    private final String cardLast4;
    private final String holderName;
    private final byte[] idCardHash;
    private final byte[] phoneHash;
    private String status;
    private final Instant createdAt;

    /** 注册构造器：创建 REGISTERED 状态的注册记录。 */
    public static RegisteredCard register(String registrationId, String userId,
                                          BankCardNumber.BankCardInfo bankInfo,
                                          String cardNumber,
                                          String holderName, String idCard, String phone,
                                          Instant now) {
        return new RegisteredCard(registrationId, userId, bankInfo.bankCode(), bankInfo.bankName(),
                bankInfo.cardType(), cardNumber,
                cardNumber.substring(0, 6),
                cardNumber.substring(cardNumber.length() - 4),
                holderName.trim(), sha256(idCard.trim()), sha256(phone.trim()),
                "REGISTERED", now);
    }

    public RegisteredCard(String registrationId, String userId, String bankCode, String bankName,
                          BankCardType cardType, String cardNumber, String cardBin, String cardLast4,
                          String holderName, byte[] idCardHash, byte[] phoneHash,
                          String status, Instant createdAt) {
        this.registrationId = registrationId;
        this.userId = userId;
        this.bankCode = bankCode;
        this.bankName = bankName;
        this.cardType = cardType;
        this.cardNumber = cardNumber;
        this.cardBin = cardBin;
        this.cardLast4 = cardLast4;
        this.holderName = holderName;
        this.idCardHash = idCardHash;
        this.phoneHash = phoneHash;
        this.status = status;
        this.createdAt = createdAt;
    }

    /** 标记为已绑定（终态）。 */
    public void markBound() {
        if (!"REGISTERED".equals(status)) {
            throw new IllegalStateException("只有 REGISTERED 状态的注册卡可以标记为绑定");
        }
        this.status = "BOUND";
    }

    /**
     * 校验绑定时输入的三要素是否与注册时存储的哈希匹配。
     *
     * @param holderName 持卡人姓名
     * @param idCard 身份证号明文
     * @param phone 手机号明文
     * @return 是否全部匹配
     */
    public boolean matchThreeElements(String holderName, String idCard, String phone) {
        boolean nameMatch = this.holderName.equals(holderName.trim());
        boolean idCardMatch = MessageDigest.isEqual(this.idCardHash, sha256(idCard.trim()));
        boolean phoneMatch = MessageDigest.isEqual(this.phoneHash, sha256(phone.trim()));
        return nameMatch && idCardMatch && phoneMatch;
    }

    /**
     * 根据银行编码和卡类型生成合法卡号（BIN + 随机数字 + Luhn 校验位）。
     *
     * @param bankInfo 银行 BIN 信息
     * @return 完整卡号字符串
     */
    public static String generateCardNumber(BankCardNumber.BankCardInfo bankInfo) {
        // 从 BIN 字典找到对应银行的所有 BIN，随机选一个
        String bin = BankCardGenerator.findBinForBank(bankInfo);
        // 生成 16 位卡号：6 位 BIN + 9 位随机 + 1 位 Luhn 校验位
        int totalLength = 16;
        StringBuilder sb = new StringBuilder(bin);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = bin.length(); i < totalLength - 1; i++) {
            sb.append(random.nextInt(10));
        }
        // 计算 Luhn 校验位
        sb.append(calculateLuhnCheckDigit(sb.toString()));
        return sb.toString();
    }

    /** 计算 Luhn 校验位：在已有数字串后追加一位使整体通过 Luhn 校验。 */
    private static int calculateLuhnCheckDigit(String partial) {
        int sum = 0;
        boolean doubleNext = true; // 从右向左，校验位本身不算，下一位开始翻倍
        for (int i = partial.length() - 1; i >= 0; i--) {
            int digit = partial.charAt(i) - '0';
            if (doubleNext) {
                digit *= 2;
                if (digit > 9) digit -= 9;
            }
            sum += digit;
            doubleNext = !doubleNext;
        }
        return (10 - (sum % 10)) % 10;
    }

    private static byte[] sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }

    // Getters
    public String getRegistrationId() { return registrationId; }
    public String getUserId() { return userId; }
    public String getBankCode() { return bankCode; }
    public String getBankName() { return bankName; }
    public BankCardType getCardType() { return cardType; }
    public String getCardNumber() { return cardNumber; }
    public String getCardBin() { return cardBin; }
    public String getCardLast4() { return cardLast4; }
    public String getHolderName() { return holderName; }
    public byte[] getIdCardHash() { return idCardHash; }
    public byte[] getPhoneHash() { return phoneHash; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
