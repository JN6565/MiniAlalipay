import React, { useState } from 'react';
import { Button, Toast } from 'antd-mobile';
import PasswordInput from '@/components/h5/PasswordInput';
import { ConfirmationMessage } from '../types';

interface Props {
  message: ConfirmationMessage;
  onConfirm: (draftId: string, password: string) => Promise<void>;
  onCancel: (draftId: string) => void;
}

const ConfirmationCard: React.FC<Props> = ({ message, onConfirm, onCancel }) => {
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const handleConfirmClick = () => setShowPassword(true);

  const handlePasswordChange = async (value: string) => {
    setPassword(value);
    if (value.length === 6) {
      setSubmitting(true);
      try {
        await onConfirm(message.draftId, value);
      } finally {
        setSubmitting(false);
        setPassword('');
        setShowPassword(false);
      }
    }
  };

  return (
    <div className="ai-message ai-message-assistant">
      <div className="ai-message-content ai-confirmation-card">
        <div className="ai-confirmation-title">
          ⚠️ {message.cardType === 'transfer' ? '请确认转账' : '请确认还款'}
        </div>
        <div className="ai-confirmation-summary">{message.summary}</div>
        {message.amountFen != null && (
          <div className="ai-confirmation-amount">
            金额：{(message.amountFen / 100).toFixed(2)} 元
          </div>
        )}
        {message.status === 'pending' && !showPassword && (
          <div className="ai-confirmation-actions">
            <Button color="danger" size="small" onClick={handleConfirmClick}>
              确认
            </Button>
            <Button size="small" onClick={() => onCancel(message.draftId)}>
              取消
            </Button>
          </div>
        )}
        {showPassword && (
          <div className="ai-password-input">
            <PasswordInput
              value={password}
              onChange={handlePasswordChange}
              length={6}
            />
          </div>
        )}
        {message.status === 'done' && (
          <div className="ai-confirmation-done">已确认 ✓</div>
        )}
      </div>
    </div>
  );
};

export default ConfirmationCard;
