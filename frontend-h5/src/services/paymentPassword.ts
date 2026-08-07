import request from './request';
import { generateIdempotencyKey } from './utils';

/**
 * 设置支付密码（注册后首次设置）。
 * @param paymentPassword 支付密码（6 位数字）
 * @returns 成功响应
 */
export const setupPaymentPassword = (paymentPassword: string) => {
  return request.put('/api/v1/payment-password', { paymentPassword }, {
    headers: { 'Idempotency-Key': generateIdempotencyKey() },
  });
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
  return request.patch('/api/v1/payment-password', params, {
    headers: { 'Idempotency-Key': generateIdempotencyKey() },
  });
};

/**
 * 验证支付密码（仅校验正确性，不签发证明）。
 *
 * @param paymentPassword 支付密码（6 位数字）
 * @returns 成功响应
 */
export const verifyPaymentPassword = (paymentPassword: string) => {
  return request.post('/api/v1/payment-password/verify', { paymentPassword }, {
    headers: { 'Idempotency-Key': generateIdempotencyKey() },
  });
};

export interface PaymentProofResult {
  paymentProof: string;
}

/**
 * 验证支付密码并签发一次性支付证明，用于转账确认。
 *
 * 后端 `/verify` 仅返回验证结果不含证明，支付证明由 `/proof` 接口签发；
 * 响应中的 `accessToken` 即原始证明令牌，此处统一映射为 `paymentProof` 供上层使用。
 *
 * @param paymentPassword 支付密码（6 位数字）
 * @param purpose 证明用途，默认 TRANSFER_CONFIRM；信用还款传 CREDIT_REPAY
 * @returns 支付证明（两分钟有效，不得写入日志、URL 或浏览器存储）
 */
export const issuePaymentProof = async (
  paymentPassword: string,
  purpose: string = 'TRANSFER_CONFIRM',
): Promise<PaymentProofResult> => {
  const result = await request.post<{ accessToken: string }>(
    '/api/v1/payment-password/proof',
    { paymentPassword, purpose },
    { headers: { 'Idempotency-Key': generateIdempotencyKey() } },
  );
  return { paymentProof: result.accessToken };
};
