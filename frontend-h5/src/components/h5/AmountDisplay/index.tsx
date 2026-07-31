import React from 'react';
import { fenToYuan, formatAmount } from '@/utils/format';
import './index.less';

interface AmountDisplayProps {
  /** 金额（分） */
  amountFen: number;
  /** 方向：收入/支出 */
  direction?: 'IN' | 'OUT';
  /** 是否显示正负号 */
  showSign?: boolean;
  /** 是否使用千分位格式 */
  useThousandSeparator?: boolean;
  /** 字体大小 */
  size?: 'small' | 'medium' | 'large';
  /** 自定义类名 */
  className?: string;
}

export const AmountDisplay: React.FC<AmountDisplayProps> = ({
  amountFen,
  direction,
  showSign = false,
  useThousandSeparator = false,
  size = 'medium',
  className = '',
}) => {
  const getDisplayAmount = () => {
    const amount = useThousandSeparator
      ? formatAmount(amountFen)
      : fenToYuan(amountFen);

    if (!showSign || !direction) {
      return amount;
    }

    return direction === 'IN' ? `+${amount}` : `-${amount}`;
  };

  const getColor = () => {
    if (!direction) return 'inherit';
    return direction === 'IN' ? '#52c41a' : '#ff4d4f';
  };

  return (
    <span
      className={`amount-display amount-display-${size} ${className}`}
      style={{ color: getColor() }}
    >
      ¥{getDisplayAmount()}
    </span>
  );
};

export default AmountDisplay;
