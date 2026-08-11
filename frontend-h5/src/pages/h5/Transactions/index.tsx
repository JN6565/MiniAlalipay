import React, { useEffect, useMemo, useState } from 'react';
import { history } from 'umi';
import { Toast } from 'antd-mobile';
import * as accountService from '@/services/account';
import * as bankCardService from '@/services/bankCard';
import { formatAmount, formatTime } from '@/utils/format';
import { MonthGroupList, EmptyState, Skeleton, IconSet } from '@/components/h5/common';
import type { IconName } from '@/components/h5/common/IconSet';
import AnalyticsPanel from './AnalyticsPanel';
import './index.less';

/** 业务类型：由账本摘要（memo）关键词推导，后端已脱敏的语义事实。 */
const BIZ_TYPES: { key: string; label: string; icon: IconName; match: (memo: string) => boolean }[] = [
  { key: 'recharge', label: '充值', icon: 'wallet', match: (m) => m.includes('充值') },
  { key: 'withdraw', label: '提现', icon: 'card', match: (m) => m.includes('提现') },
  { key: 'transfer', label: '转账', icon: 'transfer', match: (m) => m.includes('转账') },
  { key: 'credit', label: '花呗', icon: 'huabei', match: (m) => m.includes('花呗') || m.includes('还款') || m.includes('消费') },
  { key: 'pay', label: '支付', icon: 'qr', match: (m) => m.includes('支付') || m.includes('收款') },
];

const getBizType = (memo: string | null) => {
  const m = memo?.trim() || '';
  return BIZ_TYPES.find((t) => t.match(m)) || { key: 'other', label: '资金变动', icon: 'receipt' as IconName, match: () => true };
};

/**
 * 账单分类筛选（V2.1）：单行横向滑动胶囊，单选。
 * 分类语义（前端按账本 memo 关键词归类，与列表图标归类规则保持一致）：
 * - 全部：不过滤，天然包含花呗、银行卡等全部收支分录；
 * - 收入/支出：按收支方向过滤；
 * - 银行卡 / 充值提现：同义合并，均为银行卡资金流转（memo 含充值/提现）；
 * - 花呗：memo 含花呗/还款/消费；转账：memo 含转账；
 * - 扫码支付：memo 含支付/收款/扫码。
 */
export const TX_CATEGORIES: { key: string; label: string; match: (tx: accountService.Transaction) => boolean }[] = [
  { key: 'all', label: '全部', match: () => true },
  { key: 'income', label: '收入', match: (tx) => tx.direction === 'IN' },
  { key: 'expense', label: '支出', match: (tx) => tx.direction === 'OUT' },
  { key: 'bank', label: '银行卡', match: (tx) => /充值|提现/.test(tx.memo || '') },
  { key: 'credit', label: '花呗', match: (tx) => /花呗|还款|消费/.test(tx.memo || '') },
  { key: 'transfer', label: '转账', match: (tx) => (tx.memo || '').includes('转账') },
  { key: 'fund', label: '充值提现', match: (tx) => /充值|提现/.test(tx.memo || '') },
  { key: 'qrpay', label: '扫码支付', match: (tx) => /支付|收款|扫码/.test(tx.memo || '') },
];

/**
 * 加载银行卡流水并映射为账单展示项。
 *
 * 银行卡充值/提现与银行卡出资的转账/扫码支付的 TCC 只移动卡虚拟余额与账户余额，
 * 不写账本分录（无复式账本，见系统分析 9.2），账本明细接口天然不含这些记录；
 * 这里把银行卡流水投影成与账本分录同形状的行，合并后在「全部」及对应分类下可见。
 * 只取成功终态；投影行 entryId 固定为 0，列表 key 使用交易 ID 区分。
 */
const loadBankCardTransactions = async (): Promise<accountService.Transaction[]> => {
  try {
    const cards = await bankCardService.getBankCards();
    const lists = await Promise.all(
      cards.map((card) => bankCardService.getBankCardTransactions(card.cardId, 50)),
    );
    return lists.flat()
      .filter((tx) => tx.status === 'SUCCESS')
      .map((tx) => {
        // 账户视角收支：充值是卡→账户（入账）；提现、银行卡出资的转账/扫码支付
        // 均为资金离开账户（出账）
        const isIn = tx.businessType === 'BANK_CARD_RECHARGE';
        const memo =
          tx.businessType === 'BANK_CARD_RECHARGE'
            ? '银行卡充值'
            : tx.businessType === 'TRANSFER'
              ? '银行卡转账'
              : tx.businessType === 'QR_PAY'
                ? '银行卡扫码支付'
                : '银行卡提现';
        return {
          entryId: 0,
          transactionId: tx.transactionId,
          amountFen: tx.amountFen,
          direction: isIn ? 'IN' : 'OUT',
          memo,
          counterpartyName: '',
          balanceAfterFen: null,
          createdAt: tx.createdAt,
        };
      });
  } catch {
    // 银行卡流水加载失败只影响该部分投影，不阻断账本账单展示
    return [];
  }
};

