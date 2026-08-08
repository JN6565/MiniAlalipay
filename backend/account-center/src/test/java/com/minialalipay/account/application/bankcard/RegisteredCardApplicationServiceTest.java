package com.minialalipay.account.application.bankcard;

import com.minialalipay.account.application.bankcard.dto.RegisteredCardDTO;
import com.minialalipay.account.domain.bankcard.BankCardErrorCode;
import com.minialalipay.account.domain.bankcard.RegisteredCard;
import com.minialalipay.account.domain.bankcard.RegisteredCardRepository;
import com.minialalipay.account.domain.bankcard.UserCenterIdentityPort;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 银行卡注册应用服务测试。
 *
 * <p>覆盖注册全流程的成功与失败分支：格式校验、三要素交叉比对
 * （未绑定身份/不匹配/服务不可用）、银行编码不存在与成功落库。</p>
 */
class RegisteredCardApplicationServiceTest {

    private static final String USER = "USER001";
    private static final String HOLDER = "张三";
    private static final String ID_CARD = "330106199001011234";
    private static final String PHONE = "13812345678";

    private RegisteredCardRepository registeredCardRepository;
    private UserCenterIdentityPort identityPort;
    private RegisteredCardApplicationService service;

    @BeforeEach
    void setUp() {
        registeredCardRepository = mock(RegisteredCardRepository.class);
        identityPort = mock(UserCenterIdentityPort.class);
        service = new RegisteredCardApplicationService(registeredCardRepository, identityPort);

        // 默认三要素与用户绑定身份匹配，个别用例单独覆写
        when(identityPort.verifyThreeElements(any(), any(), any(), any()))
                .thenReturn(UserCenterIdentityPort.VerifyResult.MATCHED);
    }

    /** 成功注册：已绑定身份且三要素匹配，返回完整卡号并落库。 */
    @Test
    void registerSuccessReturnsFullCardNumberAndSaves() {
        RegisteredCardDTO dto = service.registerCard(USER, "ICBC", HOLDER, ID_CARD, PHONE);

        assertThat(dto.cardNumber()).hasSize(16).startsWith("62");
        assertThat(dto.bankCode()).isEqualTo("ICBC");
        assertThat(dto.status()).isEqualTo("REGISTERED");

        ArgumentCaptor<RegisteredCard> captor = ArgumentCaptor.forClass(RegisteredCard.class);
        verify(registeredCardRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER);
        assertThat(captor.getValue().getCardNumber()).isEqualTo(dto.cardNumber());
    }

    /** 未绑定身份拒绝：端口返回 IDENTITY_NOT_BOUND，不落库。 */
    @Test
    void identityNotBoundRejected() {
        when(identityPort.verifyThreeElements(any(), any(), any(), any()))
                .thenReturn(UserCenterIdentityPort.VerifyResult.IDENTITY_NOT_BOUND);

        assertThatThrownBy(() -> service.registerCard(USER, "ICBC", HOLDER, ID_CARD, PHONE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(BankCardErrorCode.IDENTITY_NOT_BOUND);
        verify(registeredCardRepository, never()).save(any());
    }

    /** 姓名不符拒绝：端口返回 MISMATCH，统一报 IDENTITY_MISMATCH。 */
    @Test
    void mismatchedHolderNameRejected() {
        when(identityPort.verifyThreeElements(any(), any(), any(), any()))
                .thenReturn(UserCenterIdentityPort.VerifyResult.MISMATCH);

        assertThatThrownBy(() -> service.registerCard(USER, "ICBC", "李四", ID_CARD, PHONE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(BankCardErrorCode.IDENTITY_MISMATCH);
    }

    /** 身份证号不符拒绝：端口返回 MISMATCH，统一报 IDENTITY_MISMATCH。 */
    @Test
    void mismatchedIdCardRejected() {
        when(identityPort.verifyThreeElements(any(), any(), any(), any()))
                .thenReturn(UserCenterIdentityPort.VerifyResult.MISMATCH);

        assertThatThrownBy(() -> service.registerCard(USER, "ICBC", HOLDER, "330106199202021234", PHONE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(BankCardErrorCode.IDENTITY_MISMATCH);
    }

    /** 手机号不符拒绝：端口返回 MISMATCH，统一报 IDENTITY_MISMATCH。 */
    @Test
    void mismatchedPhoneRejected() {
        when(identityPort.verifyThreeElements(any(), any(), any(), any()))
                .thenReturn(UserCenterIdentityPort.VerifyResult.MISMATCH);

        assertThatThrownBy(() -> service.registerCard(USER, "ICBC", HOLDER, ID_CARD, "13900000000"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(BankCardErrorCode.IDENTITY_MISMATCH);
    }

    /** 校验服务不可用拒绝：抛系统类异常（503），禁止放行，不落库。 */
    @Test
    void serviceUnavailableRejected() {
        when(identityPort.verifyThreeElements(any(), any(), any(), any()))
                .thenReturn(UserCenterIdentityPort.VerifyResult.SERVICE_UNAVAILABLE);

        assertThatThrownBy(() -> service.registerCard(USER, "ICBC", HOLDER, ID_CARD, PHONE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
        verify(registeredCardRepository, never()).save(any());
    }

    /** 出生日期非法拒绝：身份证 7-14 位为 19901301（13 月），格式校验即拒绝，不触达端口。 */
    @Test
    void invalidBirthDateRejected() {
        assertThatThrownBy(() -> service.registerCard(USER, "ICBC", HOLDER, "330106199013011234", PHONE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(BankCardErrorCode.BANK_CARD_HOLDER_INVALID);
        verify(identityPort, never()).verifyThreeElements(any(), any(), any(), any());
        verify(registeredCardRepository, never()).save(any());
    }

    /** 银行编码不存在拒绝：校验通过后查不到 BIN，抛 NOT_FOUND，不落库。 */
    @Test
    void unknownBankCodeRejected() {
        assertThatThrownBy(() -> service.registerCard(USER, "UNKNOWN_BANK", HOLDER, ID_CARD, PHONE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
        verify(registeredCardRepository, never()).save(any());
    }
}
