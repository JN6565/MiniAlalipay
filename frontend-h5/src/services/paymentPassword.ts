import request from './request';

/**
 * 设置支付密码（注册后首次设置）。
 * @param paymentPassword 支付密码（6 位数字）
 * @returns 成功响应
 */
export const setupPaymentPassword = (paymentPassword: string) => {
  return request.put('/api/v1/payment-password', { paymentPassword });
};

/**
 * 修改支付密码。
 * @param params.currentPassword 当前支付密码
 * @param params.newPassword 新支付密码（6 位数字）
 * @returns 成功响应
 */
export const changePaymentPassword = (params: {
  currentPassword: string;
  newPassword: string;
}) => {
  return request.patch('/api/v1/payment-password', params);
};

/**
 * 验证支付密码并获取支付凭证。
 * @param params 支付密码和关联信息
 * @returns 支付凭证
 */
export const verifyPaymentPassword = (params: {
  paymentPassword: string;
  subjectType: string;
  subjectId: string;
}): Promise<{ paymentProof: string; expiresAt: string }> => {
  return request.post('/api/v1/payment-password/verify', params) as unknown as Promise<{ paymentProof: string; expiresAt: string }>;
};
