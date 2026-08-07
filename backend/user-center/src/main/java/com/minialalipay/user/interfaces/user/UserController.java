package com.minialalipay.user.interfaces.user;

import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.common.trace.RequestIdGenerator;
import com.minialalipay.user.application.user.UserQueryService;
import com.minialalipay.user.application.user.dto.UserSearchResult;
import com.minialalipay.user.interfaces.dto.user.UserMeDTO;
import com.minialalipay.user.interfaces.dto.user.UserSearchResultDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户 Controller。
 *
 * <p>实现 P0 接口目录中 user-center 拥有的用户查询端点。
 * 所有接口经网关访问，禁止直连 8081 端口。</p>
 *
 * <p>接口清单：
 * <ul>
 *   <li>GET /api/v1/users/search - 按手机号搜索用户</li>
 *   <li>GET /api/v1/users/me - 查询当前用户最小资料投影（真实姓名已脱敏）</li>
 * </ul>
 * </p>
 *
 * <p>请求头规范：
 * <ul>
 *   <li>{@code X-User-Id} - 由网关解析会话后透传</li>
 *   <li>{@code X-Request-Id} - 请求编号，由 {@link RequestIdGenerator} 解析/生成</li>
 *   <li>{@code X-Trace-Id} - 链路编号，用于关联跨服务 Trace</li>
 * </ul>
 * </p>
 *
 * @see UserQueryService 用户查询应用服务
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserQueryService userQueryService;
    private final RequestIdGenerator requestIdGenerator;

    /**
     * 构造函数注入依赖。
     *
     * @param userQueryService   用户查询应用服务
     * @param requestIdGenerator 请求编号生成器
     */
    public UserController(
            UserQueryService userQueryService,
            RequestIdGenerator requestIdGenerator
    ) {
        this.userQueryService = userQueryService;
        this.requestIdGenerator = requestIdGenerator;
    }

    /**
     * 按手机号搜索用户。
     *
     * <p>搜索流程：
     * <ol>
     *   <li>从请求头获取当前用户 ID</li>
     *   <li>调用 {@link UserQueryService#searchUsers} 完成搜索</li>
     *   <li>返回搜索结果</li>
     * </ol>
     * </p>
     *
     * <p>业务规则：
     * <ul>
     *   <li>按手机号精确匹配搜索</li>
     *   <li>只返回 ACTIVE 状态的用户</li>
     *   <li>排除当前用户自己</li>
     *   <li>最多返回 20 条结果</li>
     * </ul>
     * </p>
     *
     * @param keyword     搜索手机号
     * @param userId      当前用户 ID（由网关从会话令牌解析后透传）
     * @param httpRequest HTTP 请求（用于提取 X-Request-Id 和 X-Trace-Id）
     * @return 用户搜索结果列表
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<UserSearchResultDTO>>> searchUsers(
            @RequestParam("keyword") String keyword,
            @RequestHeader("X-User-Id") String userId,
            HttpServletRequest httpRequest
    ) {
        // 1. 解析请求编号
        String requestId = requestIdGenerator.resolve(httpRequest.getHeader("X-Request-Id"));
        String traceId = httpRequest.getHeader("X-Trace-Id");

        // 2. 调用应用服务完成搜索
        List<UserSearchResult> appResults = userQueryService.searchUsers(keyword, userId);

        // 3. 转换为接口层 DTO
        List<UserSearchResultDTO> results = appResults.stream()
                .map(r -> new UserSearchResultDTO(
                        r.userId(),
                        r.accountNumber(),
                        r.nickname(),
                        r.identityStatus(),
                        r.phoneTail(),
                        r.maskedPhone()
                ))
                .collect(Collectors.toList());

        // 4. 返回搜索结果
        return ResponseEntity.ok(ApiResponse.success(results, requestId, traceId));
    }

    /**
     * 查询当前登录用户的最小资料投影。
     *
     * <p>供转账确认页等展示付款方身份；真实姓名已在服务边界脱敏，
     * 完整手机号等敏感字段不下发。只读查询，无幂等要求。</p>
     *
     * @param userId      当前用户 ID（由网关从会话令牌解析后透传）
     * @param httpRequest HTTP 请求（用于提取 X-Request-Id 和 X-Trace-Id）
     * @return 当前用户资料投影；用户不存在时返回 COMMON_NOT_FOUND
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserMeDTO>> getMyProfile(
            @RequestHeader("X-User-Id") String userId,
            HttpServletRequest httpRequest
    ) {
        String requestId = requestIdGenerator.resolve(httpRequest.getHeader("X-Request-Id"));
        String traceId = httpRequest.getHeader("X-Trace-Id");
        UserQueryService.MyProfile profile = userQueryService.getMyProfile(userId);
        UserMeDTO dto = new UserMeDTO(profile.userId(), profile.accountNumber(),
                profile.nickname(), profile.maskedRealName(), profile.createdAt());
        return ResponseEntity.ok(ApiResponse.success(dto, requestId, traceId));
    }
}
