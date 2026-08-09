import request from '../../src/services/request';
import {
  createRepaymentDraft,
  getBillDetail,
  getBills,
  getPurchases,
  getRepaymentStatus,
  submitRepayment,
} from '../../src/services/credit';

jest.mock('../../src/services/request', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
  },
}));

const mockedGet = request.get as jest.Mock;
const mockedPost = request.post as jest.Mock;

describe('信用服务接口契约', () => {
  beforeEach(() => {
    mockedGet.mockReset();
    mockedPost.mockReset();
  });

  test('账单列表使用契约路径且不发送未定义的分页参数', () => {
    getBills();

    expect(mockedGet).toHaveBeenCalledWith('/api/v1/credit/bills');
  });

  test('账单详情使用网关 API 前缀', () => {
    getBillDetail('bill-1');

    expect(mockedGet).toHaveBeenCalledWith('/api/v1/credit/bills/bill-1');
  });

  test('消费明细不带筛选时使用契约路径且不拼接查询参数', () => {
    getPurchases();

    expect(mockedGet).toHaveBeenCalledWith('/api/v1/credit/purchases');
  });

  test('消费明细按出账状态筛选时拼接 billingStatus 参数', () => {
    getPurchases('UNBILLED');

    expect(mockedGet).toHaveBeenCalledWith('/api/v1/credit/purchases?billingStatus=UNBILLED');
  });

  test('还款状态使用网关 API 前缀', () => {
    getRepaymentStatus('repayment-1');

    expect(mockedGet).toHaveBeenCalledWith('/api/v1/credit/repayments/repayment-1');
  });

  test('创建还款草稿携带契约请求体和自动生成的幂等键', () => {
    createRepaymentDraft(15400);

    expect(mockedPost).toHaveBeenCalledTimes(1);
    const [path, body, options] = mockedPost.mock.calls[0];
    expect(path).toBe('/api/v1/credit/repayment-drafts');
    expect(body).toEqual({ amountFen: 15400 });
    expect(options.headers['Idempotency-Key']).toMatch(/^idem_/);
  });

  test('创建还款草稿显式传入幂等键时原样透传，支持重试复用', () => {
    createRepaymentDraft(15400, 'retry-key-001');

    const [, , options] = mockedPost.mock.calls[0];
    expect(options.headers['Idempotency-Key']).toBe('retry-key-001');
  });

  test('提交还款携带契约请求体和幂等键', () => {
    submitRepayment({ repaymentDraftId: 'draft-1', paymentProofToken: 'proof-1' });

    expect(mockedPost).toHaveBeenCalledTimes(1);
    const [path, body, options] = mockedPost.mock.calls[0];
    expect(path).toBe('/api/v1/credit/repayments');
    expect(body).toEqual({ repaymentDraftId: 'draft-1', paymentProofToken: 'proof-1' });
    expect(options.headers['Idempotency-Key']).toMatch(/^idem_/);
  });

  test('提交还款显式传入幂等键时原样透传，支持重试复用', () => {
    submitRepayment(
      { repaymentDraftId: 'draft-1', paymentProofToken: 'proof-1' },
      'retry-key-002',
    );

    const [, , options] = mockedPost.mock.calls[0];
    expect(options.headers['Idempotency-Key']).toBe('retry-key-002');
  });
});
