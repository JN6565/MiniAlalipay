import request from '../../src/services/request';
import { getBillDetail, getBills, getRepaymentStatus } from '../../src/services/credit';

jest.mock('../../src/services/request', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
  },
}));

const mockedGet = request.get as jest.Mock;

describe('信用服务接口契约', () => {
  beforeEach(() => {
    mockedGet.mockReset();
  });

  test('账单列表使用契约路径且不发送未定义的分页参数', () => {
    getBills();

    expect(mockedGet).toHaveBeenCalledWith('/api/v1/credit/bills');
  });

  test('账单详情使用网关 API 前缀', () => {
    getBillDetail('bill-1');

    expect(mockedGet).toHaveBeenCalledWith('/api/v1/credit/bills/bill-1');
  });

  test('还款状态使用网关 API 前缀', () => {
    getRepaymentStatus('repayment-1');

    expect(mockedGet).toHaveBeenCalledWith('/api/v1/credit/repayments/repayment-1');
  });
});
