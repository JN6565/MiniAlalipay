import React, { useState, useCallback, useEffect, useMemo, useRef } from 'react';
import { Button, Toast } from 'antd-mobile';
import PasswordInput from '@/components/h5/PasswordInput';
import { ConfirmationMessage } from '../types';
import { getMyAccount } from '@/services/account';
import { updateDraft, validateDraft } from '@/services/transfer';
import {
  MAX_TRANSFER_AMOUNT_FEN,
  getTransferAmountError,
  parseYuanToFen,
} from '../utils/transferAmountValidation';

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
  const syncedAmountFenRef = useRef(message.amountFen ?? 0);
  const amountSyncPromiseRef = useRef<Promise<number | null> | null>(null);
  const [availableBalanceFen, setAvailableBalanceFen] = useState<number | null>(null);
  const [balanceLoading, setBalanceLoading] = useState(true);
  const [balanceLoadFailed, setBalanceLoadFailed] = useState(false);
  // 记录用户最后手动修改的字段，防止双向联动覆盖用户输入
  const [lastEditedField, setLastEditedField] = useState<'payee' | 'phone' | null>(null);

  const parsedAmountFen = parseYuanToFen(amountYuan);
  const amountFen = parsedAmountFen ?? 0;
  const isTransfer = message.cardType === 'transfer';
  const amountError = isTransfer
    ? getTransferAmountError(parsedAmountFen, availableBalanceFen)
    : (parsedAmountFen == null || parsedAmountFen <= 0 ? '请输入有效金额' : null);

  /** 实时查询本人可用余额；确认前会再次回源，避免使用过期页面状态。 */
  const loadAvailableBalance = useCallback(async () => {
    setBalanceLoading(true);
    setBalanceLoadFailed(false);
    try {
      const account = await getMyAccount() as any;
      const availableFen = Number(account.availableFen);
      if (!Number.isSafeInteger(availableFen) || availableFen < 0) {
        throw new Error('余额数据格式无效');
      }
      setAvailableBalanceFen(availableFen);
      return availableFen;
    } catch (error) {
      setAvailableBalanceFen(null);
      setBalanceLoadFailed(true);
      throw error;
    } finally {
      setBalanceLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!isTransfer) {
      setBalanceLoading(false);
      return;
    }
    loadAvailableBalance().catch(() => {
      // 卡片内展示失败状态，用户可主动重试。
    });
  }, [isTransfer, loadAvailableBalance]);

  // 当前选中收款人的手机号
  const selectedPayee = payeeOptions.find((o) => o.id === selectedPayeeId);
  const displayPhone = selectedPayee?.maskedPhone || selectedPayee?.phoneTail || '';

  /** 同步金额变更到后端草稿 */
  const syncAmountToDraft = useCallback((newAmountFen: number): Promise<number | null> => {
    if (!message.draftId) return Promise.resolve(null);
    if (amountSyncPromiseRef.current) return amountSyncPromiseRef.current;

    setUpdating(true);
    const syncPromise = (async () => {
      try {
        const updatedDraft = await updateDraft(message.draftId, {
          amountFen: newAmountFen,
          version: localVersion,
        }) as any;
        // 修改金额会使原校验结果失效，必须重新完成服务端余额、限额和风控预检。
        const validation = await validateDraft(message.draftId, updatedDraft.version) as any;
        setLocalVersion(validation.version);
        syncedAmountFenRef.current = newAmountFen;
        return validation.version as number;
      } catch (err: any) {
        Toast.show({ content: err?.message || '更新草稿失败' });
        return null;
      } finally {
        setUpdating(false);
      }
    })();
    amountSyncPromiseRef.current = syncPromise;
    syncPromise.finally(() => {
      if (amountSyncPromiseRef.current === syncPromise) {
        amountSyncPromiseRef.current = null;
      }
    });
    return syncPromise;
  }, [message.draftId, localVersion]);

  /** 金额失焦时同步 */
  const handleAmountBlur = () => {
    if (!amountError && amountFen !== syncedAmountFenRef.current && !updating) {
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

  const handleConfirmClick = async () => {
    if (!selectedPayeeId) {
      Toast.show({ content: '请选择收款人' });
      return;
    }
    if (amountError) {
      Toast.show({ content: amountError });
      return;
    }
    if (!password || password.length !== 6) {
      Toast.show({ content: '请输入6位支付密码' });
      return;
    }
    setSubmitting(true);
    try {
      if (isTransfer) {
        let latestBalanceFen: number;
        try {
          latestBalanceFen = await loadAvailableBalance();
        } catch {
          Toast.show({ content: '余额查询失败，暂不能确认转账' });
          return;
        }
        const latestAmountError = getTransferAmountError(amountFen, latestBalanceFen);
        if (latestAmountError) {
          Toast.show({ content: latestAmountError });
          return;
        }
      }

      let versionToConfirm = localVersion;
      if (amountSyncPromiseRef.current) {
        const syncedVersion = await amountSyncPromiseRef.current;
        if (syncedVersion == null) return;
        versionToConfirm = syncedVersion;
      }
      if (amountFen !== syncedAmountFenRef.current) {
        const nextVersion = await syncAmountToDraft(amountFen);
        if (nextVersion == null) return;
        versionToConfirm = nextVersion;
      }
      await onConfirm(message.draftId, selectedPayeeId, amountFen, password, versionToConfirm);
    } finally {
      setSubmitting(false);
      setPassword('');
    }
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
            min="0.01"
            max={isTransfer ? MAX_TRANSFER_AMOUNT_FEN / 100 : undefined}
            step="0.01"
            inputMode="decimal"
            placeholder="0.00"
            value={amountYuan}
            onChange={(e) => setAmountYuan(e.target.value)}
            onBlur={handleAmountBlur}
            disabled={updating}
          />
          {isTransfer && (
            <div className="ai-field-balance">
              {balanceLoading && '正在查询可用余额'}
              {!balanceLoading && balanceLoadFailed && (
                <button type="button" onClick={() => loadAvailableBalance().catch(() => {})}>
                  余额查询失败，重新查询
                </button>
              )}
              {!balanceLoading && availableBalanceFen != null && (
                <>可用余额 ¥{(availableBalanceFen / 100).toLocaleString('zh-CN', {
                  minimumFractionDigits: 2,
                  maximumFractionDigits: 2,
                })}</>
              )}
            </div>
          )}
          {amountError && <div className="ai-field-error">{amountError}</div>}
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
              loading={submitting || updating || balanceLoading}
              disabled={
                !selectedPayeeId
                || !!amountError
                || (isTransfer && (balanceLoadFailed || balanceLoading))
                || password.length !== 6
              }
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
            已确认
          </div>
        )}
      </div>
    </div>
  );
};

export default ConfirmationCard;
