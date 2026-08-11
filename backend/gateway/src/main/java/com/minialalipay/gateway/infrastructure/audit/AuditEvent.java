package com.minialalipay.gateway.infrastructure.audit;

/**
 * 网关安全审计事件类型。
 *
 * <p>只包含网关接入层可感知的安全事件。
 * 业务级审计（登录失败、草稿创建、交易状态变更等）由对应服务自行记录。</p>
 *
 * <h3>事件分类</h3>
 * <ul>
 *   <li>认证类：令牌缺失、令牌无效、会话过期</li>
 *   <li>授权类：角色不足、运维路径被拒</li>
 *   <li>防护类：CSRF 拒绝、限流触发、非法请求编号</li>
 * </ul>
 */
public enum AuditEvent {

    /** 请求缺少有效认证令牌或令牌格式不符合 Bearer 规范。 */
    AUTH_MISSING_TOKEN,

    /** 令牌已提交但认证端口判定无效或已过期。 */
    AUTH_INVALID_TOKEN,

    /** 当前主体已认证但角色不满足目标路径的访问要求。 */
    AUTHORIZATION_DENIED,

    /** 写请求缺少或携带非法格式的 CSRF Token。 */
    CSRF_REJECTED,

    /** 请求触发限流阈值被拒绝。 */
    RATE_LIMIT_TRIGGERED,

    /** 客户端提交的请求编号包含不安全字符，已被替换。 */
    REQUEST_ID_REPLACED
}
