package com.minialalipay.business.infrastructure.config;

import com.minialalipay.business.application.monitoring.MonitoringEventConsumer;
import com.minialalipay.business.application.monitoring.MonitoringEventStore;
import com.minialalipay.common.error.CommonExceptionMapper;
import com.minialalipay.common.trace.RequestIdGenerator;
import com.minialalipay.common.idempotency.IdempotencyKeyValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 业务中心跨子域共享的技术配置，不承载交易、风控或资金领域规则。
 */
@Configuration
public class BusinessCenterCommonConfiguration {

    /** @return 框架无关的公共异常映射器 */
    @Bean
    public CommonExceptionMapper commonExceptionMapper() {
        return new CommonExceptionMapper();
    }

    /** @return 安全解析或生成请求编号的工具 */
    @Bean
    public RequestIdGenerator requestIdGenerator() {
        return new RequestIdGenerator();
    }

    /** @return 对外写请求幂等键格式校验器 */
    @Bean
    public IdempotencyKeyValidator idempotencyKeyValidator() { return new IdempotencyKeyValidator(); }

    /**
     * 监控投影消费者。
     *
     * <p>消费者名称必须与 {@link MonitoringEventStore#quarantine} 实现中固定的 Inbox 名称保持一致，
     * 保证隔离记录归属稳定。事件来源（Outbox 中继）接入后即开始驱动投影。</p>
     */
    @Bean
    public MonitoringEventConsumer monitoringEventConsumer(MonitoringEventStore monitoringEventStore) {
        return new MonitoringEventConsumer("ops-projection", monitoringEventStore);
    }
}
