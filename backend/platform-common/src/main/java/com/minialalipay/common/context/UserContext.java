package com.minialalipay.common.context;

import java.util.Set;

/**
 * 网关透传的用户身份上下文。
 *
 * <p>由网关的 {@code AuthenticationGlobalFilter} 校验会话后写入 {@code X-User-Id}
 * 和 {@code X-User-Roles} 请求头，下游服务的 {@code UserContextFilter} 从请求头
 * 提取并放入 {@link UserContextHolder}（ThreadLocal），供业务层直接获取当前用户，
 * 无需在每个 Controller 方法上重复声明 {@code @RequestHeader}。</p>
 *
 * <p>此类是纯技术载体，不包含用户领域逻辑。身份事实来源为网关认证端口，
 * 下游服务不得从客户端提交的其他字段推导用户身份。</p>
 *
 * @param principalId 认证主体标识（用户 ID），由网关从可信会话校验接口获取
 * @param roles       主体拥有的角色集合，由网关从用户中心会话校验结果获取
 */
public record UserContext(String principalId, Set<String> roles) {

    /** 网关写入请求头的用户标识头名称。 */
    public static final String HEADER_USER_ID = "X-User-Id";

    /** 网关写入请求头的用户角色头名称。 */
    public static final String HEADER_USER_ROLES = "X-User-Roles";
}
