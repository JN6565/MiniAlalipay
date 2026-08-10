package com.minialalipay.account.application.bankcard;

import com.minialalipay.account.application.bankcard.dto.BankCardDTO;
import com.minialalipay.account.domain.account.Account;
import com.minialalipay.account.domain.account.AccountErrorCode;
import com.minialalipay.account.domain.account.AccountRepository;
import com.minialalipay.account.domain.bankcard.BankCard;
import com.minialalipay.account.domain.bankcard.BankCardErrorCode;
import com.minialalipay.account.domain.bankcard.BankCardNumber;
import com.minialalipay.account.domain.bankcard.BankCardRepository;
import com.minialalipay.account.domain.bankcard.IdCardValidator;
import com.minialalipay.account.domain.bankcard.RegisteredCard;
import com.minialalipay.account.domain.bankcard.RegisteredCardRepository;
import com.minialalipay.account.domain.bankcard.UserCenterIdentityPort;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 银行卡管理应用服务：编排绑卡、列表、详情、设默认与解绑用例。
 *
 * <p>事务边界在本层；跨聚合不变量（同一用户至多一张 ACTIVE 默认卡、
 * 解绑默认卡后递补最早活动卡）由本层在事务内用仓储条件更新保证。
 * 敏感数据约束：完整卡号、证件号、手机号只在绑卡方法入参中短暂出现，
 * 禁止写入日志或返回给调用方。</p>
 */
@Service
public class BankCardApplicationService {

