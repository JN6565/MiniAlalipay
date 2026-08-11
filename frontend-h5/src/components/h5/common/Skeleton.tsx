import React from 'react';
import './common.less';

/**
 * 骨架屏：异步加载区域占位，避免布局跳动与白屏等待感。
 *
 * 使用形态：
 * - card：大卡片占位（余额卡、资产卡）
 * - list：多行列表占位（明细列表、卡列表）
 * - 默认行数/高度可通过 props 调整
 */
const Skeleton: React.FC<{
  /** 占位形态：card 大卡片，list 行列表。 */
  variant?: 'card' | 'list';
  /** list 形态下的行数，默认 3。 */
  rows?: number;
  /** 自定义高度（card 形态），单位 px。 */
  height?: number;
}> = ({ variant = 'list', rows = 3, height = 120 }) => {
  if (variant === 'card') {
    return (
      <div className="h5-skeleton h5-skeleton-card" style={{ height }} aria-label="加载中" />
    );
  }
  return (
    <div className="h5-skeleton h5-skeleton-list" aria-label="加载中">
      {Array.from({ length: rows }).map((_, i) => (
        <div className="h5-skeleton-row" key={i}>
          <div className="h5-skeleton-circle" />
          <div className="h5-skeleton-lines">
            <div className="h5-skeleton-line long" />
            <div className="h5-skeleton-line short" />
          </div>
        </div>
      ))}
    </div>
  );
};

export default Skeleton;
