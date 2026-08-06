package com.minialalipay.user.application.auth;

import com.minialalipay.common.error.BusinessException;
import com.minialalipay.user.application.auth.dto.AuthResult;
import com.minialalipay.user.application.auth.dto.LoginRequest;
import com.minialalipay.user.application.auth.dto.RegisterRequest;
import com.minialalipay.user.domain.auth.UserErrorCode;
import com.minialalipay.user.domain.credential.Credential;
import com.minialalipay.user.domain.credential.CredentialRepository;
import com.minialalipay.user.domain.credential.PasswordHasherPort;
import com.minialalipay.user.domain.user.SessionManagerPort;
import com.minialalipay.user.domain.user.User;
import com.minialalipay.user.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 注册和登录核心限制的应用服务测试。 */
class AuthServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final CredentialRepository credentialRepository = mock(CredentialRepository.class);
    private final PasswordHasherPort passwordHasher = mock(PasswordHasherPort.class);
    private final SessionManagerPort sessionManager = mock(SessionManagerPort.class);
    private final AccountProvisioningPort accountProvisioningPort = mock(AccountProvisioningPort.class);
    private final AuthService service = new AuthService(userRepository, credentialRepository,
            passwordHasher, sessionManager, accountProvisioningPort);

    /** 验证注册会生成账户号、保存两套独立密码哈希并完成零余额账户开户调用。 */
    @Test
    void shouldRegisterWithGeneratedAccountNumberAndPaymentPassword() {
        when(userRepository.existsByPhoneNumber("13800138000")).thenReturn(false);
        when(passwordHasher.hashPassword("Login123")).thenReturn("login-hash");
        when(passwordHasher.hashPassword("123456")).thenReturn("payment-hash");
        when(accountProvisioningPort.openAccount(anyString(), anyString())).thenReturn("account-id");
        when(sessionManager.createSession(anyString())).thenReturn("session-token");

        AuthResult result = service.register(new RegisterRequest(
                "13800138000", "张三", "小张", "Login123", "123456"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertTrue(userCaptor.getValue().getAccountNumber().matches("^62\\d{14}$"));
        assertEquals("13800138000", userCaptor.getValue().getPhoneNumber());
        assertEquals("张三", userCaptor.getValue().getRealName());

        ArgumentCaptor<Credential> credentialCaptor = ArgumentCaptor.forClass(Credential.class);
        verify(credentialRepository).save(credentialCaptor.capture());
        assertEquals("login-hash", credentialCaptor.getValue().getLoginPasswordHash());
        assertEquals("payment-hash", credentialCaptor.getValue().getPaymentPasswordHash());
        assertEquals("ACTIVE", result.status());
    }

    /** 验证同一手机号无法重复注册。 */
    @Test
    void shouldRejectDuplicatedPhoneNumber() {
        when(userRepository.existsByPhoneNumber("13800138000")).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.register(
                new RegisterRequest("13800138000", "张三", null, "Login123", "123456")));

        assertEquals(UserErrorCode.PHONE_NUMBER_EXISTS, exception.errorCode());
    }

    /** 验证开户失败时注册不会返回可登录会话，避免 C 端拿到 PROVISIONING 用户。 */
    @Test
    void shouldRejectRegisterWhenAccountProvisioningFails() {
        when(userRepository.existsByPhoneNumber("13800138000")).thenReturn(false);
        when(passwordHasher.hashPassword("Login123")).thenReturn("login-hash");
        when(passwordHasher.hashPassword("123456")).thenReturn("payment-hash");
        when(accountProvisioningPort.openAccount(anyString(), anyString()))
                .thenThrow(new BusinessException(UserErrorCode.REGISTRATION_PROCESSING));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.register(
                new RegisterRequest("13800138000", "张三", "小张", "Login123", "123456")));

        assertEquals(UserErrorCode.REGISTRATION_PROCESSING, exception.errorCode());
        verify(sessionManager, never()).createSession(anyString());
    }

    /** 验证历史 PROVISIONING 用户登录时会先幂等补开户，成功后再创建会话。 */
    @Test
    void shouldRecoverProvisioningUserOnLogin() {
        User user = new User("USER12345678901234567890", "REG123456789012345678901",
                "6200000000000001", "13800138000", "张三", "小张");
        Credential credential = new Credential(user.getUserId(), "login-hash");

        when(userRepository.findByLoginIdentifier("13800138000")).thenReturn(Optional.of(user));
        when(accountProvisioningPort.openAccount(user.getUserId(), user.getRegistrationId())).thenReturn("account-id");
        when(credentialRepository.findByUserId(user.getUserId())).thenReturn(Optional.of(credential));
        when(passwordHasher.matches("Login123", "login-hash")).thenReturn(true);
        when(sessionManager.createSession(user.getUserId())).thenReturn("session-token");

        AuthResult result = service.login(new LoginRequest("13800138000", "Login123"));

        assertEquals("ACTIVE", result.status());
        assertEquals("session-token", result.accessToken());
        verify(userRepository).update(user);
    }
}