import request from './request';

const generateUUID = (): string => 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
  const random = (Math.random() * 16) | 0;
  return (char === 'x' ? random : (random & 0x3) | 0x8).toString(16);
});

export interface LoginParams {
  loginIdentifier: string;
  loginPassword: string;
}

export interface RegisterParams {
  phoneNumber: string;
  realName: string;
  nickname?: string;
  loginPassword: string;
  paymentPassword: string;
}

export interface LoginResult {
  accessToken: string;
  userId: string;
  accountNumber: string;
  nickname: string;
  userType: string;
}

export interface RegisterResult {
  userId: string;
  accountNumber: string;
  accessToken: string;
  nickname: string;
  status: string;
}

// 登录
export const login = (params: LoginParams) => {
  return request.post<LoginResult>('/api/v1/auth/login', params) as unknown as Promise<LoginResult>;
};

// 注册
export const register = (params: RegisterParams) => {
  return request.post<RegisterResult>('/api/v1/auth/register', params, {
    headers: { 'Idempotency-Key': generateUUID() },
  }) as unknown as Promise<RegisterResult>;
};

// 退出登录
export const logout = () => {
  return request.post('/api/v1/auth/logout');
};

// 修改登录密码
export const changeLoginPassword = (params: {
  currentPassword: string;
  newPassword: string;
}) => {
  return request.patch('/api/v1/auth/login-password', params);
};
