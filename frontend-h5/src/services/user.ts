import request from './request';

export interface UserInfo {
  userId: string;
  accountNumber: string;
  nickname: string;
  avatar?: string;
  userType: string;
  createdAt: string;
}

export interface PayeeInfo {
  userId: string;
  nickname: string;
  accountNumber: string;
  identityStatus?: string; // 身份状态（如：VERIFIED）
}

export interface Contact {
  payeeUserId: string;
  alias?: string;
  successCount: number;
  lastSuccessAt?: string;
  pinned: boolean;
}

// 查询当前用户信息
export const getMyInfo = () => {
  return request.get<UserInfo>('/api/v1/users/me');
};

// 搜索用户
export const searchUsers = (keyword: string, limit: number = 10) => {
  return request.get<PayeeInfo[]>('/api/v1/users/search', {
    params: { keyword, limit },
  });
};

// 查询常用联系人
export const getContacts = (limit: number = 5) => {
  return request.get<Contact[]>('/api/v1/contacts', {
    params: { limit },
  });
};

// 更新联系人属性
export const updateContact = (
  payeeUserId: string,
  params: {
    pinned?: boolean;
    hidden?: boolean;
    alias?: string;
  },
) => {
  return request.patch(`/api/v1/contacts/${payeeUserId}`, params);
};
