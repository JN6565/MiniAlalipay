import { history, useParams } from 'umi';
import { Button, Dialog, List, Toast } from 'antd-mobile';
import { useCallback, useEffect, useState } from 'react';
import {
  getBankCardDetail,
  setDefaultBankCard,
  unbindBankCard,
  formatBalance,
  getBankCardTransactions,
  type BankCard,
  type BankCardTransaction,
} from '@/services/bankCard';
import { formatTime } from '@/utils/format';
import './index.less';

/** 卡类型中文展示名。 */
const CARD_TYPE_LABEL: Record<string, string> = {
  DEBIT: '储蓄卡',
  CREDIT: '信用卡',
};

/**
 * 交易标题：以资金相对银行卡的方向描述业务动作。
 * 充值 = 卡内资金转到账户余额（卡出钱）；提现 = 账户余额转到卡（卡收钱）。
 */
const TX_TYPE_LABEL: Record<string, string> = {
  BANK_CARD_RECHARGE: '充值到账户余额',
  BANK_CARD_WITHDRAW: '提现到账',
};

/** 交易状态中文展示名。 */
const TX_STATUS_LABEL: Record<string, string> = {
  PROCESSING: '处理中',
  SUCCESS: '成功',
  CANCELLED: '已撤销',
  FAILED: '失败',
  MANUAL_REVIEW: '人工审核',
};

/**
 * 银行卡详情页：展示全掩码卡片信息、卡内余额与卡内余额明细，
 * 提供「设为默认卡」与「解除绑定」（二次确认）管理操作；
 * 充值/提现统一从充值提现页（/h5/wallet）发起。
 */
const BankCardDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const [card, setCard] = useState<BankCard | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [transactions, setTransactions] = useState<BankCardTransaction[]>([]);

  const loadDetail = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    try {
      const data = await getBankCardDetail(id);
      setCard(data);
      // 并行加载交易明细
      const txList = await getBankCardTransactions(id);
      setTransactions(txList);
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

  if (loading || !card) {
    return <div className="bank-card-detail-page" />;
  }

  return (
    <div className="bank-card-detail-page">
      <div className="detail-card-head">
        <div className="detail-bank-name">{card.bankName}</div>
        <div className="detail-card-number">**** **** **** {card.cardLast4}</div>
        <div className="detail-balance-section">
          <span className="detail-balance-label">可用余额</span>
          <span className="detail-balance-value">¥ {formatBalance(card.balanceFen || 0)}</span>
        </div>
        {card.isDefault && <div className="detail-default-badge">默认卡</div>}
      </div>

      <List header="卡片信息">
        <List.Item title="卡类型" extra={CARD_TYPE_LABEL[card.cardType] || card.cardType} />
        <List.Item title="持卡人" extra={card.holderMasked} />
        <List.Item title="身份证号" extra={card.idCardMasked} />
        <List.Item title="预留手机号" extra={card.phoneMasked} />
        <List.Item title="绑定时间" extra={formatTime(card.boundAt)} />
      </List>

      {/* 明细以卡内余额视角展示：充值为资金流出卡（-），提现为资金流入卡（+）；
          非成功交易余额未变动，金额置灰以免误导 */}
      <List header="卡内余额明细" style={{ marginTop: 12 }}>
        {transactions.length === 0 && (
          <List.Item description="暂无交易记录">暂无记录</List.Item>
        )}
        {transactions.map((tx) => {
          const isRecharge = tx.businessType === 'BANK_CARD_RECHARGE';
          const isSuccess = tx.status === 'SUCCESS';
          const amountYuan = formatBalance(tx.amountFen);
          return (
            <List.Item
              key={tx.transactionId}
              title={TX_TYPE_LABEL[tx.businessType] || tx.businessType}
              description={`${formatTime(tx.createdAt)} · ${TX_STATUS_LABEL[tx.status] || tx.status}`}
              extra={
                <span
                  style={{
                    color: !isSuccess ? '#999' : isRecharge ? '#333' : '#ff4d4f',
                    fontWeight: 500,
                  }}
                >
                  {isRecharge ? '-' : '+'}¥{amountYuan}
                </span>
              }
            />
          );
        })}
      </List>

      <div className="detail-actions">
        {!card.isDefault && (
          <Button block color="primary" size="large" loading={busy} onClick={handleSetDefault}>
            设为默认卡
          </Button>
        )}
        <Button block color="danger" fill="outline" size="large" disabled={busy} onClick={handleUnbind}>
          解除绑定
        </Button>
      </div>
    </div>
  );
};

export default BankCardDetailPage;
