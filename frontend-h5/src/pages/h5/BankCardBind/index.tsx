import { history } from 'umi';
import { Input, Toast } from 'antd-mobile';
import { useEffect, useRef, useState } from 'react';
import { bindBankCard, getRegisteredCards, RegisteredCard } from '@/services/bankCard';
import { IconSet } from '@/components/h5/common';
import './index.less';

/** 身份证号格式：17 位数字加 1 位数字或 X/x。 */
const ID_CARD_PATTERN = /^\d{17}[\dXx]$/;
/** 中国大陆手机号：1 开头 11 位数字。 */
const PHONE_PATTERN = /^1\d{10}$/;

/** 卡号四位分组展示。 */
const groupCardNumber = (digits: string) => digits.replace(/(\d{4})(?=\d)/g, '$1 ');

/**
 * 银行卡绑定页：输入注册时获得的卡号 + 三要素 → 绑定到用户账户。
 * 页面加载时同时获取已注册但未绑定的卡列表（含解绑后释放回可绑定状态的卡），供用户参考。
 *
 * 短信验证码为前端模拟：点击获取后 Toast 展示 4 位演示码并 60s 倒计时，
 * 仅做本地校验，后端契约零变更。
 */
const BankCardBindPage = () => {
  const [cardNumber, setCardNumber] = useState('');
  const [holderName, setHolderName] = useState('');
  const [idCard, setIdCard] = useState('');
  const [phone, setPhone] = useState('');
  const [smsCode, setSmsCode] = useState('');
  const [expectedCode, setExpectedCode] = useState('');
  const [countdown, setCountdown] = useState(0);
  const [agreed, setAgreed] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [registeredCards, setRegisteredCards] = useState<RegisteredCard[]>([]);
  const timerRef = useRef<number | null>(null);

  useEffect(() => {
    getRegisteredCards()
      .then(setRegisteredCards)
      .catch(() => {
        // 静默失败，用户仍可手动输入卡号
      });
    return () => {
      if (timerRef.current) window.clearInterval(timerRef.current);
    };
  }, []);

  /** 发送验证码：前端模拟，Toast 展示演示码并启动 60s 倒计时。 */
  const handleSendCode = () => {
    if (countdown > 0) return;
    if (!PHONE_PATTERN.test(phone.trim())) {
      Toast.show({ icon: 'fail', content: '请先输入正确的预留手机号' });
      return;
    }
    const code = String(Math.floor(1000 + Math.random() * 9000));
    setExpectedCode(code);
    Toast.show({ content: `【演示】短信验证码：${code}`, duration: 6000 });
    setCountdown(60);
    timerRef.current = window.setInterval(() => {
      setCountdown((prev) => {
        if (prev <= 1 && timerRef.current) {
          window.clearInterval(timerRef.current);
          timerRef.current = null;
        }
        return Math.max(0, prev - 1);
      });
    }, 1000);
  };

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
    if (!expectedCode) {
      Toast.show({ icon: 'fail', content: '请先获取短信验证码' });
      return;
    }
    if (smsCode.trim() !== expectedCode) {
      Toast.show({ icon: 'fail', content: '短信验证码不正确' });
      return;
    }
    if (!agreed) {
      Toast.show({ icon: 'fail', content: '请先阅读并同意《银行卡绑定协议》' });
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

  const cardDigits = cardNumber.replace(/\D/g, '');

  return (
    <div className="bank-card-bind-page">
      {registeredCards.length > 0 && (
        <div className="bcb-card registered-cards-section">
          <div className="section-label">您已注册的银行卡</div>
          {registeredCards.map((card, index) => (
            <div
              key={card.registrationId}
              className="registered-card-item"
              style={index === registeredCards.length - 1 ? { borderBottom: 'none' } : undefined}
              onClick={() =>
                Toast.show({ content: '完整卡号仅在注册时返回一次，请手动输入' })
              }
            >
              <span className="registered-card-icon">
                <IconSet name="card" size={15} color="var(--h5-primary)" />
              </span>
              <span className="bank-name">{card.bankName}</span>
              <span className="card-last4">尾号 {card.cardLast4}</span>
              <IconSet name="chevronRight" size={14} color="var(--h5-text-3)" />
            </div>
          ))}
          <div className="registered-tip">注：上方仅显示尾号，完整卡号仅在注册时返回一次</div>
        </div>
      )}

      {/* 表单卡 */}
      <div className="bcb-card bind-form-card">
        <div className="field-label">银行卡号</div>
        <div className="field-box">
          <Input
            className="field-input mono"
            placeholder="请输入注册时获得的完整卡号"
            value={groupCardNumber(cardDigits)}
            onChange={(value) => setCardNumber(value.replace(/\D/g, '').slice(0, 19))}
            inputMode="numeric"
          />
          <IconSet name="card" size={16} color="var(--h5-text-3)" />
        </div>

        <div className="field-label">持卡人姓名</div>
        <div className="field-box">
          <Input
            className="field-input"
            placeholder="请输入持卡人姓名"
            value={holderName}
            onChange={setHolderName}
            maxLength={32}
          />
        </div>

        <div className="field-label">身份证号</div>
        <div className="field-box">
          <Input
            className="field-input"
            placeholder="请输入 18 位身份证号"
            value={idCard}
            onChange={(value) => setIdCard(value.replace(/[^\dXx]/g, '').slice(0, 18))}
          />
          <IconSet name="shield" size={16} color="var(--h5-text-3)" />
        </div>

        <div className="field-label">预留手机号</div>
        <div className="field-box">
          <Input
            className="field-input"
            placeholder="请输入银行预留手机号"
            value={phone}
            onChange={(value) => setPhone(value.replace(/\D/g, '').slice(0, 11))}
            inputMode="numeric"
          />
          <IconSet name="phone" size={16} color="var(--h5-text-3)" />
        </div>

        <div className="field-label">短信验证码</div>
        <div className="sms-row">
          <div className="field-box sms-box">
            <Input
              className="field-input code"
              placeholder="请输入验证码"
              value={smsCode}
              onChange={(value) => setSmsCode(value.replace(/\D/g, '').slice(0, 4))}
              inputMode="numeric"
            />
          </div>
          <span
            className={`sms-send${countdown > 0 ? ' cooling' : ''}`}
            onClick={handleSendCode}
          >
            {countdown > 0 ? `${countdown}s 后重发` : '获取验证码'}
          </span>
        </div>
      </div>

      {/* 协议勾选 */}
      <div className="bind-agreement" onClick={() => setAgreed((v) => !v)}>
        <span className={`agreement-box${agreed ? ' checked' : ''}`}>
          {agreed && <IconSet name="check" size={10} color="#fff" />}
        </span>
        <span className="agreement-text">我已阅读并同意《银行卡绑定协议》</span>
      </div>

      <div className="bind-tip">
        绑定前请确保已完成身份绑定。三要素信息将与注册记录及用户存储身份交叉比对。
      </div>

      <div
        className={`h5-btn-gradient bind-submit${submitting || cardDigits.length < 16 ? ' disabled' : ''}`}
        onClick={() => !submitting && cardDigits.length >= 16 && handleSubmit()}
      >
        {submitting ? '绑定中...' : '确认绑定'}
      </div>
    </div>
  );
};

export default BankCardBindPage;
