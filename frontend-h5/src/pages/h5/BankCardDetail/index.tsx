import { history, useParams } from 'umi';
import { Dialog, Input, Popup, Toast } from 'antd-mobile';
import { useCallback, useEffect, useState } from 'react';
import {
  getBankCardDetail,
  setDefaultBankCard,
  unbindBankCard,
  getFullCardNumber,
  type BankCard,
} from '@/services/bankCard';
import { formatTime } from '@/utils/format';
import { BankCardFace, Skeleton, IconSet, type IconName } from '@/components/h5/common';
import './index.less';

/** 卡类型中文展示名。 */
const CARD_TYPE_LABEL: Record<string, string> = {
  DEBIT: '储蓄卡',
  CREDIT: '信用卡',
};

/**
 * 银行卡详情页：仿真卡面 + 操作按钮组（查看余额/查看完整卡号/查看账单），
 * 保留「设为默认卡」与「解除绑定」（二次确认）管理操作。
 *
 * 完整卡号安全约定：需输入支付密码签发一次性证明后由后端返回，
 * 明文仅存于当前组件内存，页面离开即销毁，不落日志与存储。
 */
const BankCardDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const [card, setCard] = useState<BankCard | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  // 卡内余额默认掩码；完整卡号按需拉取，未拉取为 null
  const [balanceRevealed, setBalanceRevealed] = useState(false);
  const [fullNumber, setFullNumber] = useState<string | null>(null);
  const [numberBusy, setNumberBusy] = useState(false);
  const [passwordPopupVisible, setPasswordPopupVisible] = useState(false);
  const [password, setPassword] = useState('');

  const loadDetail = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    try {
      const data = await getBankCardDetail(id);
      setCard(data);
    } catch (error: any) {
      Toast.show({ icon: 'fail', content: error?.message || '卡片不存在' });
      history.replace('/h5/bank-cards');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    loadDetail();
  }, [loadDetail]);

  const handleSetDefault = async () => {
    if (!card || busy) return;
    setBusy(true);
    try {
      const updated = await setDefaultBankCard(card.cardId);
      setCard(updated);
      Toast.show({ icon: 'success', content: '已设为默认卡' });
    } catch (error: any) {
      Toast.show({ icon: 'fail', content: error?.message || '设置失败，请重试' });
    } finally {
      setBusy(false);
    }
  };

  const handleUnbind = async () => {
    if (!card || busy) return;
    // 解绑会移除卡片并释放注册记录，二次确认防误操作；解绑后可凭完整卡号重新绑定
    const confirmed = await Dialog.confirm({
      title: '解除绑定',
      content: `确定解绑 ${card.bankName}（尾号 ${card.cardLast4}）吗？解绑后如需使用请重新绑定。`,
    });
    if (!confirmed) return;

    setBusy(true);
    try {
      await unbindBankCard(card.cardId);
      Toast.show({ icon: 'success', content: '解绑成功，可随时重新绑定该卡' });
      history.push('/h5/bank-cards');
    } catch (error: any) {
      Toast.show({ icon: 'fail', content: error?.message || '解绑失败，请重试' });
      setBusy(false);
    }
  };

  /** 查看完整卡号：弹出支付密码弹窗，签发证明后调后端受保护接口。 */
  const handleRequestFullNumber = async () => {
    if (!card || !id) return;
    if (!/^\d{6}$/.test(password)) {
      Toast.show({ icon: 'fail', content: '请输入6位数字支付密码' });
      return;
    }
    setNumberBusy(true);
    try {
      const number = await getFullCardNumber(id, password);
      setFullNumber(number);
      setPasswordPopupVisible(false);
      setPassword('');
    } catch (error: any) {
      Toast.show({ icon: 'fail', content: error?.message || '查看失败，请检查支付密码' });
    } finally {
      setNumberBusy(false);
    }
  };

  if (loading || !card) {
    return (
      <div className="bank-card-detail-page">
        <Skeleton variant="card" height={124} />
        <div style={{ marginTop: 14 }}>
          <Skeleton variant="list" rows={4} />
        </div>
      </div>
    );
  }

  // 操作入口行配置：图标 + 文案 + 行为
  const opRows: Array<{ icon: IconName; label: string; value?: string; onClick?: () => void }> = [
    {
      icon: balanceRevealed ? 'eyeOff' : 'eyeOn',
      label: balanceRevealed ? '隐藏余额' : '查看余额',
      onClick: () => setBalanceRevealed((v) => !v),
    },
    {
      icon: 'card',
      label: '完整卡号',
      value: fullNumber ? '已展示' : undefined,
      onClick: () => {
        if (fullNumber) return; // 已展示，无需重复请求
        setPassword('');
        setPasswordPopupVisible(true);
      },
    },
    {
      icon: 'receipt',
      label: '交易账单',
      value: '查看该卡收支',
      onClick: () => history.push(`/h5/bank-cards/${card.cardId}/bills`),
    },
    { icon: 'shield', label: '卡片安全', value: '已启用验签' },
  ];

  // 卡片信息行
  const infoRows: Array<[string, string]> = [
    ['卡类型', CARD_TYPE_LABEL[card.cardType] || card.cardType],
    ['持卡人', card.holderMasked],
    ['身份证号', card.idCardMasked],
    ['预留手机号', card.phoneMasked],
    ['绑定时间', formatTime(card.boundAt)],
  ];

  return (
    <div className="bank-card-detail-page">
      {/* 仿真卡面：余额随「查看余额」按钮切换掩码 */}
      <BankCardFace
        bankCode={card.bankCode}
        bankName={card.bankName}
        cardType={card.cardType}
        cardLast4={card.cardLast4}
        isDefault={card.isDefault}
        balanceFen={card.balanceFen}
        balanceRevealed={balanceRevealed}
      />

      {/* 完整卡号：仅本次会话可见，离开页面即销毁 */}
      {fullNumber && (
        <div className="full-number-card">
          <span className="full-number-label">完整卡号（仅本次可见）</span>
          <span className="full-number-value">
            {fullNumber.replace(/(\d{4})(?=\d)/g, '$1 ')}
          </span>
        </div>
      )}

      {/* 操作入口卡 */}
      <div className="bcd-card">
        {opRows.map((row, index) => (
          <div
            className="bcd-row"
            key={row.label}
            onClick={row.onClick}
            style={index === opRows.length - 1 ? { borderBottom: 'none' } : undefined}
          >
            <span className="bcd-row-icon">
              <IconSet name={row.icon} size={16} color="var(--h5-primary)" />
            </span>
            <span className="bcd-row-label">{row.label}</span>
            <span className="bcd-row-value">{row.value || ''}</span>
            {row.onClick && <IconSet name="chevronRight" size={14} color="var(--h5-text-3)" />}
          </div>
        ))}
      </div>

      {/* 卡片信息卡 */}
      <div className="bcd-card">
        <div className="bcd-section-title">卡片信息</div>
        {infoRows.map(([label, value]) => (
          <div className="info-row" key={label}>
            <span className="info-label">{label}</span>
            <span className="info-value">{value}</span>
          </div>
        ))}
      </div>

      <div className="detail-actions">
        {!card.isDefault && (
          <div
            className={`h5-btn-gradient set-default-btn${busy ? ' disabled' : ''}`}
            onClick={() => !busy && handleSetDefault()}
          >
            {busy ? '设置中...' : '设为默认卡'}
          </div>
        )}
        <div
          className={`unbind-btn${busy ? ' disabled' : ''}`}
          onClick={() => !busy && handleUnbind()}
        >
          解除绑定
        </div>
      </div>

      {/* 支付密码弹窗：仅用于签发 BANK_CARD_NUMBER_VIEW 用途的一次性证明 */}
      <Popup
        visible={passwordPopupVisible}
        onMaskClick={() => !numberBusy && setPasswordPopupVisible(false)}
        bodyStyle={{ borderTopLeftRadius: '18px', borderTopRightRadius: '18px', padding: '20px 16px 24px' }}
      >
        <div className="password-popup">
          <div className="popup-handle" />
          <div className="popup-title">查看完整卡号</div>
          <div className="popup-hint">请输入支付密码以验证身份</div>
          <Input
            type="password"
            maxLength={6}
            placeholder="请输入6位数字支付密码"
            value={password}
            onChange={setPassword}
            className="password-field"
            autoFocus
          />
          <div
            className={`h5-btn-gradient popup-confirm${numberBusy || password.length !== 6 ? ' disabled' : ''}`}
            onClick={() => !numberBusy && password.length === 6 && handleRequestFullNumber()}
          >
            {numberBusy ? '验证中...' : '确认查看'}
          </div>
          <div
            className="popup-cancel"
            onClick={() => !numberBusy && setPasswordPopupVisible(false)}
          >
            取消
          </div>
        </div>
      </Popup>
    </div>
  );
};

export default BankCardDetailPage;
