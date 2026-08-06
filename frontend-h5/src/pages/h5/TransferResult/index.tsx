import React, { useEffect, useState } from 'react';
import { history, useSearchParams, useParams } from 'umi';
import { Card, Button, Result, SpinLoading } from 'antd-mobile';
import { CheckCircleFill, CloseCircleFill } from 'antd-mobile-icons';
import * as transferService from '@/services/transfer';
import { AmountDisplay } from '@/components/h5/AmountDisplay';
import { formatTime } from '@/utils/format';
import './index.less';

const TransferResultPage: React.FC = () => {
  const { id } = useParams();
  const [loading, setLoading] = useState(true);
  const [result, setResult] = useState<transferService.TransferResult | null>(null);

  useEffect(() => {
    if (id) {
      loadResult(id);
    }
  }, [id]);

  const loadResult = async (transactionId: string) => {
    try {
      const data = await transferService.getTransferStatus(transactionId);
      setResult(data);
    } catch (error) {
      console.error('加载失败', error);
    } finally {
      setLoading(false);
    }
  };

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
          <span className="result-label">收款人</span>
          <span className="result-value">{result?.payeeNickname || '-'}</span>
        </div>
        <div className="result-row">
          <span className="result-label">金额</span>
          <span className="result-value">
            {result && <AmountDisplay amountFen={result.amountFen} />}
          </span>
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
