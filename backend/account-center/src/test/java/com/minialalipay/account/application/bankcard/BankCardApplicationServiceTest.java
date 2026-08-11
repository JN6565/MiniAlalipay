package com.minialalipay.account.application.bankcard;

import com.minialalipay.account.application.credit.PaymentProofPort;
import com.minialalipay.account.domain.account.Account;
import com.minialalipay.account.domain.account.AccountErrorCode;
import com.minialalipay.account.domain.account.AccountRepository;
import com.minialalipay.account.domain.bankcard.BankCard;
import com.minialalipay.account.domain.bankcard.BankCardErrorCode;
import com.minialalipay.account.domain.bankcard.BankCardNumber;
import com.minialalipay.account.domain.bankcard.BankCardRepository;
import com.minialalipay.account.domain.bankcard.BankCardStatus;
import com.minialalipay.account.domain.bankcard.BankCardType;
import com.minialalipay.account.domain.bankcard.RegisteredCard;
import com.minialalipay.account.domain.bankcard.RegisteredCardRepository;
import com.minialalipay.account.domain.bankcard.UserCenterIdentityPort;
import com.minialalipay.account.application.bankcard.dto.BankCardDTO;
import com.minialalipay.common.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 银行卡应用服务测试：覆盖首卡自动默认、重复绑卡、上限、
 * 设默认互斥、解绑递补与资源归属隔离等用例级不变量。
 */
class BankCardApplicationServiceTest {

    private static final String USER = "USER001";

    /** 统一测试三要素，与注册记录构造保持一致。 */
    private static final String HOLDER = "张三";
    private static final String ID_CARD = "330106199001011234";
    private static final String PHONE = "13812345678";

    private BankCardRepository cardRepository;
    private AccountRepository accountRepository;
    private RegisteredCardRepository registeredCardRepository;
    private UserCenterIdentityPort identityPort;
    private PaymentProofPort paymentProofPort;
    private BankCardApplicationService service;

    @BeforeEach
    void setUp() {
        cardRepository = mock(BankCardRepository.class);
        accountRepository = mock(AccountRepository.class);
        registeredCardRepository = mock(RegisteredCardRepository.class);
        identityPort = mock(UserCenterIdentityPort.class);
        paymentProofPort = mock(PaymentProofPort.class);
        service = new BankCardApplicationService(cardRepository, accountRepository,
                registeredCardRepository, identityPort, paymentProofPort);

        Account account = mock(Account.class);
        when(account.getAccountId()).thenReturn("ACC001");
        when(accountRepository.findByUserId(USER)).thenReturn(Optional.of(account));

        // 任意卡号都能命中本人 REGISTERED 状态的注册记录（三要素与测试入参一致）
        when(registeredCardRepository.findByCardNumber(anyString()))
                .thenAnswer(inv -> Optional.of(registrationFor(inv.getArgument(0))));
        when(registeredCardRepository.updateStatus(any())).thenReturn(true);

        // 默认三要素与用户绑定身份匹配，个别用例单独覆写
        when(identityPort.verifyThreeElements(any(), any(), any(), any()))
                .thenReturn(UserCenterIdentityPort.VerifyResult.MATCHED);

        // 默认 CAS 更新成功，个别用例单独覆写制造冲突
        when(cardRepository.updateByCas(any(), anyLong())).thenReturn(true);
    }

    /** 用给定卡号构造本人 REGISTERED 注册记录；未知 BIN 时兜底用字典首项银行信息。 */
    private static RegisteredCard registrationFor(String cardNumber) {
        BankCardNumber.BankCardInfo info = BankCardNumber.identify(cardNumber)
                .orElseGet(() -> BankCardNumber.getAllBinEntries().stream().findFirst().orElseThrow());
        return RegisteredCard.register("REG_TEST", USER, info, cardNumber,
                HOLDER, ID_CARD, PHONE, Instant.now());
    }

