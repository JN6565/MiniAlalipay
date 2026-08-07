import React, { useState } from 'react';
import { history } from 'umi';
import { Toast, Button, Input, Dialog } from 'antd-mobile';
import * as rechargeService from '@/services/recharge';
import { AMOUNT_MIN, AMOUNT_MAX, DAILY_RECHARGE_LIMIT_FEN, DAILY_RECHARGE_COUNT } from '@/constants';
import './index.less';

const RechargePage: React.FC = () => {
  const [amount, setAmount] = useState('');
  const [loading, setLoading] = useState(false);
  const [currentIdempotencyKey, setCurrentIdempotencyKey] = useState<string | null>(null);

  // 预设金额选项
  const presetAmounts = [100, 200, 500, 1000, 2000, 5000];

  // 生成幂等键
  const generateIdempotencyKey = (): string => {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
      const r = (Math.random() * 16) | 0;
      return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
    });
  };

  const handleRecharge = async () => {
    const amountNum = parseFloat(amount);

    if (!amount || isNaN(amountNum) || amountNum <= 0) {
      Toast.show({ content: '请输入有效的充值金额', icon: 'fail' });
      return;
    }

    if (amountNum < AMOUNT_MIN) {
      Toast.show({ content: `充值金额不能小于${AMOUNT_MIN}元`, icon: 'fail' });
      return;
    }

    if (amountNum > AMOUNT_MAX) {
      Toast.show({ content: `单次充值不能超过${AMOUNT_MAX}元`, icon: 'fail' });
      return;
    }

    // 大额充值确认
    if (amountNum >= 5000) {
      const confirmed = await Dialog.confirm({
        content: `确认充值 ¥${amountNum.toFixed(2)} 元？`,
        confirmText: '确认充值',
        cancelText: '取消',
      });
      if (!confirmed) return;
    }

    setLoading(true);

    // 生成新的幂等键（首次请求）或重用现有的（重试）
    const idempotencyKey = currentIdempotencyKey || generateIdempotencyKey();
    if (!currentIdempotencyKey) {
      setCurrentIdempotencyKey(idempotencyKey);
    }

    try {
      const amountFen = Math.round(amountNum * 100);
      const result = await rechargeService.createRecharge(amountFen, idempotencyKey);

      Toast.show({ content: '充值成功！', icon: 'success' });
      setCurrentIdempotencyKey(null); // 清除幂等键

      // 充值成功后跳转到首页
      setTimeout(() => {
        history.push('/h5/home');
      }, 1000);
    } catch (error: any) {
      console.error('充值失败:', error);
      // 超时错误不清除幂等键，允许重试
      if (error.code !== 'ECONNABORTED') {
        setCurrentIdempotencyKey(null); // 非超时错误清除幂等键
      }
      Toast.show({ content: error.message || '充值失败，请重试', icon: 'fail' });
    } finally {
      setLoading(false);
    }
  };

  const selectPreset = (value: number) => {
    setAmount(value.toString());
  };

  return (
    <div className="recharge-page">
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
          <li>单笔限额：{AMOUNT_MIN} - {AMOUNT_MAX.toLocaleString()} 元</li>
          <li>每日限额：{(DAILY_RECHARGE_LIMIT_FEN / 100).toLocaleString()} 元</li>
          <li>每日次数：{DAILY_RECHARGE_COUNT} 次</li>
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
