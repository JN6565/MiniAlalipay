import React from 'react';

/**
 * H5 V2 统一自绘 SVG 图标集（IconSet）。
 *
 * 规范（与 .qoder/canvases/h5-v2-icons.canvas.tsx 设计稿 1:1 定稿）：
 * - 24x24 viewBox，strokeWidth 1.8（TabBar 选中态 2.0），圆角线帽/线接，纯线性风格；
 * - 所有 path 数据即设计稿定稿，严禁在实现阶段替换为 emoji 或第三方图标；
 * - 颜色通过 color（即 stroke）传入，未选中态推荐 #94a3ba，选中态 #256cff。
 */

/** 全部可用图标名（设计稿定稿清单）。 */
export type IconName =
  /* TabBar 四图标 */
  | 'home' | 'ai' | 'contacts' | 'me'
  /* 首页快捷宫格 */
  | 'scan' | 'collect' | 'transfer' | 'wallet' | 'huabei'
  /* 生活服务区 */
  | 'bill' | 'phone' | 'train' | 'plane' | 'health' | 'citizen' | 'travel' | 'food'
  /* 通用操作与状态 */
  | 'back' | 'more' | 'eyeOff' | 'eyeOn' | 'chevronRight' | 'search' | 'shield'
  | 'setting' | 'card' | 'receipt' | 'chart' | 'plus' | 'close' | 'check'
  | 'send' | 'camera' | 'keyboard' | 'clock' | 'qr' | 'drawer' | 'lock'
  /* AI Talk 交互补充图标 */
  | 'copy' | 'refresh' | 'thumbUp' | 'thumbDown'
  /* 财喵输入栏（豆包式）补充图标 */
  | 'mic' | 'arrowUp';