    /** 用给定卡号构造本人 BOUND 注册记录，模拟已绑定状态，供解绑释放用例使用。 */
    private static RegisteredCard boundRegistrationFor(String cardNumber) {
        RegisteredCard registration = registrationFor(cardNumber);
        registration.markBound();
        return registration;
    }

    /** 生成合法 Luhn 卡号；固定主体保证测试可复现。 */
    private static String validCard(String bin) {
        return com.minialalipay.account.domain.bankcard.BankCardNumberTest
                .withLuhnCheckDigit(bin + "123456789");
    }

    @Test
    void firstCardAutomaticallyBecomesDefault() {
        when(cardRepository.countActiveByUserId(USER)).thenReturn(0L);
        when(cardRepository.existsActiveByUserAndCard(any(), any(), any())).thenReturn(false);

        BankCardDTO dto = service.bindCard(USER, validCard("621226"), "张三",
                "330106199001011234", "13812345678");

        ArgumentCaptor<BankCard> captor = ArgumentCaptor.forClass(BankCard.class);
        verify(cardRepository).save(captor.capture());
        assertThat(captor.getValue().isDefault()).isTrue();
        assertThat(dto.isDefault()).isTrue();
        assertThat(dto.bankCode()).isEqualTo("ICBC");
        // DTO 不得包含完整卡号
        assertThat(dto.cardLast4()).hasSize(4);
    }

    @Test
    void secondCardIsNotDefault() {
        when(cardRepository.countActiveByUserId(USER)).thenReturn(1L);
        when(cardRepository.existsActiveByUserAndCard(any(), any(), any())).thenReturn(false);

        service.bindCard(USER, validCard("621226"), "张三",
                "330106199001011234", "13812345678");

        ArgumentCaptor<BankCard> captor = ArgumentCaptor.forClass(BankCard.class);
        verify(cardRepository).save(captor.capture());
        assertThat(captor.getValue().isDefault()).isFalse();
    }

