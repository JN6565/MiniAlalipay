/**
 * C 端 H5 公共组件统一导出：
 * - Skeleton：异步区域骨架屏（卡片/列表形态）
 * - RevealToggle：敏感信息掩码切换（余额/卡号/手机号），明文不落日志与存储
 * - EmptyState：空数据引导（文案 + 可选操作按钮）
 * - MonthGroupList：按月分组 + 收支汇总头列表（账单页/银行卡账单页共用）
 * - BankCardFace：仿真银行卡面（银行专属渐变 + CSS 纹样 + 标识/尾号/默认角标）
 * - IconSet：V2 统一自绘 SVG 图标集（path 按 h5-v2-icons 设计稿定稿）
 * - BuiltinAvatar：4 个内置 SVG 头像（个人详情页选择用）
 */
export { default as Skeleton } from './Skeleton';
export { default as RevealToggle } from './RevealToggle';
export { default as EmptyState } from './EmptyState';
export { default as MonthGroupList } from './MonthGroupList';
export type { MonthGroupItem } from './MonthGroupList';
export { default as BankCardFace, BANK_FACE_STYLES } from './BankCardFace';
export type { BankFaceStyle } from './BankCardFace';
export { default as IconSet } from './IconSet';
export type { IconName, IconSetProps } from './IconSet';
export { default as BuiltinAvatar, AVATAR_KINDS } from './BuiltinAvatars';
export type { AvatarKind } from './BuiltinAvatars';
export { default as AvatarView } from './AvatarView';
export type { AvatarViewProps } from './AvatarView';
