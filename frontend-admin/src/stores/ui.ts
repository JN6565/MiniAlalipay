import { create } from 'zustand';

/**
 * 运营后台客户端 UI 状态。
 *
 * 状态管理职责边界（见 src/README.md）：TanStack Query 管服务端数据缓存、
 * Umi initialState 管运行时身份、Zustand 只保存纯客户端界面状态。
 * 本 store 严格遵守该边界——只记录菜单折叠这类交互状态，
 * 严禁把余额、账本、交易终态等服务端事实复制进来。
 */

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
