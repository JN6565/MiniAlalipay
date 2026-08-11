import React from 'react';
import IconSet from './IconSet';
import './common.less';

/**
 * 空态组件：列表无数据时的引导展示，提供图标、说明文案与可选操作按钮。
 * 设计约定：空态不是错误，文案要告诉用户「下一步可以做什么」。
 */
const EmptyState: React.FC<{
  /** 图标（默认账单线性图标）；新页面可传 IconSet 组件定制。 */
  icon?: React.ReactNode;
  /** 主文案，如「暂无交易记录」。 */
  text: string;
  /** 辅助说明，如「绑定银行卡后即可充值」。 */
  hint?: string;
  /** 操作按钮文案与点击行为；两者同时提供才渲染按钮。 */
  actionText?: string;
  onAction?: () => void;
}> = ({ icon = <IconSet name="receipt" size={30} color="#94a3ba" />, text, hint, actionText, onAction }) => (
  <div className="h5-empty">
    <div className="h5-empty-icon">{icon}</div>
    <p className="h5-empty-text">{text}</p>
    {hint && <p className="h5-empty-hint">{hint}</p>}
    {actionText && onAction && (
      <button type="button" className="h5-empty-action" onClick={onAction}>
        {actionText}
      </button>
    )}
  </div>
);

export default EmptyState;
