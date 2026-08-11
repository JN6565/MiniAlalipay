import { history, useParams } from 'umi';
import { Button, Input, Popup, Toast } from 'antd-mobile';
import { useCallback, useEffect, useState } from 'react';
import { getBankCardDetail, rechargeBankCard, formatBalance, type BankCard } from '@/services/bankCard';
import { getMyAccount, type AccountInfo } from '@/services/account';
import { AmountInput } from '@/components/h5/AmountInput';
import './index.less';

/**
 * 充值页：充值是针对账户余额的动作——银行卡付钱，账户余额增加。
 *
 * 页面以账户余额为中心：展示当前账户可用余额，银行卡仅作为付款来源
 * （不展示卡内余额）；输入金额后弹出密码弹窗确认，调用后端 TCC 充值接口。
 * 成功后返回充值提现页（/h5/wallet），由其重拉并展示最新账户余额。
 * 单笔 0.01-50000.00 元；银行卡余额是否充足由服务端校验并返回明确错误。
 */
const BankCardRechargePage = () => {
  const { id } = useParams<{ id: string }>();
  const [card, setCard] = useState<BankCard | null>(null);
  const [account, setAccount] = useState<AccountInfo | null>(null);
  const [amount, setAmount] = useState(0);
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [loadingData, setLoadingData] = useState(true);
  const [popupVisible, setPopupVisible] = useState(false);

  const loadData = useCallback(async () => {
    if (!id) return;
    try {
      const [cardData, accountResp] = await Promise.all([
        getBankCardDetail(id),
        getMyAccount(),
      ]);
      setCard(cardData);
      // 请求拦截器已拆包 ApiResponse，运行时返回值为业务数据而非 AxiosResponse
      setAccount(accountResp as unknown as AccountInfo);
    } catch {
      Toast.show({ icon: 'fail', content: '卡片不存在' });
      history.replace('/h5/wallet');
    } finally {
      setLoadingData(false);
    }
  }, [id]);

  useEffect(() => { loadData(); }, [loadData]);

  /** 点击确认充值：校验金额后弹出密码弹窗。 */
  const handleConfirmAmount = () => {
    if (!card || !id) return;
    if (amount < 0.01 || amount > 50000) {
      Toast.show({ icon: 'fail', content: '充值金额范围 0.01-50000.00 元' });
      return;
    }
    setPassword('');
    setPopupVisible(true);
  };

  /** 密码弹窗中确认提交。 */
  const handleSubmit = async () => {
    if (!card || !id) return;
    if (!/^\d{6}$/.test(password)) {
      Toast.show({ icon: 'fail', content: '请输入6位数字支付密码' });
      return;
    }

    setLoading(true);
    try {
      await rechargeBankCard(id, {
        amountFen: Math.round(amount * 100),
        paymentPassword: password,
      });
      setPopupVisible(false);
      Toast.show({ icon: 'success', content: '充值成功' });
      // 返回钱包页重拉账户余额，不再停留银行卡详情
      history.replace('/h5/wallet');
    } catch (error: any) {
      Toast.show({ icon: 'fail', content: error?.message || '充值失败，请重试' });
    } finally {
      setLoading(false);
    }
  };

  if (loadingData || !card) {
    return <div className="bank-card-operate-page" />;
  }

  return (
    <div className="bank-card-operate-page">
      {/* 充值目标：账户余额 */}
      <div className="operate-target-card">
        <div className="target-label">充值到账户余额</div>
        <div className="target-balance">¥ {formatBalance(account?.availableFen || 0)}</div>
        <div className="target-hint">当前账户可用余额</div>
      </div>

      {/* 付款来源：银行卡（不展示卡内余额） */}
      <div className="operate-source-row">
        <span className="source-label">付款方式</span>
        <span className="source-value">{card.bankName}（尾号 {card.cardLast4}）</span>
      </div>

      <div className="operate-form">
        <div className="form-label">充值金额</div>
        <div className="amount-input-wrapper">
          <AmountInput value={amount} onChange={setAmount} placeholder="0.00" />
        </div>
        {amount > 0 && (amount < 0.01 || amount > 50000) && (
          <div className="form-hint warning">金额范围 0.01-50000.00 元</div>
        )}
      </div>

      <div className="operate-submit">
        <Button
          block
          color="primary"
          size="large"
          disabled={amount < 0.01 || amount > 50000}
          onClick={handleConfirmAmount}
        >
          确认充值
        </Button>
      </div>

      {/* 支付密码弹窗 */}
      <Popup
        visible={popupVisible}
        onMaskClick={() => !loading && setPopupVisible(false)}
        bodyStyle={{ borderTopLeftRadius: '12px', borderTopRightRadius: '12px', padding: '20px 16px 24px' }}
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
              <span className="summary-value">{card.bankName}（尾号 {card.cardLast4}）</span>
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
            <Button
              block
              color="primary"
              size="large"
              loading={loading}
              disabled={password.length !== 6}
              onClick={handleSubmit}
            >
              确认充值
            </Button>
            <Button
              block
              size="large"
              fill="none"
              disabled={loading}
              onClick={() => setPopupVisible(false)}
              style={{ marginTop: 8 }}
            >
              取消
            </Button>
          </div>
        </div>
      </Popup>
    </div>
  );
};

export default BankCardRechargePage;
