import React, { useEffect, useState } from 'react';
import { history, useParams, useLocation } from 'umi';
import { Card, Button, Result, SpinLoading } from 'antd-mobile';
import { CheckCircleFill, CloseCircleFill } from 'antd-mobile-icons';
import * as transferService from '@/services/transfer';
import { AmountDisplay } from '@/components/h5/AmountDisplay';
import { formatTime, maskName } from '@/utils/format';
import './index.less';

const TransferResultPage: React.FC = () => {
  const { id } = useParams();
  const location = useLocation();
  // 路由 state 仅兼容提交后首次跳转；刷新和从明细进入时以后端详情为准。
  const navState = (location.state || {}) as { payeeNickname?: string };
  const [loading, setLoading] = useState(true);
  const [result, setResult] = useState<transferService.TransferResult | null>(null);

  useEffect(() => {
    if (!id) return;
    // TCC 协调异步执行，初次状态通常为 PROCESSING；每 2 秒轮询一次，
    // 最多 15 次直到终态，避免用户手动刷新；超时后按当前状态展示。
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
          return;
        }
      } catch (error) {
        console.error('加载失败', error);
      }
      if (!cancelled) setLoading(false);
    };

    load();
    return () => {
      cancelled = true;
      if (timer) clearTimeout(timer);
    };
  }, [id]);

  if (loading) {
    return (
      <div className="loading-container">
        <SpinLoading />
      </div>
    );
  }

  const isSuccess = result?.status === 'SUCCESS';
  const isProcessing = result?.status === 'PROCESSING';

  const getStatusText = () => {
    if (isSuccess) return '转账成功';
    if (result?.status === 'REVERSED') return '转账已冲正';
    if (result?.status === 'CANCELLED') return '转账已取消';
    if (isProcessing) return '转账处理中';
    return '转账状态未知';
  };

  const getStatusDescription = () => {
    if (isSuccess) return '资金已到账';
    if (result?.status === 'REVERSED') return '资金已退回';
    if (result?.status === 'CANCELLED') return '转账已取消';
    if (isProcessing) return '请稍后查看结果';
    return '请联系客服查询';
  };

  return (
    <div className="transfer-result-page">
      <Result
        icon={isSuccess ? <CheckCircleFill /> : isProcessing ? <SpinLoading /> : <CloseCircleFill />}
        status={isSuccess ? 'success' : isProcessing ? 'waiting' : 'error'}
        title={getStatusText()}
        description={getStatusDescription()}
      />

      <Card className="result-card">
        <div className="result-row">
          <span className="result-label">交易号</span>
          <span className="result-value">{result?.transactionId || '-'}</span>
        </div>
        <div className="result-row">
          <span className="result-label">付款人</span>
          <span className="result-value">
            {result?.payerDisplayName || result?.payerUserId || '-'}
            {result?.payerMaskedAccountNumber && ` (${result.payerMaskedAccountNumber})`}
          </span>
        </div>
        <div className="result-row">
          <span className="result-label">收款人</span>
          <span className="result-value">
            {result?.payeeDisplayName || maskName(navState.payeeNickname) || result?.payeeUserId || '-'}
            {result?.payeeMaskedAccountNumber && ` (${result.payeeMaskedAccountNumber})`}
          </span>
        </div>
        <div className="result-row">
          <span className="result-label">金额</span>
          <span className="result-value">
            {result && <AmountDisplay amountFen={result.amountFen} />}
          </span>
        </div>
        <div className="result-row">
          <span className="result-label">备注</span>
          <span className="result-value">{result?.remark || '-'}</span>
        </div>
        <div className="result-row">
          <span className="result-label">时间</span>
          <span className="result-value">
            {result?.createdAt ? formatTime(result.createdAt) : '-'}
          </span>
        </div>
      </Card>

      <div className="result-actions">
        <Button block color="primary" onClick={() => history.push('/h5/home')}>
          返回首页
        </Button>
      </div>
    </div>
  );
};

export default TransferResultPage;
