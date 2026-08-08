package com.minialalipay.account.application.bankcard;

import com.minialalipay.account.application.bankcard.dto.RegisteredCardDTO;
import com.minialalipay.account.domain.bankcard.BankCardNumber;
import com.minialalipay.account.domain.bankcard.BankCardType;
import com.minialalipay.account.domain.bankcard.RegisteredCard;
import com.minialalipay.account.domain.bankcard.RegisteredCardRepository;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 银行卡注册应用服务：编排注册银行卡和查询已注册卡列表用例。
 *
 * <p>注册时根据用户选择的银行自动生成合法卡号（BIN + 随机数字 + Luhn 校验位），
 * 保存三要素哈希到注册记录。完整卡号仅在注册响应中返回一次。</p>
 */
@Service
public class RegisteredCardApplicationService {

    /** 身份证号格式。 */
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("^\\d{17}[\\dXx]$");
    /** 手机号格式。 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1\\d{10}$");

    private final RegisteredCardRepository registeredCardRepository;

    public RegisteredCardApplicationService(RegisteredCardRepository registeredCardRepository) {
        this.registeredCardRepository = registeredCardRepository;
    }

    /**
     * 注册银行卡：根据银行编码自动生成卡号，保存三要素哈希。
     *
     * @param userId 用户 ID
     * @param bankCode 银行编码
     * @param holderName 持卡人姓名
     * @param idCard 身份证号明文
     * @param phone 手机号明文
     * @return 注册结果（包含完整卡号）
     */
    @Transactional
    public RegisteredCardDTO registerCard(String userId, String bankCode,
                                          String holderName, String idCard, String phone) {
        // 校验三要素格式
        validateThreeElements(holderName, idCard, phone);

        // 根据银行编码查找 BIN 信息（默认 DEBIT 类型）
        BankCardNumber.BankCardInfo bankInfo = findBankInfo(bankCode);

        // 生成合法卡号
        String cardNumber = RegisteredCard.generateCardNumber(bankInfo);

        // 创建注册记录
        String registrationId = generateId();
        RegisteredCard card = RegisteredCard.register(registrationId, userId, bankInfo,
                cardNumber, holderName, idCard, phone, Instant.now());
        registeredCardRepository.save(card);

        return toDTO(card, true);
    }

    /**
     * 查询用户已注册但未绑定的卡列表。
     *
     * @param userId 用户 ID
     * @return 已注册卡列表（不返回完整卡号）
     */
    @Transactional(readOnly = true)
    public List<RegisteredCardDTO> listRegisteredCards(String userId) {
        return registeredCardRepository.findRegisteredByUserId(userId).stream()
                .map(card -> toDTO(card, false))
                .toList();
    }

    /** 查找银行 BIN 信息：遍历 BIN 字典找到第一个匹配的银行。 */
    private BankCardNumber.BankCardInfo findBankInfo(String bankCode) {
        // 遍历已知的银行 BIN，找到匹配的银行编码
        for (var entry : BankCardNumber.getAllBinEntries()) {
            if (entry.bankCode().equals(bankCode)) {
                return entry;
            }
        }
        throw new BusinessException(CommonErrorCode.NOT_FOUND);
    }

    private void validateThreeElements(String holderName, String idCard, String phone) {
        boolean holderValid = holderName != null && !holderName.isBlank() && holderName.trim().length() <= 32;
        boolean idCardValid = idCard != null && ID_CARD_PATTERN.matcher(idCard.trim()).matches();
        boolean phoneValid = phone != null && PHONE_PATTERN.matcher(phone.trim()).matches();
        if (!holderValid || !idCardValid || !phoneValid) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    private RegisteredCardDTO toDTO(RegisteredCard card, boolean includeFullNumber) {
        return new RegisteredCardDTO(
                card.getRegistrationId(), card.getBankCode(), card.getBankName(),
                card.getCardType().name(),
                includeFullNumber ? card.getCardNumber() : null,
                card.getCardBin(), card.getCardLast4(),
                card.getStatus(), card.getCreatedAt());
    }

    private String generateId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 26);
    }
}
