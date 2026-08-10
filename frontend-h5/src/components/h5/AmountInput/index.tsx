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
  // 内部字符串状态，保留用户输入的中间状态（如 "3."）
  const [inputStr, setInputStr] = useState<string>(value?.toString() || '');

  // 当外部 value 变化时同步（如预设金额选择）
  React.useEffect(() => {
    const newStr = value?.toString() || '';
    // 只在数值实际变化时同步，避免覆盖用户正在输入的内容
    const currentNum = parseFloat(inputStr);
    if (value !== currentNum) {
      setInputStr(newStr);
    }
  }, [value]);

  const handleChange = (val: string) => {
    // 只允许数字和小数点
    const cleaned = val.replace(/[^\d.]/g, '');

    // 防止多个小数点
    const parts = cleaned.split('.');
    if (parts.length > 2) return;

    // 限制两位小数
    if (parts[1] && parts[1].length > 2) return;

    // 处理前导零：保留 "0" 和 "0.x" 的情况，其他情况去掉前导零
    let formatted = cleaned;
    if (cleaned.length > 1 && cleaned[0] === '0' && cleaned[1] !== '.') {
      formatted = cleaned.replace(/^0+/, '') || '0';
    }

    // 更新显示的字符串
    setInputStr(formatted);

    const numVal = parseFloat(formatted);

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
          value={inputStr}
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
