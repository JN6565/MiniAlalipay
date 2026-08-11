import React from 'react';
import BuiltinAvatar, { AvatarKind } from './BuiltinAvatars';
import { getProfilePreference } from '@/utils/profile';

/** 头像编码 → 内置头像种类的映射（与 utils/profile 的编码体系一致）。 */
const CODE_TO_KIND: Record<string, AvatarKind> = {
  USER: 'user',
  SMILE: 'smile',
  HILLS: 'hills',
  CAT: 'cat',
};

export interface AvatarViewProps {
  /** 边长（px），默认 40。 */
  size?: number;
  /** 指定头像编码；不传则读取当前浏览器本地偏好。 */
  avatarCode?: string;
  /** 指定上传头像 Data URL；显式传 undefined 以外的值优先于内置头像。 */
  avatarDataUrl?: string;
  className?: string;
  onClick?: () => void;
}

/**
 * 统一头像展示组件：上传过头像（本地 Data URL）优先，否则渲染内置 SVG 头像。
 * 首页头部/我的页/收款码卡片等所有头像展示位统一使用本组件，保证视觉一致。
 */
const AvatarView: React.FC<AvatarViewProps> = ({ size = 40, avatarCode, avatarDataUrl, className, onClick }) => {
  const preference = avatarCode === undefined && avatarDataUrl === undefined ? getProfilePreference() : null;
  const dataUrl = avatarDataUrl ?? preference?.avatarDataUrl;
  const code = avatarCode ?? preference?.avatarCode ?? 'USER';

  if (dataUrl) {
    return (
      <img
        src={dataUrl}
        alt="头像"
        className={className}
        onClick={onClick}
        style={{ width: size, height: size, borderRadius: '50%', objectFit: 'cover', display: 'block', flex: 'none', cursor: onClick ? 'pointer' : 'default' }}
      />
    );
  }
  return (
    <BuiltinAvatar kind={CODE_TO_KIND[code] || 'user'} size={size} className={className} onClick={onClick} />
  );
};

export default AvatarView;
