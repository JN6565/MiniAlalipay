import { gatewayRequest } from './request';

/** 网关健康检查响应，对应当前 OpenAPI 中唯一已实现的操作。 */
export interface HealthResponse {
  /** 网关状态，正常时为 `UP`。 */
  status: string;
}

/** 查询网关健康状态，不涉及业务数据或鉴权。 */
export function getGatewayHealth(): Promise<HealthResponse> {
  return gatewayRequest<HealthResponse>('/actuator/health');
}
