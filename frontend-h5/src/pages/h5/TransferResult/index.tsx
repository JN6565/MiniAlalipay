import React, { useEffect, useState } from 'react';
import { history, useParams, useLocation } from 'umi';
import { SpinLoading } from 'antd-mobile';
import * as transferService from '@/services/transfer';
import { formatBalance } from '@/services/bankCard';
import { IconSet, type IconName } from '@/components/h5/common';
import { formatTime, maskName } from '@/utils/format';
import './index.less';

const TransferResultPage: React.FC = () => {
  const { id } = useParams();
  const location = useLocation();
  // 路由 state 仅兼容提交后首次跳转；刷新和从明细进入时以后端详情为准。
  const navState = (location.state || {}) as {
    payeeNickname?: string;
    /** 提交接口返回的初始交易数据，后端不可用时降级展示 */
    initialResult?: transferService.TransferResult;
  };
  // 提交后跳转时路由 state 必带收款人昵称；此时直接展示”处理中”乐观首屏，不等首次轮询
  const justSubmitted = !!navState.payeeNickname;
  const [loading, setLoading] = useState(!justSubmitted);
  const [result, setResult] = useState<transferService.TransferResult | null>(navState.initialResult ?? null);

  useEffect(() => {
    if (!id) return;
    // TCC 协调异步执行，初次状态通常为 PROCESSING；轮询直到终态，避免用户手动刷新；
    // 超时后按当前状态展示。提交后首次查询延迟 1 秒：受理瞬间必然还是 PROCESSING，
    // 立即查询只会浪费一次请求；后续保持 2 秒间隔，最多 15 次。
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let attempts = 0;

    const load = async () => {
      try {
        const data = await transferService.getTransferStatus(id);
        if (cancelled) return;
        setResult(data);
        if ((data.status === 'PROCESSING' || data.status === 'COMPENSATING') && attempts < 15) {
          attempts += 1;
          timer = setTimeout(load, 2000);
          return; // 继续轮询，不设置 loading = false
        }
        // 终态或达到最大轮询次数，停止 loading
        if (!cancelled) setLoading(false);
      } catch (error) {
        console.error('加载失败', error);
        // 后端不可用时立即停止轮询，避免无限重试
        if (!cancelled) setLoading(false);
      }
    };

    timer = setTimeout(load, justSubmitted ? 1000 : 0);
    return () => {
      cancelled = true;
      if (timer) clearTimeout(timer);
    };
  }, [id, justSubmitted]);

  if (loading) {
    return (
      <div className="loading-container">
        <SpinLoading />
      </div>
    );
  }

  const isSuccess = result?.status === 'SUCCESS';
  const isProcessing = result?.status === 'PROCESSING' || result?.status === 'COMPENSATING';

  // 三态统一版式：success 绿 / fail 红 / processing 橙，圆形状态图标 + 标题 + 金额 + 说明
  const statusCfg = isSuccess
    ? { color: 'var(--h5-success)', icon: 'check' as IconName }
    : isProcessing
      ? { color: 'var(--h5-warning)', icon: 'clock' as IconName }
      : { color: 'var(--h5-amount-in)', icon: 'close' as IconName };

  const getStatusText = () => {
    if (isSuccess) return '转账成功';
    if (result?.status === 'REVERSED') return '转账失败';
    if (result?.status === 'CANCELLED') return '转账已取消';
    if (isProcessing) return '处理中';
    return '转账状态未知';
  };

  const getStatusDescription = () => {
    if (isSuccess) return '资金已实时到账对方账户';
    if (result?.status === 'REVERSED') return '资金已冲正退回，可稍后重试';
    if (result?.status === 'CANCELLED') return '转账已取消，资金未扣除';
    if (isProcessing) return '系统处理中，请稍后在账单中查看结果';
    return '请联系客服查询';
  };

  const detailRows: Array<[string, string]> = [
    ['交易号', result?.transactionId || '-'],
    [
      '付款人',
      `${result?.payerDisplayName || result?.payerUserId || '-'}` +
        (result?.payerMaskedAccountNumber ? ` (${result.payerMaskedAccountNumber})` : ''),
    ],
    [
      '收款人',
      `${result?.payeeDisplayName || maskName(navState.payeeNickname) || result?.payeeUserId || '-'}` +
        (result?.payeeMaskedAccountNumber ? ` (${result.payeeMaskedAccountNumber})` : ''),
    ],
    ['备注', result?.remark || '-'],
    ['时间', result?.createdAt ? formatTime(result.createdAt) : '-'],
  ];

  return (
    <div className="transfer-result-page">
      {/* 状态区：圆形状态图标 + 标题 + 金额 + 说明 + 双入口 */}
      <div className="result-status">
        <div className="status-icon" style={{ background: statusCfg.color }}>
          <IconSet name={statusCfg.icon} size={26} width={2.4} color="#fff" />
        </div>
        <div className="status-title">{getStatusText()}</div>
        {result && (
          <div className="status-amount">¥{formatBalance(result.amountFen)}</div>
        )}
        <div className="status-desc">{getStatusDescription()}</div>
        <div className="status-actions">
          <div className="result-btn outline" onClick={() => history.push('/h5/home')}>
            返回首页
          </div>
          <div
            className="h5-btn-gradient result-btn"
            onClick={() => history.push('/h5/account/transactions')}
          >
            查看账单
          </div>
        </div>
      </div>

      {/* 交易凭证字段 */}
      <div className="result-receipt">
        <div className="receipt-divider" />
        {detailRows.map(([label, value]) => (
          <div className="receipt-row" key={label}>
            <span className="receipt-label">{label}</span>
            <span className="receipt-value">{value}</span>
          </div>
        ))}
      </div>
    </div>
  );
};

export default TransferResultPage;
