import { useParams } from 'umi';
import { Toast } from 'antd-mobile';
import { useEffect, useMemo, useState } from 'react';
import {
  getBankCardDetail,
  getBankCardTransactions,
  formatBalance,
  type BankCard,
} from '@/services/bankCard';
import { formatTime } from '@/utils/format';
import { MonthGroupList, EmptyState, IconSet, Skeleton, type MonthGroupItem } from '@/components/h5/common';
import './index.less';

/** 账单条目：卡内余额视角；amountFen 可选，非成功交易不传以不计入汇总。 */
interface BillItem extends MonthGroupItem {
  transactionId: string;
  businessType: 'BANK_CARD_RECHARGE' | 'BANK_CARD_WITHDRAW';
  status: string;
  /** 卡内余额视角：提现 = 资金流入卡（IN），充值 = 资金流出卡（OUT）。 */
  direction: 'IN' | 'OUT';
}

const TX_STATUS_LABEL: Record<string, string> = {
  PROCESSING: '处理中',
  SUCCESS: '成功',
  CANCELLED: '已撤销',
  FAILED: '失败',
  MANUAL_REVIEW: '人工审核',
};

/**
 * 银行卡账单页：卡内余额视角的充值/提现历史，按月分组 + 方向筛选。
 * 符号约定：提现为资金流入卡（+），充值为资金流出卡（−）；
 * 非成功交易余额未变动，金额置灰且不计入月度汇总。
 */
const BankCardBillsPage = () => {
  const { id } = useParams<{ id: string }>();
  const [card, setCard] = useState<BankCard | null>(null);
  const [bills, setBills] = useState<BillItem[]>([]);
  const [loading, setLoading] = useState(true);
  // 筛选：全部 / 收入 / 支出（卡内余额视角）
  const [filter, setFilter] = useState<'ALL' | 'IN' | 'OUT'>('ALL');

  useEffect(() => {
    if (!id) return;
    let cancelled = false;
    const load = async () => {
      setLoading(true);
      try {
        const [cardData, txList] = await Promise.all([
          getBankCardDetail(id),
          getBankCardTransactions(id, 50),
        ]);
        if (cancelled) return;
        setCard(cardData);
        setBills(
          (txList || []).map((tx) => ({
            ...tx,
            // 充值 = 卡付钱给账户（OUT）；提现 = 账户付钱给卡（IN）
            direction: tx.businessType === 'BANK_CARD_WITHDRAW' ? 'IN' : 'OUT',
            // 仅成功交易计入月度汇总（余额真实变动）
            amountFen: tx.status === 'SUCCESS' ? tx.amountFen : undefined,
          })),
        );
      } catch (error: any) {
        Toast.show({ icon: 'fail', content: error?.message || '账单加载失败' });
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    load();
    return () => {
      cancelled = true;
    };
  }, [id]);

  const visible = useMemo(() => {
    if (filter === 'ALL') return bills;
    return bills.filter((b) => b.direction === filter);
  }, [bills, filter]);

  if (loading) {
    return (
      <div className="bank-card-bills-page">
        <Skeleton variant="card" height={72} />
        <div style={{ marginTop: 14 }}>
          <Skeleton variant="list" rows={5} />
        </div>
      </div>
    );
  }

  return (
    <div className="bank-card-bills-page">
      {/* 卡片摘要头 */}
      {card && (
        <div className="bills-card-head">
          <span className="head-bank">
            {card.bankName}（尾号 {card.cardLast4}）
          </span>
          <span className="head-balance">卡内余额 ¥ {formatBalance(card.balanceFen || 0)}</span>
        </div>
      )}

      <div className="bills-filters">
        {(
          [
            { key: 'ALL', label: '全部' },
            { key: 'IN', label: '收入' },
            { key: 'OUT', label: '支出' },
          ] as const
        ).map((f) => (
          <span
            key={f.key}
            className={`bills-filter-chip${filter === f.key ? ' active' : ''}`}
            onClick={() => setFilter(f.key)}
          >
            {f.label}
          </span>
        ))}
      </div>

      {visible.length === 0 ? (
        <EmptyState
          icon={<IconSet name="receipt" size={40} color="var(--h5-text-3)" />}
          text="暂无账单记录"
          hint="充值、提现完成后将在这里展示"
        />
      ) : (
        <MonthGroupList
          items={visible}
          getKey={(b) => b.transactionId}
          renderItem={(b) => {
            const isSuccess = b.status === 'SUCCESS';
            const isRecharge = b.businessType === 'BANK_CARD_RECHARGE';
            return (
              <div className="bill-row">
                <div className="bill-icon">
                  <IconSet
                    name={isRecharge ? 'send' : 'wallet'}
                    size={15}
                    color="var(--h5-primary)"
                  />
                </div>
                <div className="bill-main">
                  <div className="bill-title">
                    {isRecharge ? '充值到账户余额' : '提现到账'}
                    {!isSuccess && (
                      <span className="bill-status">{TX_STATUS_LABEL[b.status] || b.status}</span>
                    )}
                  </div>
                  <div className="bill-time">{formatTime(b.createdAt, 'YYYY-MM-DD HH:mm')}</div>
                </div>
                <div className={`bill-amount${isSuccess ? (isRecharge ? ' amount-out' : ' amount-in') : ' muted'}`}>
                  {isRecharge ? '−' : '+'}¥{formatBalance(b.amountFen || 0)}
                </div>
              </div>
            );
          }}
        />
      )}
    </div>
  );
};

export default BankCardBillsPage;