/**
 * 账单页（原明细页）：V2.1 双 Tab 结构——
 * 「账单」分类胶囊筛选 + 月分组列表；「分析」汇总指标 + 分类占比 + 趋势 + 交易对象排行。
 */
const TransactionsPage: React.FC = () => {
  const [tab, setTab] = useState<'bills' | 'analytics'>('bills');
  const [loading, setLoading] = useState(true);
  const [transactions, setTransactions] = useState<accountService.Transaction[]>([]);
  const [category, setCategory] = useState('all');

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      setLoading(true);
      try {
        // 一次性拉取全部分录（含花呗、银行卡等），分类筛选在前端完成；
        // 条数从 50 提升到 100，减少筛选后列表过短的问题
        const [data, bankCardItems] = await Promise.all([
          accountService.getTransactions({ pageSize: 100 }),
          loadBankCardTransactions(),
        ]);
        if (!cancelled) {
          // 账本分录与银行卡充值/提现投影按创建时间倒序合并
          const merged = [...(data.items || []), ...bankCardItems].sort((a, b) =>
            (b.createdAt || '').localeCompare(a.createdAt || ''),
          );
          setTransactions(merged);
        }
      } catch (error: any) {
        Toast.show({ content: error?.message || '当前网络环境较差，数据暂未返回，请稍后重试', icon: 'fail' });
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };
    load();
    return () => {
      cancelled = true;
    };
  }, []);

  // 分类筛选：全部不过滤；收入/支出按方向；其余业务分类按 memo 关键词
  const visible = useMemo(() => {
    const filter = TX_CATEGORIES.find((c) => c.key === category) || TX_CATEGORIES[0];
    return transactions.filter(filter.match);
  }, [transactions, category]);

  return (
    <div className="transactions-page">
      {/* 顶部双 Tab：账单 / 分析 */}
      <div className="tx-top-tabs">
        {([['bills', '账单'], ['analytics', '分析']] as const).map(([key, label]) => (
          <div
            key={key}
            className={`tx-top-tab${tab === key ? ' active' : ''}`}
            onClick={() => setTab(key)}
          >
            <span className="tx-top-tab-text">{label}</span>
            {tab === key && <span className="tx-top-tab-bar" />}
          </div>
        ))}
      </div>

      {tab === 'analytics' ? (
        <AnalyticsPanel />
      ) : (
        <div className="tx-bills-body">
          {/* 分类筛选胶囊：横向滑动单选，覆盖全部/收支方向/业务分类 */}
          <div className="tx-chips-scroll">
            <div className="tx-chips tx-chips-inline">
              {TX_CATEGORIES.map((item) => (
                <span
                  key={item.key}
                  className={`tx-chip${category === item.key ? ' active' : ''}`}
                  onClick={() => setCategory(item.key)}
                >
                  {item.label}
                </span>
              ))}
            </div>
          </div>

          {loading ? (
            <>
              <Skeleton variant="card" height={110} />
              <div style={{ marginTop: 10 }}>
                <Skeleton variant="card" height={110} />
              </div>
            </>
          ) : visible.length === 0 ? (
            <EmptyState
              icon={<IconSet name="receipt" size={30} color="var(--h5-primary)" />}
              text={category === 'all' ? '暂无交易记录' : '该分类下暂无账单'}
              hint="充值、转账、花呗消费、扫码支付完成后将在这里展示"
            />
          ) : (
            <MonthGroupList
              items={visible}
              getKey={(tx) => `${tx.entryId}-${tx.transactionId}`}
              renderItem={(tx) => {
                const biz = getBizType(tx.memo);
                const clickable = tx.memo?.includes('转账');
                const party = tx.counterpartyName
                  ? tx.direction === 'IN'
                    ? `来自 ${tx.counterpartyName}`
                    : `转给 ${tx.counterpartyName}`
                  : accountService.getLedgerEntryTitle(tx);
                return (
                  <div
                    className="tx-row"
                    onClick={() => clickable && history.push(`/h5/transfer/result/${tx.transactionId}`)}
                  >
                    <div className="tx-icon">
                      <IconSet name={biz.icon} size={15} color="var(--h5-primary)" />
                    </div>
                    <div className="tx-main">
                      <div className="tx-title">{biz.label} · {party}</div>
                      <div className="tx-sub">
                        {formatTime(tx.createdAt, 'YYYY-MM-DD HH:mm')}
                        {tx.balanceAfterFen !== null && (
                          <span className="tx-balance"> · 余额 ¥{formatAmount(tx.balanceAfterFen)}</span>
                        )}
                      </div>
                    </div>
                    <div className={`tx-amount ${tx.direction === 'IN' ? 'amount-in' : ''}`}>
                      {tx.direction === 'IN' ? '+' : '−'}¥{formatAmount(tx.amountFen)}
                    </div>
                  </div>
                );
              }}
            />
          )}
        </div>
      )}
    </div>
  );
};

export default TransactionsPage;
