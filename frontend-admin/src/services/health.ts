import { gatewayRequest } from './request';

/**
 * 网关健康检查服务。
 *
 * `/actuator/health` 是当前 OpenAPI 中唯一已实现的后端操作（见 AGENTS.md 网关基础接口表），
 * 因此健康检查也是 B 端唯一真正发起的业务外请求，用于判断本地网关是否可用。
 * 该接口不涉及鉴权，任何运营页面都可在进入后查询。
 */

/** 网关健康检查响应，对应当前 OpenAPI 中唯一已实现的操作。 */
export interface HealthResponse {
  /** 网关状态，正常时为 `UP`。 */
  status: string;
}

/** 查询网关健康状态，不涉及业务数据或鉴权。 */
export function getGatewayHealth(): Promise<HealthResponse> {
  return gatewayRequest<HealthResponse>('/actuator/health');
}
