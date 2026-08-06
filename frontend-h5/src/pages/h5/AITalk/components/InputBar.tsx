import React from 'react';
import { Input, Button } from 'antd-mobile';
import { SendOutline } from 'antd-mobile-icons';

interface Props {
  value: string;
  onChange: (v: string) => void;
  onSend: () => void;
  loading: boolean;
  disabled: boolean;
}

const InputBar: React.FC<Props> = ({ value, onChange, onSend, loading, disabled }) => (
  <div className="ai-input-bar">
    <Input
      className="ai-input"
      placeholder="输入消息..."
      value={value}
      onChange={onChange}
      onEnterPress={onSend}
    />
    <Button
      className="ai-send-btn"
      color="primary"
      fill="solid"
      onClick={onSend}
      loading={loading}
      disabled={disabled || !value.trim()}
    >
      <SendOutline />
    </Button>
  </div>
);

export default InputBar;
