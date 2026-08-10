import React, { useState } from 'react';
import { history } from '@umijs/max';
import { Toast } from 'antd-mobile';
import { register } from '@/services/auth';
import { ApiError } from '@/services/request';
import { seedNicknamePreference } from '@/utils/profile';
import './index.less';

type FieldName = 'phoneNumber' | 'nickname' | 'loginPassword' |
  'confirmPassword' | 'paymentPassword' | 'confirmPaymentPassword';

const RegisterPage: React.FC = () => {
  const [step, setStep] = useState(0);
  const [phoneNumber, setPhoneNumber] = useState('');
  const [nickname, setNickname] = useState('');
  const [loginPassword, setLoginPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [paymentPassword, setPaymentPassword] = useState('');
  const [confirmPaymentPassword, setConfirmPaymentPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState<Partial<Record<FieldName, string>>>({});

  const validatePhone = (value: string) => !value ? '请输入手机号' : /^1[3-9]\d{9}$/.test(value) ? '' : '请输入正确的11位手机号';
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

  const handleNext = () => {
    const nextErrors = {
      phoneNumber: validatePhone(phoneNumber),
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
        PHONE_NUMBER_EXISTS: '该手机号已注册',
        PASSWORD_POLICY_VIOLATION: '登录密码不符合安全规则',
        REGISTRATION_PROCESSING: '开户暂未完成，请稍后重试登录',
        NETWORK_ERROR: '网络异常，请检查网络连接',
      };
      Toast.show({ content: messages[code] || error.message || '注册失败', icon: 'fail' });
    } finally {
      setLoading(false);
    }
  };

  const FieldError = ({ field }: { field: FieldName }) => errors[field] ? <div className="field-error">{errors[field]}</div> : null;

  return (
    <div className="register-page">
      <div className="register-top"><h1>注册账号</h1><p>创建您的MiniAlalipay账号</p></div>
      <div className="register-card">
        <div className="step-bar">
          <div className={`step ${step === 0 ? 'active' : 'done'}`}><div className="step-num">{step === 0 ? '1' : '✓'}</div><span>基本信息</span></div>
          <div className={`step ${step === 1 ? 'active' : ''}`}><div className="step-num">2</div><span>设置支付密码</span></div>
        </div>

        {step === 0 ? <>
          <div className={`field ${errors.phoneNumber ? 'has-error' : ''}`}><label>手机号</label><input type="tel" inputMode="numeric" maxLength={11} placeholder="请输入11位手机号" value={phoneNumber} aria-invalid={Boolean(errors.phoneNumber)} onBlur={() => setFieldError('phoneNumber', validatePhone(phoneNumber))} onChange={(e) => { const value = e.target.value.replace(/\D/g, ''); setPhoneNumber(value); if (errors.phoneNumber) setFieldError('phoneNumber', validatePhone(value)); }} /><FieldError field="phoneNumber" /></div>
          <div className={`field ${errors.nickname ? 'has-error' : ''}`}><label>昵称（选填）</label><input type="text" maxLength={20} placeholder="不填写时默认使用手机号脱敏值" value={nickname} aria-invalid={Boolean(errors.nickname)} onBlur={() => setFieldError('nickname', nickname.trim().length <= 20 ? '' : '昵称不能超过20位')} onChange={(e) => { setNickname(e.target.value); if (errors.nickname) setFieldError('nickname', e.target.value.trim().length <= 20 ? '' : '昵称不能超过20位'); }} /><FieldError field="nickname" /></div>
          <div className={`field ${errors.loginPassword ? 'has-error' : ''}`}><label>登录密码</label><input type="password" maxLength={32} placeholder="8-32位，包含大小写字母和数字" value={loginPassword} aria-invalid={Boolean(errors.loginPassword)} onBlur={() => setFieldError('loginPassword', validateLoginPassword(loginPassword))} onChange={(e) => { setLoginPassword(e.target.value); if (errors.loginPassword) setFieldError('loginPassword', validateLoginPassword(e.target.value)); if (confirmPassword) setFieldError('confirmPassword', e.target.value === confirmPassword ? '' : '两次登录密码不一致'); }} /><FieldError field="loginPassword" /></div>
          <div className={`field ${errors.confirmPassword ? 'has-error' : ''}`}><label>确认密码</label><input type="password" maxLength={32} placeholder="请再次输入登录密码" value={confirmPassword} aria-invalid={Boolean(errors.confirmPassword)} onBlur={() => setFieldError('confirmPassword', !confirmPassword ? '请再次输入登录密码' : loginPassword === confirmPassword ? '' : '两次登录密码不一致')} onChange={(e) => { setConfirmPassword(e.target.value); if (errors.confirmPassword) setFieldError('confirmPassword', !e.target.value ? '请再次输入登录密码' : loginPassword === e.target.value ? '' : '两次登录密码不一致'); }} /><FieldError field="confirmPassword" /></div>
          <button className="register-btn" onClick={handleNext}>下一步</button>
        </> : <>
          <div className={`field ${errors.paymentPassword ? 'has-error' : ''}`}><label>支付密码（6位数字）</label><input type="password" inputMode="numeric" maxLength={6} placeholder="请输入6位数字支付密码" value={paymentPassword} aria-invalid={Boolean(errors.paymentPassword)} onBlur={() => setFieldError('paymentPassword', validatePaymentPassword(paymentPassword))} onChange={(e) => { const value = e.target.value.replace(/\D/g, ''); setPaymentPassword(value); if (errors.paymentPassword) setFieldError('paymentPassword', validatePaymentPassword(value)); if (confirmPaymentPassword) setFieldError('confirmPaymentPassword', value === confirmPaymentPassword ? '' : '两次支付密码不一致'); }} /><FieldError field="paymentPassword" /></div>
          <div className={`field ${errors.confirmPaymentPassword ? 'has-error' : ''}`}><label>确认支付密码</label><input type="password" inputMode="numeric" maxLength={6} placeholder="请再次输入6位数字支付密码" value={confirmPaymentPassword} aria-invalid={Boolean(errors.confirmPaymentPassword)} onBlur={() => setFieldError('confirmPaymentPassword', !confirmPaymentPassword ? '请再次输入支付密码' : paymentPassword === confirmPaymentPassword ? '' : '两次支付密码不一致')} onChange={(e) => { const value = e.target.value.replace(/\D/g, ''); setConfirmPaymentPassword(value); if (errors.confirmPaymentPassword) setFieldError('confirmPaymentPassword', !value ? '请再次输入支付密码' : paymentPassword === value ? '' : '两次支付密码不一致'); }} /><FieldError field="confirmPaymentPassword" /></div>
          <button className="register-btn" onClick={handleSubmit} disabled={loading}>{loading ? '注册中...' : '完成注册'}</button>
        </>}
        <div className="links"><span onClick={() => history.push('/h5/login')}>已有账号？立即登录</span></div>
      </div>
    </div>
  );
};

export default RegisterPage;
