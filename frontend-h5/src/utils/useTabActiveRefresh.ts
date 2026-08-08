import { useContext, useEffect, useRef } from 'react';
import { TabActiveContext } from '@/layouts/H5Layout/TabViews';

/**
 * Tab 回切静默刷新钩子（保活页面数据新鲜度）。
 *
 * 保活容器中的页面不会因切换而卸载重建，`useEffect` 初始加载只执行一次；
 * 转账、充值等业务操作完成后回切时，页面需要后台重拉数据才能展示最新事实。
 * 本钩子在所属 Tab 重新变为激活状态时调用 refresh，由页面自行静默刷新
 * （不置 loading、不闪加载圈），页面仍保持保活的瞬时切换体验。
 *
 * 首次挂载即激活的场景不触发：页面自身的初始加载已覆盖该次请求，
 * 避免进入页面时重复请求同一份数据。refresh 通过 ref 保存最新引用，
 * 调用方无需为回调稳定性做 useCallback 包装。
 *
 * @param tabPath 所属 Tab 的路由路径，如 '/h5/home'
 * @param refresh Tab 重新激活时执行的刷新逻辑，应为静默刷新（不触发全屏加载态）
 */
export function useTabActiveRefresh(tabPath: string, refresh: () => void): void {
  const activePath = useContext(TabActiveContext);
  const refreshRef = useRef(refresh);
  refreshRef.current = refresh;
  // 跳过首次挂载时的激活，避免与页面自身初始加载重复请求。
  const mountedRef = useRef(false);

  useEffect(() => {
    if (!mountedRef.current) {
      mountedRef.current = true;
      return;
    }
    if (activePath === tabPath) {
      refreshRef.current();
    }
  }, [activePath, tabPath]);
}
