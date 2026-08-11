import { history } from 'umi';
import { Toast } from 'antd-mobile';
import { useEffect, useState } from 'react';
import { getBankCards, type BankCard } from '@/services/bankCard';
import { BankCardFace, EmptyState, IconSet, Skeleton } from '@/components/h5/common';
import './index.less';

/**
 * 银行卡列表页（二级页）：仿真卡面展示已绑定银行卡（银行专属渐变 + CSS 纹样），
 * 卡内余额默认掩码，点击进入详情，底部提供注册/绑定银行卡入口。
 */
const BankCardsPage = () => {
  const [cards, setCards] = useState<BankCard[]>([]);
  const [loading, setLoading] = useState(true);
  // 卡内余额统一掩码，点击顶部按钮批量切换；明文仅在内存。
  const [balanceRevealed, setBalanceRevealed] = useState(false);

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
      {cards.length > 0 && (
        <div className="bank-cards-toolbar">
          <span className="toolbar-count">共 {cards.length} 张</span>
          <span className="toolbar-reveal" onClick={() => setBalanceRevealed((v) => !v)}>
            <IconSet name={balanceRevealed ? 'eyeOff' : 'eyeOn'} size={14} />
            {balanceRevealed ? '隐藏余额' : '查看余额'}
          </span>
        </div>
      )}

      {loading ? (
        <div className="bank-card-list">
          <Skeleton variant="card" height={124} />
          <Skeleton variant="card" height={124} />
        </div>
      ) : cards.length === 0 ? (
        <EmptyState
          icon={<IconSet name="card" size={40} color="var(--h5-text-3)" />}
          text="还没有绑定银行卡"
          hint="绑定后可用于充值、提现与支付"
          actionText="去注册银行卡"
          onAction={() => history.push('/h5/bank-cards/add')}
        />
      ) : (
        <div className="bank-card-list">
          {cards.map((card) => (
            <BankCardFace
              key={card.cardId}
              bankCode={card.bankCode}
              bankName={card.bankName}
              cardType={card.cardType}
              cardLast4={card.cardLast4}
              isDefault={card.isDefault}
              balanceFen={card.balanceFen}
              balanceRevealed={balanceRevealed}
              onClick={() => history.push(`/h5/bank-cards/${card.cardId}`)}
            />
          ))}
        </div>
      )}

      <div className="bank-card-actions">
        <div className="bank-card-add" onClick={() => history.push('/h5/bank-cards/add')}>
          <span className="add-icon">
            <IconSet name="plus" size={16} />
          </span>
          <span>注册银行卡</span>
        </div>
        <div className="bank-card-bind" onClick={() => history.push('/h5/bank-card-bind')}>
          <span className="add-icon">
            <IconSet name="card" size={16} />
          </span>
          <span>绑定银行卡</span>
        </div>
      </div>
    </div>
  );
};

export default BankCardsPage;
