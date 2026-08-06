package com.minialalipay.user.infrastructure.persistence.mapper;

import com.minialalipay.user.infrastructure.persistence.po.UserPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户 MyBatis Mapper 接口。
 *
 * <p>负责 {@code app_user} 表的数据库操作，包括插入、查询和更新。
 * 所有 SQL 语句定义在对应的 XML 映射文件中（{@code UserMapper.xml}）。</p>
 *
 * <p>命名规范：
 * <ul>
 *   <li>{@code insert} - 插入新记录</li>
 *   <li>{@code selectByXxx} - 按条件查询单条记录</li>
 *   <li>{@code update} - 更新记录（使用乐观锁 version）</li>
 *   <li>{@code existsByXxx} - 检查记录是否存在</li>
 * </ul>
 * </p>
 *
 * <p>注意事项：
 * <ul>
 *   <li>所有时间字段使用 {@code java.time.Instant}，MyBatis 自动处理 UTC 转换</li>
 *   <li>更新操作必须包含 {@code WHERE version = #{expectedVersion}}，防止并发覆盖</li>
 *   <li>唯一约束冲突（如 {@code login_name} 重复）由数据库抛出异常，由上层捕获并转换为业务异常</li>
 * </ul>
 * </p>
 *
 * @see UserPO 用户持久化对象
 * @see com.minialalipay.user.domain.user.User 用户领域模型
 */
@Mapper
public interface UserMapper {

    /**
     * 插入新用户记录。
     *
     * <p>注册时调用，插入新用户记录到 {@code app_user} 表。
     * 如果 {@code login_name} 已存在，数据库抛出唯一约束异常。</p>
     *
     * @param user 用户持久化对象
     * @return 插入的行数（成功时返回 1）
     */
    int insert(UserPO user);

    /**
     * 根据用户 ID 查询用户。
     *
     * @param userId 用户 ID（ULID 格式，26 位字符）
     * @return 用户持久化对象，如果不存在则返回 null
     */
    UserPO selectByUserId(@Param("userId") String userId);

    /**
     * 根据登录名查询用户。
     *
     * <p>登录名已规范化存储（转小写、去空格），查询时也需要规范化。</p>
     *
     * @param loginName 登录名（已规范化）
     * @return 用户持久化对象，如果不存在则返回 null
     */
    UserPO selectByAccountNumber(@Param("accountNumber") String accountNumber);

    /** 根据完整手机号查询用户。 */
    UserPO selectByPhoneNumber(@Param("phoneNumber") String phoneNumber);

    /**
     * 根据注册幂等键查询用户。
     *
     * <p>用于开户恢复场景：账户中心以 {@code registration_id}
     * 作为开户幂等键，查询用户是否存在。</p>
     *
     * @param registrationId 注册幂等键（ULID 格式，26 位字符）
     * @return 用户持久化对象，如果不存在则返回 null
     */
    UserPO selectByRegistrationId(@Param("registrationId") String registrationId);

    /**
     * 更新用户信息。
     *
     * <p>更新用户资料或状态时调用，使用乐观锁（version）保证并发安全。
     * 更新成功后 version 自动递增 1。</p>
     *
     * @param user 用户持久化对象（包含更新后的数据）
     * @return 更新的行数（成功时返回 1，版本冲突时返回 0）
     */
    int update(UserPO user);

    /**
     * 检查登录名是否已存在。
     *
     * <p>注册时用于校验用户名唯一性，避免插入后才报唯一约束冲突。</p>
     *
     * @param loginName 登录名（已规范化）
     * @return 如果登录名已存在则返回 true
     */
    boolean existsByAccountNumber(@Param("accountNumber") String accountNumber);

    /** 检查手机号唯一性。 */
    boolean existsByPhoneNumber(@Param("phoneNumber") String phoneNumber);

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
    List<UserPO> searchByKeyword(
            @Param("keyword") String keyword,
            @Param("excludeId") String excludeId,
            @Param("limit") int limit
    );
}
