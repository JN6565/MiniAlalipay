import { history, useParams } from 'umi';
import { Input, Popup, Toast } from 'antd-mobile';
import { useCallback, useEffect, useState } from 'react';
import { getBankCards, withdrawBankCard, formatBalance, type BankCard } from '@/services/bankCard';
import { getMyAccount, type AccountInfo } from '@/services/account';
import { confirmBalanceChange } from '@/utils/balanceConfirm';
import { AmountInput } from '@/components/h5/AmountInput';
import { Skeleton, EmptyState, IconSet } from '@/components/h5/common';
import FundResultScreen from '@/components/h5/FundResultScreen';
import './index.less';

/**
 * 提现页（V2.1）：提现是针对账户余额的动作——账户余额减少，钱转到银行卡。
 *
 * 页面内三态与充值对称（form / processing / result）：
 * - form：输入金额 → 确认提现 → 底部 Popup 选到账银行卡 → 支付密码；
 * - processing：接口受理成功后轮询确认余额扣减（后端为异步 TCC，接口返回不代表已出账）；
 * - result：确认成功后展示最新余额；超时未确认展示受理提示与手动刷新入口。
 * 返回钱包时携带 balanceDirty 标记，钱包页静默补拉确保余额新鲜。
 * 路由参数 :id 可选（旧入口兼容）：存在时作为默认选中卡。
 * 单笔 0.01-50000.00 元，不超过账户可用余额。
 */
