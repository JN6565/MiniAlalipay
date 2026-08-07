package com.minialalipay.user.application.auth;

import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import com.minialalipay.user.application.auth.dto.AuthResult;
import com.minialalipay.user.application.auth.dto.CurrentIdentity;
import com.minialalipay.user.application.auth.dto.LoginRequest;
import com.minialalipay.user.application.auth.dto.RegisterRequest;
import com.minialalipay.user.application.auth.dto.SessionIdentity;
import com.minialalipay.user.domain.auth.UserErrorCode;
import com.minialalipay.user.domain.credential.Credential;
import com.minialalipay.user.domain.credential.CredentialRepository;
import com.minialalipay.user.domain.credential.PasswordHasherPort;
import com.minialalipay.user.domain.user.RoleAssignmentRepository;
import com.minialalipay.user.domain.user.SessionManagerPort;
import com.minialalipay.user.domain.user.User;
import com.minialalipay.user.domain.user.UserRepository;
import com.minialalipay.user.domain.user.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 认证应用服务。
 *
 * <p>负责用户注册、登录和会话管理，是用户中心的核心应用服务。该服务只编排认证相关流程，密码哈希、会话管理和
 * 跨服务开户均通过端口完成，避免应用层依赖具体基础设施实现。</p>
 *
 * <p>事务边界：注册在用户中心本地事务内保存用户和凭证，并在开户成功后激活用户；登录在本地事务内完成状态恢复、
 * 密码校验和失败计数更新。Redis 会话创建只在用户可登录后执行。</p>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final CredentialRepository credentialRepository;
    private final PasswordHasherPort passwordHasher;
    private final SessionManagerPort sessionManager;
    private final AccountProvisioningPort accountProvisioningPort;
    private final RoleAssignmentRepository roleAssignmentRepository;

    /**
     * 构造认证服务所需依赖。
     *
     * @param userRepository 用户聚合仓储
     * @param credentialRepository 凭证仓储
     * @param passwordHasher 密码哈希端口
     * @param sessionManager 会话管理端口
     * @param accountProvisioningPort 账户中心开户注册端口
     * @param roleAssignmentRepository 角色授权仓储端口
     */
    public AuthService(
            UserRepository userRepository,
            CredentialRepository credentialRepository,
            PasswordHasherPort passwordHasher,
            SessionManagerPort sessionManager,
            AccountProvisioningPort accountProvisioningPort,
            RoleAssignmentRepository roleAssignmentRepository
    ) {
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.passwordHasher = passwordHasher;
        this.sessionManager = sessionManager;
        this.accountProvisioningPort = accountProvisioningPort;
        this.roleAssignmentRepository = roleAssignmentRepository;
    }

    /**
     * 使用手机号、真实姓名、登录密码和支付密码注册 C 端用户，并自动开立零余额账户。
     *
     * <p>注册成功的对外语义必须包含开户成功：如果账户中心不可用或拒绝开户，本方法抛出业务异常，不创建会话，
     * 防止前端拿到 PROVISIONING 用户后停留在“注册开户中”。账户中心按 registrationId 幂等，后续登录恢复仍可复用同一编号。</p>
     *
     * @param request 注册请求，包含手机号、真实姓名、可选昵称、登录密码和支付密码
     * @return 认证结果，包含会话令牌、用户 ID、系统账户号、昵称和用户状态
     * @throws BusinessException 手机号重复、密码不符合规则或开户注册失败时抛出
     */
    @Transactional
    public AuthResult register(RegisterRequest request) {
        String phoneNumber = normalizePhoneNumber(request.phoneNumber());

        // 手机号既是登录凭据也是敏感唯一标识，应用预检和数据库唯一索引共同防止并发重复注册。
        if (userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new BusinessException(UserErrorCode.PHONE_NUMBER_EXISTS);
        }

        validatePassword(request.loginPassword());
        validatePaymentPassword(request.paymentPassword());

        String userId = generateId();
        String registrationId = generateId();
        String accountNumber = generateAccountNumber();

        User user = new User(userId, registrationId, accountNumber, phoneNumber,
                request.realName().trim(), request.nickname());

        String hashedPassword = passwordHasher.hashPassword(request.loginPassword());
        Credential credential = new Credential(userId, hashedPassword);
        credential.setPaymentPasswordHash(passwordHasher.hashPassword(request.paymentPassword()));

        userRepository.save(user);
        credentialRepository.save(credential);

        log.info("开始调用账户中心开户 userId={}, registrationId={}", userId, registrationId);
        accountProvisioningPort.openAccount(userId, registrationId);
        log.info("账户中心开户成功 userId={}", userId);

        // 开户成功后才能激活并发放会话，避免用户带着 PROVISIONING 身份进入 C 端。
        user.activate();
        userRepository.update(user);

        String token = sessionManager.createSession(userId);
        return new AuthResult(token, userId, accountNumber, user.getNickname(), user.getStatus().name());
    }

    /**
     * 使用手机号或系统账户号登录。
     *
     * <p>如果用户处于 PROVISIONING 状态，会先按 registrationId 幂等重试开户；恢复成功后继续校验密码并创建会话，
     * 恢复失败则返回注册开户处理中，不发放会话。</p>
     *
     * @param request 登录请求，包含手机号或系统账户号以及登录密码
     * @return 认证结果，包含会话令牌、用户 ID、系统账户号、昵称和用户状态
     * @throws BusinessException 用户不存在、密码错误、登录锁定、用户禁用或开户恢复失败时抛出
     */
    @Transactional
    public AuthResult login(LoginRequest request) {
        String loginIdentifier = normalizeLoginIdentifier(request.loginIdentifier());

        User user = userRepository.findByLoginIdentifier(loginIdentifier)
                .orElseThrow(() -> new BusinessException(UserErrorCode.LOGIN_INVALID));

        if (user.isDisabled()) {
            throw new BusinessException(UserErrorCode.LOGIN_INVALID);
        }
        if (user.isProvisioning()) {
            user = recoverProvisioningUser(user);
        }

        Credential credential = credentialRepository.findByUserId(user.getUserId())
                .orElseThrow(() -> new BusinessException(UserErrorCode.LOGIN_INVALID));

        if (credential.isLoginLocked()) {
            throw new BusinessException(UserErrorCode.LOGIN_LOCKED);
        }

        if (!passwordHasher.matches(request.loginPassword(), credential.getLoginPasswordHash())) {
            boolean locked = credential.recordLoginFailure();
            credentialRepository.update(credential);

            if (locked) {
                throw new BusinessException(UserErrorCode.LOGIN_LOCKED);
            }
            throw new BusinessException(UserErrorCode.LOGIN_INVALID);
        }

        credential.resetLoginFailCount();
        credentialRepository.update(credential);

        String token = sessionManager.createSession(user.getUserId());
        return new AuthResult(token, user.getUserId(), user.getAccountNumber(), user.getNickname(), user.getStatus().name());
    }

    /**
     * 对已经持久化但还未激活的用户进行一次开户恢复。
     *
     * <p>账户中心开户注册接口以 registrationId 幂等，登录时重试可以修复早先因服务重启、网络抖动或旧版本注册逻辑
     * 留下的 PROVISIONING 用户。如果恢复仍然失败，继续返回注册开户处理中，并且不创建会话。</p>
     */
    private User recoverProvisioningUser(User user) {
        try {
            log.info("登录时尝试恢复注册开户 userId={}, registrationId={}",
                    user.getUserId(), user.getRegistrationId());
            accountProvisioningPort.openAccount(user.getUserId(), user.getRegistrationId());
            user.activate();
            userRepository.update(user);
            return user;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("登录时恢复注册开户失败 userId={}", user.getUserId(), e);
            throw new BusinessException(UserErrorCode.REGISTRATION_PROCESSING);
        }
    }

    /**
     * 退出登录并销毁当前会话。
     *
     * @param token 会话令牌
     */
    public void logout(String token) {
        sessionManager.destroySession(token);
    }

    /** 供内部网关校验会话并解析真实用户 ID。 */
    public String validateSession(String token) {
        return sessionManager.validateSession(token);
    }

    /**
     * 校验会话并解析可信主体与真实角色集合。
     *
     * <p>角色来自 {@code role_assignment} 表；普通用户无授权时回退为默认 {@code USER} 角色。
     * 无效会话返回空结果，调用方不得据此伪造身份。</p>
     *
     * @param token 不含 Bearer 前缀的会话令牌
     * @return 会话对应的主体与角色；会话无效返回空
     */
    public Optional<SessionIdentity> resolveSession(String token) {
        String userId = sessionManager.validateSession(token);
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        // 冻结（DISABLED）与未完成开户（PROVISIONING）用户会话立即失效：
        // 冻结须即时禁止发起新业务，而非仅阻止下次登录。
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            return Optional.empty();
        }
        return Optional.of(new SessionIdentity(userId, resolveRoles(userId)));
    }

    /**
     * 查询当前身份（展示名 + 角色），供 B 端当前身份接口消费。
     *
     * @param userId 网关注入的可信用户 ID
     * @return 当前身份
     * @throws BusinessException 用户不存在时抛出 {@link CommonErrorCode#NOT_FOUND}
     */
    public CurrentIdentity currentIdentity(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        String displayName = (user.getRealName() == null || user.getRealName().isBlank())
                ? user.getNickname()
                : user.getRealName();
        return new CurrentIdentity(userId, displayName, resolveRoles(userId));
    }

    /**
     * 解析用户实际角色；无角色授权时回退为默认 {@code USER}，保证普通用户仍可访问 C 端能力。
     */
    private Set<String> resolveRoles(String userId) {
        Set<String> roles = roleAssignmentRepository.findRolesByUserId(userId);
        return roles.isEmpty() ? Set.of("USER") : roles;
    }

    /**
     * 校验当前密码后更新登录密码，并销毁当前会话。
     *
     * @param userId 网关注入的可信用户 ID
     * @param token 当前会话令牌
     * @param currentPassword 当前登录密码
     * @param newPassword 新登录密码
     */
    @Transactional
    public void changeLoginPassword(String userId, String token, String currentPassword, String newPassword) {
        Credential credential = credentialRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.LOGIN_INVALID));
        if (!passwordHasher.matches(currentPassword, credential.getLoginPasswordHash())) {
            throw new BusinessException(UserErrorCode.CURRENT_LOGIN_PASSWORD_INVALID);
        }
        validatePassword(newPassword);
        if (passwordHasher.matches(newPassword, credential.getLoginPasswordHash())) {
            throw new BusinessException(UserErrorCode.PASSWORD_REUSE_NOT_ALLOWED);
        }
        credential.setLoginPasswordHash(passwordHasher.hashPassword(newPassword));
        credential.resetLoginFailCount();
        credentialRepository.update(credential);

        // 只有数据库事务提交后才废弃全部会话，避免提交失败却提前把用户踢下线。
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sessionManager.destroyAllSessions(userId);
                }
            });
        } else {
            sessionManager.destroyAllSessions(userId);
        }
    }

    private String normalizeLoginIdentifier(String loginIdentifier) {
        if (loginIdentifier == null || loginIdentifier.isBlank()) {
            throw new IllegalArgumentException("手机号或账户号不能为空");
        }
        return loginIdentifier.trim();
    }

    private String normalizePhoneNumber(String phoneNumber) {
        String normalized = phoneNumber == null ? "" : phoneNumber.trim();
        if (!normalized.matches("^1[3-9]\\d{9}$")) {
            throw new IllegalArgumentException("手机号格式不正确");
        }
        return normalized;
    }

    private void validatePaymentPassword(String paymentPassword) {
        if (paymentPassword == null || !paymentPassword.matches("^\\d{6}$")) {
            throw new BusinessException(UserErrorCode.PASSWORD_POLICY_VIOLATION);
        }
    }

    /** 生成 16 位纯数字系统账户号，62 前缀用于和 11 位手机号明确区分。 */
    private String generateAccountNumber() {
        long value = Math.abs(UUID.randomUUID().getMostSignificantBits()) % 100_000_000_000_000L;
        return "62" + String.format("%014d", value);
    }

    /**
     * 校验登录密码是否符合安全规则。
     *
     * @param password 登录密码，要求 8-32 位且至少包含大写字母、小写字母和数字
     * @throws BusinessException 密码不符合规则时抛出
     */
    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 32) {
            throw new BusinessException(UserErrorCode.PASSWORD_POLICY_VIOLATION);
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new BusinessException(UserErrorCode.PASSWORD_POLICY_VIOLATION);
        }
        if (!password.matches(".*[a-z].*")) {
            throw new BusinessException(UserErrorCode.PASSWORD_POLICY_VIOLATION);
        }
        if (!password.matches(".*\\d.*")) {
            throw new BusinessException(UserErrorCode.PASSWORD_POLICY_VIOLATION);
        }
    }

    /**
     * 生成 26 位业务 ID。
     *
     * <p>当前沿用项目既有 UUID 截断策略；后续如统一 ULID，应通过独立 ID 组件替换。</p>
     */
    private String generateId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 26).toUpperCase();
    }
}
