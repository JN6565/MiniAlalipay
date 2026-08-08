import request from '../../src/services/request';
import { createOrderConfirmation } from '../../src/services/collection';

jest.mock('../../src/services/request', () => ({
  __esModule: true,
  default: {
    post: jest.fn(),
  },
}));

const mockedPost = request.post as jest.Mock;

describe('个人码与固定金额收款支付服务契约', () => {
  beforeEach(() => mockedPost.mockReset());

  test('确认请求携带用户明确选择的 Mini 花呗资金来源', () => {
    createOrderConfirmation('order-1', 'proof-token', 3, 'MINI_CREDIT');

    expect(mockedPost).toHaveBeenCalledWith(
      '/api/v1/p2p-collections/orders/order-1/confirmations',
      { version: 3, paymentProof: 'proof-token', fundingSource: 'MINI_CREDIT' },
    );
  });
});
