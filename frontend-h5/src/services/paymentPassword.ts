import request from './request';

/**
 * 设置支付密码（注册后首次设置）。
 *
 * @param paymentPassword 支付密码（6 位数字）
 * @returns 成功响应
 */
export const setupPaymentPassword = (paymentPassword: string) => {
  return request.put('/v1/payment-password', {
    data: { paymentPassword },
  });
};

/**
 * 修改支付密码。
 *
 * @param params.currentPassword 当前支付密码
 * @param params.newPassword     新支付密码（6 位数字）
 * @returns 成功响应
 */
export const changePaymentPassword = (params: {
  currentPassword: string;
  newPassword: string;
}) => {
  return request.patch('/v1/payment-password', { data: params });
};

/**
 * 验证支付密码。
 *
 * @param paymentPassword 支付密码（6 位数字）
 * @returns 成功响应
 */
export const verifyPaymentPassword = (paymentPassword: string) => {
  return request.post('/v1/payment-password/verify', {
    data: { paymentPassword },
  });
};
