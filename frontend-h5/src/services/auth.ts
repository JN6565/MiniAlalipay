import request from './request';

export interface LoginParams {
  loginName: string;
  loginPassword: string;
}

export interface RegisterParams {
  loginName: string;
  nickname: string;
  loginPassword: string;
}

export interface LoginResult {
  accessToken: string;
  userId: string;
  nickname: string;
  userType: string;
}

export interface RegisterResult {
  userId: string;
  accessToken: string;
  initialBalanceFen: number;
}

// 登录
export const login = (params: LoginParams) => {
  return request.post<LoginResult>('/api/v1/auth/login', { data: params });
};

// 注册
export const register = (params: RegisterParams) => {
  return request.post<RegisterResult>('/api/v1/auth/register', {
    data: params,
  });
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
  return request.patch('/api/v1/auth/login-password', { data: params });
};
