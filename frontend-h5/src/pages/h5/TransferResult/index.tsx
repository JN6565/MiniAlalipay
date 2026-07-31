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

  return (
    <div className="transfer-result-page">
      <Result
        icon={isSuccess ? <CheckCircleFill /> : <CloseCircleFill />}
        status={isSuccess ? 'success' : 'error'}
        title={isSuccess ? '转账成功' : '转账处理中'}
        description={isSuccess ? '资金已到账' : '请稍后查看结果'}
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
