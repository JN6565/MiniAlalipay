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
  nickname: string;
  status: string;
}

// 登录
export const login = (params: LoginParams) => {
  console.log('登录请求参数:', params);
  return request.post<LoginResult>('/api/v1/auth/login', params);
};

// 注册
export const register = (params: RegisterParams) => {
  console.log('注册请求参数:', params);
  return request.post<RegisterResult>('/api/v1/auth/register', params);
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
