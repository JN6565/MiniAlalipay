package com.minialalipay.user.application.user;

import com.minialalipay.user.application.user.dto.UserSearchResult;
import com.minialalipay.user.domain.user.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户查询应用服务。
 *
 * <p>负责用户相关的只读查询操作，不包含业务逻辑。</p>
 *
 * <p>职责边界：
 * <ul>
 *   <li>只负责用户查询操作（搜索、详情等）</li>
 *   <li>不负责用户写入操作（由 {@link com.minialalipay.user.application.auth.AuthService} 负责）</li>
 *   <li>不负责支付密码管理（由 {@link com.minialalipay.user.application.payment.PaymentPasswordService} 负责）</li>
 * </ul>
 * </p>
 *
 * @see UserRepository 用户仓储
 */
@Service
public class UserQueryService {

    private final UserRepository userRepository;

    /**
     * 构造函数注入依赖。
     *
     * @param userRepository 用户仓储
     */
    public UserQueryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 搜索用户（支持手机号精确匹配、昵称/真实姓名前缀匹配）。
     *
     * <p>搜索规则：
     * <ul>
     *   <li>手机号精确相等匹配</li>
     *   <li>昵称前缀模糊匹配</li>
     *   <li>真实姓名前缀模糊匹配</li>
     *   <li>只返回 ACTIVE 状态的用户</li>
     *   <li>排除当前用户自己</li>
     *   <li>最多返回 20 条结果</li>
     * </ul>
     * </p>
     *
     * @param keyword       搜索关键词（手机号、昵称或姓名）
     * @param currentUserId 当前用户 ID（排除自己）
     * @return 用户搜索结果列表
     */
    public List<UserSearchResult> searchUsers(String keyword, String currentUserId) {
        // 1. 校验关键词
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }

        // 2. 规范化关键词
        String normalizedKeyword = keyword.trim();

        // 3. 调用仓储搜索用户
        return userRepository.searchByKeyword(normalizedKeyword, currentUserId, 20).stream()
                .map(user -> new UserSearchResult(
                        user.getUserId(),
                        user.getAccountNumber(),
                        user.getNickname(),
                        user.getIdentityStatus(),
                        user.getPhoneTail()
                ))
                .collect(Collectors.toList());
    }
}