    /** 单用户 ACTIVE 绑定上限，超过后拒绝继续绑卡。 */
    private static final int MAX_ACTIVE_CARDS = 10;
    /** 中国大陆手机号格式：1 开头的 11 位数字。 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1\\d{10}$");

    private final BankCardRepository bankCardRepository;
    private final AccountRepository accountRepository;
    private final RegisteredCardRepository registeredCardRepository;
    private final UserCenterIdentityPort userCenterIdentityPort;

    public BankCardApplicationService(BankCardRepository bankCardRepository,
                                      AccountRepository accountRepository,
                                      RegisteredCardRepository registeredCardRepository,
                                      UserCenterIdentityPort userCenterIdentityPort) {
        this.bankCardRepository = bankCardRepository;
        this.accountRepository = accountRepository;
        this.registeredCardRepository = registeredCardRepository;
        this.userCenterIdentityPort = userCenterIdentityPort;
    }

    /**
     * 绑定银行卡：基于注册记录的新绑卡流程。
     *
     * <p>新逻辑：
     * <ol>
     *   <li>查询用户账户（已有）</li>
     *   <li>根据 cardNumber 查找 bank_card_registration 记录，不存在则返回 REGISTRATION_NOT_FOUND</li>
     *   <li>校验注册记录的 card_bin + card_last4 与输入卡号一致</li>
     *   <li>校验注册记录中 holder_name、id_card_hash、phone_hash 与输入的三要素匹配</li>
     *   <li>调用 user-center 内部接口校验三要素与用户存储身份完全匹配</li>
     *   <li>校验通过 → 创建 bank_card 记录（关联 registration_id），标记注册记录 status=BOUND</li>
     *   <li>首张卡自动默认（保持现有逻辑）</li>
     * </ol>
     *
     * @param userId 网关会话用户 ID
     * @param cardNumber 完整卡号（注册时获得的卡号，允许空格分组）
     * @param holderName 持卡人姓名明文
     * @param idCard 身份证号明文
     * @param phone 预留手机号明文
     * @return 已绑定卡片的掩码视图
     * @throws BusinessException 账户不存在、注册记录不存在、三要素不匹配或达到上限
     */
    @Transactional
    public BankCardDTO bindCard(String userId, String cardNumber, String holderName,
                                String idCard, String phone) {
        Account account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        long activeCount = bankCardRepository.countActiveByUserId(userId);
        if (activeCount >= MAX_ACTIVE_CARDS) {
            throw new BusinessException(BankCardErrorCode.BANK_CARD_LIMIT_EXCEEDED);
        }

        String normalized = BankCardNumber.normalize(cardNumber);
        if (!BankCardNumber.isValid(normalized)) {
            throw new BusinessException(BankCardErrorCode.BANK_CARD_INVALID);
        }

        // 查找注册记录
        RegisteredCard registration = registeredCardRepository.findByCardNumber(normalized)
                .orElseThrow(() -> new BusinessException(BankCardErrorCode.REGISTRATION_NOT_FOUND));

        // 校验注册记录归属当前用户且状态为 REGISTERED
        if (!registration.getUserId().equals(userId)) {
            throw new BusinessException(BankCardErrorCode.REGISTRATION_NOT_FOUND);
        }
        if (!"REGISTERED".equals(registration.getStatus())) {
            throw new BusinessException(BankCardErrorCode.BANK_CARD_ALREADY_BOUND);
        }

        // 校验卡号 BIN + 尾号与注册记录一致
        String cardBin = normalized.substring(0, 6);
        String cardLast4 = normalized.substring(normalized.length() - 4);
        if (!cardBin.equals(registration.getCardBin()) || !cardLast4.equals(registration.getCardLast4())) {
            throw new BusinessException(BankCardErrorCode.REGISTRATION_NOT_FOUND);
        }

        // 校验三要素格式
        validateHolder(holderName, idCard, phone);

        // 校验三要素与注册记录存储的哈希匹配
        if (!registration.matchThreeElements(holderName, idCard, phone)) {
            throw new BusinessException(BankCardErrorCode.IDENTITY_MISMATCH);
        }

        // 调用 user-center 交叉校验三要素与用户绑定身份完全匹配；
        // 未绑定身份与不匹配分别返回对应错误码，校验服务不可用时拒绝绑卡
        UserCenterIdentityPort.VerifyResult verifyResult = userCenterIdentityPort.verifyThreeElements(
                userId, holderName.trim(), idCard.trim(), phone.trim());
        switch (verifyResult) {
            case MATCHED -> { /* 校验通过，继续绑卡流程 */ }
            case IDENTITY_NOT_BOUND -> throw new BusinessException(BankCardErrorCode.IDENTITY_NOT_BOUND);
            case MISMATCH -> throw new BusinessException(BankCardErrorCode.IDENTITY_MISMATCH);
            case SERVICE_UNAVAILABLE -> throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        }

        // 识别银行信息
        BankCardNumber.BankCardInfo bankInfo = BankCardNumber.identify(normalized)
                .orElseThrow(() -> new BusinessException(BankCardErrorCode.BANK_CARD_INVALID));

        // 重复绑卡校验：解绑后允许重绑，所以只检查 ACTIVE 绑定
        if (bankCardRepository.existsActiveByUserAndCard(userId, cardBin, cardLast4)) {
            throw new BusinessException(BankCardErrorCode.BANK_CARD_ALREADY_BOUND);
        }

        // 首张卡自动默认，保证用户始终有一张可直接使用的默认卡
        BankCard card = BankCard.bind(generateId(), userId, account.getAccountId(), bankInfo,
                normalized, holderName.trim(), idCard.trim(), phone.trim(),
                activeCount == 0, Instant.now());
        bankCardRepository.save(card);

        // 标记注册记录为 BOUND
        registration.markBound();
        if (!registeredCardRepository.updateStatus(registration)) {
            throw new BusinessException(AccountErrorCode.VERSION_CONFLICT);
        }

        return toDTO(card);
    }

    /**
     * 查询用户全部 ACTIVE 银行卡，默认卡排最前，其余按绑定时间升序。
     *
     * @param userId 网关会话用户 ID
     * @return 掩码卡片列表
     */
    @Transactional(readOnly = true)
    public List<BankCardDTO> listMyCards(String userId) {
        return bankCardRepository.findActiveByUserId(userId).stream()
                .sorted(Comparator.comparing(BankCard::isDefault).reversed()
                        .thenComparing(BankCard::getCreatedAt))
                .map(this::toDTO)
                .toList();
    }

    /**
     * 查询银行卡详情；只允许查看本人卡片，访问他人卡片统一返回不存在。
     *
     * @param userId 网关会话用户 ID
     * @param cardId 银行卡 ID
     * @return 掩码卡片详情
     * @throws BusinessException 卡片不存在或不属于当前用户
     */
    @Transactional(readOnly = true)
    public BankCardDTO getCard(String userId, String cardId) {
        return toDTO(loadOwnedCard(userId, cardId));
    }

