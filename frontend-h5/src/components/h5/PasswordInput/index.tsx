import React, { useState, useRef } from 'react';
import './index.less';

interface PasswordInputProps {
  value?: string;
  onChange?: (value: string) => void;
  length?: number;
  disabled?: boolean;
}

export const PasswordInput: React.FC<PasswordInputProps> = ({
  value = '',
  onChange,
  length = 6,
  disabled = false,
}) => {
  const inputRef = useRef<HTMLInputElement>(null);
  const [focused, setFocused] = useState(false);

  const handleClick = () => {
    if (!disabled) {
      inputRef.current?.focus();
    }
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newValue = e.target.value.replace(/\D/g, '').slice(0, length);
    onChange?.(newValue);
  };

  const handleFocus = () => {
    setFocused(true);
  };

  const handleBlur = () => {
    setFocused(false);
  };

  return (
    <div className="password-input-wrapper" onClick={handleClick}>
      <input
        ref={inputRef}
        type="number"
        className="password-input-hidden"
        value={value}
        onChange={handleChange}
        onFocus={handleFocus}
        onBlur={handleBlur}
        maxLength={length}
        disabled={disabled}
        autoFocus
      />
      <div className="password-input-boxes">
        {Array.from({ length }).map((_, index) => (
          <div
            key={index}
            className={`password-input-box ${
              focused && index === value.length ? 'active' : ''
            } ${index < value.length ? 'filled' : ''}`}
          >
            {index < value.length ? '●' : ''}
          </div>
        ))}
      </div>
    </div>
  );
};

export default PasswordInput;
