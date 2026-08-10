import { decideManualCase } from '../../src/services/ops';
import { gatewayRequest } from '../../src/services/request';
import { createRequestId } from '../../src/utils/requestId';

jest.mock('../../src/services/request', () => ({
  gatewayRequest: jest.fn(),
}));

jest.mock('../../src/utils/requestId', () => ({
  createRequestId: jest.fn(),
}));

/**
 * B 端运维写操作服务测试。
 *
 * 工单处置等写操作必须带幂等键，且生成方式应复用兼容旧浏览器的请求编号工具，
 * 避免浏览器不支持 Web Crypto randomUUID 时在请求发出前失败。
 */
describe('运维写操作服务', () => {
  const mockedGatewayRequest = jest.mocked(gatewayRequest);
  const mockedCreateRequestId = jest.mocked(createRequestId);

  beforeEach(() => {
    mockedGatewayRequest.mockReset();
    mockedCreateRequestId.mockReset();
    mockedCreateRequestId.mockReturnValue('7aa84745-1b56-4b5e-9b02-f9706652e9cf');
  });

  it('解决人工工单时使用兼容的请求编号工具生成幂等键', () => {
    decideManualCase('case-001', 'RESOLVE', 3, '已核实异常原因', '审计记录-001');

    expect(mockedCreateRequestId).toHaveBeenCalledTimes(1);
    expect(mockedGatewayRequest).toHaveBeenCalledWith('/api/v1/manual-cases/case-001/decisions', {
      method: 'POST',
      data: {
        decision: 'RESOLVE',
        version: 3,
        reason: '已核实异常原因',
        evidence: '审计记录-001',
      },
      headers: { 'Idempotency-Key': '7aa84745-1b56-4b5e-9b02-f9706652e9cf' },
    });
  });
});
