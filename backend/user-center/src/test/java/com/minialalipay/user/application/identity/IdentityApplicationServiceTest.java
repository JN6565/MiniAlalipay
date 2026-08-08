package com.minialalipay.user.application.identity;

import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import com.minialalipay.user.domain.auth.UserErrorCode;
import com.minialalipay.user.domain.user.User;
import com.minialalipay.user.domain.user.UserRepository;
import com.minialalipay.user.application.identity.dto.IdentityDTO;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 身份绑定应用服务测试。
 *
 * <p>重点覆盖身份证号唯一性不变量：同一身份证号全系统只允许绑定一个账户，
 * 但本人重复提交相同身份证号（幂等重绑）必须放行。</p>
 */
class IdentityApplicationServiceTest {

    private static final String USER_ID = "USRTESTUSER0120260807000001";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final IdentityApplicationService service = new IdentityApplicationService(userRepository);

    /** 验证身份证号已被其他账户绑定时抛出 ID_CARD_ALREADY_BOUND，且不执行更新。 */
    @Test
    void shouldRejectIdCardAlreadyBoundByOtherAccount() {
        User user = new User(USER_ID, "REG" + USER_ID,
                "6200000000000001", "13800138000", "张三", "小张");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.existsByIdCardHashExcluding(any(byte[].class), eq(USER_ID))).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.bindIdentity(USER_ID, "张三", "110101199003071234"));

        assertEquals(UserErrorCode.ID_CARD_ALREADY_BOUND, exception.errorCode());
        verify(userRepository, never()).update(any());
    }

    /** 验证本人重复提交相同身份证号允许通过（查重排除本人，幂等重绑）。 */
    @Test
    void shouldAllowSameUserRebindSameIdCard() {
        User user = new User(USER_ID, "REG" + USER_ID,
                "6200000000000001", "13800138000", "张三", "小张");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.existsByIdCardHashExcluding(any(byte[].class), eq(USER_ID))).thenReturn(false);

        IdentityDTO result = service.bindIdentity(USER_ID, "张三", "110101199003071234");

        assertNotNull(result);
        assertEquals("张三", result.realName());
        assertEquals("VERIFIED", result.identityStatus());
        verify(userRepository).update(user);
    }

    /** 验证用户不存在时抛出 NOT_FOUND。 */
    @Test
    void shouldRejectWhenUserNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () ->
                service.bindIdentity(USER_ID, "张三", "110101199003071234"));
    }

    /** 验证出生日期非法的身份证号被拒绝（项目统一校验口径），且不执行更新。 */
    @Test
    void shouldRejectIdCardWithInvalidBirthDate() {
        User user = new User(USER_ID, "REG" + USER_ID,
                "6200000000000001", "13800138000", "张三", "小张");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.bindIdentity(USER_ID, "张三", "110101199013071234"));

        assertEquals(CommonErrorCode.INVALID_REQUEST, exception.errorCode());
        verify(userRepository, never()).update(any());
    }

    // ==================== 三要素交叉校验 ====================

    private static final String HOLDER = "张三";
    private static final String ID_CARD = "330106199001011234";
    private static final String PHONE = "13812345678";

    /** 构造已绑定身份的用户：姓名/身份证哈希/手机号与测试三要素一致。 */
    private User boundUser() {
        User user = new User(USER_ID, "REG" + USER_ID,
                "6200000000000001", PHONE, null, "小张");
        user.bindIdentity(HOLDER, sha256(ID_CARD), "3301**********1234");
        return user;
    }

    private static byte[] sha256(String input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 三要素全部匹配：matched=true 且 identityBound=true。 */
    @Test
    void verifyThreeElementsAllMatched() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(boundUser()));

        IdentityApplicationService.VerifyResult result =
                service.verifyThreeElements(USER_ID, HOLDER, ID_CARD, PHONE);

        assertEquals(true, result.matched());
        assertEquals(true, result.identityBound());
    }

    /** 姓名不符：matched=false，但已绑定身份 identityBound=true。 */
    @Test
    void verifyThreeElementsMismatchedName() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(boundUser()));

        IdentityApplicationService.VerifyResult result =
                service.verifyThreeElements(USER_ID, "李四", ID_CARD, PHONE);

        assertEquals(false, result.matched());
        assertEquals(true, result.identityBound());
    }

    /** 身份证哈希不符：matched=false，identityBound=true。 */
    @Test
    void verifyThreeElementsMismatchedIdCard() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(boundUser()));

        IdentityApplicationService.VerifyResult result =
                service.verifyThreeElements(USER_ID, HOLDER, "330106199202021234", PHONE);

        assertEquals(false, result.matched());
        assertEquals(true, result.identityBound());
    }

    /** 手机号不符：matched=false，identityBound=true。 */
    @Test
    void verifyThreeElementsMismatchedPhone() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(boundUser()));

        IdentityApplicationService.VerifyResult result =
                service.verifyThreeElements(USER_ID, HOLDER, ID_CARD, "13900000000");

        assertEquals(false, result.matched());
        assertEquals(true, result.identityBound());
    }

    /** 用户不存在：按未绑定身份处理（matched=false 且 identityBound=false），不泄露账号存在性。 */
    @Test
    void verifyThreeElementsUserNotFoundTreatedAsNotBound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        IdentityApplicationService.VerifyResult result =
                service.verifyThreeElements(USER_ID, HOLDER, ID_CARD, PHONE);

        assertEquals(false, result.matched());
        assertEquals(false, result.identityBound());
    }
}
