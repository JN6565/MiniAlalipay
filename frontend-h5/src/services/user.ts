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
  phoneTail: string;
  successCount?: number;
  lastSuccessAt?: string;
  pinned?: boolean;
  hidden?: boolean;
  alias?: string;
}

// 查询当前用户信息
export const getMyInfo = () => {
  return request.get<UserInfo>('/api/v1/users/me');
};

// 搜索用户
export const searchUsers = (keyword: string, limit: number = 10) => {
  return request.get<{ items: PayeeInfo[] }>('/api/v1/users/search', {
    params: { keyword, limit },
  });
};

// 查询常用收款人
export const getContacts = (cursor?: string, limit: number = 20) => {
  return request.get<{ items: PayeeInfo[]; nextCursor?: string }>(
    '/api/v1/contacts',
    { params: { cursor, limit } },
  );
};

// 设置常用收款人属性
export const updateContact = (
  payeeUserId: string,
  params: {
    pinned?: boolean;
    hidden?: boolean;
    alias?: string;
    version: number;
  },
) => {
  return request.patch(`/v1/contacts/${payeeUserId}`, { data: params });
};
