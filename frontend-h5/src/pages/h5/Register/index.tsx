import React, { useState, useRef, useEffect } from 'react';
import { history } from '@umijs/max';
import { Toast } from 'antd-mobile';
import { register } from '@/services/auth';
import { ApiError } from '@/services/request';
import { seedNicknamePreference } from '@/utils/profile';
import { IconSet } from '@/components/h5/common';
import './index.less';

type FieldName = 'phoneNumber' | 'smsCode' | 'nickname' | 'loginPassword' |
  'confirmPassword' | 'paymentPassword' | 'confirmPaymentPassword';

const PHONE_PATTERN = /^1[3-9]\d{9}$/;

const RegisterPage: React.FC = () => {
  const [step, setStep] = useState(0);
  const [phoneNumber, setPhoneNumber] = useState('');
  const [smsCode, setSmsCode] = useState('');
  const [expectedCode, setExpectedCode] = useState('');
  const [countdown, setCountdown] = useState(0);
  const timerRef = useRef<number | null>(null);
  const [nickname, setNickname] = useState('');
  const [loginPassword, setLoginPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [paymentPassword, setPaymentPassword] = useState('');
  const [confirmPaymentPassword, setConfirmPaymentPassword] = useState('');
  const [showLoginPwd, setShowLoginPwd] = useState(false);
  const [showConfirmPwd, setShowConfirmPwd] = useState(false);
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState<Partial<Record<FieldName, string>>>({});

  useEffect(() => () => {
    if (timerRef.current) window.clearInterval(timerRef.current);
  }, []);

  const validatePhone = (value: string) => !value ? '请输入手机号' : PHONE_PATTERN.test(value) ? '' : '请输入正确的11位手机号';
  const validateLoginPassword = (value: string) => {
    if (!value) return '请输入登录密码';
    if (value.length < 8 || value.length > 32) return '登录密码长度必须为8-32位';
    return /[A-Z]/.test(value) && /[a-z]/.test(value) && /\d/.test(value)
      ? '' : '登录密码必须包含大写字母、小写字母和数字';
  };
  const validatePaymentPassword = (value: string) => /^\d{6}$/.test(value) ? '' : '支付密码必须为6位数字';
  const setFieldError = (field: FieldName, message: string) => setErrors((current) => ({ ...current, [field]: message }));
  const showErrors = (nextErrors: Partial<Record<FieldName, string>>) => {
    setErrors((current) => ({ ...current, ...nextErrors }));
    const firstError = Object.values(nextErrors).find(Boolean);
    if (firstError) Toast.show({ content: firstError, icon: 'fail' });
    return !firstError;
  };

  // 验证码前端模拟：Toast 展示 4 位演示码 + 60s 倒计时，后端零变更
  const handleSendCode = () => {
    if (countdown > 0) return;
    if (!PHONE_PATTERN.test(phoneNumber.trim())) {
      Toast.show({ icon: 'fail', content: '请先输入正确的手机号' });
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

  const handleNext = () => {
    const nextErrors = {
      phoneNumber: validatePhone(phoneNumber),
      smsCode: !expectedCode ? '请先获取短信验证码'
        : smsCode !== expectedCode ? '短信验证码不正确' : '',
      nickname: nickname.trim().length <= 20 ? '' : '昵称不能超过20位',
      loginPassword: validateLoginPassword(loginPassword),
      confirmPassword: !confirmPassword ? '请再次输入登录密码' : loginPassword === confirmPassword ? '' : '两次登录密码不一致',
    };
    if (showErrors(nextErrors)) setStep(1);
  };

  const handleSubmit = async () => {
    const nextErrors = {
      paymentPassword: validatePaymentPassword(paymentPassword),
      confirmPaymentPassword: !confirmPaymentPassword ? '请再次输入支付密码' : paymentPassword === confirmPaymentPassword ? '' : '两次支付密码不一致',
    };
    if (!showErrors(nextErrors)) return;

    setLoading(true);
    try {
      const result = await register({ phoneNumber, nickname: nickname.trim() || undefined, loginPassword, paymentPassword });
      localStorage.setItem('accessToken', result.accessToken);
      localStorage.setItem('userId', result.userId);
      localStorage.setItem('accountNumber', result.accountNumber);
      // 昵称为浏览器本地展示偏好：仅首次初始化时用服务端昵称填充，保留用户本地编辑
      seedNicknamePreference(result.nickname);
      Toast.show({ icon: 'success', content: `注册成功，账户号：${result.accountNumber}`, duration: 3000 });
      history.replace('/h5/home');
    } catch (error: any) {
      const code = error instanceof ApiError ? error.code : 'UNKNOWN';
      const messages: Record<string, string> = {
        PHONE_NUMBER_EXISTS: '该手机号已注册，请直接登录',
        PASSWORD_POLICY_VIOLATION: '登录密码不符合安全规则，需8-32位且包含大小写字母和数字',
        REGISTRATION_PROCESSING: '开户暂未完成，请稍后重试登录',
        NETWORK_ERROR: '网络异常，请检查网络连接后重试',
      };
      Toast.show({ content: messages[code] || error.message || '注册失败', icon: 'fail' });
    } finally {
      setLoading(false);
    }
  };

  const FieldError = ({ field }: { field: FieldName }) => errors[field] ? <div className="register-field-error">{errors[field]}</div> : null;

  return (
    <div className="register-page">
      {/* 品牌头图 */}
      <div className="register-hero">
        <div className="register-hero-title">创建账号</div>
        <div className="register-hero-sub">一分钟完成注册</div>
      </div>

      <div className="register-card">
        {/* 步骤指示 */}
        <div className="register-steps">
          <div className={`register-step ${step === 0 ? 'active' : 'done'}`}>
            <span className="register-step-dot">{step === 0 ? '1' : <IconSet name="check" size={10} />}</span>基本信息
          </div>
          <div className="register-step-line" />
          <div className={`register-step ${step === 1 ? 'active' : ''}`}>
            <span className="register-step-dot">2</span>设置支付密码
          </div>
        </div>

        {step === 0 ? (
          <>
            <div className="register-field-box">
              <input
                className="register-field-input"
                type="tel"
                inputMode="numeric"
                maxLength={11}
                placeholder="手机号"
                value={phoneNumber}
                aria-invalid={Boolean(errors.phoneNumber)}
                onBlur={() => setFieldError('phoneNumber', validatePhone(phoneNumber))}
                onChange={(e) => { const value = e.target.value.replace(/\D/g, ''); setPhoneNumber(value); if (errors.phoneNumber) setFieldError('phoneNumber', validatePhone(value)); }}
              />
            </div>
            <FieldError field="phoneNumber" />

            {/* 短信验证码（前端模拟） */}
            <div className="register-sms-row">
              <div className="register-field-box sms-box">
                <input
                  className="register-field-input"
                  type="text"
                  inputMode="numeric"
                  maxLength={4}
                  placeholder="短信验证码"
                  value={smsCode}
                  aria-invalid={Boolean(errors.smsCode)}
                  onChange={(e) => { const value = e.target.value.replace(/\D/g, ''); setSmsCode(value); if (errors.smsCode) setFieldError('smsCode', ''); }}
                />
              </div>
              <span
                className={`register-sms-send ${countdown > 0 ? 'cooling' : ''}`}
                onClick={handleSendCode}
              >
                {countdown > 0 ? `${countdown}s 后重发` : '获取验证码'}
              </span>
            </div>
            <FieldError field="smsCode" />

            <div className="register-field-box">
              <input
                className="register-field-input"
                type="text"
                maxLength={20}
                placeholder="昵称（选填，默认使用手机号脱敏值）"
                value={nickname}
                aria-invalid={Boolean(errors.nickname)}
                onBlur={() => setFieldError('nickname', nickname.trim().length <= 20 ? '' : '昵称不能超过20位')}
                onChange={(e) => { setNickname(e.target.value); if (errors.nickname) setFieldError('nickname', e.target.value.trim().length <= 20 ? '' : '昵称不能超过20位'); }}
              />
            </div>
            <FieldError field="nickname" />

            <div className="register-field-box">
              <input
                className="register-field-input"
                type={showLoginPwd ? 'text' : 'password'}
                maxLength={32}
                placeholder="登录密码（8-32位，含大小写字母和数字）"
                value={loginPassword}
                aria-invalid={Boolean(errors.loginPassword)}
                onBlur={() => setFieldError('loginPassword', validateLoginPassword(loginPassword))}
                onChange={(e) => { setLoginPassword(e.target.value); if (errors.loginPassword) setFieldError('loginPassword', validateLoginPassword(e.target.value)); if (confirmPassword) setFieldError('confirmPassword', e.target.value === confirmPassword ? '' : '两次登录密码不一致'); }}
              />
              <span className="register-eye" onClick={() => setShowLoginPwd((v) => !v)}>
                <IconSet name={showLoginPwd ? 'eyeOn' : 'eyeOff'} size={16} color="#94a3ba" />
              </span>
            </div>
            <FieldError field="loginPassword" />

            <div className="register-field-box">
              <input
                className="register-field-input"
                type={showConfirmPwd ? 'text' : 'password'}
                maxLength={32}
                placeholder="确认登录密码"
                value={confirmPassword}
                aria-invalid={Boolean(errors.confirmPassword)}
                onBlur={() => setFieldError('confirmPassword', !confirmPassword ? '请再次输入登录密码' : loginPassword === confirmPassword ? '' : '两次登录密码不一致')}
                onChange={(e) => { setConfirmPassword(e.target.value); if (errors.confirmPassword) setFieldError('confirmPassword', !e.target.value ? '请再次输入登录密码' : loginPassword === e.target.value ? '' : '两次登录密码不一致'); }}
              />
              <span className="register-eye" onClick={() => setShowConfirmPwd((v) => !v)}>
                <IconSet name={showConfirmPwd ? 'eyeOn' : 'eyeOff'} size={16} color="#94a3ba" />
              </span>
            </div>
            <FieldError field="confirmPassword" />

            <div className="register-submit" onClick={handleNext}>下一步</div>
          </>
        ) : (
          <>
            <div className="register-field-box">
              <input
                className="register-field-input"
                type="password"
                inputMode="numeric"
                maxLength={6}
                placeholder="支付密码（6位数字）"
                value={paymentPassword}
                aria-invalid={Boolean(errors.paymentPassword)}
                onBlur={() => setFieldError('paymentPassword', validatePaymentPassword(paymentPassword))}
                onChange={(e) => { const value = e.target.value.replace(/\D/g, ''); setPaymentPassword(value); if (errors.paymentPassword) setFieldError('paymentPassword', validatePaymentPassword(value)); if (confirmPaymentPassword) setFieldError('confirmPaymentPassword', value === confirmPaymentPassword ? '' : '两次支付密码不一致'); }}
              />
            </div>
            <FieldError field="paymentPassword" />

            <div className="register-field-box">
              <input
                className="register-field-input"
                type="password"
                inputMode="numeric"
                maxLength={6}
                placeholder="确认支付密码"
                value={confirmPaymentPassword}
                aria-invalid={Boolean(errors.confirmPaymentPassword)}
                onBlur={() => setFieldError('confirmPaymentPassword', !confirmPaymentPassword ? '请再次输入支付密码' : paymentPassword === confirmPaymentPassword ? '' : '两次支付密码不一致')}
                onChange={(e) => { const value = e.target.value.replace(/\D/g, ''); setConfirmPaymentPassword(value); if (errors.confirmPaymentPassword) setFieldError('confirmPaymentPassword', !value ? '请再次输入支付密码' : paymentPassword === value ? '' : '两次支付密码不一致'); }}
              />
            </div>
            <FieldError field="confirmPaymentPassword" />

            <div className={`register-submit ${loading ? 'disabled' : ''}`} onClick={() => { if (!loading) handleSubmit(); }}>
              {loading ? '注册中...' : '完成注册'}
            </div>
            <div className="register-back-step" onClick={() => setStep(0)}>返回上一步</div>
          </>
        )}

        <div className="register-login-tip">
          已有账号？<span onClick={() => history.push('/h5/login')}>立即登录</span>
        </div>
      </div>
    </div>
  );
};

export default RegisterPage;