    /**
     * 设为默认卡：事务内先清旧默认再置新，保证至多一张默认卡；
     * 对已是默认卡的请求幂等返回。
     *
     * @param userId 网关会话用户 ID
     * @param cardId 银行卡 ID
     * @return 更新后的卡片视图
     * @throws BusinessException 卡片不存在、已解绑或版本冲突
     */
    @Transactional
    public BankCardDTO setDefault(String userId, String cardId) {
        BankCard card = loadOwnedCard(userId, cardId);
        if (card.isDefault()) {
            return toDTO(card);
        }
        Instant now = Instant.now();
        for (BankCard existing : bankCardRepository.findActiveByUserId(userId)) {
            if (!existing.isDefault()) {
                continue;
            }
            // 先清旧默认再置新：任一步 CAS 失败整体回滚，避免出现两张默认卡
            long expected = existing.getVersion();
            existing.clearDefault(now);
            updateOrConflict(existing, expected);
        }
        long expected = card.getVersion();
        card.markDefault(now);
        updateOrConflict(card, expected);
        return toDTO(card);
    }

    /**
     * 解绑银行卡（软删：状态置为 UNBOUND 终态），并同步释放对应的注册记录
     * （BOUND → REGISTERED），使该卡可重新走绑卡流程；
     * 解绑默认卡后自动把最早绑定的活动卡递补为默认。
     *
     * @param userId 网关会话用户 ID
     * @param cardId 银行卡 ID
     * @throws BusinessException 卡片不存在、已解绑或版本冲突
     */
    @Transactional
    public void unbind(String userId, String cardId) {
        BankCard card = loadOwnedCard(userId, cardId);
        boolean wasDefault = card.isDefault();
        Instant now = Instant.now();
        long expected = card.getVersion();
        card.unbind(now);
        updateOrConflict(card, expected);

        // 同步释放注册记录，支持解绑后重绑；无注册记录的旧绑定数据静默跳过。
        // 释放 CAS 失败视为并发修改，抛版本冲突整体回滚，避免出现
        // “卡已解绑但注册记录仍为 BOUND”的中间态导致永远无法重绑
        registeredCardRepository.findBoundByUserAndCard(userId, card.getCardBin(), card.getCardLast4())
                .ifPresent(registration -> {
                    if (!registeredCardRepository.releaseStatus(registration.getRegistrationId())) {
                        throw new BusinessException(AccountErrorCode.VERSION_CONFLICT);
                    }
                });

        if (wasDefault) {
            // 递补最早绑定的活动卡为默认，维持「至多且尽量有一张默认卡」的体验
            bankCardRepository.findActiveByUserId(userId).stream().findFirst().ifPresent(next -> {
                long nextExpected = next.getVersion();
                next.markDefault(now);
                updateOrConflict(next, nextExpected);
            });
        }
    }

    /** 加载本人卡片；他人卡片与不存在统一返回 BANK_CARD_NOT_FOUND，不暴露资源归属。 */
    private BankCard loadOwnedCard(String userId, String cardId) {
        return bankCardRepository.findById(cardId)
                .filter(card -> card.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(BankCardErrorCode.BANK_CARD_NOT_FOUND));
    }

    /** 四要素格式校验（模拟）：不发起真实银行通道校验，格式不合法即拒绝；身份证执行项目统一校验口径。 */
    private void validateHolder(String holderName, String idCard, String phone) {
        boolean holderValid = holderName != null && !holderName.isBlank() && holderName.trim().length() <= 32;
        boolean idCardValid = IdCardValidator.validate(idCard) == null;
        boolean phoneValid = phone != null && PHONE_PATTERN.matcher(phone.trim()).matches();
        if (!holderValid || !idCardValid || !phoneValid) {
            throw new BusinessException(BankCardErrorCode.BANK_CARD_HOLDER_INVALID);
        }
    }

    /** CAS 更新失败意味着并发修改，抛出版本冲突要求客户端重试。 */
    private void updateOrConflict(BankCard card, long expectedVersion) {
        if (!bankCardRepository.updateByCas(card, expectedVersion)) {
            throw new BusinessException(AccountErrorCode.VERSION_CONFLICT);
        }
    }

    private BankCardDTO toDTO(BankCard card) {
        return new BankCardDTO(card.getCardId(), card.getBankCode(), card.getBankName(),
                card.getCardType().name(), card.getCardLast4(), card.getHolderMasked(),
                card.getIdCardMasked(), card.getPhoneMasked(), card.getBalanceFen(),
                card.isDefault(), card.getStatus().name(), card.getCreatedAt());
    }

    /** 沿用账户中心既有 ID 约定：26 位字符串。 */
    private String generateId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 26);
    }
}
