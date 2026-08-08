package com.minialalipay.account.application.bankcard;

import com.minialalipay.account.application.bankcard.dto.RegisteredCardDTO;
import com.minialalipay.account.domain.bankcard.BankCardErrorCode;
import com.minialalipay.account.domain.bankcard.BankCardNumber;
import com.minialalipay.account.domain.bankcard.BankCardType;
import com.minialalipay.account.domain.bankcard.IdCardValidator;
import com.minialalipay.account.domain.bankcard.RegisteredCard;
import com.minialalipay.account.domain.bankcard.RegisteredCardRepository;
import com.minialalipay.account.domain.bankcard.UserCenterIdentityPort;
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
 * <p>注册时先执行三要素格式校验与 user-center 交叉比对（姓名/身份证/手机号
 * 必须与用户已绑定身份完全一致），通过后才生成卡号并保存三要素哈希。
 * 完整卡号仅在注册响应中返回一次。</p>
 */
@Service
public class RegisteredCardApplicationService {

    /** 持卡人姓名长度：与绑定身份的真实姓名规则保持一致（2-32 位）。 */
    private static final int HOLDER_NAME_MIN = 2;
    private static final int HOLDER_NAME_MAX = 32;
    /** 手机号格式。 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1\\d{10}$");

    private final RegisteredCardRepository registeredCardRepository;
    private final UserCenterIdentityPort userCenterIdentityPort;

    public RegisteredCardApplicationService(RegisteredCardRepository registeredCardRepository,
                                            UserCenterIdentityPort userCenterIdentityPort) {
        this.registeredCardRepository = registeredCardRepository;
        this.userCenterIdentityPort = userCenterIdentityPort;
    }

    /**
     * 注册银行卡：格式校验 → 三要素交叉比对 → 生成卡号 → 落库。
     *
     * <p>校验顺序及不变量：
     * <ol>
     *   <li>三要素格式校验（姓名 2-32 位、身份证项目统一口径、手机号 11 位），
     *       不合规返回 BANK_CARD_HOLDER_INVALID</li>
     *   <li>调用 user-center 与用户已绑定身份交叉比对：未绑定身份返回
     *       IDENTITY_NOT_BOUND，任一要素不一致返回 IDENTITY_MISMATCH，
     *       校验服务不可用时拒绝注册（禁止在无法完成安全校验时放行）</li>
     *   <li>校验全部通过后才生成卡号并落库，避免无效注册记录污染</li>
     * </ol>
     *
     * @param userId 用户 ID
     * @param bankCode 银行编码
     * @param holderName 持卡人姓名
     * @param idCard 身份证号明文
     * @param phone 手机号明文
     * @return 注册结果（包含完整卡号）
     * @throws BusinessException 三要素格式不合规、未绑定身份、三要素不匹配、
     *         校验服务不可用或银行编码不存在
     */
    @Transactional
    public RegisteredCardDTO registerCard(String userId, String bankCode,
                                          String holderName, String idCard, String phone) {
        // 校验三要素格式：身份证执行项目统一校验口径（格式 + 出生日期）
        validateThreeElements(holderName, idCard, phone);

        // 与用户已绑定身份交叉比对，拦截非本人的虚假三要素；
        // 必须在生成卡号与落库之前完成，避免无效注册记录污染
        UserCenterIdentityPort.VerifyResult verifyResult = userCenterIdentityPort.verifyThreeElements(
                userId, holderName.trim(), idCard.trim(), phone.trim());
        switch (verifyResult) {
            case MATCHED -> { /* 校验通过，继续注册流程 */ }
            case IDENTITY_NOT_BOUND -> throw new BusinessException(BankCardErrorCode.IDENTITY_NOT_BOUND);
            case MISMATCH -> throw new BusinessException(BankCardErrorCode.IDENTITY_MISMATCH);
            case SERVICE_UNAVAILABLE -> throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        }

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

    /** 三要素格式校验：不合规统一返回 BANK_CARD_HOLDER_INVALID，与绑卡环节口径一致。 */
    private void validateThreeElements(String holderName, String idCard, String phone) {
        int nameLength = holderName == null ? 0 : holderName.trim().length();
        boolean holderValid = nameLength >= HOLDER_NAME_MIN && nameLength <= HOLDER_NAME_MAX;
        boolean idCardValid = IdCardValidator.validate(idCard) == null;
        boolean phoneValid = phone != null && PHONE_PATTERN.matcher(phone.trim()).matches();
        if (!holderValid || !idCardValid || !phoneValid) {
            throw new BusinessException(BankCardErrorCode.BANK_CARD_HOLDER_INVALID);
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
