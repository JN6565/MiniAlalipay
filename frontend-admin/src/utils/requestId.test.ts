import { createRequestId } from './requestId';

describe('createRequestId', () => {
  it('生成标准 UUID 格式的请求编号', () => {
    expect(createRequestId()).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
    );
  });

  it('连续生成的请求编号不重复', () => {
    expect(createRequestId()).not.toBe(createRequestId());
  });
});
