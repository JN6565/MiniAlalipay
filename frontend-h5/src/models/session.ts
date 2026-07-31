import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import * as authService from '@/services/auth';
import * as userService from '@/services/user';

interface SessionState {
  // 状态
  accessToken: string | null;
  userId: string | null;
  nickname: string | null;
  userType: string | null;

  // 操作
  login: (params: authService.LoginParams) => Promise<void>;
  register: (params: authService.RegisterParams) => Promise<void>;
  logout: () => Promise<void>;
  checkAuth: () => boolean;
  updateUserInfo: (info: Partial<SessionState>) => void;
}

export const useSessionStore = create<SessionState>()(
  persist(
    (set, get) => ({
      // 初始状态
      accessToken: null,
      userId: null,
      nickname: null,
      userType: null,

      // 登录
      login: async (params: authService.LoginParams) => {
        const result = await authService.login(params);
        set({
          accessToken: result.accessToken,
          userId: result.userId,
          nickname: result.nickname,
          userType: result.userType,
        });
      },

      // 注册
      register: async (params: authService.RegisterParams) => {
        const result = await authService.register(params);
        set({
          accessToken: result.accessToken,
          userId: result.userId,
          nickname: params.nickname,
          userType: 'NORMAL',
        });
      },

      // 退出登录
      logout: async () => {
        try {
          await authService.logout();
        } finally {
          set({
            accessToken: null,
            userId: null,
            nickname: null,
            userType: null,
          });
        }
      },

      // 检查登录状态
      checkAuth: () => {
        return !!get().accessToken;
      },

      // 更新用户信息
      updateUserInfo: (info: Partial<SessionState>) => {
        set(info);
      },
    }),
    {
      name: 'session-storage',
      partialize: (state) => ({
        accessToken: state.accessToken,
        userId: state.userId,
        nickname: state.nickname,
        userType: state.userType,
      }),
    },
  ),
);
