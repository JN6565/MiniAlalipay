import React from 'react';
import { history } from 'umi';
import { Button, Result, Card } from 'antd-mobile';
import { CheckCircleFill } from 'antd-mobile-icons';
import './index.less';

const CollectionResultPage: React.FC = () => {
  return (
    <div className="collection-result-page">
      <Result
        icon={<CheckCircleFill />}
        status="success"
        title="收款成功"
        description="资金已到账"
      />

      <Card className="result-card">
        <div className="result-row">
          <span className="result-label">交易号</span>
          <span className="result-value">-</span>
        </div>
        <div className="result-row">
          <span className="result-label">付款人</span>
          <span className="result-value">-</span>
        </div>
        <div className="result-row">
          <span className="result-label">金额</span>
          <span className="result-value">-</span>
        </div>
        <div className="result-row">
          <span className="result-label">时间</span>
          <span className="result-value">-</span>
        </div>
      </Card>

      <Button block color="primary" onClick={() => history.push('/h5/home')}>
        返回首页
      </Button>
    </div>
  );
};

export default CollectionResultPage;
