package com.minialalipay.user.domain.user;

import java.time.Instant;

/**
 * 用户聚合根。
 *
 * <p>保存用户主体、展示资料和账户级状态，不保存密码或 RBAC 角色。
 * 密码保存在 {@code credential} 表中，角色保存在 {@code role_assignment} 表中。</p>
 *
 * <p>状态流转规则：
 * <ul>
 *   <li>注册时初始状态为 {@link UserStatus#PROVISIONING}，等待账户中心开户</li>
 *   <li>账户中心开户完成后，状态变为 {@link UserStatus#ACTIVE}，用户可以登录</li>
 *   <li>管理操作可以将状态设为 {@link UserStatus#DISABLED}，禁止用户登录</li>
 * </ul>
 * </p>
 *
 * <p>关键不变量：
 * <ul>
 *   <li>只有 {@link UserStatus#ACTIVE} 状态的用户可以登录</li>
 *   <li>{@code registrationId} 是跨服务开户幂等键，由用户中心生成</li>
 *   <li>{@code loginName} 在系统内唯一，用于登录和唯一识别</li>
 *   <li>{@code nickname} 可重复，仅用于展示</li>
 * </ul>
 * </p>
 *
 * @see UserStatus 用户状态枚举
 * @see com.minialalipay.user.domain.credential.Credential 用户凭证实体
 */
public class User {

    /**
     * 用户 ID（26 位字符，格式：{@code USR} + 9 位随机大写字母 + {@code YYYYMMDD} + 6 位日序列号）。
     * <p>跨模块引用用户的稳定标识，在整个系统生命周期内不变。注册时由
     * {@link UserIdGeneratorPort} 实现与 {@code registrationId} 成对生成。</p>
     */
    private final String userId;

    /**
     * 注册幂等键（26 位字符，格式：{@code REG} + 9 位随机大写字母 + {@code YYYYMMDD} + 6 位日序列号）。
     * <p>用户中心生成的注册幂等键，用于开户恢复和既有资源查询。
     * 账户中心以 {@code registration_id} 作为开户幂等键，保证注册和开户的原子性。
     * 注册时与 {@code userId} 成对生成，两者共享同一日期序列号但前缀和随机段不同。</p>
     */
    private final String registrationId;

    /**
     * 登录名（规范化存储，最大 64 字符）。
     * <p>用于登录和唯一识别，系统内唯一。注册时规范化处理（如转小写、去空格）。</p>
     */
    private String accountNumber;

    /** 完整手机号，规范化为 11 位数字并在数据库中保持唯一。 */
    private final String phoneNumber;

    /** 用户真实姓名，注册时为 null，绑定身份后设置，用于收款人精确或模糊查询。 */
    private String realName;

    /**
     * 昵称（最大 64 字符）。
     * <p>可重复的展示名称，不要求唯一。</p>
     */
    private String nickname;

    /**
     * 手机号尾号（4 位字符，可空）。
     * <p>仅用于辅助检索和脱敏展示，不保存完整手机号。</p>
     */
    private String phoneTail;

    /**
     * 演示身份状态（最大 16 字符）。
     * <p>不代表真实 KYC，仅用于演示身份核验流程。
     * 取值：PENDING_VERIFICATION / VERIFIED / REJECTED</p>
     */
    private String identityStatus;

    /**
     * 用户状态（最大 16 字符）。
     * <p>控制用户是否可以登录和使用系统功能。
     * 取值：PROVISIONING / ACTIVE / DISABLED</p>
     */
    private UserStatus status;

    /**
     * 版本号（乐观锁）。
     * <p>用于并发控制，每次状态或资料变更时递增。
     * 更新时必须校验版本号一致，防止并发覆盖。</p>
     */
    private long version;

    /** 管理冻结操作者用户 ID（仅 DISABLED 状态有值），用于 B 端审计展示。 */
    private String disabledBy;

    /** 管理冻结理由（仅 DISABLED 状态有值），用于 B 端审计展示。 */
    private String disabledReason;

    /** 身份证号掩码（如 3301**********1234），绑定身份后保存。 */
    private String idCard;

    /** 身份证号明文 SHA-256 哈希，用于绑卡时三要素交叉比对。 */
    private byte[] idCardHash;

    /**
     * 用户注册时间（UTC，毫秒精度）。
     * <p>注册时设置，之后不可修改。</p>
     */
    private final Instant createdAt;

    /**
     * 最近资料或状态变更时间（UTC，毫秒精度）。
     * <p>每次修改用户资料或状态时更新。</p>
     */
    private Instant updatedAt;

