package com.minialalipay.user.infrastructure.persistence.po;

import java.time.Instant;

/**
 * 用户持久化对象（PO）。
 *
 * <p>与 {@code app_user} 表结构一一对应，用于 MyBatis 数据库操作。
 * PO 对象只在基础设施层使用，不得暴露到领域层或接口层。</p>
 *
 * <p>字段映射规则：
 * <ul>
 *   <li>数据库字段使用 {@code snake_case} 命名，Java 字段使用 {@code camelCase} 命名</li>
 *   <li>MyBatis 配置 {@code map-underscore-to-camel-case=true} 自动转换</li>
 *   <li>时间字段使用 {@code java.time.Instant}，对应数据库 {@code DATETIME(3)}</li>
 *   <li>金额字段使用 {@code long}（整数分），对应数据库 {@code BIGINT UNSIGNED}</li>
 * </ul>
 * </p>
 *
 * <p>与领域模型的转换：
 * <ul>
 *   <li>{@code UserPO} → {@code User}：从数据库加载时，由 Repository 实现转换</li>
 *   <li>{@code User} → {@code UserPO}：保存到数据库时，由 Repository 实现转换</li>
 *   <li>转换逻辑集中在 Repository 实现中，避免分散到各处</li>
 * </ul>
 * </p>
 *
 * @see com.minialalipay.user.domain.user.User 用户领域模型
 * @see UserMapper 用户 Mapper 接口
 */
public class UserPO {

    /**
     * 用户 ID（ULID 格式，26 位字符）。
     * <p>主键，对应数据库字段 {@code user_id}。</p>
     */
    private String userId;

    /**
     * 注册幂等键（ULID 格式，26 位字符）。
     * <p>唯一键，对应数据库字段 {@code registration_id}。
     * 用于开户恢复和既有资源查询。</p>
     */
    private String registrationId;

    /**
     * 登录名（规范化存储，最大 64 字符）。
     * <p>唯一键，对应数据库字段 {@code login_name}。
     * 注册时规范化处理（转小写、去空格）。</p>
     */
    private String accountNumber;

    /** 完整手机号，对应唯一列 {@code phone_number}。 */
    private String phoneNumber;

    /** 真实姓名，对应列 {@code real_name}。 */
    private String realName;

    /**
     * 昵称（最大 64 字符）。
     * <p>可重复的展示名称和模糊搜索条件，对应数据库字段 {@code nickname}。</p>
     */
    private String nickname;

    /**
     * 手机号尾号（4 位字符，可空）。
     * <p>仅用于辅助检索和脱敏展示，对应数据库字段 {@code phone_tail}。</p>
     */
    private String phoneTail;

    /**
     * 演示身份状态（最大 16 字符）。
     * <p>不代表真实 KYC，对应数据库字段 {@code identity_status}。
     * 取值：PENDING_VERIFICATION / VERIFIED / REJECTED</p>
     */
    private String identityStatus;

    /**
     * 用户状态（最大 16 字符）。
     * <p>控制用户是否可以登录，对应数据库字段 {@code status}。
     * 取值：PROVISIONING / ACTIVE / DISABLED</p>
     */
    private String status;

    /**
     * 版本号（乐观锁）。
     * <p>用于并发控制，每次状态或资料变更时递增，对应数据库字段 {@code version}。</p>
     */
    private long version;

    /**
     * 用户注册时间（UTC，毫秒精度）。
     * <p>注册时设置，之后不可修改，对应数据库字段 {@code created_at}。</p>
     */
    private Instant createdAt;

    /**
     * 最近资料或状态变更时间（UTC，毫秒精度）。
     * <p>每次修改用户资料或状态时更新，对应数据库字段 {@code updated_at}。</p>
     */
    private Instant updatedAt;

    /**
     * 默认构造函数（MyBatis 反射需要）。
     */
    public UserPO() {
    }

    /**
     * 全参数构造函数（用于测试或手动构建）。
     */
    public UserPO(
            String userId,
            String registrationId,
            String accountNumber,
            String phoneNumber,
            String realName,
            String nickname,
            String phoneTail,
            String identityStatus,
            String status,
            long version,
            Instant createdAt,
            Instant updatedAt
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
    }

    // ==================== Getters and Setters ====================

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRegistrationId() {
        return registrationId;
    }

    public void setRegistrationId(String registrationId) {
        this.registrationId = registrationId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getPhoneNumber() { return phoneNumber; }

    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getRealName() { return realName; }

    public void setRealName(String realName) { this.realName = realName; }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getPhoneTail() {
        return phoneTail;
    }

    public void setPhoneTail(String phoneTail) {
        this.phoneTail = phoneTail;
    }

    public String getIdentityStatus() {
        return identityStatus;
    }

    public void setIdentityStatus(String identityStatus) {
        this.identityStatus = identityStatus;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
