package com.minialalipay.user.infrastructure.persistence;

import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import com.minialalipay.user.domain.credential.Credential;
import com.minialalipay.user.domain.credential.CredentialRepository;
import com.minialalipay.user.infrastructure.persistence.mapper.CredentialMapper;
import com.minialalipay.user.infrastructure.persistence.po.CredentialPO;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 凭证仓储实现类。
 *
 * <p>实现 {@link CredentialRepository} 接口，负责用户凭证的持久化操作。
 * 使用 MyBatis {@link CredentialMapper} 进行数据库操作。</p>
 *
 * <p>职责边界：
 * <ul>
 *   <li>只负责凭证（{@code credential} 表）的持久化</li>
 *   <li>不负责用户主体（{@code app_user} 表）的持久化，用户由 {@link UserRepositoryImpl} 管理</li>
 *   <li>领域模型与持久化对象的转换逻辑集中在本类中</li>
 * </ul>
 * </p>
 *
 * <p>异常处理：
 * <ul>
 *   <li>版本冲突时抛出 {@link BusinessException}，包含版本冲突错误码</li>
 * </ul>
 * </p>
 *
 * @see CredentialRepository 凭证仓储接口
 * @see CredentialMapper 凭证 Mapper 接口
 * @see Credential 凭证实体
 */
@Repository
public class CredentialRepositoryImpl implements CredentialRepository {

    private final CredentialMapper credentialMapper;

    /**
     * 构造函数注入 CredentialMapper。
     *
     * @param credentialMapper MyBatis 凭证 Mapper
     */
    public CredentialRepositoryImpl(CredentialMapper credentialMapper) {
        this.credentialMapper = credentialMapper;
    }

    /**
     * 根据用户 ID 查询凭证。
     *
     * @param userId 用户 ID（26 位字符，USR 前缀）
     * @return 凭证对象，如果不存在则返回 empty
     */
    @Override
    public Optional<Credential> findByUserId(String userId) {
        CredentialPO credentialPO = credentialMapper.selectByUserId(userId);
        return Optional.ofNullable(credentialPO).map(this::toDomain);
    }

    /**
     * 保存新凭证。
     *
     * <p>注册时调用，插入新凭证记录。
     * 必须与用户记录在同一事务内保存，保证注册的原子性。</p>
     *
     * @param credential 凭证实体
     */
    @Override
    public void save(Credential credential) {
        CredentialPO credentialPO = toPO(credential);
        credentialMapper.insert(credentialPO);
    }

    /**
     * 更新凭证信息。
     *
     * <p>更新密码、失败次数或锁定状态时调用。
     * 必须使用乐观锁（version）保证并发安全。</p>
     *
     * @param credential 凭证实体（包含更新后的数据）
     * @throws BusinessException 如果版本冲突
     */
    @Override
    public void update(Credential credential) {
        CredentialPO credentialPO = toPO(credential);
        int rows = credentialMapper.update(credentialPO);
        if (rows == 0) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    /**
     * 将领域模型转换为持久化对象。
     *
     * <p>保存到数据库时调用，将 {@link Credential} 转换为 {@link CredentialPO}。</p>
     *
     * @param credential 凭证领域模型
     * @return 凭证持久化对象
     */
    private CredentialPO toPO(Credential credential) {
        return new CredentialPO(
                credential.getUserId(),
                credential.getLoginPasswordHash(),
                credential.getPaymentPasswordHash(),
                credential.getLoginFailCount(),
                credential.getPayFailCount(),
                credential.getLoginLockUntil(),
                credential.getPayLockUntil(),
                credential.getPayPasswordVersion(),
                credential.getVersion(),
                credential.getUpdatedAt()
        );
    }

    /**
     * 将持久化对象转换为领域模型。
     *
     * <p>从数据库加载时调用，将 {@link CredentialPO} 转换为 {@link Credential}。</p>
     *
     * @param credentialPO 凭证持久化对象
     * @return 凭证领域模型
     */
    private Credential toDomain(CredentialPO credentialPO) {
        return new Credential(
                credentialPO.getUserId(),
                credentialPO.getLoginPasswordHash(),
                credentialPO.getPaymentPasswordHash(),
                credentialPO.getLoginFailCount(),
                credentialPO.getPayFailCount(),
                credentialPO.getLoginLockUntil(),
                credentialPO.getPayLockUntil(),
                credentialPO.getPayPasswordVersion(),
                credentialPO.getVersion(),
                credentialPO.getUpdatedAt()
        );
    }
}
