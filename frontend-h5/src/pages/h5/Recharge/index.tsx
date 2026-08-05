import React, { useState } from 'react';
import { history } from 'umi';
import { Toast, Button, Input } from 'antd-mobile';
import './index.less';

const RechargePage: React.FC = () => {
  const [amount, setAmount] = useState('');
  const [loading, setLoading] = useState(false);

  // 预设金额选项
  const presetAmounts = [100, 200, 500, 1000, 2000, 5000];

  const handleRecharge = async () => {
    const amountNum = parseFloat(amount);

    if (!amount || isNaN(amountNum) || amountNum <= 0) {
      Toast.show({ content: '请输入有效的充值金额', icon: 'fail' });
      return;
    }

    if (amountNum < 0.01) {
      Toast.show({ content: '充值金额不能小于0.01元', icon: 'fail' });
      return;
    }

    if (amountNum > 50000) {
      Toast.show({ content: '单次充值不能超过50000元', icon: 'fail' });
      return;
    }

    setLoading(true);

    try {
      // TODO: 调用充值接口
      // await recharge({ amountFen: Math.round(amountNum * 100) });

      Toast.show({ content: '充值功能开发中...', icon: 'success' });

      // 模拟充值成功后返回首页
      setTimeout(() => {
        history.push('/h5/home');
      }, 1500);
    } catch (error: any) {
      console.error('充值失败:', error);
      Toast.show({ content: '充值失败，请重试', icon: 'fail' });
    } finally {
      setLoading(false);
    }
  };

  const selectPreset = (value: number) => {
    setAmount(value.toString());
  };

  return (
    <div className="recharge-page">
      {/* 头部 */}
      <div className="header">
        <div className="back" onClick={() => history.goBack()}>
          ← 返回
        </div>
        <h1>充值</h1>
        <div className="placeholder" />
      </div>

      {/* 充值说明 */}
      <div className="notice">
        <p>💰 模拟充值 - 虚拟资金</p>
        <p>充值金额将添加到您的可用余额</p>
      </div>

      {/* 金额输入 */}
      <div className="amount-section">
        <div className="amount-label">充值金额（元）</div>
        <div className="amount-input">
          <span className="currency">¥</span>
          <Input
            type="number"
            placeholder="请输入充值金额"
            value={amount}
            onChange={(value) => setAmount(value)}
          />
        </div>

        {/* 预设金额 */}
        <div className="preset-amounts">
          {presetAmounts.map((value) => (
            <div
              key={value}
              className={`preset-item ${amount === value.toString() ? 'active' : ''}`}
              onClick={() => selectPreset(value)}
            >
              {value}元
            </div>
          ))}
        </div>
      </div>

      {/* 充值规则 */}
      <div className="rules">
        <h3>充值规则</h3>
        <ul>
          <li>单笔限额：0.01 - 50,000 元</li>
          <li>每日限额：100,000 元</li>
          <li>每日次数：10 次</li>
          <li>充值方式：模拟充值（虚拟资金）</li>
        </ul>
      </div>

      {/* 充值按钮 */}
      <div className="action">
        <Button
          color="primary"
          size="large"
          block
          loading={loading}
          onClick={handleRecharge}
          disabled={!amount || loading}
        >
          立即充值
        </Button>
      </div>
    </div>
  );
};

export default RechargePage;
