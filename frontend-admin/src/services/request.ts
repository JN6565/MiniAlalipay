/**
 * 网关请求基础层。
 *
 * 负责统一访问本地网关（http://localhost:8080 的同源代理路径）：
 * 1. 只放行网关公开前缀，拦截绕过网关直连后端服务端口的请求；
 * 2. 为每个请求注入唯一的 X-Request-Id，串联网关与各服务日志；
 * 3. 将网关返回的业务错误统一归一化为 GatewayRequestError，保留错误码与请求编号，
 *    供页面提示与运营排查使用，避免调用方各自解析原始异常。
 *
 * 当前正式契约的健康检查为裸响应，因此这里不预设统一 `code/data` 外壳；
 * 业务响应归一化应在对应 OpenAPI 契约落地后实现。
 */
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
 * 网络异常（网关未启动、超时等）与业务错误都会走到 catch 分支：
 * 若响应体携带统一错误外壳则提取其中的 message/code/requestId 作为根因，
 * 否则视为网关级不可用，给出通用的中文兜底提示。
 */
export async function gatewayRequest<T>(url: string, options: RequestOptions = {}): Promise<T> {
  // 白名单校验：前端只允许访问网关公开前缀，从根上杜绝直连后端服务端口（8081~8084）的路径。
  if (!url.startsWith('/api/') && !url.startsWith('/actuator/')) {
    throw new GatewayRequestError('B 端请求只能访问网关公开路径');
  }

  try {
    return await request<T>(url, {
      // 运营后台请求统一 10 秒超时，避免慢接口长时间占用操作台。
      timeout: 10_000,
      ...options,
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        // 每个请求都生成新编号，同一批请求按编号串接，便于跨服务链路排查。
        'X-Request-Id': createRequestId(),
        ...options.headers,
      },
    });
  } catch (error) {
    const response = (error as { response?: { data?: ErrorBody } }).response;
    const body = response?.data;
    // 透传服务端原始错误信息与请求编号，使运营能按编号回查服务端日志；traceId 作为 requestId 的兜底。
    throw new GatewayRequestError(
      body?.message ?? '网关服务暂时不可用，请稍后重试',
      body?.code,
      body?.requestId ?? body?.traceId,
    );
  }
}
