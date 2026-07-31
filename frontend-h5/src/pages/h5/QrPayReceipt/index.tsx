import React from 'react';
import { history } from 'umi';
import { Button, Result, Card } from 'antd-mobile';
import { CheckCircleFill } from 'antd-mobile-icons';
import './index.less';

const QrPayReceiptPage: React.FC = () => {
  return (
    <div className="qr-pay-receipt-page">
      <Result
        icon={<CheckCircleFill />}
        status="success"
        title="支付成功"
        description="虚拟资金支付完成"
      />

      <Card className="receipt-card">
        <div className="receipt-row">
          <span className="receipt-label">交易号</span>
          <span className="receipt-value">-</span>
        </div>
        <div className="receipt-row">
          <span className="receipt-label">商户</span>
          <span className="receipt-value">-</span>
        </div>
        <div className="receipt-row">
          <span className="receipt-label">金额</span>
          <span className="receipt-value">-</span>
        </div>
        <div className="receipt-row">
          <span className="receipt-label">时间</span>
          <span className="receipt-value">-</span>
        </div>
      </Card>

      <Button block color="primary" onClick={() => history.push('/h5/home')}>
        返回首页
      </Button>
    </div>
  );
};

export default QrPayReceiptPage;
