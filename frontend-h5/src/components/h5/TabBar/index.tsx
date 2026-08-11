import React from 'react';
import { history } from '@umijs/max';
import IconSet, { IconName } from '@/components/h5/common/IconSet';
import './index.less';

/** 底部导航单项配置：目标路由路径、图标、文案。 */
interface TabItem {
  path: string;
  icon: IconName;
  label: string;
}

/** 四个 Tab 页配置，顺序即展示顺序（图标为 V2 设计稿定稿自绘 SVG）。 */
const TAB_ITEMS: TabItem[] = [
  { path: '/h5/home', icon: 'home', label: '首页' },
  { path: '/h5/ai-talk', icon: 'ai', label: '招财喵' },
  { path: '/h5/contacts', icon: 'contacts', label: '联系人' },
  { path: '/h5/profile', icon: 'me', label: '我的' },
];

/** 参与底部导航的路由路径集合，布局层据此决定是否渲染导航栏与保活容器。 */
export const TAB_PATHS: string[] = TAB_ITEMS.map((item) => item.path);

/** 判断给定路径是否为底部导航 Tab 页。 */
export function isTabPath(pathname: string): boolean {
  return TAB_PATHS.includes(pathname);
}

/**
 * 底部导航栏（支付宝式）。
 *
 * 由 H5Layout 布局层常驻渲染，切换 Tab 时导航栏本身不销毁重绘，
 * 仅内容区随路由变化；激活态由外部传入的当前路径推导，
 * 避免各 Tab 页各自硬编码高亮。点击已激活项时不重复跳转。
 */
const TabBar: React.FC<{ activePath: string }> = ({ activePath }) => {
  return (
    <div className="tabbar">
      {TAB_ITEMS.map((item) => {
        const active = item.path === activePath;
        return (
          <div
            key={item.path}
            className={`tab${active ? ' on' : ''}`}
            onClick={() => {
              if (item.path !== activePath) {
                history.push(item.path);
              }
            }}
          >
            <IconSet name={item.icon} size={21} width={active ? 2 : 1.8} color={active ? 'var(--h5-primary)' : 'var(--h5-text-3)'} />
            <span className="tab-label">{item.label}</span>
            {/* 选中态渐变指示条（设计稿定稿：12x2.5 品牌渐变圆角条） */}
            {active && <span className="tab-indicator" />}
          </div>
        );
      })}
    </div>
  );
};

export default TabBar;
