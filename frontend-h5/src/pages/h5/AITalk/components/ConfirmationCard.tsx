import React, { useState, useCallback, useMemo } from 'react';
import { Button, Toast } from 'antd-mobile';
import PasswordInput from '@/components/h5/PasswordInput';
import { ConfirmationMessage } from '../types';
import { updateDraft } from '@/services/transfer';

interface Props {
  message: ConfirmationMessage;
  onConfirm: (draftId: string, payeeId: string, amountFen: number, password: string, version?: number) => Promise<void>;
  onCancel: (draftId: string) => void;
}

/** 收款人选项（含防重名信息） */
interface PayeeOption {
  id: string;
  name: string;
  maskedPhone: string;
  phoneTail: string;
  /** 显示标签，含防重名后缀 */
  label: string;
}

const ConfirmationCard: React.FC<Props> = ({ message, onConfirm, onCancel }) => {
  const rawOptions = message.payeeOptions || [];

  // 构建收款人选项列表，含防重名处理
  const payeeOptions: PayeeOption[] = useMemo(() => {
    const nameCount = new Map<string, number>();
    const phoneCount = new Map<string, number>();
    for (const o of rawOptions) {
      const name = o.label.split(' (')[0] || o.label;
      const phone = o.maskedPhone || o.phoneTail || '';
      nameCount.set(name, (nameCount.get(name) || 0) + 1);
      phoneCount.set(phone, (phoneCount.get(phone) || 0) + 1);
    }
    return rawOptions.map((o) => {
      const name = o.label.split(' (')[0] || o.label;
      const phone = o.maskedPhone || o.phoneTail || '';
      // 防重名：同名超过1人时，标签显示手机号
      const needDisambiguate = (nameCount.get(name) || 0) > 1;
      const label = needDisambiguate && phone
        ? `${name} (${phone})`
        : name;
      return {
        id: o.id,
        name,
        maskedPhone: o.maskedPhone || '',
        phoneTail: o.phoneTail || '',
        label,
      };
    });
  }, [rawOptions]);

  // 默认选中第一个（AI 已根据用户输入预填）
  const [selectedPayeeId, setSelectedPayeeId] = useState(
    payeeOptions.length > 0 ? payeeOptions[0].id : ''
  );
  const [amountYuan, setAmountYuan] = useState(
    message.amountFen ? (message.amountFen / 100).toFixed(2) : ''
  );
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [updating, setUpdating] = useState(false);
  const [localVersion, setLocalVersion] = useState(message.version ?? 0);
  // 记录用户最后手动修改的字段，防止双向联动覆盖用户输入
  const [lastEditedField, setLastEditedField] = useState<'payee' | 'phone' | null>(null);

  const amountFen = Math.round(parseFloat(amountYuan || '0') * 100);

  // 当前选中收款人的手机号
  const selectedPayee = payeeOptions.find((o) => o.id === selectedPayeeId);
  const displayPhone = selectedPayee?.maskedPhone || selectedPayee?.phoneTail || '';

  /** 同步金额变更到后端草稿 */
  const syncAmountToDraft = useCallback(async (newAmountFen: number) => {
    if (!message.draftId || updating) return;
    setUpdating(true);
    try {
      const result = await updateDraft(message.draftId, {
        amountFen: newAmountFen,
        version: localVersion,
      }) as any;
      setLocalVersion(result.version);
    } catch (err: any) {
      Toast.show({ content: err?.message || '更新草稿失败' });
    } finally {
      setUpdating(false);
    }
  }, [message.draftId, localVersion, updating]);

  /** 金额失焦时同步 */
  const handleAmountBlur = () => {
    if (amountFen > 0 && amountFen !== message.amountFen) {
      syncAmountToDraft(amountFen);
    }
  };

  /** 选择收款人 → 自动填入对应手机号 */
  const handlePayeeSelect = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const newId = e.target.value;
    setSelectedPayeeId(newId);
    setLastEditedField('payee');
  };

  /** 选择手机号 → 自动填入对应收款人 */
  const handlePhoneSelect = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const phone = e.target.value;
    // 根据手机号找到对应收款人
    const matched = payeeOptions.find(
      (o) => o.maskedPhone === phone || o.phoneTail === phone
    );
    if (matched) {
      setSelectedPayeeId(matched.id);
      setLastEditedField('phone');
    }
  };

  const handleConfirmClick = () => {
    if (!selectedPayeeId) {
      Toast.show({ content: '请选择收款人' });
      return;
    }
    if (!amountFen || amountFen <= 0) {
      Toast.show({ content: '请输入有效金额' });
      return;
    }
    if (!password || password.length !== 6) {
      Toast.show({ content: '请输入6位支付密码' });
      return;
    }
    setSubmitting(true);
    onConfirm(message.draftId, selectedPayeeId, amountFen, password, localVersion)
      .catch(() => {})
      .finally(() => {
        setSubmitting(false);
        setPassword('');
      });
  };

  return (
    <div className="ai-confirmation-card-wrapper">
      <div className="ai-confirmation-card">
        <div className="ai-confirmation-title">
          {message.cardType === 'transfer' ? '转账确认' : '还款确认'}
        </div>

        {message.note && <div className="ai-confirmation-note">{message.note}</div>}

        {/* 收款人 */}
        <div className="ai-confirmation-field">
          <div className="ai-field-label">收款人</div>
          {payeeOptions.length > 1 ? (
            <select
              className="ai-field-select"
              value={selectedPayeeId}
              onChange={handlePayeeSelect}
              disabled={updating}
            >
              {payeeOptions.map((o) => (
                <option key={o.id} value={o.id}>
                  {o.label}
                </option>
              ))}
            </select>
          ) : payeeOptions.length === 1 ? (
            <div className="ai-field-value">{payeeOptions[0].label}</div>
          ) : (
            <input
              className="ai-field-input"
              placeholder="输入收款人姓名"
              value={selectedPayeeId}
              onChange={(e) => {
                setSelectedPayeeId(e.target.value);
                setLastEditedField('payee');
              }}
              disabled={updating}
            />
          )}
        </div>

        {/* 手机号 */}
        <div className="ai-confirmation-field">
          <div className="ai-field-label">手机号</div>
          {payeeOptions.length > 1 ? (
            <select
              className="ai-field-select"
              value={displayPhone}
              onChange={handlePhoneSelect}
              disabled={updating}
            >
              {payeeOptions.map((o) => {
                const phone = o.maskedPhone || o.phoneTail;
                return (
                  <option key={o.id} value={phone}>
                    {phone}
                  </option>
                );
              })}
            </select>
          ) : displayPhone ? (
            <div className="ai-field-value">{displayPhone}</div>
          ) : (
            <input
              className="ai-field-input"
              placeholder="输入手机号"
              value={displayPhone}
              onChange={(e) => {
                setLastEditedField('phone');
              }}
              disabled={updating}
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
            onBlur={handleAmountBlur}
            disabled={updating}
          />
        </div>

        {/* 支付密码 */}
        <div className="ai-confirmation-field">
          <div className="ai-field-label">支付密码</div>
          <PasswordInput
            value={password}
            onChange={setPassword}
            length={6}
          />
        </div>

        {message.status === 'pending' && (
          <div className="ai-confirmation-actions">
            <Button
              color="primary"
              size="middle"
              onClick={handleConfirmClick}
              loading={submitting || updating}
              disabled={!selectedPayeeId || !amountFen || amountFen <= 0 || password.length !== 6}
            >
              确认转账
            </Button>
            <Button size="middle" onClick={() => onCancel(message.draftId)} disabled={updating}>
              取消
            </Button>
          </div>
        )}

        {message.status === 'done' && (
          <div className="ai-confirmation-done">
            已确认 ✓
          </div>
        )}
      </div>
    </div>
  );
};

export default ConfirmationCard;
