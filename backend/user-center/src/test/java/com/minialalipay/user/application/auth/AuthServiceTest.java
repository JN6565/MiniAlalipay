package com.minialalipay.user.application.auth;

import com.minialalipay.common.error.BusinessException;
import com.minialalipay.user.application.auth.dto.AuthResult;
import com.minialalipay.user.application.auth.dto.LoginRequest;
import com.minialalipay.user.application.auth.dto.RegisterRequest;
import com.minialalipay.user.domain.auth.UserErrorCode;
import com.minialalipay.user.domain.credential.Credential;
import com.minialalipay.user.domain.credential.CredentialRepository;
import com.minialalipay.user.domain.credential.PasswordHasherPort;
import com.minialalipay.user.domain.user.RoleAssignmentRepository;
import com.minialalipay.user.domain.user.SessionManagerPort;
import com.minialalipay.user.domain.user.User;
import com.minialalipay.user.domain.user.UserRepository;
import com.minialalipay.user.domain.user.UserStatus;
import com.minialalipay.user.domain.user.UserIdGeneratorPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.Set;

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
    private final RoleAssignmentRepository roleAssignmentRepository = mock(RoleAssignmentRepository.class);
    private final UserIdGeneratorPort userIdGenerator = mock(UserIdGeneratorPort.class);
    private final AuthService service = new AuthService(userRepository, credentialRepository,
            passwordHasher, sessionManager, accountProvisioningPort, roleAssignmentRepository, userIdGenerator);

    /** 验证注册会生成账户号、保存两套独立密码哈希并完成零余额账户开户调用。 */
    @Test
    void shouldRegisterWithGeneratedAccountNumberAndPaymentPassword() {
        when(userRepository.existsByPhoneNumber("13800138000")).thenReturn(false);
        when(passwordHasher.hashPassword("Login123")).thenReturn("login-hash");
        when(passwordHasher.hashPassword("123456")).thenReturn("payment-hash");
        when(userIdGenerator.generatePair()).thenReturn(
                new UserIdGeneratorPort.IdPair("USRABCDEFGHI20260807000001", "REGABCDEFGHI20260807000001"));
        when(accountProvisioningPort.openAccount(anyString(), anyString())).thenReturn("account-id");
        when(sessionManager.createSession(anyString())).thenReturn("session-token");

        AuthResult result = service.register(new RegisterRequest(
                "13800138000", "张三", "Login123", "123456"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertTrue(userCaptor.getValue().getAccountNumber().matches("^62\\d{14}$"));
        assertEquals("13800138000", userCaptor.getValue().getPhoneNumber());
        assertEquals("张三", userCaptor.getValue().getNickname());

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
                new RegisterRequest("13800138000", "张三", "Login123", "123456")));

        assertEquals(UserErrorCode.PHONE_NUMBER_EXISTS, exception.errorCode());
    }

    /** 验证开户失败时注册不会返回可登录会话，避免 C 端拿到 PROVISIONING 用户。 */
    @Test
    void shouldRejectRegisterWhenAccountProvisioningFails() {
        when(userRepository.existsByPhoneNumber("13800138000")).thenReturn(false);
        when(passwordHasher.hashPassword("Login123")).thenReturn("login-hash");
        when(passwordHasher.hashPassword("123456")).thenReturn("payment-hash");
        when(userIdGenerator.generatePair()).thenReturn(
                new UserIdGeneratorPort.IdPair("USRFAILREGISTRATION01", "REGFAILREGISTRATION01"));
        when(accountProvisioningPort.openAccount(anyString(), anyString()))
                .thenThrow(new BusinessException(UserErrorCode.REGISTRATION_PROCESSING));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.register(
                new RegisterRequest("13800138000", "张三", "Login123", "123456")));

        assertEquals(UserErrorCode.REGISTRATION_PROCESSING, exception.errorCode());
        verify(sessionManager, never()).createSession(anyString());
    }

    /** 验证历史 PROVISIONING 用户登录时会先幂等补开户，成功后再创建会话。 */
    @Test
    void shouldRecoverProvisioningUserOnLogin() {
        User user = new User("USRTESTUSER0120260807000001", "REGTESTUSER0120260807000001",
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

    /** 冻结账号仅在凭据验证通过后返回明确提示，避免泄露账号状态。 */
    @Test
    void shouldReportFrozenAccountAfterValidPassword() {
        User user = activeUser("USER123");
        user.freeze("ADMIN", "运营冻结");
        Credential credential = new Credential(user.getUserId(), "login-hash");
        when(userRepository.findByLoginIdentifier("13800138000")).thenReturn(Optional.of(user));
        when(credentialRepository.findByUserId(user.getUserId())).thenReturn(Optional.of(credential));
        when(passwordHasher.matches("Login123", "login-hash")).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.login(new LoginRequest("13800138000", "Login123")));

        assertEquals(UserErrorCode.ACCOUNT_FROZEN, exception.errorCode());
        verify(sessionManager, never()).createSession(anyString());
    }

    /** 验证修改密码会更新强哈希并立即销毁当前会话。 */
    @Test
    void shouldChangeLoginPasswordAndDestroyCurrentSession() {
        Credential credential = new Credential("USER123", "old-hash");
        when(credentialRepository.findByUserId("USER123")).thenReturn(Optional.of(credential));
        when(passwordHasher.matches("OldPass1", "old-hash")).thenReturn(true);
        when(passwordHasher.hashPassword("NewPass2")).thenReturn("new-hash");

        service.changeLoginPassword("USER123", "old-token", "OldPass1", "NewPass2");

        assertEquals("new-hash", credential.getLoginPasswordHash());
        verify(credentialRepository).update(credential);
        verify(sessionManager).destroyAllSessions("USER123");
    }

    /** 当前密码错误时不得更新凭证或销毁会话。 */
    @Test
    void shouldRejectWrongCurrentLoginPasswordWithoutDestroyingSessions() {
        Credential credential = new Credential("USER123", "old-hash");
        when(credentialRepository.findByUserId("USER123")).thenReturn(Optional.of(credential));
        when(passwordHasher.matches("WrongPass1", "old-hash")).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.changeLoginPassword("USER123", "token", "WrongPass1", "NewPass456"));

        assertEquals(UserErrorCode.CURRENT_LOGIN_PASSWORD_INVALID, exception.errorCode());
        verify(credentialRepository, never()).update(credential);
        verify(sessionManager, never()).destroyAllSessions(anyString());
    }

    /** 会话解析必须返回角色授权表中的真实角色，普通用户无授权时回退为默认 USER。 */
    @Test
    void shouldResolveSessionWithRealRoles() {
        when(sessionManager.validateSession("valid-token")).thenReturn("USER123");
        when(userRepository.findById("USER123")).thenReturn(Optional.of(activeUser("USER123")));
        when(roleAssignmentRepository.findRolesByUserId("USER123")).thenReturn(Set.of("ADMIN", "OPERATOR"));

        var identity = service.resolveSession("valid-token").orElseThrow();

        assertEquals("USER123", identity.userId());
        assertEquals(Set.of("ADMIN", "OPERATOR"), identity.roles());
    }

    /** 无效会话解析结果必须为空，禁止据此伪造主体。 */
    @Test
    void shouldRejectInvalidSessionResolution() {
        when(sessionManager.validateSession("expired-token")).thenReturn(null);

        assertTrue(service.resolveSession("expired-token").isEmpty());
    }

    /** 冻结（DISABLED）用户即使令牌有效也不得解析主体，冻结即时禁止发起新业务。 */
    @Test
    void shouldRejectDisabledUserSessionResolution() {
        when(sessionManager.validateSession("frozen-token")).thenReturn("USER123");
        User user = activeUser("USER123");
        user.freeze("ADMIN", "风控冻结");
        when(userRepository.findById("USER123")).thenReturn(Optional.of(user));

        assertTrue(service.resolveSession("frozen-token").isEmpty());
    }

    /** 用户不存在时解析结果必须为空，不得据此伪造主体。 */
    @Test
    void shouldRejectUnknownUserSessionResolution() {
        when(sessionManager.validateSession("orphan-token")).thenReturn("USER999");
        when(userRepository.findById("USER999")).thenReturn(Optional.empty());

        assertTrue(service.resolveSession("orphan-token").isEmpty());
    }

    /** 构造并激活一个 ACTIVE 用户（6 参构造器默认 PROVISIONING）。 */
    private User activeUser(String userId) {
        User user = new User(userId, "REG" + userId, "6200000000000001", "13800138000", "张三", "小张");
        user.activate();
        return user;
    }

    /** 当前身份接口返回真实姓名优先的展示名与真实角色，普通用户回退为 USER。 */
    @Test
    void shouldReturnCurrentIdentityWithDisplayNameAndDefaultRole() {
        User user = new User("USER123", "REG123", "6200000000000001", "13800138000", "张三", "小张");
        when(userRepository.findById("USER123")).thenReturn(Optional.of(user));
        when(roleAssignmentRepository.findRolesByUserId("USER123")).thenReturn(Set.of());

        var identity = service.currentIdentity("USER123");

        assertEquals("张三", identity.displayName());
        assertEquals(Set.of("USER"), identity.roles());
    }
}