/** 图标 path 数据：与设计稿 ICON_PATHS 完全一致，逐条搬入不得改动。 */
const ICON_PATHS: Record<IconName, React.ReactNode> = {
  /* ---- TabBar ---- */
  home: (
    <>
      <path d="M4 10.5 12 4l8 6.5" />
      <path d="M6 9.5V19a1 1 0 0 0 1 1h3.2v-4.4a1.8 1.8 0 0 1 3.6 0V20H17a1 1 0 0 0 1-1V9.5" />
    </>
  ),
  ai: (
    <>
      <path d="M12 3.5c4.7 0 8.5 3.1 8.5 7 0 3.9-3.8 7-8.5 7-.9 0-1.9-.1-2.7-.4L5 19.5l.7-3.2c-1.4-1.3-2.2-3-2.2-4.8 0-3.9 3.8-8 8.5-8Z" />
      <path d="m12.6 8-2.4 3.4h3.6L11.4 15" />
    </>
  ),
  contacts: (
    <>
      <circle cx="9" cy="8.6" r="3.1" />
      <path d="M3.6 19.5c.5-3 2.7-5 5.4-5s4.9 2 5.4 5" />
      <path d="M15.4 5.9a3.1 3.1 0 0 1 0 5.4M17.4 14.9c1.6.8 2.7 2.4 3 4.6" />
    </>
  ),
  me: (
    <>
      <circle cx="12" cy="8.2" r="3.6" />
      <path d="M4.8 20c.8-3.8 3.7-6.2 7.2-6.2s6.4 2.4 7.2 6.2" />
    </>
  ),
  /* ---- 首页快捷宫格 ---- */
  scan: (
    <>
      <path d="M4 8V6a2 2 0 0 1 2-2h2M16 4h2a2 2 0 0 1 2 2v2M20 16v2a2 2 0 0 1-2 2h-2M8 20H6a2 2 0 0 1-2-2v-2" />
      <path d="M4.5 12h15" strokeDasharray="2.4 2.2" />
      <path d="M9 8.5h6M9 15.5h6" opacity="0.55" />
    </>
  ),
  collect: (
    <>
      <path d="M12 3.6v8.6" />
      <path d="m8.6 9 3.4 3.4L15.4 9" />
      <path d="M5 13.5v3.7A2.8 2.8 0 0 0 7.8 20h8.4a2.8 2.8 0 0 0 2.8-2.8v-3.7" />
    </>
  ),
  transfer: (
    <>
      <path d="M4 8.4h13.4" />
      <path d="m14.6 5.4 3 3-3 3" />
      <path d="M20 15.6H6.6" />
      <path d="m9.4 12.6-3 3 3 3" />
    </>
  ),
  wallet: (
    <>
      <path d="M4 7.5A2.5 2.5 0 0 1 6.5 5h10A2.5 2.5 0 0 1 19 7.5v.3" />
      <path d="M4 8v9a2.5 2.5 0 0 0 2.5 2.5h11A2.5 2.5 0 0 0 20 17v-6.5A2.5 2.5 0 0 0 17.5 8H4Z" />
      <circle cx="16" cy="13.9" r="1.15" fill="currentColor" stroke="none" />
    </>
  ),
  huabei: (
    <>
      <path d="M19.2 13.6A7.6 7.6 0 0 1 10.4 5 7.6 7.6 0 1 0 19.2 13.6Z" />
      <path d="m15.4 5.4.5 1.5 1.5.5-1.5.5-.5 1.5-.5-1.5-1.5-.5 1.5-.5Z" fill="currentColor" stroke="none" />
    </>
  ),
  /* ---- 生活服务区 ---- */
  bill: <path d="M13 3 6.4 13h4.2L11 21l6.6-10h-4.2Z" />,
  phone: (
    <>
      <rect x="7" y="3.4" width="10" height="17.2" rx="2.4" />
      <path d="M10.6 5.6h2.8" />
      <circle cx="12" cy="17.6" r="1.05" fill="currentColor" stroke="none" />
    </>
  ),
  train: (
    <>
      <rect x="5.5" y="3.8" width="13" height="12.4" rx="3" />
      <path d="M5.5 10h13" />
      <circle cx="9.2" cy="13.2" r="1" fill="currentColor" stroke="none" />
      <circle cx="14.8" cy="13.2" r="1" fill="currentColor" stroke="none" />
      <path d="m8 20 1.6-3.4M16 20l-1.6-3.4M7 20h10" />
    </>
  ),
  plane: (
    <path d="M10.2 20.6 12 14l6.6-6.6a1.9 1.9 0 0 0-2.7-2.7L9.3 11.3l-5.9 1.6 1.9 1.9 3.4-.7 1.9 1.9-.7 3.4Z" />
  ),
  health: (
    <>
      <rect x="4" y="4" width="16" height="16" rx="4.2" />
      <path d="M12 8.4v7.2M8.4 12h7.2" />
    </>
  ),
  citizen: (
    <>
      <path d="m12 3.4 8 4.2H4Z" />
      <path d="M5.6 7.6v8M10 7.6v8M14 7.6v8M18.4 7.6v8" />
      <path d="M4 15.6h16M3.4 19.4h17.2" />
    </>
  ),
  travel: (
    <>
      <circle cx="6.4" cy="16" r="3.4" />
      <circle cx="17.6" cy="16" r="3.4" />
      <path d="M6.4 16 9.6 8.6h4.2M13.8 8.6 17.6 16M13.8 8.6l-1.4-2.6h-2" />
    </>
  ),
  food: (
    <>
      <path d="M4.4 11.4h15.2a7.6 7.6 0 0 1-6 6.8l-.2 1.6h-2.8l-.2-1.6a7.6 7.6 0 0 1-6-6.8Z" />
      <path d="M9 8.4c0-1.1.9-1.2.9-2.3M13.2 8.4c0-1.1.9-1.2.9-2.3" />
    </>
  ),
  /* ---- 通用操作 ---- */
  back: <path d="m14.5 5.5-6.5 6.5 6.5 6.5" />,
  more: (
    <path d="M5.5 12h.01M12 12h.01M18.5 12h.01" strokeWidth="2.6" />
  ),
  eyeOff: (
    <>
      <path d="M4 5.5 20 19" />
      <path d="M9.2 6.4A8.6 8.6 0 0 1 12 6c4.4 0 7.8 3.2 9 6-.4 1-1.1 2.1-2 3.1M6.3 8.1C5 9.3 4 10.7 3 12c1.2 2.8 4.6 6 9 6 1 0 2-.2 2.9-.5" />
      <path d="M10 10.3a2.8 2.8 0 0 0 3.9 3.9" />
    </>
  ),
  eyeOn: (
    <>
      <path d="M3 12c1.2-2.8 4.6-6 9-6s7.8 3.2 9 6c-1.2 2.8-4.6 6-9 6s-7.8-3.2-9-6Z" />
      <circle cx="12" cy="12" r="2.8" />
    </>
  ),
  chevronRight: <path d="m9.5 5.5 6.5 6.5-6.5 6.5" />,
  search: (
    <>
      <circle cx="11" cy="11" r="6.4" />
      <path d="m15.8 15.8 4.2 4.2" />
    </>
  ),
  shield: (
    <>
      <path d="M12 3.6 19 6v5.4c0 4.4-3 7.6-7 9-4-1.4-7-4.6-7-9V6Z" />
      <path d="m9.2 11.8 2 2 3.6-3.8" />
    </>
  ),
  setting: (
    <>
      <circle cx="12" cy="12" r="2.9" />
      <path d="M12 3.8v2M12 18.2v2M3.8 12h2M18.2 12h2M6.2 6.2l1.4 1.4M16.4 16.4l1.4 1.4M17.8 6.2l-1.4 1.4M7.6 16.4l-1.4 1.4" />
    </>
  ),
  card: (
    <>
      <rect x="3.4" y="5.4" width="17.2" height="13.2" rx="2.6" />
      <path d="M3.4 9.8h17.2M6.6 14.6h4" />
    </>
  ),
  receipt: (
    <>
      <path d="M6.4 3.8h11.2V20l-2.2-1.5L13.2 20l-2.2-1.5L8.8 20l-2.4-1.5Z" />
      <path d="M9.2 8.2h5.6M9.2 11.6h5.6M9.2 15h3.2" />
    </>
  ),
  chart: (
    <>
      <path d="M4 4v15a1 1 0 0 0 1 1h15" />
      <path d="M8 15.5v-4M12.5 15.5V8M17 15.5v-6.5" />
    </>
  ),
  plus: <path d="M12 5.5v13M5.5 12h13" />,
  close: <path d="m6.5 6.5 11 11M17.5 6.5l-11 11" />,
  check: <path d="m5 12.5 4.5 4.5L19 7.5" />,
  send: <path d="M4.5 11 19.5 4l-3.6 15.6-4.2-5.9ZM11.7 13.7 19.5 4" />,
  camera: (
    <>
      <path d="M4 8.4A2.4 2.4 0 0 1 6.4 6h1.4l1.3-2h5.8l1.3 2h1.4A2.4 2.4 0 0 1 20 8.4v8.2a2.4 2.4 0 0 1-2.4 2.4H6.4A2.4 2.4 0 0 1 4 16.6Z" />
      <circle cx="12" cy="12.4" r="3.2" />
    </>
  ),
  keyboard: (
    <>
      <rect x="3.4" y="6.4" width="17.2" height="11.2" rx="2.4" />
      <path d="M6.6 10h.01M10 10h.01M13.4 10h.01M16.8 10h.01M6.6 13.4h.01M17.4 13.4h.01M9.4 13.4h5.2" strokeWidth="2" />
    </>
  ),
  /* ---- 流程与模块补充图标（flows/modules 稿定稿） ---- */
  clock: (
    <>
      <circle cx="12" cy="12" r="8.4" />
      <path d="M12 7.6V12l3 2.2" />
    </>
  ),
  qr: (
    <>
      <rect x="4" y="4" width="6.4" height="6.4" rx="1.4" />
      <rect x="13.6" y="4" width="6.4" height="6.4" rx="1.4" />
      <rect x="4" y="13.6" width="6.4" height="6.4" rx="1.4" />
      <path d="M13.6 13.6h2.6v2.6M20 13.6v2.6M16.2 20h-2.6M20 20h.01" />
    </>
  ),
  drawer: <path d="M4 6.5h16M4 12h11M4 17.5h16" />,
  lock: (
    <>
      <rect x="5.4" y="10.4" width="13.2" height="9.6" rx="2.6" />
      <path d="M8.4 10.4V8a3.6 3.6 0 0 1 7.2 0v2.4" />
    </>
  ),
  /* ---- AI Talk 交互补充 ---- */
  copy: (
    <>
      <rect x="8.4" y="8.4" width="11.2" height="11.2" rx="2.2" />
      <path d="M5.6 15.2A1.8 1.8 0 0 1 4 13.4V6a2 2 0 0 1 2-2h7.4a1.8 1.8 0 0 1 1.8 1.6" />
    </>
  ),
  refresh: (
    <>
      <path d="M19 12a7 7 0 1 1-2.05-4.95" />
      <path d="M19.2 3.8v3.4h-3.4" />
    </>
  ),
  thumbUp: (
    <>
      <path d="M7 10.6v9.4H5a1.4 1.4 0 0 1-1.4-1.4v-6.6A1.4 1.4 0 0 1 5 10.6Z" />
      <path d="M7 19.4h9.2a2.2 2.2 0 0 0 2.16-1.76l1.1-5.4a2.2 2.2 0 0 0-2.16-2.64h-4.6l.8-3.7a1.7 1.7 0 0 0-3-1.3L7 10.6" />
    </>
  ),
  thumbDown: (
    <>
      <path d="M17 13.4V4H19a1.4 1.4 0 0 1 1.4 1.4V12A1.4 1.4 0 0 1 19 13.4Z" />
      <path d="M17 4.6H7.8a2.2 2.2 0 0 0-2.16 1.76l-1.1 5.4a2.2 2.2 0 0 0 2.16 2.64h4.6l-.8 3.7a1.7 1.7 0 0 0 3 1.3L17 13.4" />
    </>
  ),
  /* ---- 财喵输入栏（豆包式）补充 ---- */
  mic: (
    <>
      <rect x="9.2" y="3.6" width="5.6" height="10" rx="2.8" />
      <path d="M5.8 11.6a6.2 6.2 0 0 0 12.4 0" />
      <path d="M12 17.8v2.6M8.8 20.4h6.4" />
    </>
  ),
  arrowUp: <path d="M12 19V5.5M6.5 11 12 5.5 17.5 11" />,
};

export interface IconSetProps {
  /** 图标名（IconName 之一）。 */
  name: IconName;
  /** 边长（px），默认 24。 */
  size?: number;
  /** 线条颜色（stroke），默认 currentColor。 */
  color?: string;
  /** 线宽，默认 1.8（TabBar 选中态传 2.0）。 */
  width?: number;
  /** 自定义类名。 */
  className?: string;
  style?: React.CSSProperties;
}

/** 统一线性图标组件：path 数据严格按 h5-v2-icons 设计稿定稿绘制。 */
const IconSet: React.FC<IconSetProps> = ({ name, size = 24, color = 'currentColor', width = 1.8, className, style }) => {
  return (
    <svg
      viewBox="0 0 24 24"
      width={size}
      height={size}
      fill="none"
      stroke={color}
      strokeWidth={width}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      style={style}
    >
      {ICON_PATHS[name]}
    </svg>
  );
};

export default IconSet;