    @Test
    void duplicateBindRejected() {
        when(cardRepository.countActiveByUserId(USER)).thenReturn(1L);
        when(cardRepository.existsActiveByUserAndCard(any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.bindCard(USER, validCard("621226"), "张三",
                "330106199001011234", "13812345678"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(BankCardErrorCode.BANK_CARD_ALREADY_BOUND);
    }

    @Test
    void bindLimitRejected() {
        when(cardRepository.countActiveByUserId(USER)).thenReturn(10L);

        assertThatThrownBy(() -> service.bindCard(USER, validCard("621226"), "张三",
                "330106199001011234", "13812345678"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(BankCardErrorCode.BANK_CARD_LIMIT_EXCEEDED);
    }

    @Test
    void luhnInvalidCardRejected() {
        assertThatThrownBy(() -> service.bindCard(USER, "6212261234567891", "张三",
                "330106199001011234", "13812345678"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(BankCardErrorCode.BANK_CARD_INVALID);
    }

    @Test
    void unknownBankRejected() {
        assertThatThrownBy(() -> service.bindCard(USER, validCard("999999"), "张三",
                "330106199001011234", "13812345678"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(BankCardErrorCode.BANK_CARD_INVALID);
    }

    @Test
    void invalidHolderRejected() {
        assertThatThrownBy(() -> service.bindCard(USER, validCard("621226"), "张三",
                "123456", "13812345678"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(BankCardErrorCode.BANK_CARD_HOLDER_INVALID);
    }

    private BankCard card(String cardId, boolean isDefault, Instant createdAt) {
        return new BankCard(cardId, USER, "ACC001", "ICBC", "中国工商银行",
                BankCardType.DEBIT, "621226", "1234", "张*", "3301**********1234",
                "138****5678", 0L, isDefault, BankCardStatus.ACTIVE, null,
                0L, createdAt, createdAt);
    }

    @Test
    void setDefaultSwapsOldDefaultToNew() {
        Instant t1 = Instant.parse("2026-08-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-08-02T00:00:00Z");
        BankCard oldDefault = card("CARD_A", true, t1);
        BankCard target = card("CARD_B", false, t2);
        when(cardRepository.findById("CARD_B")).thenReturn(Optional.of(target));
        when(cardRepository.findActiveByUserId(USER)).thenReturn(List.of(oldDefault, target));

        BankCardDTO dto = service.setDefault(USER, "CARD_B");

        assertThat(oldDefault.isDefault()).isFalse();
        assertThat(target.isDefault()).isTrue();
        assertThat(dto.isDefault()).isTrue();
    }

    @Test
    void setDefaultOnCurrentDefaultIsIdempotent() {
        BankCard current = card("CARD_A", true, Instant.now());
        when(cardRepository.findById("CARD_A")).thenReturn(Optional.of(current));

        BankCardDTO dto = service.setDefault(USER, "CARD_A");

        assertThat(dto.isDefault()).isTrue();
    }

    @Test
    void unbindDefaultCardPromotesOldestActive() {
        Instant t1 = Instant.parse("2026-08-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-08-02T00:00:00Z");
        BankCard defaultCard = card("CARD_A", true, t1);
        BankCard oldest = card("CARD_B", false, t2);
        when(cardRepository.findById("CARD_A")).thenReturn(Optional.of(defaultCard));
        // 解绑后剩余的活动卡
        when(cardRepository.findActiveByUserId(USER)).thenReturn(List.of(oldest));

        service.unbind(USER, "CARD_A");

        assertThat(defaultCard.getStatus()).isEqualTo(BankCardStatus.UNBOUND);
        assertThat(oldest.isDefault()).isTrue();
    }

    @Test
    void unbindOtherUserCardReturnsNotFound() {
        BankCard othersCard = new BankCard("CARD_X", "OTHER_USER", "ACC002", "ICBC", "中国工商银行",
                BankCardType.DEBIT, "621226", "1234", "张*", "3301**********1234",
                "138****5678", 0L, false, BankCardStatus.ACTIVE, null, 0L, Instant.now(), Instant.now());
        when(cardRepository.findById("CARD_X")).thenReturn(Optional.of(othersCard));

        assertThatThrownBy(() -> service.unbind(USER, "CARD_X"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(BankCardErrorCode.BANK_CARD_NOT_FOUND);
    }

    @Test
    void casConflictThrowsVersionConflict() {
        BankCard current = card("CARD_A", true, Instant.now());
        when(cardRepository.findById("CARD_A")).thenReturn(Optional.of(current));
        when(cardRepository.updateByCas(any(), anyLong())).thenReturn(false);

        assertThatThrownBy(() -> service.unbind(USER, "CARD_A"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(AccountErrorCode.VERSION_CONFLICT);
    }

    @Test
    void unbindReleasesBoundRegistration() {
        // 解绑必须同步释放 BOUND 注册记录，否则该卡永远无法重绑
        BankCard current = card("CARD_A", false, Instant.now());
        when(cardRepository.findById("CARD_A")).thenReturn(Optional.of(current));
        when(registeredCardRepository.findBoundByUserAndCard(USER, "621226", "1234"))
                .thenReturn(Optional.of(boundRegistrationFor(validCard("621226"))));
        when(registeredCardRepository.releaseStatus("REG_TEST")).thenReturn(true);

        service.unbind(USER, "CARD_A");

        assertThat(current.getStatus()).isEqualTo(BankCardStatus.UNBOUND);
        verify(registeredCardRepository).releaseStatus("REG_TEST");
    }

    @Test
    void unbindWithoutRegistrationSkipsSilently() {
        // 无注册记录的旧绑定数据解绑时静默跳过释放，不报错
        BankCard current = card("CARD_A", false, Instant.now());
        when(cardRepository.findById("CARD_A")).thenReturn(Optional.of(current));
        when(registeredCardRepository.findBoundByUserAndCard(any(), any(), any()))
                .thenReturn(Optional.empty());

        service.unbind(USER, "CARD_A");

        assertThat(current.getStatus()).isEqualTo(BankCardStatus.UNBOUND);
        verify(registeredCardRepository, never()).releaseStatus(anyString());
    }

    @Test
    void unbindReleaseConflictThrowsVersionConflict() {
        // 释放 CAS 失败必须整体回滚，禁止出现“卡已解绑但注册记录仍 BOUND”的中间态
        BankCard current = card("CARD_A", false, Instant.now());
        when(cardRepository.findById("CARD_A")).thenReturn(Optional.of(current));
        when(registeredCardRepository.findBoundByUserAndCard(USER, "621226", "1234"))
                .thenReturn(Optional.of(boundRegistrationFor(validCard("621226"))));
        when(registeredCardRepository.releaseStatus("REG_TEST")).thenReturn(false);

        assertThatThrownBy(() -> service.unbind(USER, "CARD_A"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(AccountErrorCode.VERSION_CONFLICT);
    }

    @Test
    void rebindAfterUnbindSucceeds() {
        // 全链路：绑定 → 解绑（注册记录释放回 REGISTERED）→ 重绑成功
        String cardNumber = validCard("621226");
        when(cardRepository.countActiveByUserId(USER)).thenReturn(0L);
        when(cardRepository.existsActiveByUserAndCard(any(), any(), any())).thenReturn(false);
        when(cardRepository.findById("CARD_A")).thenAnswer(inv -> {
            // 重绑前解绑：返回已绑定的卡供 unbind 使用
            return Optional.of(card("CARD_A", true, Instant.now()));
        });
        when(registeredCardRepository.findBoundByUserAndCard(any(), any(), any()))
                .thenReturn(Optional.of(boundRegistrationFor(cardNumber)));
        when(registeredCardRepository.releaseStatus("REG_TEST")).thenReturn(true);

        // 首次绑定
        service.bindCard(USER, cardNumber, HOLDER, ID_CARD, PHONE);
        verify(registeredCardRepository).updateStatus(any());

        // 解绑并释放注册记录（打桩模拟释放后注册记录回到 REGISTERED，
        // findByCardNumber 兜底返回的即 REGISTERED 记录）
        service.unbind(USER, "CARD_A");
        verify(registeredCardRepository).releaseStatus("REG_TEST");

        // 重绑：注册记录已回到 REGISTERED，校验链路与新绑一致，生成新绑定记录
        service.bindCard(USER, cardNumber, HOLDER, ID_CARD, PHONE);
        verify(cardRepository, times(2)).save(any());
    }

    @Test
    void fullCardNumberConsumesProofThenReturnsRegistrationNumber() {
        // 完整卡号：先验归属、消费一次性证明后从注册记录返回明文
        String cardNumber = validCard("621226");
        when(cardRepository.findById("CARD_A"))
                .thenReturn(Optional.of(card("CARD_A", false, Instant.now())));
        when(paymentProofPort.verify(USER, "proof-token", "BANK_CARD_NUMBER_VIEW"))
                .thenReturn(new PaymentProofPort.VerifiedProof("PROOF_ID", 1L));
        when(registeredCardRepository.findBoundByUserAndCard(USER, "621226", "1234"))
                .thenReturn(Optional.of(boundRegistrationFor(cardNumber)));

        assertThat(service.getFullCardNumber(USER, "CARD_A", "proof-token")).isEqualTo(cardNumber);
        verify(paymentProofPort).verify(USER, "proof-token", "BANK_CARD_NUMBER_VIEW");
    }

    @Test
    void fullCardNumberMissingCardDoesNotConsumeProof() {
        // 非本人或不存在的卡直接拒绝，不得浪费用户的一次性证明
        when(cardRepository.findById("CARD_X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getFullCardNumber(USER, "CARD_X", "proof-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(BankCardErrorCode.BANK_CARD_NOT_FOUND);
        verify(paymentProofPort, never()).verify(any(), any(), any());
    }
}
