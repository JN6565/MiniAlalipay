package com.minialalipay.gateway.infrastructure.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 用户中心会话认证适配器的地址选择测试。
 */
class UserCenterAuthenticationAdapterTest {

    @Test
    void 直连地址不应挂载服务发现负载均衡过滤器() {
        ObjectProvider<ReactorLoadBalancerExchangeFilterFunction> provider = provider();

        new UserCenterAuthenticationAdapter(
                WebClient.builder(), provider, "http://localhost:8081", "test-token");

        verifyNoInteractions(provider);
    }

    @Test
    void 服务名地址应挂载服务发现负载均衡过滤器() {
        ObjectProvider<ReactorLoadBalancerExchangeFilterFunction> provider = provider();
        ReactorLoadBalancerExchangeFilterFunction filter = mock(ReactorLoadBalancerExchangeFilterFunction.class);
        WebClient.Builder builder = mock(WebClient.Builder.class);
        when(builder.baseUrl("lb://user-center")).thenReturn(builder);
        when(builder.filter(filter)).thenReturn(builder);
        when(builder.build()).thenReturn(mock(WebClient.class));
        doAnswer(invocation -> {
            Consumer<ReactorLoadBalancerExchangeFilterFunction> consumer = invocation.getArgument(0);
            consumer.accept(filter);
            return null;
        }).when(provider).ifAvailable(any());

        new UserCenterAuthenticationAdapter(
                builder, provider, "lb://user-center", "test-token");

        verify(provider).ifAvailable(any());
        verify(builder).filter(filter);
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<ReactorLoadBalancerExchangeFilterFunction> provider() {
        return mock(ObjectProvider.class);
    }
}