    /**
     * 创建新用户（注册时使用）。
     *
     * <p>注册时自动设置以下属性：
     * <ul>
     *   <li>{@code identityStatus} = PENDING_VERIFICATION（待核验）</li>
     *   <li>{@code status} = PROVISIONING（等待开户）</li>
     *   <li>{@code version} = 0（初始版本）</li>
     *   <li>{@code createdAt} 和 {@code updatedAt} = 当前时间</li>
     * </ul>
     * </p>
     *
     * @param userId         用户 ID（26 位字符，USR 前缀）
     * @param registrationId 注册幂等键（26 位字符，REG 前缀）
     * @param loginName      登录名（已规范化，最大 64 字符）
     * @param nickname       昵称（最大 64 字符）
     * @throws IllegalArgumentException 如果任何必填参数为空
     */
    public User(String userId, String registrationId, String accountNumber, String phoneNumber, String realName, String nickname) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("用户 ID 不能为空");
        }
        if (registrationId == null || registrationId.isBlank()) {
            throw new IllegalArgumentException("注册幂等键不能为空");
        }
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new IllegalArgumentException("账户号不能为空");
        }
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("手机号不能为空");
        }
        this.userId = userId;
        this.registrationId = registrationId;
        this.accountNumber = accountNumber;
        this.phoneNumber = phoneNumber;
        this.realName = (realName == null || realName.isBlank()) ? null : realName.trim();
        this.nickname = nickname == null || nickname.isBlank()
                ? maskPhoneNumber(phoneNumber)
                : nickname.trim();
        this.phoneTail = phoneNumber.substring(phoneNumber.length() - 4);
        this.identityStatus = "PENDING_VERIFICATION";
        this.status = UserStatus.PROVISIONING;
        this.version = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * 重建用户对象（从数据库加载时使用）。
     *
     * <p>此构造函数用于从数据库加载已有用户，不执行业务校验。
     * 所有参数直接赋值，保持数据库中的原始状态。</p>
     *
     * @param userId         用户 ID
     * @param registrationId 注册幂等键
     * @param loginName      登录名
     * @param nickname       昵称
     * @param phoneTail      手机号尾号（可空）
     * @param identityStatus 身份状态
     * @param status         用户状态
     * @param version        版本号
     * @param createdAt      注册时间
     * @param updatedAt      最近更新时间
     */
    public User(
            String userId,
            String registrationId,
            String accountNumber,
            String phoneNumber,
            String realName,
            String nickname,
            String phoneTail,
            String identityStatus,
            UserStatus status,
            long version,
            Instant createdAt,
            Instant updatedAt,
            String disabledBy,
            String disabledReason,
            String idCard,
            byte[] idCardHash
    ) {
        this.userId = userId;
        this.registrationId = registrationId;
        this.accountNumber = accountNumber;
        this.phoneNumber = phoneNumber;
        this.realName = realName;
        this.nickname = nickname;
        this.phoneTail = phoneTail;
        this.identityStatus = identityStatus;
        this.status = status;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.disabledBy = disabledBy;
        this.disabledReason = disabledReason;
        this.idCard = idCard;
        this.idCardHash = idCardHash;
    }

    /**
     * 检查用户是否可以登录。
     *
     * <p>只有 {@link UserStatus#ACTIVE} 状态的用户可以登录。
     * PROVISIONING 状态表示账户中心尚未完成开户，DISABLED 状态表示用户已被停用。</p>
     *
     * @return 如果用户处于 ACTIVE 状态则返回 true
     */
    public boolean canLogin() {
        return this.status == UserStatus.ACTIVE;
    }

    /**
     * 检查用户是否正在开户中。
     *
     * <p>PROVISIONING 状态表示注册已成功，但账户中心尚未完成开户。
     * 此状态下用户不能登录，需要等待开户完成。</p>
     *
     * @return 如果用户处于 PROVISIONING 状态则返回 true
     */
    public boolean isProvisioning() {
        return this.status == UserStatus.PROVISIONING;
    }

    /**
     * 检查用户是否已被停用。
     *
     * @return 如果用户处于 DISABLED 状态则返回 true
     */
    public boolean isDisabled() {
        return this.status == UserStatus.DISABLED;
    }

    /**
     * 激活用户。
     *
     * <p>账户中心开户完成后调用，将用户状态从 PROVISIONING 变为 ACTIVE。
     * 激活后用户可以登录并使用系统功能。</p>
     *
     * @throws IllegalStateException 如果用户不在 PROVISIONING 状态
     */
    public void activate() {
        if (this.status != UserStatus.PROVISIONING) {
            throw new IllegalStateException("只有 PROVISIONING 状态的用户可以激活，当前状态: " + this.status);
        }
        this.status = UserStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    /**
     * B 端管理冻结用户。
     *
     * <p>仅 {@link UserStatus#ACTIVE} 状态的用户可被冻结；冻结后状态变为 {@link UserStatus#DISABLED}，
     * 禁止登录和发起新的业务。记录操作者与理由供审计展示。</p>
     *
     * @param operatorId 操作者用户 ID（来自网关注入的可信 {@code X-User-Id}）
     * @param reason     冻结理由，用于审计；不能为空白
     * @throws IllegalStateException 如果用户不在 ACTIVE 状态
     */
    public void freeze(String operatorId, String reason) {
        if (this.status != UserStatus.ACTIVE) {
            throw new IllegalStateException("只有 ACTIVE 状态的用户可以冻结，当前状态: " + this.status);
        }
        if (operatorId == null || operatorId.isBlank()) {
            throw new IllegalArgumentException("冻结操作者不能为空");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("冻结理由不能为空");
        }
        this.status = UserStatus.DISABLED;
        this.disabledBy = operatorId;
        this.disabledReason = reason.trim();
        this.updatedAt = Instant.now();
    }

    /**
     * B 端管理解冻用户。
     *
     * <p>仅 {@link UserStatus#DISABLED} 状态的用户可解冻；解冻后状态恢复为 {@link UserStatus#ACTIVE}
     * 并清空冻结操作者与理由。临时登录锁定不受影响，解冻不清除 {@code credential.login_lock_until}。</p>
     *
     * @throws IllegalStateException 如果用户不在 DISABLED 状态
     */
    public void unfreeze() {
        if (this.status != UserStatus.DISABLED) {
            throw new IllegalStateException("只有 DISABLED 状态的用户可以解冻，当前状态: " + this.status);
        }
        this.status = UserStatus.ACTIVE;
        this.disabledBy = null;
        this.disabledReason = null;
        this.updatedAt = Instant.now();
    }

    /**
     * 绑定身份信息：设置真实姓名和身份证号，计算哈希，更新身份状态为 VERIFIED。
     *
     * <p>绑定后用户可以通过三要素交叉校验来绑定银行卡。</p>
     *
     * @param realName 真实姓名
     * @param idCard 身份证号明文（用于计算哈希）
     * @param idCardHash 身份证号明文 SHA-256 哈希
     * @param idCardMasked 身份证号掩码
     */
    public void bindIdentity(String realName, byte[] idCardHashBytes, String idCardMasked) {
        if (realName == null || realName.isBlank()) {
            throw new IllegalArgumentException("真实姓名不能为空");
        }
        this.realName = realName.trim();
        this.idCard = idCardMasked;
        this.idCardHash = idCardHashBytes;
        this.identityStatus = "VERIFIED";
        this.updatedAt = Instant.now();
    }

    // ==================== Getters ====================

    /**
     * 获取用户 ID。
     *
     * @return 用户 ID（26 位字符，USR 前缀）
     */
    public String getUserId() {
        return userId;
    }

    /**
     * 获取注册幂等键。
     *
     * @return 注册幂等键（26 位字符，REG 前缀）
     */
    public String getRegistrationId() {
        return registrationId;
    }

    /**
     * 获取登录名。
     *
     * @return 登录名（已规范化，最大 64 字符）
     */
    public String getAccountNumber() {
        return accountNumber;
    }

    /** @return 完整手机号，仅供用户中心认证和受控查询使用 */
    public String getPhoneNumber() {
        return phoneNumber;
    }

    /** @return 真实姓名，绑定身份后非 null */
    public String getRealName() {
        return realName;
    }

    /** @return 身份证号掩码，绑定身份后非 null */
    public String getIdCard() {
        return idCard;
    }

    /** @return 身份证号哈希，绑定身份后非 null */
    public byte[] getIdCardHash() {
        return idCardHash;
    }

    /**
     * 获取昵称。
     *
     * @return 昵称（最大 64 字符）
     */
    public String getNickname() {
        return nickname;
    }

    /**
     * 获取手机号尾号。
     *
     * @return 手机号尾号（4 位字符），可能为 null
     */
    public String getPhoneTail() {
        return phoneTail;
    }

    /**
     * 获取身份状态。
     *
     * @return 身份状态字符串（PENDING_VERIFICATION / VERIFIED / REJECTED）
     */
    public String getIdentityStatus() {
        return identityStatus;
    }

    /**
     * 获取用户状态。
     *
     * @return 用户状态枚举（PROVISIONING / ACTIVE / DISABLED）
     */
    public UserStatus getStatus() {
        return status;
    }

    /**
     * 获取版本号。
     *
     * @return 版本号（用于乐观锁）
     */
    public long getVersion() {
        return version;
    }

    /**
     * 获取注册时间。
     *
     * @return 注册时间（UTC，毫秒精度）
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * 获取最近更新时间。
     *
     * @return 最近更新时间（UTC，毫秒精度）
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 获取管理冻结操作者用户 ID。
     *
     * @return 操作者用户 ID；仅 DISABLED 状态有值，否则为 null
     */
    public String getDisabledBy() {
        return disabledBy;
    }

    /**
     * 获取管理冻结理由。
     *
     * @return 冻结理由；仅 DISABLED 状态有值，否则为 null
     */
    public String getDisabledReason() {
        return disabledReason;
    }

    /** 将手机号转为脱敏昵称格式，如 13812345678 → 手机用户1234。 */
    private static String maskPhoneNumber(String phone) {
        if (phone == null || phone.length() < 4) {
            return "手机用户";
        }
        return "手机用户" + phone.substring(phone.length() - 4);
    }
}
