import React, { useEffect, useMemo, useState } from 'react';
import { history } from 'umi';
import { Toast } from 'antd-mobile';
import dayjs from 'dayjs';
import * as accountService from '@/services/account';
import * as bankCardService from '@/services/bankCard';
import { formatAmount } from '@/utils/format';
import { MonthGroupList, EmptyState, Skeleton, IconSet } from '@/components/h5/common';
import type { IconName } from '@/components/h5/common/IconSet';
import { mergeUniqueTransactions, projectBankCardTransaction } from './viewModel';
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
 * 全局账单主标题：按 memo 关键词 + 收支方向组合生成（设计方案 1.3）。
 * 花呗付款方的信用应收分录展示为消费，收款方的余额负债分录展示为扫码收款。
 */
const getTxTitle = (memo: string | null, direction: 'IN' | 'OUT'): string => {
  const m = memo?.trim() || '';
  if (m.includes('充值')) return '银行卡充值';
  if (m.includes('提现')) return '银行卡提现';
  if (m.includes('花呗还款') || m.includes('还款')) return '花呗还款';
  if ((m.includes('花呗') || m.includes('信用支付')) && direction === 'OUT') return '花呗消费';
  if (m.includes('转账')) {
    if (m.includes('银行卡')) return '银行卡转账';
    return direction === 'IN' ? '转账收款' : '转账';
  }
  if (m.includes('支付') || m.includes('收款') || m.includes('扫码')) {
    if (direction === 'IN') return '扫码收款';
    if (m.includes('银行卡')) return '银行卡扫码支付';
    return '扫码支付';
  }
  return accountService.getLedgerEntryTitle({ memo });
};

/** 资金渠道标签：BALANCE 不展示（默认），银行卡/花呗展示小标签（设计方案 1.3）。 */
const getChannelTag = (memo: string | null): { label: string; cls: string } | null => {
  const m = memo?.trim() || '';
  if (m.includes('花呗') || m.includes('信用支付')) return { label: '花呗', cls: 'credit' };
  if (m.includes('银行卡')) return { label: '银行卡', cls: 'bank' };
  return null;
};

/** 智能时间格式：当天 HH:mm，当年 MM-DD HH:mm，跨年 YYYY-MM-DD。 */
const formatTxTime = (dateStr: string): string => {
  const t = dayjs(dateStr);
  if (!t.isValid()) return dateStr;
  const now = dayjs();
  if (t.isSame(now, 'day')) return t.format('HH:mm');
  if (t.isSame(now, 'year')) return t.format('MM-DD HH:mm');
  return t.format('YYYY-MM-DD');
};

/**
 * 账单分类筛选：单行横向滑动胶囊，单选。
 * 分类语义（前端按账本 memo 关键词归类，与列表图标归类规则保持一致）。
 */
export const TX_CATEGORIES: { key: string; label: string; match: (tx: accountService.Transaction) => boolean }[] = [
  { key: 'all', label: '全部', match: () => true },
  { key: 'income', label: '收入', match: (tx) => tx.direction === 'IN' },
  { key: 'expense', label: '支出', match: (tx) => tx.direction === 'OUT' },
  { key: 'bank', label: '银行卡', match: (tx) => /充值|提现/.test(tx.memo || '') },
  { key: 'credit', label: '花呗', match: (tx) => /花呗|还款|信用支付/.test(tx.memo || '') },
  { key: 'transfer', label: '转账', match: (tx) => (tx.memo || '').includes('转账') },
  { key: 'qrpay', label: '扫码支付', match: (tx) => /支付|收款|扫码/.test(tx.memo || '') },
];

/**
 * 加载银行卡流水并映射为账单展示项。
 *
 * 银行卡充值/提现与银行卡出资的转账/扫码支付的 TCC 只移动卡虚拟余额与账户余额，
 * 不写账本分录（无复式账本，见系统分析 9.2），账本明细接口天然不含这些记录；
 * 这里把银行卡流水投影成与账本分录同形状的行，合并后在「全部」及对应分类下可见。
 * 只取成功终态；投影行 entryId 固定为 0，合并时使用交易 ID 去重。
 *
 * 余额变动明细页（BalanceEntries）复用本投影逻辑。
 */
export const loadBankCardTransactions = async (): Promise<accountService.Transaction[]> => {
  try {
    const cards = await bankCardService.getBankCards();
    const lists = await Promise.all(
      cards.map((card) => bankCardService.getBankCardTransactions(card.cardId, 50)),
    );
    return lists.flat()
      .filter((tx) => tx.status === 'SUCCESS')
      .map(projectBankCardTransaction);
  } catch {
    // 银行卡流水加载失败只影响该部分投影，不阻断账本账单展示
    return [];
  }
};

