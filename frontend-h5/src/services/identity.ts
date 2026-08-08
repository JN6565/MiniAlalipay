import request from './request';

export interface BindIdentityParams {
  realName: string;
  idCard: string;
}

export interface IdentityInfo {
  realName: string | null;
  idCardMasked: string | null;
  identityStatus: 'UNVERIFIED' | 'VERIFIED';
}

// 绑定身份信息
export const bindIdentity = (params: BindIdentityParams) => {
  return request.post<IdentityInfo>('/api/v1/identity/bind', params) as unknown as Promise<IdentityInfo>;
};

// 查询身份绑定状态
export const getIdentity = () => {
  return request.get<IdentityInfo>('/api/v1/identity') as unknown as Promise<IdentityInfo>;
};
