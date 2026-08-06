import { createRequestId } from '../../src/utils/requestId';

/**
 * 请求编号生成单元测试。
 *
 * 请求编号用于网关与各服务日志串联，格式必须始终是标准 UUID v4；
 * 测试同时覆盖 Web Crypto 缺失时的降级路径，确保弱环境仍产出合法编号或明确失败。
 */
describe('createRequestId', () => {
  it('生成标准 UUID 格式的请求编号', () => {
    // 正则断言版本位为 4、变体位为 8/9/a/b，验证编号符合 RFC 4122 v4 语义。
    expect(createRequestId()).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
    );
  });

  it('连续生成的请求编号不重复', () => {
    expect(createRequestId()).not.toBe(createRequestId());
  });

  describe('Web Crypto 能力降级', () => {
    // 每个用例结束后清除对 globalThis.crypto 的改动，避免污染其他用例。
    afterEach(() => {
      delete (globalThis.crypto as unknown as Record<string, unknown>).randomUUID;
      delete (globalThis.crypto as unknown as Record<string, unknown>).getRandomValues;
    });

    it('缺少 randomUUID 时使用 getRandomValues 生成 UUID v4', () => {
      // 模拟仅支持 getRandomValues 的旧环境，验证降级路径产出相同格式编号。
      Object.defineProperty(globalThis.crypto, 'randomUUID', {
        value: undefined,
        configurable: true,
      });

      expect(createRequestId()).toMatch(
        /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
      );
    });

    it('randomUUID 与 getRandomValues 均不可用时抛出中文错误', () => {
      // 极端环境不允许静默降级为弱随机，应明确报错让上层感知。
      Object.defineProperty(globalThis.crypto, 'randomUUID', {
        value: undefined,
        configurable: true,
      });
      Object.defineProperty(globalThis.crypto, 'getRandomValues', {
        value: undefined,
        configurable: true,
      });

      expect(() => createRequestId()).toThrow('当前环境不支持 Web Crypto，无法生成请求编号');
    });
  });
});
