import { gatewayRequest } from './request';
import type { ApiResponse } from './ops';

/**
 * B 端用户管理网关服务（user-center 限界上下文）。
 *
 * 只访问网关公开前缀（/api/v1/admin），统一走 gatewayRequest 注入 X-Request-Id 并归一化错误。
 * 登录名只展示服务端脱敏值（loginNameMasked），禁止展示完整登录名或手机号；
 * 冻结/解冻以用户行版本 CAS 保护并发，因此与告警阈值一致不携带幂等键。
 */

/** 用户状态；DISABLED 即管理冻结，仅 ACTIVE 可冻结、仅 DISABLED 可解冻。 */
export type AdminUserStatus = 'PROVISIONING' | 'ACTIVE' | 'DISABLED';

/** 用户管理列表行，与 OpenAPI AdminUserResponse 对齐。 */
export interface AdminUserItem {
  userId: string;
  /** 脱敏登录名，服务端掩蔽中间位，不返回明文。 */
  loginNameMasked: string;
  nickname: string;
  status: AdminUserStatus;
  loginLockedUntil: string | null;
  /** 管理冻结操作者用户 ID。 */
  disabledBy: string | null;
  /** 管理冻结理由。 */
  disabledReason: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** 用户管理分页，与 OpenAPI AdminUserPage 对齐。 */
export interface AdminUserPage {
  items: AdminUserItem[];
  nextCursor: string | null;
}

/** 分页查询 B 端用户只读列表，支持状态与登录名关键词筛选及稳定 ID 游标分页。 */
export function listAdminUsers(
  status?: string,
  loginName?: string,
  cursor?: string,
  limit = 50,
): Promise<ApiResponse<AdminUserPage>> {
  const params = new URLSearchParams({ limit: String(limit) });
  if (status) params.set('status', status);
  if (loginName) params.set('loginName', loginName);
  if (cursor) params.set('cursor', cursor);
  return gatewayRequest<ApiResponse<AdminUserPage>>(`/api/v1/admin/users?${params.toString()}`);
}

/** 管理冻结用户（仅 ACTIVE），记录操作者与冻结理由，返回最新版本视图。 */
export function freezeAdminUser(
  userId: string,
  version: number,
  reason: string,
): Promise<ApiResponse<AdminUserItem>> {
  return gatewayRequest<ApiResponse<AdminUserItem>>(
    `/api/v1/admin/users/${encodeURIComponent(userId)}/freeze`,
    { method: 'POST', data: { version, reason } },
  );
}

/** 管理解冻用户（仅 DISABLED），清空冻结审计字段，返回最新版本视图。 */
export function unfreezeAdminUser(
  userId: string,
  version: number,
): Promise<ApiResponse<AdminUserItem>> {
  return gatewayRequest<ApiResponse<AdminUserItem>>(
    `/api/v1/admin/users/${encodeURIComponent(userId)}/unfreeze`,
    { method: 'POST', data: { version } },
  );
}
