import React, { useState } from 'react';
import { Button, Toast, Selector } from 'antd-mobile';
import PasswordInput from '@/components/h5/PasswordInput';
import { ConfirmationMessage } from '../types';

interface Props {
  message: ConfirmationMessage;
  onConfirm: (draftId: string, payeeId: string, amountFen: number, password: string) => Promise<void>;
  onCancel: (draftId: string) => void;
}

const ConfirmationCard: React.FC<Props> = ({ message, onConfirm, onCancel }) => {
  const options = message.payeeOptions || [];
  const [selectedPayee, setSelectedPayee] = useState<string>(
    options.length > 0 ? options[0].id : ''
  );
  const [amountYuan, setAmountYuan] = useState(
    message.amountFen ? (message.amountFen / 100).toFixed(2) : ''
  );
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const amountFen = Math.round(parseFloat(amountYuan || '0') * 100);

  const handleConfirmClick = () => {
    if (!selectedPayee && options.length === 0) {
      Toast.show({ content: '请输入收款人' });
      return;
    }
    if (!amountFen || amountFen <= 0) {
      Toast.show({ content: '请输入有效金额' });
      return;
    }
    setShowPassword(true);
  };

  const handlePasswordChange = async (value: string) => {
    setPassword(value);
    if (value.length === 6) {
      setSubmitting(true);
      try {
        await onConfirm(message.draftId, selectedPayee, amountFen, value);
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
          {message.cardType === 'transfer' ? '转账确认' : '还款确认'}
        </div>

        {message.note && <div className="ai-confirmation-note">{message.note}</div>}

        {/* 收款人 */}
        <div className="ai-confirmation-field">
          <div className="ai-field-label">收款人</div>
          {options.length > 0 ? (
            <Selector
              options={options.map((o) => ({ label: o.label, value: o.id }))}
              value={selectedPayee ? [selectedPayee] : []}
              onChange={(vals) => setSelectedPayee(vals[0] || '')}
            />
          ) : (
            <input
              className="ai-field-input"
              placeholder="输入收款人姓名"
              value={selectedPayee}
              onChange={(e) => setSelectedPayee(e.target.value)}
            />
          )}
        </div>

        {/* 金额 */}
        <div className="ai-confirmation-field">
          <div className="ai-field-label">金额（元）</div>
          <input
            className="ai-field-input ai-field-amount"
            type="number"
            placeholder="0.00"
            value={amountYuan}
            onChange={(e) => setAmountYuan(e.target.value)}
          />
        </div>

        {message.status === 'pending' && !showPassword && (
          <div className="ai-confirmation-actions">
            <Button color="primary" size="middle" onClick={handleConfirmClick} loading={submitting}>
              确认转账
            </Button>
            <Button size="middle" onClick={() => onCancel(message.draftId)}>
              取消
            </Button>
          </div>
        )}

        {showPassword && (
          <div className="ai-password-input">
            <div className="ai-password-hint">请输入6位支付密码</div>
            <PasswordInput
              value={password}
              onChange={handlePasswordChange}
              length={6}
            />
          </div>
        )}

        {message.status === 'done' && (
          <div className="ai-confirmation-done">
            {message.status === 'done' && '已确认 ✓'}
          </div>
        )}
      </div>
    </div>
  );
};

export default ConfirmationCard;
