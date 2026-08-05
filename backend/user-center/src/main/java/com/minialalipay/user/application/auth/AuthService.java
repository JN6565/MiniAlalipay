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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 认证应用服务。
 *
 * <p>负责用户注册、登录和会话管理，是用户中心的核心应用服务。
 * 协调领域模型、仓储和安全工具，完成认证相关的业务流程。</p>
 *
 * <p>职责边界：
 * <ul>
 *   <li>只负责认证相关的业务逻辑（注册、登录、会话）</li>
 *   <li>不负责用户资料管理（修改昵称、手机号等）</li>
 *   <li>不负责支付密码管理（设置、修改、验证）</li>
 *   <li>不负责账户开户（由账户中心负责）</li>
 * </ul>
 * </p>
 *
 * <p>事务边界：
 * <ul>
 *   <li>注册操作在同一事务内保存用户和凭证，保证原子性</li>
 *   <li>登录操作在同一事务内验证密码和更新失败计数，保证一致性</li>
 *   <li>会话操作不涉及数据库事务，只操作 Redis</li>
 * </ul>
 * </p>
 *
 * @see UserRepository 用户仓储
 * @see CredentialRepository 凭证仓储
 * @see PasswordHasher 密码哈希工具
 * @see SessionManager 会话管理器
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final CredentialRepository credentialRepository;
    private final PasswordHasherPort passwordHasher;
    private final SessionManagerPort sessionManager;
    private final AccountProvisioningPort accountProvisioningPort;

    /**
     * 构造函数注入依赖。
     *
     * @param userRepository       用户仓储
     * @param credentialRepository 凭证仓储
     * @param passwordHasher       密码哈希端口
     * @param sessionManager       会话管理端口
     * @param accountProvisioningPort  账户中心开户端口
     */
    public AuthService(
            UserRepository userRepository,
            CredentialRepository credentialRepository,
            PasswordHasherPort passwordHasher,
            SessionManagerPort sessionManager,
            AccountProvisioningPort accountProvisioningPort
    ) {
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.passwordHasher = passwordHasher;
        this.sessionManager = sessionManager;
        this.accountProvisioningPort = accountProvisioningPort;
    }

    /**
     * 用户注册。
     *
     * <p>注册流程：
     * <ol>
     *   <li>校验登录名是否已存在</li>
     *   <li>校验密码是否符合安全规则</li>
     *   <li>生成用户 ID 和注册幂等键</li>
     *   <li>创建用户对象（状态为 PROVISIONING）</li>
     *   <li>对密码进行 BCrypt 哈希</li>
     *   <li>创建凭证对象</li>
     *   <li>在同一事务内保存用户和凭证</li>
     *   <li>创建会话并返回认证结果</li>
     * </ol>
     * </p>
     *
     * <p>业务规则：
     * <ul>
     *   <li>登录名唯一，规范化存储（转小写、去空格）</li>
     *   <li>登录密码使用 BCrypt 强哈希</li>
     *   <li>注册时状态为 PROVISIONING，需要等账户中心开户完成后才能变为 ACTIVE</li>
     *   <li>初始余额为 0（由账户中心负责）</li>
     * </ul>
     * </p>
     *
     * @param request 注册请求
     * @return 认证结果（包含会话令牌和用户信息）
     * @throws BusinessException 如果登录名已存在或密码不符合规则
     */
    @Transactional
    public AuthResult register(RegisterRequest request) {
        // 1. 规范化登录名
        String loginName = normalizeLoginName(request.loginName());

        // 2. 校验登录名是否已存在
        if (userRepository.existsByLoginName(loginName)) {
            throw new BusinessException(UserErrorCode.LOGIN_NAME_EXISTS);
        }

        // 3. 校验密码是否符合安全规则
        validatePassword(request.loginPassword());

        // 4. 生成用户 ID 和注册幂等键
        String userId = generateId();
        String registrationId = generateId();

        // 5. 创建用户对象（状态为 PROVISIONING）
        User user = new User(userId, registrationId, loginName, request.nickname());

        // 6. 对密码进行 BCrypt 哈希
        String hashedPassword = passwordHasher.hashPassword(request.loginPassword());

        // 7. 创建凭证对象
        Credential credential = new Credential(userId, hashedPassword);

        // 8. 在同一事务内保存用户和凭证
        userRepository.save(user);
        credentialRepository.save(credential);

        // 9. 调用账户中心开户
        try {
            log.info("开始调用账户中心开户: userId={}, registrationId={}", userId, registrationId);
            accountProvisioningPort.openAccount(userId, registrationId);
            log.info("账户中心开户成功: userId={}", userId);

            // 10. 开户成功，激活用户
            user.activate();
            userRepository.update(user);
        } catch (Exception e) {
            // 开户失败，用户保持 PROVISIONING 状态
            log.error("账户中心开户失败，用户保持 PROVISIONING 状态: userId={}", userId, e);
            // 不抛出异常，注册流程继续，返回 PROVISIONING 状态
        }

        // 11. 创建会话并返回认证结果
        String token = sessionManager.createSession(userId);
        return new AuthResult(token, userId, request.nickname(), user.getStatus().name());
    }

    /**
     * 用户登录。
     *
     * <p>登录流程：
     * <ol>
     *   <li>规范化登录名</li>
     *   <li>根据登录名查询用户</li>
     *   <li>校验用户状态（不能是 DISABLED 或 PROVISIONING）</li>
     *   <li>查询用户凭证</li>
     *   <li>校验登录是否被锁定</li>
     *   <li>校验登录密码</li>
     *   <li>如果密码错误，记录失败次数</li>
     *   <li>如果密码正确，重置失败计数</li>
     *   <li>创建会话并返回认证结果</li>
     * </ol>
     * </p>
     *
     * <p>业务规则：
     * <ul>
     *   <li>校验用户状态（不能是 DISABLED 或 PROVISIONING）</li>
     *   <li>校验登录密码哈希</li>
     *   <li>连续失败 5 次后锁定 30 分钟</li>
     *   <li>登录成功后创建会话</li>
     * </ul>
     * </p>
     *
     * @param request 登录请求
     * @return 认证结果（包含会话令牌和用户信息）
     * @throws BusinessException 如果登录名不存在、密码错误或账户被锁定
     */
    @Transactional
    public AuthResult login(LoginRequest request) {
        // 1. 规范化登录名
        String loginName = normalizeLoginName(request.loginName());

        // 2. 根据登录名查询用户
        User user = userRepository.findByLoginName(loginName)
                .orElseThrow(() -> new BusinessException(UserErrorCode.LOGIN_INVALID));

        // 3. 校验用户状态
        if (user.isDisabled()) {
            throw new BusinessException(UserErrorCode.LOGIN_INVALID);
        }
        if (user.isProvisioning()) {
            throw new BusinessException(UserErrorCode.REGISTRATION_PROCESSING);
        }

        // 4. 查询用户凭证
        Credential credential = credentialRepository.findByUserId(user.getUserId())
                .orElseThrow(() -> new BusinessException(UserErrorCode.LOGIN_INVALID));

        // 5. 校验登录是否被锁定
        if (credential.isLoginLocked()) {
            throw new BusinessException(UserErrorCode.LOGIN_LOCKED);
        }

        // 6. 校验登录密码
        if (!passwordHasher.matches(request.loginPassword(), credential.getLoginPasswordHash())) {
            // 7. 密码错误，记录失败次数
            boolean locked = credential.recordLoginFailure();
            credentialRepository.update(credential);

            if (locked) {
                throw new BusinessException(UserErrorCode.LOGIN_LOCKED);
            }
            throw new BusinessException(UserErrorCode.LOGIN_INVALID);
        }

        // 8. 密码正确，重置失败计数
        credential.resetLoginFailCount();
        credentialRepository.update(credential);

        // 9. 创建会话并返回认证结果
        String token = sessionManager.createSession(user.getUserId());
        return new AuthResult(token, user.getUserId(), user.getNickname(), user.getStatus().name());
    }

    /**
     * 用户退出登录。
     *
     * <p>销毁会话，使会话令牌立即失效。
     * 退出后用户需要重新登录。</p>
     *
     * @param token 会话令牌
     */
    public void logout(String token) {
        sessionManager.destroySession(token);
    }

    /**
     * 规范化登录名。
     *
     * <p>登录名规范化规则：
     * <ul>
     *   <li>去除首尾空格</li>
     *   <li>转换为小写</li>
     * </ul>
     * </p>
     *
     * @param loginName 原始登录名
     * @return 规范化后的登录名
     */
    private String normalizeLoginName(String loginName) {
        if (loginName == null) {
            throw new IllegalArgumentException("登录名不能为空");
        }
        return loginName.trim().toLowerCase();
    }

    /**
     * 校验密码是否符合安全规则。
     *
     * <p>密码安全规则：
     * <ul>
     *   <li>长度 8-32 位</li>
     *   <li>至少包含一个大写字母</li>
     *   <li>至少包含一个小写字母</li>
     *   <li>至少包含一个数字</li>
     * </ul>
     * </p>
     *
     * @param password 密码
     * @throws BusinessException 如果密码不符合规则
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
     * 生成唯一 ID（ULID 格式）。
     *
     * <p>使用 UUID 生成 26 位字符的 ID，用于用户 ID 和注册幂等键。
     * 生产环境应使用 ULID 算法，保证时序性和唯一性。</p>
     *
     * @return 26 位字符的唯一 ID
     */
    private String generateId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 26).toUpperCase();
    }
}
