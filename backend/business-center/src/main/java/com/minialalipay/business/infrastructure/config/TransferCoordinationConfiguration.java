package com.minialalipay.business.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 转账 TCC 异步协调技术配置。
 *
 * <p>转账受理以 PROCESSING 持久化后立即返回提交响应，Seata 全局事务协调改在本有界
 * 线程池异步执行，避免 TC 注册与参与者 try/confirm 往返阻塞 HTTP 响应。
 * 异步执行失败时由交易恢复扫描器按既有补偿语义接管，资金安全不依赖同步执行。</p>
 */
@Configuration
public class TransferCoordinationConfiguration {

    /**
     * @return 转账 TCC 协调专用有界线程池；队列满时由调用线程兜底执行，确保协调任务不丢失
     */
    @Bean(name = "transferCoordinationExecutor")
    public Executor transferCoordinationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("transfer-coord-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
