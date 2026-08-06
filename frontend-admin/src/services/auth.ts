import { gatewayRequest } from './request';
import type { ApiResponse } from './ops';

/**
 * B 端认证服务。
 *
 * 只访问网关公开前缀（/api/v1/auth/**），统一走 gatewayRequest：
 * 登录后由请求层注入 Authorization: Bearer <token> 完成会话闭环。
 * 密码只通过请求体提交给网关，不写入日志、浏览器存储或 URL。
 */

/** 登录成功响应，与 user-center AuthResponseDTO 对齐。 */
export interface LoginResult {
  accessToken: string;
  userId: string;
  accountNumber: string;
  nickname: string;
  status: string;
}

/** 当前身份响应，与 user-center CurrentIdentityResponseDTO 对齐。 */
export interface CurrentIdentity {
  userId: string;
  displayName: string;
  roles: string[];
}

/** 运营账号口令登录。 */
export function adminLogin(loginIdentifier: string, loginPassword: string) {
  return gatewayRequest<ApiResponse<LoginResult>>('/api/v1/auth/login', {
    method: 'POST',
    data: { loginIdentifier, loginPassword },
  });
}

/** 查询当前身份（展示名 + 角色），用于初始化权限模型。 */
export function getCurrentIdentity() {
  return gatewayRequest<ApiResponse<CurrentIdentity>>('/api/v1/auth/me', {
    method: 'GET',
  });
}

/** 退出登录并销毁服务端会话。 */
export function adminLogout() {
  return gatewayRequest<ApiResponse<null>>('/api/v1/auth/logout', {
    method: 'POST',
  });
}
