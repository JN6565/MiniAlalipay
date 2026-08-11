import { history, useParams } from 'umi';
import { Input, Popup, Toast } from 'antd-mobile';
import { useCallback, useEffect, useState } from 'react';
import { getBankCards, rechargeBankCard, formatBalance, type BankCard } from '@/services/bankCard';
import { getMyAccount, type AccountInfo } from '@/services/account';
import { AmountInput } from '@/components/h5/AmountInput';
import { Skeleton, EmptyState, IconSet } from '@/components/h5/common';
import './index.less';

/**
 * 充值页（V2）：充值是针对账户余额的动作——银行卡付钱，账户余额增加。
 *
 * 流程按设计稿：输入金额 → 确认充值 → 底部 Popup 选银行卡 → 支付密码 →
 * 成功后返回钱包页（/h5/wallet），钱包页重拉即展示最新余额。
 * 路由参数 :id 可选（旧入口兼容）：存在时作为默认选中卡。
 * 单笔 0.01-50000.00 元；银行卡余额是否充足由服务端校验并返回明确错误。
 */
const BankCardRechargePage = () => {
  const { id } = useParams<{ id?: string }>();
  const [cards, setCards] = useState<BankCard[]>([]);
  const [account, setAccount] = useState<AccountInfo | null>(null);
  const [selectedId, setSelectedId] = useState<string>('');
  const [amount, setAmount] = useState(0);
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [loadingData, setLoadingData] = useState(true);
  const [selectVisible, setSelectVisible] = useState(false);
  const [passwordVisible, setPasswordVisible] = useState(false);

  const loadData = useCallback(async () => {
    try {
      const [cardsResp, accountResp] = await Promise.all([getBankCards(), getMyAccount()]);
      const list = cardsResp || [];
      setCards(list);
      // 请求拦截器已拆包 ApiResponse，运行时返回值为业务数据而非 AxiosResponse
      setAccount(accountResp as unknown as AccountInfo);
      // 默认选中：路由指定卡 > 默认卡 > 第一张卡
      const preset = list.find((c) => c.cardId === id)
        || list.find((c) => c.isDefault)
        || list[0];
      if (preset) setSelectedId(preset.cardId);
    } catch (error: any) {
      Toast.show({ icon: 'fail', content: error?.message || '加载失败' });
    } finally {
      setLoadingData(false);
    }
  }, [id]);

  useEffect(() => { loadData(); }, [loadData]);

  const selectedCard = cards.find((c) => c.cardId === selectedId) || null;
  const amountValid = amount >= 0.01 && amount <= 50000;

  /** 点击确认充值：校验金额后弹出底部选卡 Popup。 */
  const handleOpenSelect = () => {
    if (!amountValid) {
      Toast.show({ icon: 'fail', content: '充值金额范围 0.01-50000.00 元' });
      return;
    }
    if (!selectedCard) {
      Toast.show({ icon: 'fail', content: '请先绑定银行卡' });
      return;
    }
    setSelectVisible(true);
  };

  /** 选卡 Popup 中确认：进入支付密码环节。 */
  const handleSelectConfirm = () => {
    setSelectVisible(false);
    setPassword('');
    setPasswordVisible(true);
  };

  /** 密码弹窗中确认提交。 */
  const handleSubmit = async () => {
    if (!selectedCard) return;
    if (!/^\d{6}$/.test(password)) {
      Toast.show({ icon: 'fail', content: '请输入6位数字支付密码' });
      return;
    }

    setLoading(true);
    try {
      await rechargeBankCard(selectedCard.cardId, {
        amountFen: Math.round(amount * 100),
        paymentPassword: password,
      });
      setPasswordVisible(false);
      Toast.show({ icon: 'success', content: '充值成功' });
      // 返回钱包页重拉账户余额
      history.replace('/h5/wallet');
    } catch (error: any) {
      Toast.show({ icon: 'fail', content: error?.message || '充值失败，请重试' });
    } finally {
      setLoading(false);
    }
  };

  if (loadingData) {
    return (
      <div className="bank-card-operate-page">
        <Skeleton variant="card" height={96} />
        <div style={{ marginTop: 12 }}>
          <Skeleton variant="card" height={110} />
        </div>
      </div>
    );
  }

  return (
    <div className="bank-card-operate-page">
      {/* 顶部品牌区：充值目标为账户余额 */}
      <div className="operate-hero">
        <div className="hero-label">充值到账户余额</div>
        <div className="hero-num">¥ {formatBalance(account?.availableFen || 0)}</div>
        <div className="hero-hint">当前账户可用余额</div>
      </div>

      {cards.length === 0 ? (
        <EmptyState
          icon={<IconSet name="card" size={30} color="var(--h5-primary)" />}
          text="还没有绑定银行卡"
          hint="绑定银行卡后才能发起充值"
          actionText="去绑卡"
          onAction={() => history.push('/h5/bank-cards')}
        />
      ) : (
        <>
          {/* 金额输入卡 */}
          <div className="operate-amount-card">
            <div className="amount-label">充值金额</div>
            <div className="amount-input-wrapper">
              <AmountInput value={amount} onChange={setAmount} placeholder="0.00" />
            </div>
            <div className="amount-divider" />
            <div className="amount-hint">
              {amount > 0 && !amountValid ? '金额范围 0.01-50000.00 元' : '单笔 0.01-50000.00 元'}
            </div>
          </div>

          {/* 确认充值：打开底部 Popup 选卡 */}
          <div
            className={`h5-btn-gradient operate-submit${amountValid ? '' : ' disabled'}`}
            onClick={handleOpenSelect}
          >
            确认充值
          </div>
        </>
      )}

      {/* 底部 Popup：选择充值银行卡（替代下拉框） */}
      <Popup
        visible={selectVisible}
        onMaskClick={() => setSelectVisible(false)}
        bodyStyle={{ borderTopLeftRadius: '18px', borderTopRightRadius: '18px', padding: '14px 14px 20px' }}
      >
        <div className="card-select-popup">
          <div className="popup-handle" />
          <div className="popup-title">选择充值银行卡</div>
          {cards.map((card) => {
            const on = card.cardId === selectedId;
            return (
              <div
                key={card.cardId}
                className={`card-select-row${on ? ' active' : ''}`}
                onClick={() => setSelectedId(card.cardId)}
              >
                <span className={`card-select-logo${on ? ' active' : ''}`}>
                  {on ? <IconSet name="check" size={14} color="#fff" /> : card.bankName.slice(0, 1)}
                </span>
                <span className="card-select-name">
                  {card.bankName} · {card.cardType === 'DEBIT' ? '储蓄卡' : '信用卡'}（{card.cardLast4}）
                </span>
                {on ? (
                  <IconSet name="check" size={16} color="var(--h5-primary)" />
                ) : (
                  <span className="card-select-radio" />
                )}
              </div>
            );
          })}
          <div className="h5-btn-gradient card-select-confirm" onClick={handleSelectConfirm}>
            确认充值 ¥{amount.toFixed(2)}
          </div>
        </div>
      </Popup>

      {/* 支付密码弹窗 */}
      <Popup
        visible={passwordVisible}
        onMaskClick={() => !loading && setPasswordVisible(false)}
        bodyStyle={{ borderTopLeftRadius: '18px', borderTopRightRadius: '18px', padding: '20px 16px 24px' }}
      >
        <div className="password-popup">
          <div className="popup-title">请输入支付密码</div>
          <div className="popup-summary">
            <div className="summary-row">
              <span className="summary-label">充值到</span>
              <span className="summary-value">账户余额</span>
            </div>
            <div className="summary-row">
              <span className="summary-label">付款方式</span>
              <span className="summary-value">
                {selectedCard ? `${selectedCard.bankName}（尾号 ${selectedCard.cardLast4}）` : '-'}
              </span>
            </div>
            <div className="summary-row">
              <span className="summary-label">充值金额</span>
              <span className="summary-value amount">¥ {amount.toFixed(2)}</span>
            </div>
          </div>
          <div className="popup-password">
            <Input
              type="password"
              maxLength={6}
              placeholder="请输入6位数字支付密码"
              value={password}
              onChange={setPassword}
              className="password-field"
              autoFocus
            />
          </div>
          <div className="popup-actions">
            <div
              className={`h5-btn-gradient${loading || password.length !== 6 ? ' disabled' : ''}`}
              onClick={handleSubmit}
            >
              {loading ? '充值中...' : '确认充值'}
            </div>
            <div
              className="operate-cancel-btn"
              onClick={() => !loading && setPasswordVisible(false)}
            >
              取消
            </div>
          </div>
        </div>
      </Popup>
    </div>
  );
};

export default BankCardRechargePage;
