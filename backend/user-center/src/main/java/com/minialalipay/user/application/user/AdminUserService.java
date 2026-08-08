package com.minialalipay.user.application.user;

import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import com.minialalipay.user.domain.auth.UserErrorCode;
import com.minialalipay.user.domain.user.User;
import com.minialalipay.user.domain.user.UserAdminView;
import com.minialalipay.user.domain.user.UserRepository;
import com.minialalipay.user.domain.user.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * B 端用户管理应用服务。
 *
 * <p>仅供系统管理员使用的用户列表与冻结/解冻操作。所有写操作必须携带操作者 ID，
 * 通过网关注入的可信 {@code X-User-Id} 传递；冻结理由一并持久化用于审计展示。</p>
 *
 * <p>状态流转与 CAS：冻结仅允许 {@code ACTIVE -> DISABLED}，解冻仅允许
 * {@code DISABLED -> ACTIVE}，状态变更由 {@code version} 乐观锁保护，
 * 客户端提交的版本与当前版本不一致时抛出 {@link UserErrorCode#VERSION_CONFLICT}。</p>
 *
 * @see UserRepository 用户仓储
 */
@Service
public class AdminUserService {

    private final UserRepository userRepository;

    /** 构造函数注入依赖。 */
    public AdminUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * B 端分页查询用户只读投影。
     *
     * @param status    用户状态过滤，null 表示不限定
     * @param loginName 登录名关键词过滤（按账户号模糊匹配），null 表示不限定
     * @param cursor    上一页最后一条 {@code user_id}，null 表示第一页
     * @param limit     每页最大返回条数
     * @return 用户只读投影列表
     */
    public List<UserAdminView> list(UserStatus status, String loginName, String cursor, int limit) {
        return userRepository.findAdminPage(status, loginName, cursor, limit);
    }

    /**
     * 管理冻结用户（仅 ACTIVE 可冻结，冻结后禁止登录）。
     *
     * @param userId     目标用户 ID
     * @param version    客户端读取到的版本号（CAS）
     * @param operatorId 操作者用户 ID（来自网关注入的可信 {@code X-User-Id}）
     * @param reason     冻结理由，用于审计
     * @return 冻结结果（含变更后的用户与最新版本号）
     */
    @Transactional
    public UserUpdateResult freeze(String userId, long version, String operatorId, String reason) {
        User user = requireUser(userId);
        requireVersion(user, version);
        try {
            user.freeze(operatorId, reason);
        } catch (IllegalStateException e) {
            throw new BusinessException(UserErrorCode.USER_STATE_INVALID);
        }
        userRepository.update(user);
        return new UserUpdateResult(user, user.getVersion() + 1);
    }

    /**
     * 管理解冻用户（仅 DISABLED 可解冻，解冻后恢复登录）。
     *
     * @param userId     目标用户 ID
     * @param version    客户端读取到的版本号（CAS）
     * @param operatorId 操作者用户 ID（用于审计留痕）
     * @return 解冻结果（含变更后的用户与最新版本号）
     */
    @Transactional
    public UserUpdateResult unfreeze(String userId, long version, String operatorId) {
        User user = requireUser(userId);
        requireVersion(user, version);
        try {
            user.unfreeze();
        } catch (IllegalStateException e) {
            throw new BusinessException(UserErrorCode.USER_STATE_INVALID);
        }
        userRepository.update(user);
        return new UserUpdateResult(user, user.getVersion() + 1);
    }

    /**
     * 状态变更结果：变更后的内存用户对象（状态、冻结审计字段已更新）与最新版本号。
     *
     * <p>版本号 = 变更前版本 + 1：仓储 {@code update} 使用 {@code version = version + 1}
     * 原子递增并 CAS 校验，成功后必然比内存对象多 1；同一事务内二次查询受 MyBatis
     * 一级缓存影响可能返回旧行，因此由服务层直接给出确定的新版本。</p>
     */
    public record UserUpdateResult(User user, long version) {
    }

    private User requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private void requireVersion(User user, long version) {
        if (user.getVersion() != version) {
            throw new BusinessException(UserErrorCode.VERSION_CONFLICT);
        }
    }
}
