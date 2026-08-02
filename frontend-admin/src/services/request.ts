import { request } from '@umijs/max';
import type { RequestOptions } from '@umijs/max';
import { createRequestId } from '@/utils/requestId';

/** 网关请求错误，保留服务端请求编号以便运营排查。 */
export class GatewayRequestError extends Error {
  /** 服务端或网关返回的请求编号。 */
  requestId?: string;

  /** 标准错误码；健康检查等裸响应可能不包含。 */
  code?: string;

  constructor(message: string, code?: string, requestId?: string) {
    super(message);
    this.name = 'GatewayRequestError';
    this.code = code;
    this.requestId = requestId;
  }
}

interface ErrorBody {
  code?: string;
  message?: string;
  requestId?: string;
  traceId?: string;
}

/**
 * 通过同源路径访问本地网关。
 *
 * 当前正式契约的健康检查为裸响应，因此此处不预设统一 `code/data` 外壳；
 * 业务响应归一化应在对应 OpenAPI 契约落地后实现。
 */
export async function gatewayRequest<T>(url: string, options: RequestOptions = {}): Promise<T> {
  if (!url.startsWith('/api/') && !url.startsWith('/actuator/')) {
    throw new GatewayRequestError('B 端请求只能访问网关公开路径');
  }

  try {
    return await request<T>(url, {
      timeout: 10_000,
      ...options,
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-Request-Id': createRequestId(),
        ...options.headers,
      },
    });
  } catch (error) {
    const response = (error as { response?: { data?: ErrorBody } }).response;
    const body = response?.data;
    throw new GatewayRequestError(
      body?.message ?? '网关服务暂时不可用，请稍后重试',
      body?.code,
      body?.requestId ?? body?.traceId,
    );
  }
}
