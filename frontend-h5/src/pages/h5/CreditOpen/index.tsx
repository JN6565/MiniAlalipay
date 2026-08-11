import React, { useEffect, useState } from 'react';
import { history } from 'umi';
import { Checkbox, Toast } from 'antd-mobile';
import * as creditService from '@/services/credit';
import { formatAmount } from '@/utils/format';
import { IconSet } from '@/components/h5/common';
import './index.less';

/** Mini 花呗开通页：用户必须显式勾选确认后才会写入开通事实。 */
const CreditOpenPage: React.FC = () => {
  const [credit, setCredit] = useState<creditService.CreditSummary | null>(null);
  const [checked, setChecked] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    creditService.getCreditSummary().then(data => {
      const summary = data as unknown as creditService.CreditSummary;
      setCredit(summary);
      if (summary.opened) history.replace('/h5/credit');
    }).catch(() => {});
  }, []);

  const handleOpen = async () => {
    if (!checked) {
      Toast.show({ content: '请先确认已了解 Mini 花呗规则', icon: 'fail' });
      return;
    }
    setSubmitting(true);
    try {
      await creditService.openCredit();
      Toast.show({ content: 'Mini 花呗已开通', icon: 'success' });
      history.replace('/h5/credit');
    } catch (error: any) {
      Toast.show({ content: error?.message || '开通失败，请稍后重试', icon: 'fail' });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="credit-open-page">
      <div className="open-hero">
        <div className="open-title">开通 Mini 花呗</div>
        <div className="open-limit">¥{formatAmount(credit?.totalLimitFen || 500000)}</div>
        <div className="open-sub">固定虚拟额度 · 不接入真实征信</div>
      </div>

      <div className="open-body">
        <div className="feature-grid">
          {[
            ['scan', '扫一扫可用', '开通后可用于扫一扫付款'],
            ['receipt', '账单清晰', '每笔消费可在账单中查看'],
            ['shield', '按时还款', '保持良好使用记录'],
            ['lock', '额度隔离', '固定虚拟额度与真实额度隔离'],
          ].map(([icon, title, desc]) => (
            <div className="feature-item" key={title}>
              <div className="feature-icon">
                <IconSet name={icon as any} size={18} color="var(--h5-primary)" />
              </div>
              <div>
                <div className="feature-title">{title}</div>
                <div className="feature-desc">{desc}</div>
              </div>
            </div>
          ))}
        </div>

        <div className="rule-card">
          <div className="rule-title">规则说明</div>
          <div className="rule-line">• Mini 花呗为演示虚拟信用额度，仅用于功能体验。</div>
          <div className="rule-line">• 额度固定，不支持提现或普通转账。</div>
          <div className="rule-line">• 逾期、暂停或额度不足时不能发起花呗支付。</div>
          <div className="rule-line">• 请合理消费，按时还款。</div>
        </div>

        <div className="open-check">
          <Checkbox checked={checked} onChange={setChecked} />
          <span>我已了解 Mini 花呗为演示虚拟信用额度</span>
        </div>

        <div className={`open-submit${!checked || submitting ? ' disabled' : ''}`} onClick={() => !submitting && handleOpen()}>
          {submitting ? '开通中...' : '确认开通'}
        </div>
      </div>
    </div>
  );
};

export default CreditOpenPage;
