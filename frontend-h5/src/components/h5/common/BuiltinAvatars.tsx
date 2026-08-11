import React, { useId } from 'react';

/**
 * 4 个内置头像（SVG 自绘，与 .qoder/canvases/h5-v2-icons.canvas.tsx 定稿一致）。
 *
 * 头像与资料沿用浏览器本地存储策略（localStorage，不上传后端）；
 * 个人详情页可选内置头像或上传本地图片（≤1MB）。
 */

/** 内置头像种类：人像/笑脸/山景/猫耳。 */
export type AvatarKind = 'user' | 'smile' | 'hills' | 'cat';

/** 全部内置头像种类（个人详情页选择区按此顺序渲染）。 */
export const AVATAR_KINDS: AvatarKind[] = ['user', 'smile', 'hills', 'cat'];

export interface BuiltinAvatarProps {
  kind: AvatarKind;
  /** 边长（px），默认 56（个人详情页选择区规格）。 */
  size?: number;
  /** 选中态：品牌蓝 2px 描边圈。 */
  selected?: boolean;
  className?: string;
  onClick?: () => void;
}

/** 单个内置头像：渐变底 + 简洁几何图形，gradient id 用 useId 避免同页冲突。 */
export const BuiltinAvatar: React.FC<BuiltinAvatarProps> = ({ kind, size = 56, selected, className, onClick }) => {
  const uid = useId().replace(/:/g, '');
  const gradId = `av-${kind}-${uid}`;
  const grads: Record<AvatarKind, [string, string]> = {
    user: ['#3d7bff', '#22c3e6'],
    smile: ['#ffb648', '#ff7a59'],
    hills: ['#2ed3a3', '#12a4c9'],
    cat: ['#9a7bff', '#5f6cff'],
  };
  const [from, to] = grads[kind];
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 64 64"
      className={className}
      onClick={onClick}
      style={{ borderRadius: '50%', display: 'block', flex: 'none', boxShadow: selected ? `0 0 0 2px #fff, 0 0 0 4px #256cff` : 'none', cursor: onClick ? 'pointer' : 'default' }}
    >
      <defs>
        <linearGradient id={gradId} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor={from} />
          <stop offset="1" stopColor={to} />
        </linearGradient>
      </defs>
      <rect width="64" height="64" fill={`url(#${gradId})`} />
      {kind === 'user' && (
        <>
          <circle cx="32" cy="25" r="10.5" fill="#fff" opacity="0.94" />
          <path d="M12 58c2.6-11.4 10.2-17 20-17s17.4 5.6 20 17Z" fill="#fff" opacity="0.94" />
        </>
      )}
      {kind === 'smile' && (
        <>
          <circle cx="23" cy="27" r="3" fill="#7a3c11" />
          <circle cx="41" cy="27" r="3" fill="#7a3c11" />
          <path d="M22 39c3 4.6 6.6 6.6 10 6.6S39 43.6 42 39" stroke="#7a3c11" strokeWidth="3" strokeLinecap="round" fill="none" />
        </>
      )}
      {kind === 'hills' && (
        <>
          <circle cx="43" cy="20" r="5" fill="#fff" opacity="0.9" />
          <path d="M-2 58 20 26l14 20 8-10 26 22Z" fill="#fff" opacity="0.94" />
        </>
      )}
      {kind === 'cat' && (
        <>
          <path d="M18 20l4-9 7 6Z" fill="#fff" opacity="0.94" />
          <path d="M46 20l-4-9-7 6Z" fill="#fff" opacity="0.94" />
          <circle cx="32" cy="34" r="13" fill="#fff" opacity="0.94" />
          <circle cx="27.5" cy="32" r="1.8" fill="#4a3a6b" />
          <circle cx="36.5" cy="32" r="1.8" fill="#4a3a6b" />
          <path d="M29 39c1.8 1.6 4.2 1.6 6 0" stroke="#4a3a6b" strokeWidth="1.6" strokeLinecap="round" fill="none" />
        </>
      )}
    </svg>
  );
};

export default BuiltinAvatar;