/**
 * 全局账单页：用户维度全渠道交易流水（余额/银行卡/花呗）。
 * 分析功能已迁移为独立页 /h5/account/analytics，本页仅保留账单列表。
 *
 * 交易状态标签说明：账本明细接口仅返回成功终态分录，无 PROCESSING 等中间态数据源，
 * 故不渲染状态标签（设计方案 1.3 的状态规则保留给后续接入 fund_transaction 时使用）。
 */
const TransactionsPage: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [transactions, setTransactions] = useState<accountService.Transaction[]>([]);
  const [category, setCategory] = useState('all');
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const sentinelRef = React.useRef<HTMLDivElement>(null);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      setLoading(true);
      try {
        // 首屏按设计使用 50 条游标分页；银行卡流水仅在首屏补充一次。
        const [data, bankCardItems] = await Promise.all([
          accountService.getTransactions({ pageSize: 50 }),
          loadBankCardTransactions(),
        ]);
        if (!cancelled) {
          // 全局账单按交易号唯一；同一交易的多科目分录不得重复展示。
          setTransactions(mergeUniqueTransactions(data.items || [], bankCardItems));
          setNextCursor(data.nextCursor);
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

  /** 按服务端不透明游标加载下一页账本分录。 */
  const loadMore = React.useCallback(async () => {
    if (!nextCursor || loadingMore) return;
    setLoadingMore(true);
    try {
      const data = await accountService.getTransactions({ cursor: nextCursor, pageSize: 50 });
      setTransactions((current) => mergeUniqueTransactions(current, data.items || []));
      setNextCursor(data.nextCursor);
    } catch (error: any) {
      Toast.show({ content: error?.message || '加载更多账单失败，请稍后重试', icon: 'fail' });
    } finally {
      setLoadingMore(false);
    }
  }, [loadingMore, nextCursor]);

  // 分类筛选：全部不过滤；收入/支出按方向；其余业务分类按 memo 关键词
  const visible = useMemo(() => {
    const filter = TX_CATEGORIES.find((c) => c.key === category) || TX_CATEGORIES[0];
    return transactions.filter(filter.match);
  }, [transactions, category]);

  useEffect(() => {
    const node = sentinelRef.current;
    if (!node || !nextCursor || loadingMore) return undefined;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) void loadMore();
      },
      { rootMargin: '160px' },
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, [loadMore, loadingMore, nextCursor, visible.length]);

  return (
    <div className="transactions-page">
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
          <>
            <EmptyState
              icon={<IconSet name="receipt" size={30} color="var(--h5-primary)" />}
              text={category === 'all' ? '暂无交易记录' : '正在查找该分类账单'}
              hint={nextCursor ? '继续加载历史账单中…' : '充值、转账、花呗消费、扫码支付完成后将在这里展示'}
            />
            {nextCursor && <div ref={sentinelRef} className="tx-sentinel">加载中…</div>}
          </>
        ) : (
          <>
            <MonthGroupList
              items={visible}
              getKey={(tx) => tx.transactionId}
              renderItem={(tx) => {
              const biz = getBizType(tx.memo);
              const clickable = tx.memo?.includes('转账');
              const channel = getChannelTag(tx.memo);
              const party = tx.counterpartyName
                ? tx.direction === 'IN'
                  ? `来自 ${tx.counterpartyName}`
                  : `转给 ${tx.counterpartyName}`
                : null;
              return (
                <div
                  className="tx-row"
                  onClick={() => clickable && history.push(`/h5/transfer/result/${tx.transactionId}`)}
                >
                  <div className="tx-icon">
                    <IconSet name={biz.icon} size={15} color="var(--h5-primary)" />
                  </div>
                  <div className="tx-main">
                    <div className="tx-title">
                      <span className="tx-title-text">{getTxTitle(tx.memo, tx.direction)}</span>
                      {channel && <span className={`tx-channel-tag ${channel.cls}`}>{channel.label}</span>}
                    </div>
                    {party && <div className="tx-party">{party}</div>}
                    <div className="tx-sub">
                      {formatTxTime(tx.createdAt)}
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
            {nextCursor && (
              <div ref={sentinelRef} className="tx-sentinel">
                {loadingMore ? '加载中…' : '继续上滑加载更多'}
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
};

export default TransactionsPage;
