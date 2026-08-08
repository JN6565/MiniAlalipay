import { history } from 'umi';
import { Button, Input, Toast } from 'antd-mobile';
import { useEffect, useState } from 'react';
import { bindBankCard, getRegisteredCards, RegisteredCard } from '@/services/bankCard';
import './index.less';

/** 身份证号格式：17 位数字加 1 位数字或 X/x。 */
const ID_CARD_PATTERN = /^\d{17}[\dXx]$/;
/** 中国大陆手机号：1 开头 11 位数字。 */
const PHONE_PATTERN = /^1\d{10}$/;

/**
 * 银行卡绑定页：输入注册时获得的卡号 + 三要素 → 绑定到用户账户。
 * 页面加载时同时获取已注册但未绑定的卡列表，供用户参考。
 */
const BankCardBindPage = () => {
  const [cardNumber, setCardNumber] = useState('');
  const [holderName, setHolderName] = useState('');
  const [idCard, setIdCard] = useState('');
  const [phone, setPhone] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [registeredCards, setRegisteredCards] = useState<RegisteredCard[]>([]);

  useEffect(() => {
    getRegisteredCards()
      .then(setRegisteredCards)
      .catch(() => {
        // 静默失败，用户仍可手动输入卡号
      });
  }, []);

  const handleSubmit = async () => {
    const digits = cardNumber.replace(/\D/g, '');
    if (digits.length < 16 || digits.length > 19) {
      Toast.show({ icon: 'fail', content: '卡号长度不正确' });
      return;
    }
    if (!holderName.trim()) {
      Toast.show({ icon: 'fail', content: '请输入持卡人姓名' });
      return;
    }
    if (!ID_CARD_PATTERN.test(idCard.trim())) {
      Toast.show({ icon: 'fail', content: '身份证号格式不正确' });
      return;
    }
    if (!PHONE_PATTERN.test(phone.trim())) {
      Toast.show({ icon: 'fail', content: '预留手机号格式不正确' });
      return;
    }

    setSubmitting(true);
    try {
      await bindBankCard({
        cardNumber: digits,
        holderName: holderName.trim(),
        idCard: idCard.trim(),
        phone: phone.trim(),
      });
      Toast.show({ icon: 'success', content: '绑定成功' });
      history.push('/h5/bank-cards');
    } catch (error: any) {
      Toast.show({ icon: 'fail', content: error?.message || '绑定失败，请稍后重试' });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="bank-card-bind-page">
      {registeredCards.length > 0 && (
        <div className="registered-cards-section">
          <div className="section-label">您已注册的银行卡（点击自动填充卡号）</div>
          {registeredCards.map((card) => (
            <div
              key={card.registrationId}
              className="registered-card-item"
              onClick={() => setCardNumber(card.cardBin + '****' + card.cardLast4)}
            >
              <span className="bank-name">{card.bankName}</span>
              <span className="card-last4">尾号 {card.cardLast4}</span>
            </div>
          ))}
          <div className="registered-tip">注：上方仅显示尾号，完整卡号请在注册时记录</div>
        </div>
      )}

      <div className="bind-section">
        <div className="section-label">卡号</div>
        <Input
          placeholder="请输入注册时获得的完整卡号"
          value={cardNumber}
          onChange={(value) => setCardNumber(value.replace(/\D/g, '').slice(0, 19))}
          inputMode="numeric"
          clearable
        />

        <div className="section-label">持卡人姓名</div>
        <Input placeholder="请输入持卡人姓名" value={holderName} onChange={setHolderName} maxLength={32} clearable />

        <div className="section-label">身份证号</div>
        <Input placeholder="请输入 18 位身份证号" value={idCard} onChange={setIdCard} maxLength={18} clearable />

        <div className="section-label">预留手机号</div>
        <Input
          placeholder="请输入银行预留手机号"
          value={phone}
          onChange={setPhone}
          inputMode="numeric"
          maxLength={11}
          clearable
        />
      </div>

      <div className="bind-tip">
        绑定前请确保已完成身份绑定。三要素信息将与注册记录及用户存储身份交叉比对。
      </div>

      <Button
        block
        color="primary"
        size="large"
        loading={submitting}
        disabled={cardNumber.replace(/\D/g, '').length < 16}
        onClick={handleSubmit}
      >
        绑定银行卡
      </Button>
    </div>
  );
};

export default BankCardBindPage;
