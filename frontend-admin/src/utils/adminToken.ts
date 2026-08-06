/**
 * B 端会话令牌存储。
 *
 * 职责：
 * 1. 维护内存中的“当前生效令牌”（请求层只读它，避免每次从 localStorage 同步）；
 * 2. 将真实登录令牌持久化到 localStorage，刷新页面后仍保持登录；
 * 3. 提供本地演示使用的网关 dev Stub 令牌常量（仅开发环境、内存态，绝不持久化）。
 *
 * 安全约束：dev Stub 令牌只存在于内存，不写入 localStorage，也不出现在日志或 URL；
 * 生产构建（NODE_ENV=production）不注入任何演示令牌，未登录时一律引导到登录页。
 */

const STORAGE_KEY = 'minialalipay.admin.token';

/** 网关 dev 鉴权桩默认令牌，需与网关 stub-token 配置保持一致。 */
export const DEV_STUB_TOKEN = 'dev-admin-token';

let activeToken: string | null = null;

/** 读取持久化的真实登录令牌；dev Stub 令牌永远不会出现在这里。 */
export function readStoredToken(): string | null {
  try {
    return window.localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

/** 读取内存中生效令牌；未设置时回退到 localStorage 中持久化的登录令牌。 */
export function getActiveToken(): string | null {
  return activeToken ?? readStoredToken();
}

/** 设置内存生效令牌（dev Stub 走此路径，不持久化）。 */
export function setActiveToken(token: string | null): void {
  activeToken = token;
}

/** 持久化真实登录令牌（同时更新内存）。 */
export function rememberToken(token: string): void {
  activeToken = token;
  try {
    window.localStorage.setItem(STORAGE_KEY, token);
  } catch {
    // 隐私模式等存储不可用时静默降级为内存态会话。
  }
}

/** 清除内存与持久化令牌（登出/会话失效）。 */
export function clearToken(): void {
  activeToken = null;
  try {
    window.localStorage.removeItem(STORAGE_KEY);
  } catch {
    // 同上，忽略存储异常。
  }
}
