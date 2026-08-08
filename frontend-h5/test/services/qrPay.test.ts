import request from '../../src/services/request';
import { createConfirmation, submitPayment } from '../../src/services/qrPay';

jest.mock('../../src/services/request', () => ({
  __esModule: true,
  default: {
    post: jest.fn(),
  },
}));

const mockedPost = request.post as jest.Mock;

describe('动态扫码支付服务契约', () => {
  beforeEach(() => {
    mockedPost.mockReset();
  });

  test('确认请求绑定订单版本支付证明和资金来源', () => {
    createConfirmation('order-1', {
      version: 3,
      paymentProof: 'proof-token',
      fundingSource: 'MINI_CREDIT',
    });

    expect(mockedPost).toHaveBeenCalledWith(
      '/api/v1/qr-pay/orders/order-1/confirmations',
      { version: 3, paymentProof: 'proof-token', fundingSource: 'MINI_CREDIT' },
    );
  });

  test('支付请求不允许覆盖资金来源并携带幂等键', () => {
    submitPayment('order-1', { confirmationToken: 'confirmation-token' });

    expect(mockedPost).toHaveBeenCalledWith(
      '/api/v1/qr-pay/orders/order-1/pay',
      { confirmationToken: 'confirmation-token' },
      { headers: { 'Idempotency-Key': expect.stringMatching(/^idem_/) } },
    );
  });
});
