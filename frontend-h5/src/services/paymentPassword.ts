import request from './request';

// 设置支付密码（注册后首次设置）
export const setupPaymentPassword = (paymentPassword: string) => {
  return request.put('/api/v1/payment-password', {
    data: { paymentPassword },
  });
};

// 修改支付密码（需验证登录密码）
export const changePaymentPassword = (params: {
  loginPassword: string;
  newPaymentPassword: string;
}) => {
  return request.patch('/api/v1/payment-password', { data: params });
};

// 校验支付密码并获取确认令牌
export const verifyPaymentPassword = (params: {
  paymentPassword: string;
  subjectType: string;
  subjectId: string;
}) => {
  return request.post<{ confirmationToken: string; expiresAt: string }>(
    '/api/v1/payment-password/verify',
    { data: params },
  );
};
