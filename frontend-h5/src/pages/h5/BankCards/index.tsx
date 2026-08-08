import { history } from 'umi';
import { Toast } from 'antd-mobile';
import { useEffect, useState } from 'react';
import { getBankCards, type BankCard } from '@/services/bankCard';
import './index.less';

/** 银行主题渐变背景：按银行编码区分，未收录银行使用中性深蓝。 */
const BANK_GRADIENT: Record<string, string> = {
  ICBC: 'linear-gradient(135deg, #d43f3f, #a01f24)',
  CCB: 'linear-gradient(135deg, #2b6cb0, #1a4a80)',
  ABC: 'linear-gradient(135deg, #2f9e63, #1d7048)',
  BOC: 'linear-gradient(135deg, #8a4b2d, #6b3419)',
  CMB: 'linear-gradient(135deg, #c2453b, #8f2b2b)',
  BCM: 'linear-gradient(135deg, #33569e, #20386b)',
  PSBC: 'linear-gradient(135deg, #2e8b57, #1f6b41)',
};

/** 卡类型中文展示名。 */
const CARD_TYPE_LABEL: Record<string, string> = {
  DEBIT: '储蓄卡',
  CREDIT: '信用卡',
};

/**
 * 银行卡列表页（二级页）：卡片式展示已绑定银行卡，
 * 点击进入详情，底部提供添加银行卡入口。
 */
const BankCardsPage = () => {
  const [cards, setCards] = useState<BankCard[]>([]);
  const [loading, setLoading] = useState(true);

  const loadCards = async () => {
    setLoading(true);
    try {
      const data = await getBankCards();
      setCards(data || []);
    } catch (error: any) {
      Toast.show({ icon: 'fail', content: error?.message || '加载银行卡失败' });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadCards();
  }, []);

  return (
    <div className="bank-cards-page">
      {cards.length === 0 && !loading && (
        <div className="bank-cards-empty">
          <div className="empty-icon">💳</div>
          <p>还没有绑定银行卡</p>
          <p className="empty-hint">绑定后可用于后续的充值与支付</p>
        </div>
      )}

      <div className="bank-card-list">
        {cards.map((card) => (
          <div
            key={card.cardId}
            className="bank-card-item"
            style={{ background: BANK_GRADIENT[card.bankCode] || 'linear-gradient(135deg, #3a4a63, #232f42)' }}
            onClick={() => history.push(`/h5/bank-cards/${card.cardId}`)}
          >
            <div className="bank-card-head">
              <span className="bank-name">{card.bankName}</span>
              <span className="card-type">{CARD_TYPE_LABEL[card.cardType] || card.cardType}</span>
              {card.isDefault && <span className="default-badge">默认</span>}
            </div>
            <div className="bank-card-number">**** **** **** {card.cardLast4}</div>
          </div>
        ))}
      </div>

      <div className="bank-card-actions">
        <div className="bank-card-add" onClick={() => history.push('/h5/bank-cards/add')}>
          <span className="add-icon">＋</span>
          <span>注册银行卡</span>
        </div>
        <div className="bank-card-bind" onClick={() => history.push('/h5/bank-card-bind')}>
          <span className="add-icon">🔗</span>
          <span>绑定银行卡</span>
        </div>
      </div>
    </div>
  );
};

export default BankCardsPage;
