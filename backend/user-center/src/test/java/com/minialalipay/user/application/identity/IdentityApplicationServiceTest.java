package com.minialalipay.user.application.identity;

import com.minialalipay.common.error.BusinessException;
import com.minialalipay.user.domain.auth.UserErrorCode;
import com.minialalipay.user.domain.user.User;
import com.minialalipay.user.domain.user.UserRepository;
import com.minialalipay.user.application.identity.dto.IdentityDTO;
import org.junit.jupiter.api.Test;

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
}
