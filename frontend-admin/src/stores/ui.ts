import { create } from 'zustand';

/** 运营后台的纯客户端界面状态，不保存服务端业务事实。 */
export interface AdminUiState {
  /** 左侧导航是否收起。 */
  menuCollapsed: boolean;
  /** 切换左侧导航展开状态。 */
  toggleMenu: () => void;
  /** 按断点或外部事件设置左侧导航展开状态。 */
  setMenuCollapsed: (collapsed: boolean) => void;
}

export const useAdminUiStore = create<AdminUiState>((set) => ({
  menuCollapsed: false,
  toggleMenu: () => set((state) => ({ menuCollapsed: !state.menuCollapsed })),
  setMenuCollapsed: (collapsed) => set({ menuCollapsed: collapsed }),
}));
