import dayjs from 'dayjs';
import React, { useMemo } from 'react';
import { formatAmount } from '@/utils/format';
import './common.less';

/** 月分组列表对数据项的最小要求：可取 Key、时间与收支信息。 */
export interface MonthGroupItem {
  /** ISO 时间字符串。 */
  createdAt: string;
  /** 金额（分），用于月度汇总；不传则不计入汇总。 */
  amountFen?: number;
  /** 收支方向，用于月度汇总；不传则不计入汇总。 */
  direction?: 'IN' | 'OUT';
}

interface MonthGroup<T> {
  /** 分组键：YYYY-MM。 */
  key: string;
  /** 分组标题：如 2026 年 8 月。 */
  label: string;
  /** 当月收入合计（分）。 */
  incomeFen: number;
  /** 当月支出合计（分）。 */
  expenseFen: number;
  items: T[];
}

/**
 * 按月分组 + 收支汇总头列表：账户明细页与银行卡账单页共用。
 *
 * 设计约定：
 * - 分组按月份倒序（最新月在上），组内保持入参顺序（调用方负责按时间倒序）；
 * - 汇总头仅统计提供了 amountFen + direction 的条目，处理中/失败记录由
 *   调用方决定是否传入金额（余额未变动的记录建议不计入汇总）；
 * - 渲染单元由 renderItem 决定，本组件只负责分组骨架与汇总头。
 */
function MonthGroupList<T extends MonthGroupItem>(props: {
  items: T[];
  getKey: (item: T) => string;
  renderItem: (item: T) => React.ReactNode;
  /** 分组头部自定义文案，默认「收入 ¥xx · 支出 ¥xx」。 */
  renderSummary?: (group: MonthGroup<T>) => React.ReactNode;
}) {
  const { items, getKey, renderItem, renderSummary } = props;

  const groups = useMemo<MonthGroup<T>[]>(() => {
    const map = new Map<string, MonthGroup<T>>();
    for (const item of items) {
      const d = dayjs(item.createdAt);
      const key = d.format('YYYY-MM');
      let group = map.get(key);
      if (!group) {
        group = {
          key,
          label: `${d.year()} 年 ${d.month() + 1} 月`,
          incomeFen: 0,
          expenseFen: 0,
          items: [],
        };
        map.set(key, group);
      }
      if (item.amountFen !== undefined && item.direction) {
        if (item.direction === 'IN') {
          group.incomeFen += item.amountFen;
        } else {
          group.expenseFen += item.amountFen;
        }
      }
      group.items.push(item);
    }
    // 月份倒序：键为 YYYY-MM，字符串排序即时间排序
    return Array.from(map.values()).sort((a, b) => (a.key < b.key ? 1 : -1));
  }, [items]);

  return (
    <div className="h5-month-groups">
      {groups.map((group) => (
        <div className="h5-month-group" key={group.key}>
          <div className="h5-month-group-head">
            <span className="h5-month-group-label">{group.label}</span>
            <span className="h5-month-group-summary">
              {renderSummary ? (
                renderSummary(group)
              ) : (
                <>
                  收入 <b className="amount-in">¥{formatAmount(group.incomeFen)}</b>
                  {' · '}
                  支出 ¥{formatAmount(group.expenseFen)}
                </>
              )}
            </span>
          </div>
          <div className="h5-month-group-body h5-card">
            {group.items.map((item) => (
              <React.Fragment key={getKey(item)}>{renderItem(item)}</React.Fragment>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}

export default MonthGroupList;
