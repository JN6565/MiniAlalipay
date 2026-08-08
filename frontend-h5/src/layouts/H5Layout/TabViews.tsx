import React, { createContext, useEffect, useRef, useState } from 'react';
import { useLocation } from '@umijs/max';
import HomePage from '@/pages/h5/Home';
import AITalkPage from '@/pages/h5/AITalk';
import ContactsPage from '@/pages/h5/Contacts';
import ProfilePage from '@/pages/h5/Profile';
import { isTabPath } from '@/components/h5/TabBar';

/** Tab 路由路径到页面组件的映射，保活容器据此直接挂载页面。 */
const TAB_VIEW_MAP: Record<string, React.ComponentType> = {
  '/h5/home': HomePage,
  '/h5/ai-talk': AITalkPage,
  '/h5/contacts': ContactsPage,
  '/h5/profile': ProfilePage,
};

/**
 * 当前激活的 Tab 路径上下文；处于二级页或无 Tab 激活时为 null。
 * 保活页面据此在重新可见时静默刷新数据（见 useTabActiveRefresh），
 * 保证转账等业务操作后回切看到的余额、列表是最新事实。
 */
export const TabActiveContext = createContext<string | null>(null);

/**
 * Tab 页保活容器（支付宝式切换）。
 *
 * 借鉴支付宝底部 Tab 行为：页面首次进入才挂载（懒挂载，避免一进应用
 * 就并发请求四个页面的数据），之后切走仅用 display:none 隐藏而不卸载，
 * 组件状态、已加载数据与流式会话全部保留，回切瞬时显示、不重复请求。
 *
 * 由 H5Layout 常驻渲染：进入二级页（转账、扫码等）时本容器不卸载，
 * 返回 Tab 页后仍保持保活状态。滚动位置按路径各自保存恢复，
 * 模拟支付宝每个 Tab 独立滚动位置的效果。
 */
const TabViews: React.FC = () => {
  const { pathname } = useLocation();
  const activePath = isTabPath(pathname) ? pathname : null;

  // 已访问过的 Tab 才挂载；初始值覆盖直接进入某个 Tab 页的深链场景。
  const [visited, setVisited] = useState<string[]>(() =>
    isTabPath(pathname) ? [pathname] : [],
  );
  // 按路径保存各 Tab 的滚动位置，切走时记录、回切时恢复。
  const scrollPosRef = useRef<Record<string, number>>({});
  const prevActiveRef = useRef<string | null>(activePath);

  useEffect(() => {
    if (activePath && !visited.includes(activePath)) {
      setVisited((prev) => [...prev, activePath]);
    }
  }, [activePath, visited]);

  useEffect(() => {
    // 进入登录/注册页时清空保活缓存：退出或切换账号后，
    // 不能把上一个账号的余额、会话等旧数据直接展示给新登录用户。
    if (pathname === '/h5/login' || pathname === '/h5/register') {
      setVisited([]);
      scrollPosRef.current = {};
      prevActiveRef.current = null;
    }
  }, [pathname]);

  useEffect(() => {
    const prev = prevActiveRef.current;
    if (prev === activePath) {
      return;
    }
    // 切换前记录旧 Tab 的滚动位置；隐藏的元素无法保留可视滚动，必须显式保存。
    if (prev) {
      scrollPosRef.current[prev] = window.scrollY;
    }
    prevActiveRef.current = activePath;
    // 等新 Tab 显示后再恢复其滚动位置；进入二级页时从顶部开始。
    requestAnimationFrame(() => {
      window.scrollTo(0, activePath ? scrollPosRef.current[activePath] ?? 0 : 0);
    });
  }, [activePath]);

  if (visited.length === 0) {
    return null;
  }

  return (
    <TabActiveContext.Provider value={activePath}>
      {visited.map((path) => {
        const Page = TAB_VIEW_MAP[path];
        return (
          <div key={path} style={{ display: path === activePath ? 'block' : 'none' }}>
            <Page />
          </div>
        );
      })}
    </TabActiveContext.Provider>
  );
};

export default TabViews;
