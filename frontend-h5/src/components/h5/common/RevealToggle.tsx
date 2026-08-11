import React, { useState } from 'react';
import IconSet from './IconSet';
import './common.less';

/**
 * 掩码切换组件：余额、卡号、手机号等敏感信息的默认掩码展示与点击可见。
 *
 * 安全约定：
 * - 明文仅存在于组件内存状态，不写入日志、localStorage 或 URL；
 * - 页面卸载后明文状态即销毁，重新进入页面恢复掩码；
 * - 明文的获取（如完整卡号）由调用方在展开时自行请求，本组件只负责展示切换。
 */
const RevealToggle: React.FC<{
  /** 掩码态展示内容，如 **** 或 ¥ ****。 */
  mask: string;
  /** 明文内容；未提供时显示 mask。 */
  value?: string;
  /** 默认是否展开明文，默认 false（掩码优先）。 */
  defaultRevealed?: boolean;
  /** 切换回调：revealed=true 表示用户请求查看明文，可用于按需拉取敏感数据。 */
  onToggle?: (revealed: boolean) => void;
  /** 明文字体样式类（如金额数字字体）。 */
  valueClassName?: string;
}> = ({ mask, value, defaultRevealed = false, onToggle, valueClassName }) => {
  const [revealed, setRevealed] = useState(defaultRevealed);

  const handleToggle = () => {
    const next = !revealed;
    setRevealed(next);
    onToggle?.(next);
  };

  return (
    <span className="h5-reveal" onClick={handleToggle}>
      <span className={`h5-reveal-text${valueClassName ? ` ${valueClassName}` : ''}`}>
        {revealed && value !== undefined ? value : mask}
      </span>
      <span className="h5-reveal-icon" aria-label={revealed ? '隐藏' : '查看'}>
        <IconSet name={revealed ? 'eyeOn' : 'eyeOff'} size={15} color="var(--h5-text-3)" />
      </span>
    </span>
  );
};

export default RevealToggle;
