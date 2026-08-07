import React, { useEffect, useState } from 'react';
import { useParams, history } from 'umi';
import { Button, Result, Card, SpinLoading } from 'antd-mobile';
import { CheckCircleFill } from 'antd-mobile-icons';
import * as collectionService from '@/services/collection';
import './index.less';

const maskName = (name?: string) => {
  if (!name) return '***';
  if (name.length <= 1) return name + '**';
  return name[0] + '*'.repeat(name.length - 1);
};

const CollectionResultPage: React.FC = () => {
  const { id } = useParams();
  const [order, setOrder] = useState<collectionService.CollectionOrder | null>(null);

  useEffect(() => {
    document.title = '转账结果';
    if (id) {
      collectionService.getOrderStatus(id).then(setOrder).catch(() => {});
    }
  }, [id]);

  if (!order) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', paddingTop: 120 }}>
        <SpinLoading />
      </div>
    );
  }

  return (
    <div className="collection-result-page">
      <Result
        icon={<CheckCircleFill />}
        status="success"
        title="转账成功"
        description="资金已到账"
      />

      <Card className="result-card">
        <div className="result-row">
          <span className="result-label">交易号</span>
          <span className="result-value">{order.orderId}</span>
        </div>
        <div className="result-row">
          <span className="result-label">付款人</span>
          <span className="result-value">{maskName(order.payerName)}</span>
        </div>
        <div className="result-row">
          <span className="result-label">收款人</span>
          <span className="result-value">{maskName(order.payeeName)}</span>
        </div>
        <div className="result-row">
          <span className="result-label">金额</span>
          <span className="result-value">¥{((order.amountFen || 0) / 100).toFixed(2)}</span>
        </div>
      </Card>

      <Button block color="primary" onClick={() => history.push('/h5/home')}>
        返回首页
      </Button>
    </div>
  );
};

export default CollectionResultPage;
