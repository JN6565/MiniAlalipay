package com.minialalipay.user.infrastructure.persistence;

import com.minialalipay.common.error.BusinessException;
import com.minialalipay.user.domain.auth.UserErrorCode;
import com.minialalipay.user.domain.user.User;
import com.minialalipay.user.domain.user.UserAdminView;
import com.minialalipay.user.domain.user.UserRepository;
import com.minialalipay.user.domain.user.UserStatus;
import com.minialalipay.user.infrastructure.persistence.mapper.UserMapper;
import com.minialalipay.user.infrastructure.persistence.po.UserPO;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 用户仓储实现类。
 *
 * <p>实现 {@link UserRepository} 接口，负责用户聚合根的持久化操作。
 * 使用 MyBatis {@link UserMapper} 进行数据库操作。</p>
 *
 * <p>职责边界：
 * <ul>
 *   <li>只负责用户主体（{@code app_user} 表）的持久化</li>
 *   <li>不负责凭证（{@code credential} 表）和角色（{@code role_assignment} 表）的持久化</li>
 *   <li>领域模型与持久化对象的转换逻辑集中在本类中</li>
 * </ul>
 * </p>
 *
 * <p>异常处理：
 * <ul>
 *   <li>唯一约束冲突（如 {@code account_number} 重复）捕获后转换为 {@link UserErrorCode#ACCOUNT_NUMBER_EXISTS}</li>
 *   <li>版本冲突时抛出 {@link BusinessException}，包含版本冲突错误码</li>
 * </ul>
 * </p>
 *
 * @see UserRepository 用户仓储接口
 * @see UserMapper 用户 Mapper 接口
 * @see User 用户聚合根
 */
@Repository
public class UserRepositoryImpl implements UserRepository {

    private final UserMapper userMapper;

    /**
     * 构造函数注入 UserMapper。
     *
     * @param userMapper MyBatis 用户 Mapper
     */
    public UserRepositoryImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 根据用户 ID 查询用户。
     *
     * @param userId 用户 ID（ULID 格式，26 位字符）
     * @return 用户对象，如果不存在则返回 empty
     */
    @Override
    public Optional<User> findById(String userId) {
        UserPO userPO = userMapper.selectByUserId(userId);
        return Optional.ofNullable(userPO).map(this::toDomain);
    }

    /**
     * 根据登录名查询用户。
     *
     * <p>登录名已规范化存储（转小写、去空格），查询时也需要规范化。</p>
     *
     * @param loginName 登录名（已规范化）
     * @return 用户对象，如果不存在则返回 empty
     */
    @Override
    public Optional<User> findByAccountNumber(String accountNumber) {
        UserPO userPO = userMapper.selectByAccountNumber(accountNumber);
        return Optional.ofNullable(userPO).map(this::toDomain);
    }

    @Override
    public Optional<User> findByLoginIdentifier(String loginIdentifier) {
        UserPO userPO = loginIdentifier.matches("^1[3-9]\\d{9}$")
                ? userMapper.selectByPhoneNumber(loginIdentifier)
                : userMapper.selectByAccountNumber(loginIdentifier);
        return Optional.ofNullable(userPO).map(this::toDomain);
    }

    /**
     * 根据注册幂等键查询用户。
     *
     * <p>用于开户恢复场景：账户中心以 {@code registration_id}
     * 作为开户幂等键，查询用户是否存在。</p>
     *
     * @param registrationId 注册幂等键（ULID 格式，26 位字符）
     * @return 用户对象，如果不存在则返回 empty
     */
    @Override
    public Optional<User> findByRegistrationId(String registrationId) {
        UserPO userPO = userMapper.selectByRegistrationId(registrationId);
        return Optional.ofNullable(userPO).map(this::toDomain);
    }

    /**
     * 保存新用户。
     *
     * <p>注册时调用，插入新用户记录。
     * 如果 {@code account_number} 已存在，抛出 {@link UserErrorCode#ACCOUNT_NUMBER_EXISTS}。</p>
     *
     * @param user 用户聚合根
     * @throws BusinessException 如果登录名已存在
     */
    @Override
    public void save(User user) {
        UserPO userPO = toPO(user);
        try {
            userMapper.insert(userPO);
        } catch (Exception e) {
            // 唯一约束冲突（login_name 重复）
            if (e.getMessage() != null && e.getMessage().contains("uk_app_user_phone_number")) {
                throw new BusinessException(UserErrorCode.PHONE_NUMBER_EXISTS);
            }
            if (e.getMessage() != null && e.getMessage().contains("uk_app_user_account_number")) {
                throw new BusinessException(UserErrorCode.ACCOUNT_NUMBER_EXISTS);
            }
            throw e;
        }
    }

    /**
     * 更新用户信息。
     *
     * <p>更新用户资料或状态时调用，必须使用乐观锁（version）保证并发安全。
     * 如果版本号不匹配，抛出 {@link BusinessException}。</p>
     *
     * @param user 用户聚合根（包含更新后的数据）
     * @throws BusinessException 如果版本冲突
     */
    @Override
    public void update(User user) {
        UserPO userPO = toPO(user);
        int rows = userMapper.update(userPO);
        if (rows == 0) {
            throw new BusinessException(UserErrorCode.VERSION_CONFLICT);
        }
    }

    /**
     * 检查登录名是否已存在。
     *
     * <p>注册时用于校验用户名唯一性，避免插入后才报唯一约束冲突。</p>
     *
     * @param loginName 登录名（已规范化）
     * @return 如果登录名已存在则返回 true
     */
    @Override
    public boolean existsByAccountNumber(String accountNumber) {
        return userMapper.existsByAccountNumber(accountNumber);
    }

    @Override
    public boolean existsByPhoneNumber(String phoneNumber) {
        return userMapper.existsByPhoneNumber(phoneNumber);
    }

    /**
     * 按关键词搜索用户。
     *
     * <p>搜索规则：
     * <ul>
     *   <li>按登录名或昵称模糊搜索</li>
     *   <li>只返回 ACTIVE 状态的用户</li>
     *   <li>排除指定的用户 ID（通常是当前用户）</li>
     *   <li>最多返回指定数量的结果</li>
     * </ul>
     * </p>
     *
     * @param keyword   搜索关键词（登录名或昵称）
     * @param excludeId 排除的用户 ID（可为 null）
     * @param limit     最大返回数量
     * @return 用户列表
     */
    @Override
    public List<User> searchByKeyword(String keyword, String excludeId, int limit) {
        String searchPattern = "%" + keyword + "%";
        return userMapper.searchByKeyword(keyword, searchPattern, excludeId, limit).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * 将领域模型转换为持久化对象。
     *
     * <p>保存到数据库时调用，将 {@link User} 转换为 {@link UserPO}。</p>
     *
     * @param user 用户领域模型
     * @return 用户持久化对象
     */
    private UserPO toPO(User user) {
        return new UserPO(
                user.getUserId(),
                user.getRegistrationId(),
                user.getAccountNumber(),
                user.getPhoneNumber(),
                user.getRealName(),
                user.getNickname(),
                user.getPhoneTail(),
                user.getIdentityStatus(),
                user.getStatus().name(),
                user.getVersion(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getDisabledBy(),
                user.getDisabledReason()
        );
    }

    /**
     * 将持久化对象转换为领域模型。
     *
     * <p>从数据库加载时调用，将 {@link UserPO} 转换为 {@link User}。</p>
     *
     * @param userPO 用户持久化对象
     * @return 用户领域模型
     */
    private User toDomain(UserPO userPO) {
        return new User(
                userPO.getUserId(),
                userPO.getRegistrationId(),
                userPO.getAccountNumber(),
                userPO.getPhoneNumber(),
                userPO.getRealName(),
                userPO.getNickname(),
                userPO.getPhoneTail(),
                userPO.getIdentityStatus(),
                UserStatus.valueOf(userPO.getStatus()),
                userPO.getVersion(),
                userPO.getCreatedAt(),
                userPO.getUpdatedAt(),
                userPO.getDisabledBy(),
                userPO.getDisabledReason()
        );
    }

    /**
     * B 端管理分页查询用户（只读投影）。
     *
     * @param status 用户状态过滤，null 表示不限定
     * @param cursor 上一页最后一条 {@code user_id}，null 表示第一页
     * @param limit  每页最大返回条数
     * @return 用户只读投影列表
     */
    @Override
    public List<UserAdminView> findAdminPage(UserStatus status, String cursor, int limit) {
        String statusName = status == null ? null : status.name();
        return userMapper.selectAdminPage(statusName, cursor, limit).stream()
                .map(po -> new UserAdminView(toDomain(po), po.getLoginLockedUntil()))
                .collect(Collectors.toList());
    }
}
