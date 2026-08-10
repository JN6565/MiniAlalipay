import React, { useEffect, useState } from 'react';
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

/** 金额格式：整数部分任意位，小数部分最多两位（允许空串与中间态如 "11."）。 */
const AMOUNT_PATTERN = /^(\d+(\.\d{0,2})?)?$/;

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
  // 输入框显示值用字符串承载：若直接用 number 作受控值，
  // 输入 "11." 会被 parseFloat 归约为 11 并在重渲染时吞掉小数点，导致无法输入小数。
  const [text, setText] = useState(value ? String(value) : '');

  // 外部 value 变化（如“全部充值”一键填充）且与当前文本解析值不一致时，同步显示文本。
  useEffect(() => {
    const parsed = text === '' ? 0 : parseFloat(text);
    const expected = value ?? 0;
    if ((isNaN(parsed) ? 0 : parsed) !== expected) {
      setText(expected ? String(expected) : '');
    }
  }, [value]);

  const handleChange = (val: string) => {
    // 只允许数字和小数点
    const cleaned = val.replace(/[^\d.]/g, '');

    // 格式不合法（多个小数点、小数超过两位等）时拒绝本次输入，保持旧文本
    if (!AMOUNT_PATTERN.test(cleaned)) return;

    setText(cleaned);

    const numVal = cleaned === '' ? NaN : parseFloat(cleaned);

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
          value={text}
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
