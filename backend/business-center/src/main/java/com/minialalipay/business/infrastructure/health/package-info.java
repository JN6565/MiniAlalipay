/**
 * 运营看板健康探针的出站适配器。
 *
 * <p>本包只访问服务的 Actuator 健康端点和业务中心已配置的 Redis 连接；禁止调用业务写接口，
 * 禁止将探针结果写入交易、账户或账本事实。</p>
 */
package com.minialalipay.business.infrastructure.health;
