package com.minialalipay.account.application.bankcard;

import com.minialalipay.account.domain.account.Account;
import com.minialalipay.account.domain.account.AccountErrorCode;
import com.minialalipay.account.domain.account.AccountRepository;
import com.minialalipay.account.domain.bankcard.BankCard;
import com.minialalipay.account.domain.bankcard.BankCardErrorCode;
import com.minialalipay.account.domain.bankcard.BankCardRepository;
import com.minialalipay.account.domain.bankcard.BankCardStatus;
import com.minialalipay.account.domain.bankcard.BankCardType;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 银行卡应用服务测试：覆盖首卡自动默认、重复绑卡、上限、
 * 设默认互斥、解绑递补与资源归属隔离等用例级不变量。
 */
class BankCardApplicationServiceTest {

    private static final String USER = "USER001";

    private BankCardRepository cardRepository;
    private AccountRepository accountRepository;
    private BankCardApplicationService service;

    @BeforeEach
    void setUp() {
        cardRepository = mock(BankCardRepository.class);
        accountRepository = mock(AccountRepository.class);
        service = new BankCardApplicationService(cardRepository, accountRepository);

        Account account = mock(Account.class);
        when(account.getAccountId()).thenReturn("ACC001");
        when(accountRepository.findByUserId(USER)).thenReturn(Optional.of(account));

        // 默认 CAS 更新成功，个别用例单独覆写制造冲突
        when(cardRepository.updateByCas(any(), anyLong())).thenReturn(true);
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
                "138****5678", isDefault, BankCardStatus.ACTIVE, null,
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
                "138****5678", false, BankCardStatus.ACTIVE, null, 0L, Instant.now(), Instant.now());
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
}
