import React, { useEffect, useState } from 'react';
import { Input } from 'antd-mobile';
import { normalizeAmountInput } from '@/utils/amountInput';
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
  // 使用字符串保存输入中间态，避免用户输入末尾小数点时被数值转换抹掉。
  const [inputValue, setInputValue] = useState(value ? String(value) : '');

  useEffect(() => {
    setInputValue((current) => {
      const currentNumber = Number.parseFloat(current);
      if (current && currentNumber === value) return current;
      return value ? String(value) : '';
    });
  }, [value]);

  const handleChange = (val: string) => {
    const normalized = normalizeAmountInput(val);
    setInputValue(normalized);
    const numVal = Number.parseFloat(normalized);

    if (Number.isNaN(numVal)) {
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
          value={inputValue}
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
