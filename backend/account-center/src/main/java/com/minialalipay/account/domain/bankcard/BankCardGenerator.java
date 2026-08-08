package com.minialalipay.account.domain.bankcard;

import java.util.List;
import java.util.Map;

/**
 * 银行卡号生成辅助工具。
 *
 * <p>根据银行 BIN 字典反向查找可用的 BIN 前缀，供注册时生成合法卡号。</p>
 */
public final class BankCardGenerator {

    /** 反向 BIN 映射：bankCode+cardType → BIN 列表。 */
    private static final Map<String, List<String>> REVERSE_BIN;

    static {
        // 从 BankCardNumber 的 BIN 字典反向构建映射
        var reverseBinBuilder = new java.util.HashMap<String, List<String>>();
        // 手动维护与 BankCardNumber.BIN_TABLE 一致的反向映射
        reverseBinBuilder.put("ICBC:DEBIT", List.of("621226", "622202"));
        reverseBinBuilder.put("ICBC:CREDIT", List.of("622208"));
        reverseBinBuilder.put("CCB:DEBIT", List.of("621700"));
        reverseBinBuilder.put("CCB:CREDIT", List.of("622280"));
        reverseBinBuilder.put("ABC:DEBIT", List.of("622848"));
        reverseBinBuilder.put("BOC:DEBIT", List.of("621661"));
        reverseBinBuilder.put("CMB:DEBIT", List.of("621483"));
        reverseBinBuilder.put("CMB:CREDIT", List.of("622575"));
        reverseBinBuilder.put("BCM:DEBIT", List.of("622262"));
        reverseBinBuilder.put("PSBC:DEBIT", List.of("621098", "622188"));
        REVERSE_BIN = Map.copyOf(reverseBinBuilder);
    }

    private BankCardGenerator() {
    }

    /**
     * 根据银行信息查找对应的 BIN 前缀。
     *
     * @param bankInfo 银行信息
     * @return 6 位 BIN 前缀
     * @throws IllegalArgumentException 如果银行不在 BIN 字典中
     */
    public static String findBinForBank(BankCardNumber.BankCardInfo bankInfo) {
        String key = bankInfo.bankCode() + ":" + bankInfo.cardType().name();
        List<String> bins = REVERSE_BIN.get(key);
        if (bins == null || bins.isEmpty()) {
            throw new IllegalArgumentException("不支持的银行: " + bankInfo.bankCode() + " " + bankInfo.cardType());
        }
        // 如果有多个 BIN，随机选一个
        return bins.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(bins.size()));
    }
}
