/**
 * 请求编号（X-Request-Id）生成工具。
 *
 * B 端发出的每个网关请求都携带唯一的请求编号，供网关、各后端服务与日志链路串联追踪，
 * 是排查跨服务问题的关键关联字段，因此要求全局唯一且格式稳定。
 *
 * 实现上优先使用 Web Crypto 的标准 `randomUUID`；在不支持该 API 的环境中降级为
 * 手动生成 UUID v4，保证两种路径产出的编号格式一致，避免下游解析分叉。
 */

/** 生成一个浏览器侧请求编号，返回标准 UUID 格式字符串。 */
export function createRequestId(): string {
  const cryptoApi = typeof crypto !== 'undefined' ? crypto : undefined;

  if (cryptoApi && typeof cryptoApi.randomUUID === 'function') {
    return cryptoApi.randomUUID();
  }

  if (!cryptoApi || typeof cryptoApi.getRandomValues !== 'function') {
    // 极端环境（如部分无安全随机源的老式 WebView）无法保证编号不可预测，直接失败比降级为弱随机更安全。
    throw new Error('当前环境不支持 Web Crypto，无法生成请求编号');
  }

  // 降级路径：取 16 字节安全随机数，再按 RFC 4122 修正为 UUID v4。
  const randomBytes = new Uint8Array(16);
  cryptoApi.getRandomValues(randomBytes);
  // 版本号字段置为 4；时钟序列高位置为 10xx（RFC 4122 变体位），保持与标准 UUID 语义一致。
  randomBytes[6] = (randomBytes[6] & 0x0f) | 0x40;
  randomBytes[8] = (randomBytes[8] & 0x3f) | 0x80;
  const hex = Array.from(randomBytes, (value) => value.toString(16).padStart(2, '0'));

  // 按 8-4-4-4-12 分段拼接，与网关/服务端解析 `X-Request-Id` 的格式约定保持一致。
  return `${hex.slice(0, 4).join('')}-${hex.slice(4, 6).join('')}-${hex
    .slice(6, 8)
    .join('')}-${hex.slice(8, 10).join('')}-${hex.slice(10).join('')}`;
}
