package com.minialalipay.business.application.port;

import com.minialalipay.business.application.monitoring.DashboardSummary.ServiceHealth;

import java.util.List;

/**
 * 运营看板服务健康探针端口。
 *
 * <p>实现只读取各服务的健康状态，不允许通过探针调用业务写接口或把探针结果作为资金事实来源。</p>
 */
public interface ServiceHealthProbe {
    /** 查询已配置服务的本次健康探针结果；无法连接时必须返回 UNKNOWN 或 DOWN 的明确语义。 */
    List<ServiceHealth> probeAll();
}
