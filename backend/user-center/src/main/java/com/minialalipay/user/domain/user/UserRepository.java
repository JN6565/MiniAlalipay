package com.minialalipay.user.domain.user;

import java.util.List;
import java.util.Optional;

/**
 * 用户仓储接口。
 *
 * <p>定义用户聚合根的持久化操作，由基础设施层实现。
 * 仓储接口属于领域层，不依赖具体的持久化技术（如 MyBatis、JPA）。</p>
 *
 * <p>职责边界：
 * <ul>
 *   <li>只负责用户主体（{@code app_user} 表）的持久化</li>
 *   <li>不负责凭证（{@code credential} 表）和角色（{@code role_assignment} 表）的持久化</li>
 *   <li>凭证和角色由各自的仓储接口管理</li>
 * </ul>
 * </p>
 *
 * <p>实现要求：
 * <ul>
 *   <li>所有查询方法返回 {@link Optional}，避免返回 null</li>
 *   <li>保存操作必须保证原子性（在同一事务内）</li>
 *   <li>唯一约束冲突时抛出相应的业务异常，不返回 null 或 false</li>
 * </ul>
 * </p>
 *
 * @see User 用户聚合根
 * @see com.minialalipay.user.infrastructure.persistence.UserRepositoryImpl 仓储实现
 */
public interface UserRepository {

    /**
     * 根据用户 ID 查询用户。
     *
     * @param userId 用户 ID（26 位字符，USR 前缀）
     * @return 用户对象，如果不存在则返回 empty
     */
    Optional<User> findById(String userId);

    /**
     * 根据登录名查询用户。
     *
     * <p>登录名已规范化存储（转小写、去空格），查询时也需要规范化。</p>
     *
     * @param loginName 登录名（已规范化）
     * @return 用户对象，如果不存在则返回 empty
     */
    Optional<User> findByAccountNumber(String accountNumber);

    /** 根据手机号或系统账户号查询登录用户。 */
    Optional<User> findByLoginIdentifier(String loginIdentifier);

    /**
     * 根据注册幂等键查询用户。
     *
     * <p>用于开户恢复场景：账户中心以 {@code registration_id}
     * 作为开户幂等键，查询用户是否存在。</p>
     *
     * @param registrationId 注册幂等键（26 位字符，REG 前缀）
     * @return 用户对象，如果不存在则返回 empty
     */
    Optional<User> findByRegistrationId(String registrationId);

    /**
     * 保存新用户。
     *
     * <p>注册时调用，插入新用户记录。
     * 如果 {@code account_number} 已存在，抛出 {@link com.minialalipay.user.domain.auth.UserErrorCode#ACCOUNT_NUMBER_EXISTS}。</p>
     *
     * @param user 用户聚合根
     * @throws com.minialalipay.common.error.BusinessException 如果登录名已存在
     */
    void save(User user);

    /**
     * 更新用户信息。
     *
     * <p>更新用户资料或状态时调用，必须使用乐观锁（version）保证并发安全。
     * 如果版本号不匹配，抛出 {@link com.minialalipay.common.error.BusinessException}。</p>
     *
     * @param user 用户聚合根（包含更新后的数据）
     * @throws com.minialalipay.common.error.BusinessException 如果版本冲突
     */
    void update(User user);

    /**
     * 检查登录名是否已存在。
     *
     * <p>注册时用于校验用户名唯一性，避免插入后才报唯一约束冲突。</p>
     *
     * @param loginName 登录名（已规范化）
     * @return 如果登录名已存在则返回 true
     */
    boolean existsByAccountNumber(String accountNumber);

    /** 检查完整手机号是否已经注册。 */
    boolean existsByPhoneNumber(String phoneNumber);

    /**
     * 检查身份证号哈希是否已被其他用户绑定。
     *
     * <p>身份证号全系统唯一：同一身份证号不得被多个账户绑定。
     * 排除本人，允许同一用户重复提交相同身份证号（幂等重绑）。</p>
     *
     * @param idCardHash    身份证号 SHA-256 哈希
     * @param excludeUserId 排除的用户 ID（当前用户本人）
     * @return 如果其他用户已绑定该身份证号则返回 true
     */
    boolean existsByIdCardHashExcluding(byte[] idCardHash, String excludeUserId);

    /**
     * 按手机号搜索用户。
     *
     * <p>搜索规则：
     * <ul>
     *   <li>按手机号精确匹配搜索</li>
     *   <li>只返回 ACTIVE 状态的用户</li>
     *   <li>排除指定的用户 ID（通常是当前用户）</li>
     *   <li>最多返回指定数量的结果</li>
     * </ul>
     * </p>
     *
     * @param keyword   搜索手机号
     * @param excludeId 排除的用户 ID（可为 null）
     * @param limit     最大返回数量
     * @return 用户列表
     */
    List<User> searchByKeyword(String keyword, String excludeId, int limit);

    /**
     * B 端管理分页查询用户（只读投影）。
     *
     * <p>按稳定 {@code user_id} 游标分页，可选按用户状态过滤。只用于 B 端运营管理，
     * 不返回密码、支付密码或手机号等敏感原值。</p>
     *
     * @param status 用户状态过滤，null 表示不限定
     * @param cursor 上一页最后一条 {@code user_id}，null 表示第一页
     * @param limit  每页最大返回条数
     * @return 用户只读投影列表
     */
    List<UserAdminView> findAdminPage(UserStatus status, String cursor, int limit);
}
