import React, { useState } from 'react';
import { Input } from 'antd-mobile';
import './index.less';

interface AmountInputProps {
  value?: number;
  onChange?: (value: number) => void;
  min?: number;
  max?: number;
  placeholder?: string;
  disabled?: boolean;
  label?: string;
}

export const AmountInput: React.FC<AmountInputProps> = ({
  value,
  onChange,
  min = 0.01,
  max = 50000,
  placeholder = '请输入金额',
  disabled = false,
  label,
}) => {
  const [error, setError] = useState<string | null>(null);

  const handleChange = (val: string) => {
    // 只允许数字和小数点
    const cleaned = val.replace(/[^\d.]/g, '');

    // 防止多个小数点
    const parts = cleaned.split('.');
    if (parts.length > 2) return;

    // 限制两位小数
    if (parts[1] && parts[1].length > 2) return;

    const numVal = parseFloat(cleaned);

    if (isNaN(numVal)) {
      onChange?.(0);
      setError(null);
      return;
    }

    if (numVal < min) {
      setError(`金额不能小于${min}元`);
    } else if (numVal > max) {
      setError(`单笔金额不能超过${max}元`);
    } else {
      setError(null);
    }

    onChange?.(numVal);
  };

  return (
    <div className="amount-input-wrapper">
      {label && <div className="amount-input-label">{label}</div>}
      <div className="amount-input-container">
        <span className="amount-prefix">¥</span>
        <Input
          type="text"
          inputMode="decimal"
          value={value?.toString() || ''}
          onChange={handleChange}
          placeholder={placeholder}
          disabled={disabled}
          className="amount-input"
        />
      </div>
      {error && <span className="amount-error">{error}</span>}
    </div>
  );
};

export default AmountInput;
