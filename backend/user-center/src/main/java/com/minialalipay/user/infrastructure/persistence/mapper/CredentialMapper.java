package com.minialalipay.user.infrastructure.persistence.mapper;

import com.minialalipay.user.infrastructure.persistence.po.CredentialPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 凭证 MyBatis Mapper 接口。
 *
 * <p>负责 {@code credential} 表的数据库操作，包括插入、查询和更新。
 * 所有 SQL 语句定义在对应的 XML 映射文件中（{@code CredentialMapper.xml}）。</p>
 *
 * <p>命名规范：
 * <ul>
 *   <li>{@code insert} - 插入新记录</li>
 *   <li>{@code selectByXxx} - 按条件查询单条记录</li>
 *   <li>{@code update} - 更新记录（使用乐观锁 version）</li>
 * </ul>
 * </p>
 *
 * <p>注意事项：
 * <ul>
 *   <li>所有时间字段使用 {@code java.time.Instant}，MyBatis 自动处理 UTC 转换</li>
 *   <li>更新操作必须包含 {@code WHERE version = #{expectedVersion}}，防止并发覆盖</li>
 *   <li>凭证表与用户表在同一事务内保存，保证注册的原子性</li>
 * </ul>
 * </p>
 *
 * @see CredentialPO 凭证持久化对象
 * @see com.minialalipay.user.domain.credential.Credential 凭证领域模型
 */
@Mapper
public interface CredentialMapper {

    /**
     * 插入新凭证记录。
     *
     * <p>注册时调用，插入新凭证记录到 {@code credential} 表。
     * 必须与用户记录在同一事务内保存，保证注册的原子性。</p>
     *
     * @param credential 凭证持久化对象
     * @return 插入的行数（成功时返回 1）
     */
    int insert(CredentialPO credential);

    /**
     * 根据用户 ID 查询凭证。
     *
     * @param userId 用户 ID（26 位字符，USR 前缀）
     * @return 凭证持久化对象，如果不存在则返回 null
     */
    CredentialPO selectByUserId(@Param("userId") String userId);

    /**
     * 更新凭证信息。
     *
     * <p>更新密码、失败次数或锁定状态时调用。
     * 使用乐观锁（version）保证并发安全，更新成功后 version 自动递增 1。</p>
     *
     * @param credential 凭证持久化对象（包含更新后的数据）
     * @return 更新的行数（成功时返回 1，版本冲突时返回 0）
     */
    int update(CredentialPO credential);
}
