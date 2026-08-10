import { history } from 'umi';
import { Button, SpinLoading, Toast } from 'antd-mobile';
import { useCallback, useEffect, useState } from 'react';
import * as accountService from '@/services/account';
import { getBankCards, formatBalance, type BankCard } from '@/services/bankCard';
import './index.less';

/** 银行主题渐变背景：与银行卡列表页保持一致的视觉风格。 */
const BANK_GRADIENT: Record<string, string> = {
  ICBC: 'linear-gradient(135deg, #d43f3f, #a01f24)',
  CCB: 'linear-gradient(135deg, #2b6cb0, #1a4a80)',
  ABC: 'linear-gradient(135deg, #2f9e63, #1d7048)',
  BOC: 'linear-gradient(135deg, #8a4b2d, #6b3419)',
  CMB: 'linear-gradient(135deg, #c2453b, #8f2b2b)',
  BCM: 'linear-gradient(135deg, #33569e, #20386b)',
  PSBC: 'linear-gradient(135deg, #2e8b57, #1f6b41)',
};

/**
 * 充值提现页（钱包）：银行卡充值/提现的统一入口。
 *
 * 用户资金流入的真实渠道只有两种：他人转账、银行卡充值（卡虚拟余额 → 账户余额）；
 * 资金流出到银行卡通过提现（账户余额 → 卡虚拟余额）。本页展示账户余额与
 * 银行卡列表（含虚拟余额），每卡提供充值/提现操作，跳转既有 TCC 操作页。
 */
const WalletPage = () => {
  const [account, setAccount] = useState<accountService.AccountInfo | null>(null);
  const [cards, setCards] = useState<BankCard[]>([]);
  const [loading, setLoading] = useState(true);

  // 每次进入页面重拉余额与卡列表：充值/提现成功后返回本页即展示最新事实。
  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [accountResp, cardsResp] = await Promise.allSettled([
        accountService.getMyAccount(),
        getBankCards(),
      ]);
      if (accountResp.status === 'fulfilled') {
        setAccount(accountResp.value as unknown as accountService.AccountInfo);
      }
      if (cardsResp.status === 'fulfilled') {
        setCards(cardsResp.value || []);
      }
    } catch (error: any) {
      Toast.show({ icon: 'fail', content: error?.message || '加载失败' });
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  if (loading) {
    return (
      <div className="wallet-page">
        <div className="wallet-loading">
          <SpinLoading />
        </div>
      </div>
    );
  }

  return (
    <div className="wallet-page">
      {/* 账户余额卡片 */}
      <div className="wallet-account-card">
        <div className="account-title">账户余额（元）</div>
        <div className="account-available">{formatBalance(account?.availableFen || 0)}</div>
        <div className="account-cols">
          <div>
            <span>冻结金额</span>
            <b>{formatBalance(account?.frozenFen || 0)}</b>
          </div>
          <div>
            <span>总资产</span>
            <b>{formatBalance(account?.totalFen || 0)}</b>
          </div>
        </div>
      </div>

      {/* 银行卡列表与充值/提现入口 */}
      <div className="wallet-section-head">
        <span>银行卡</span>
        <span className="wallet-section-link" onClick={() => history.push('/h5/bank-cards')}>
          管理银行卡 &gt;
        </span>
      </div>

      {cards.length === 0 ? (
        <div className="wallet-empty">
          <div className="empty-icon">💳</div>
          <p>还没有绑定银行卡</p>
          <p className="empty-hint">绑定后可从银行卡充值到账户余额</p>
          <Button color="primary" size="small" onClick={() => history.push('/h5/bank-cards')}>
            去绑卡
          </Button>
        </div>
      ) : (
        <div className="wallet-card-list">
          {cards.map((card) => (
            <div
              key={card.cardId}
              className="wallet-card-item"
              style={{ background: BANK_GRADIENT[card.bankCode] || 'linear-gradient(135deg, #3a4a63, #232f42)' }}
            >
              <div className="card-head" onClick={() => history.push(`/h5/bank-cards/${card.cardId}`)}>
                <span className="bank-name">{card.bankName}</span>
                <span className="card-number">**** **** **** {card.cardLast4}</span>
                {card.isDefault && <span className="default-badge">默认</span>}
              </div>
              <div className="card-balance">
                卡内余额 <b>¥ {formatBalance(card.balanceFen || 0)}</b>
              </div>
              <div className="card-actions">
                <Button
                  size="small"
                  fill="outline"
                  className="card-action-btn"
                  onClick={() => history.push(`/h5/bank-cards/${card.cardId}/recharge`)}
                >
                  充值
                </Button>
                <Button
                  size="small"
                  fill="outline"
                  className="card-action-btn"
                  onClick={() => history.push(`/h5/bank-cards/${card.cardId}/withdraw`)}
                >
                  提现
                </Button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* 资金渠道说明 */}
      <div className="wallet-tips">
        <p>· 充值：银行卡余额转入账户余额</p>
        <p>· 提现：账户余额转入银行卡余额</p>
        <p>· 他人转账与银行卡充值是账户资金流入的全部渠道</p>
      </div>
    </div>
  );
};

export default WalletPage;
