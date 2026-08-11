package com.minialalipay.gateway.interfaces.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 安全响应头过滤器测试。
 */
class SecurityHeadersGlobalFilterTest {

    private final SecurityHeadersGlobalFilter filter = new SecurityHeadersGlobalFilter();

    @Test
    @DisplayName("所有响应均包含标准安全头")
    void addsStandardSecurityHeadersToAllResponses() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/transfers"));

        filter.filter(exchange, chain -> reactor.core.publisher.Mono.empty()).block();

        assertThat(exchange.getResponse().getHeaders().getFirst("X-Content-Type-Options"))
                .isEqualTo("nosniff");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Frame-Options"))
                .isEqualTo("DENY");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-XSS-Protection"))
                .isEqualTo("0");
        assertThat(exchange.getResponse().getHeaders().getFirst("Referrer-Policy"))
                .isEqualTo("strict-origin-when-cross-origin");
    }

    @Test
    @DisplayName("普通路径不设置二维码敏感路径的额外安全头")
    void ordinaryPathDoesNotSetQrSensitiveHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/transfers"));

        filter.filter(exchange, chain -> reactor.core.publisher.Mono.empty()).block();

        assertThat(exchange.getResponse().getHeaders().getFirst("X-Robots-Tag")).isNull();
        assertThat(exchange.getResponse().getHeaders().getFirst("Cache-Control")).isNull();
    }

    @Test
    @DisplayName("二维码支付路径设置严格缓存和引用策略")
    void qrPayPathSetsStrictHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/qr-pay/orders/by-token"));

        filter.filter(exchange, chain -> reactor.core.publisher.Mono.empty()).block();

        assertThat(exchange.getResponse().getHeaders().getFirst("Cache-Control"))
                .isEqualTo("no-store");
        assertThat(exchange.getResponse().getHeaders().getFirst("Referrer-Policy"))
                .isEqualTo("no-referrer");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Robots-Tag"))
                .isEqualTo("noindex");
    }

    @Test
    @DisplayName("个人收款路径设置严格缓存和引用策略")
    void p2pCollectionPathSetsStrictHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/p2p-collections/by-token"));

        filter.filter(exchange, chain -> reactor.core.publisher.Mono.empty()).block();

        assertThat(exchange.getResponse().getHeaders().getFirst("Cache-Control"))
                .isEqualTo("no-store");
        assertThat(exchange.getResponse().getHeaders().getFirst("Referrer-Policy"))
                .isEqualTo("no-referrer");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Robots-Tag"))
                .isEqualTo("noindex");
    }

    @Test
    @DisplayName("二维码路径子路径也设置严格策略")
    void qrPaySubPathSetsStrictHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/qr-pay/orders/status"));

        filter.filter(exchange, chain -> reactor.core.publisher.Mono.empty()).block();

        assertThat(exchange.getResponse().getHeaders().getFirst("Cache-Control"))
                .isEqualTo("no-store");
        assertThat(exchange.getResponse().getHeaders().getFirst("Referrer-Policy"))
                .isEqualTo("no-referrer");
    }

    @Test
    @DisplayName("Filter 顺序使用命名常量")
    void usesNamedOrderConstant() {
        assertThat(filter.getOrder()).isEqualTo(GatewayFilterOrders.SECURITY_HEADERS);
    }

    @Test
    @DisplayName("安全头不影响 Filter 链继续执行")
    void filterChainContinuesAfterSettingHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/transfers"));
        boolean[] chainCalled = {false};

        filter.filter(exchange, downstream -> {
            chainCalled[0] = true;
            return reactor.core.publisher.Mono.empty();
        }).block();

        assertThat(chainCalled[0]).isTrue();
    }
}