const BankCardWithdrawPage = () => {
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
  // 页面内三态：form 表单 → processing 出账确认中 → result 结果展示
  const [stage, setStage] = useState<'form' | 'processing' | 'result'>('form');
  const [resultConfirmed, setResultConfirmed] = useState(false);
  const [resultBalanceFen, setResultBalanceFen] = useState<number | null>(null);
  // 操作前余额基准，用于轮询判断余额是否已按预期方向变化
  const [beforeBalanceFen, setBeforeBalanceFen] = useState(0);
  const [submittedAmountFen, setSubmittedAmountFen] = useState(0);

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
  const accountBalanceYuan = account ? (account.availableFen || 0) / 100 : 0;
  const balanceInsufficient = !loadingData && accountBalanceYuan < 0.01;
  const amountValid = amount >= 0.01 && amount <= 50000 && amount <= accountBalanceYuan;

  /** 点击确认提现：校验金额后弹出底部选卡 Popup。 */
  const handleOpenSelect = () => {
    if (amount < 0.01 || amount > 50000) {
      Toast.show({ icon: 'fail', content: '提现金额范围 0.01-50000.00 元' });
      return;
    }
    if (amount > accountBalanceYuan) {
      Toast.show({ icon: 'fail', content: '提现金额不能超过账户可用余额' });
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

  /** 轮询确认余额扣减：确认后切 result 态并同步页头余额展示；beforeFen 显式传入避免闭包读到旧 state。 */
  const waitForBalanceConfirm = async (beforeFen: number) => {
    setStage('processing');
    const { confirmed, newBalanceFen } = await confirmBalanceChange(beforeFen, 'OUT');
    setResultConfirmed(confirmed);
    setResultBalanceFen(newBalanceFen);
    // 确认成功后同步页头余额，避免顶部仍显示旧值
    if (newBalanceFen !== null) {
      setAccount((prev) => (prev ? { ...prev, availableFen: newBalanceFen } : prev));
    }
    setStage('result');
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
      const amountFen = Math.round(amount * 100);
      await withdrawBankCard(selectedCard.cardId, {
        amountFen,
        paymentPassword: password,
      });
      setPasswordVisible(false);
      // 受理成功：记录操作前余额基准后进入出账确认轮询，不再直接跳回钱包（避免看到旧余额）
      const beforeFen = account?.availableFen || 0;
      setBeforeBalanceFen(beforeFen);
      setSubmittedAmountFen(amountFen);
      setLoading(false);
      await waitForBalanceConfirm(beforeFen);
    } catch (error: any) {
      setLoading(false);
      Toast.show({ icon: 'fail', content: error?.message || '提现失败，请重试' });
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

  // 处理中 / 结果态：保留页头余额区，下方展示结果屏
  if (stage !== 'form') {
    return (
      <div className="bank-card-operate-page">
        <div className="operate-hero">
          <div className="hero-label">从账户余额提现</div>
          <div className="hero-num">¥ {formatBalance(account?.availableFen || 0)}</div>
          <div className="hero-hint">当前账户可用余额（可提现上限）</div>
        </div>
        <FundResultScreen
          stage={stage}
          actionLabel="提现"
          amountFen={submittedAmountFen}
          direction="OUT"
          confirmed={resultConfirmed}
          newBalanceFen={resultBalanceFen}
          onRefresh={() => { waitForBalanceConfirm(beforeBalanceFen); }}
          onBackHome={() => history.push('/h5/home')}
          onBackWallet={() => history.replace('/h5/wallet', { balanceDirty: true })}
        />
      </div>
    );
  }

  return (
    <div className="bank-card-operate-page">
      {/* 顶部品牌区：提现来源为账户余额 */}
      <div className="operate-hero">
        <div className="hero-label">从账户余额提现</div>
        <div className="hero-num">¥ {formatBalance(account?.availableFen || 0)}</div>
        <div className="hero-hint">当前账户可用余额（可提现上限）</div>
      </div>

      {cards.length === 0 ? (
        <EmptyState
          icon={<IconSet name="card" size={30} color="var(--h5-primary)" />}
          text="还没有绑定银行卡"
          hint="绑定银行卡后才能发起提现"
          actionText="去绑卡"
          onAction={() => history.push('/h5/bank-cards')}
        />
      ) : balanceInsufficient ? (
        <div className="balance-insufficient">
          <div className="insufficient-icon">
            <IconSet name="wallet" size={24} />
          </div>
          <p className="insufficient-title">账户余额不足</p>
          <p className="insufficient-hint">账户可用余额不足 0.01 元，无法发起提现</p>
        </div>
      ) : (
        <>
          {/* 金额输入卡 */}
          <div className="operate-amount-card">
            <div className="amount-label">提现金额</div>
            <div className="amount-input-wrapper">
              <AmountInput value={amount} onChange={setAmount} placeholder="0.00" />
            </div>
            <div className="amount-divider" />
            <div className="amount-hint">
              {amount > 0 && (amount < 0.01 || amount > 50000)
                ? '金额范围 0.01-50000.00 元'
                : amount > accountBalanceYuan && accountBalanceYuan > 0
                  ? '超过账户可用余额'
                  : '单笔 0.01-50000.00 元'}
            </div>
            {accountBalanceYuan > 0 && (
              <div className="operate-form-extra">
                <span
                  className="form-hint-link"
                  onClick={() => setAmount(Math.floor(accountBalanceYuan * 100) / 100)}
                >
                  全部提现
                </span>
              </div>
            )}
          </div>

          {/* 确认提现：打开底部 Popup 选卡 */}
          <div
            className={`h5-btn-gradient operate-submit${amountValid ? '' : ' disabled'}`}
            onClick={handleOpenSelect}
          >
            确认提现
          </div>
        </>
      )}

      {/* 底部 Popup：选择到账银行卡（替代下拉框） */}
      <Popup
        visible={selectVisible}
        onMaskClick={() => setSelectVisible(false)}
        bodyStyle={{ borderTopLeftRadius: '18px', borderTopRightRadius: '18px', padding: '14px 14px 20px' }}
      >
        <div className="card-select-popup">
          <div className="popup-handle" />
          <div className="popup-title">选择到账银行卡</div>
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
            确认提现 ¥{amount.toFixed(2)}
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
              <span className="summary-label">提现来源</span>
              <span className="summary-value">账户余额</span>
            </div>
            <div className="summary-row">
              <span className="summary-label">到账银行卡</span>
              <span className="summary-value">
                {selectedCard ? `${selectedCard.bankName}（尾号 ${selectedCard.cardLast4}）` : '-'}
              </span>
            </div>
            <div className="summary-row">
              <span className="summary-label">提现金额</span>
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
              {loading ? '提现中...' : '确认提现'}
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

export default BankCardWithdrawPage;
