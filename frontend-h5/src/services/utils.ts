/**
 * 生成幂等键，格式为 idem_<uuid>。
 */
export const generateIdempotencyKey = (): string => {
  const uuid = crypto.randomUUID?.() ?? generateFallbackUuid();
  return `idem_${uuid}`;
};

const generateFallbackUuid = (): string => {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
};
