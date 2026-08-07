import request from './request';

export interface UserInfo {
  userId: string;
  accountNumber: string;
  nickname: string;
  avatar?: string;
  userType?: string; // 后端资料投影未返回时缺省
  createdAt: string;
  maskedRealName?: string; // 脱敏真实姓名（如 吕*），由用户中心在服务边界脱敏后返回
}

export interface PayeeInfo {
  userId: string;
  nickname: string;
  accountNumber: string;
  identityStatus?: string; // 身份状态（如：VERIFIED）
  phoneTail?: string; // 手机尾号（4 位），后端未返回脱敏手机号时的降级展示
  maskedPhone?: string; // 脱敏手机号，如 138****9150
}

export interface Contact {
  payeeUserId: string;
  payeeName?: string;
  accountNumber?: string;
  alias?: string;
  successCount: number;
  lastSuccessAt?: string;
  pinned: boolean;
  payeeNickname?: string; // 收款人昵称；收款人不存在或非 ACTIVE 时为空，前端降级展示
  payeeAccountNumber?: string; // 收款人系统账户号（同上，可空）
  maskedPhone?: string; // 收款人脱敏手机号，如 138****9150
  phoneTail?: string; // 收款人手机尾号（4 位），脱敏手机号缺失时的降级展示
}

export interface Friend {
  friendUserId: string;
  friendName: string;
  accountNumber: string;
  alias?: string;
  createdAt: string;
}

export interface FriendRequest {
  requestId: string;
  fromUserId: string;
  fromUserName: string;
  toUserId: string;
  status: string;
  message?: string;
  createdAt: string;
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

// ====== 好友系统 ======

// 发送好友请求
export const sendFriendRequest = (toUserId: string, message?: string) => {
  return request.post<FriendRequest>('/api/v1/friends/request', { toUserId, message });
};

// 获取待处理的好友请求
export const getPendingFriendRequests = () => {
  return request.get<FriendRequest[]>('/api/v1/friends/request/pending');
};

// 接受好友请求
export const acceptFriendRequest = (requestId: string) => {
  return request.post<FriendRequest>(`/api/v1/friends/request/${requestId}/accept`);
};

// 拒绝好友请求
export const rejectFriendRequest = (requestId: string) => {
  return request.post<FriendRequest>(`/api/v1/friends/request/${requestId}/reject`);
};

// 获取好友列表
export const getFriends = () => {
  return request.get<Friend[]>('/api/v1/friends/list');
};

// 删除好友
export const removeFriend = (friendUserId: string) => {
  return request.delete(`/api/v1/friends/${friendUserId}`);
};
