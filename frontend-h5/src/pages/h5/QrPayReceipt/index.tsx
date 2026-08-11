import React from 'react';
import { history, useParams, useLocation } from 'umi';
import { IconSet } from '@/components/h5/common';
import { formatBalance } from '@/services/bankCard';
import { formatTime } from '@/utils/format';
import './index.less';

/**
 * 扫码支付回执页（V2）：凭证样式 = 成功圆标 + 金额 + 虚线分隔 + 关键字段 + 完成按钮。
 * 回执无独立查询接口，展示字段由付款页通过路由 state 携带。
 */
const QrPayReceiptPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const location = useLocation();
  const navState = (location.state || {}) as {
    payeeName?: string;
    amountFen?: number;
    fundingSource?: 'BALANCE' | 'MINI_CREDIT' | 'BANK_CARD';
  };

  const fundingLabel =
    navState.fundingSource === 'MINI_CREDIT'
      ? 'Mini 花呗'
      : navState.fundingSource === 'BANK_CARD'
        ? '银行卡'
        : '账户余额';

  const rows: Array<[string, string]> = [
    ['收款方', navState.payeeName || '-'],
    ['支付时间', formatTime(new Date().toISOString(), 'YYYY-MM-DD HH:mm')],
    ['支付方式', fundingLabel],
    ['交易单号', id || '-'],
  ];

  return (
    <div className="qr-pay-receipt-page">
      <div className="receipt-head">
        <div className="receipt-icon">
          <IconSet name="check" size={22} width={2.4} color="#fff" />
        </div>
        <div className="receipt-title">支付成功</div>
        {navState.amountFen != null && (
          <div className="receipt-amount">¥{formatBalance(navState.amountFen)}</div>
        )}
      </div>

      <div className="receipt-divider" />

      {rows.map(([label, value]) => (
        <div className="receipt-row" key={label}>
          <span className="receipt-label">{label}</span>
          <span className="receipt-value">{value}</span>
        </div>
      ))}

      <div className="h5-btn-gradient receipt-done" onClick={() => history.push('/h5/home')}>
        完成
      </div>
    </div>
  );
};

export default QrPayReceiptPage;
