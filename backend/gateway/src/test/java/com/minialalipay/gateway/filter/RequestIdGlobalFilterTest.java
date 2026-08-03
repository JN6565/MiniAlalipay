package com.minialalipay.gateway.filter;

import com.minialalipay.common.trace.RequestIdGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 请求编号过滤器测试。
 *
 * <p>覆盖生成、透传、非法值拒绝和 Exchange 属性写入。</p>
 */
class RequestIdGlobalFilterTest {

    private final RequestIdGlobalFilter filter = new RequestIdGlobalFilter(new RequestIdGenerator());

    @Test
    @DisplayName("透传合法的客户端请求编号到下游和响应")
    void preservesInboundRequestIdForDownstreamAndResponse() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/transfers")
                        .header(RequestIdGlobalFilter.HEADER_NAME, "req-client-001")
        );
        AtomicReference<String> downstreamRequestId = new AtomicReference<>();

        filter.filter(exchange, downstream -> captureRequestId(downstream, downstreamRequestId)).block();

        assertThat(downstreamRequestId).hasValue("req-client-001");
        assertThat(exchange.getResponse().getHeaders().getFirst(RequestIdGlobalFilter.HEADER_NAME))
                .isEqualTo("req-client-001");
    }

    @Test
    @DisplayName("客户端未提供请求编号时生成 req_ 前缀的新编号")
    void generatesRequestIdWhenInboundHeaderIsMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health"));
        AtomicReference<String> downstreamRequestId = new AtomicReference<>();

        filter.filter(exchange, downstream -> captureRequestId(downstream, downstreamRequestId)).block();

        assertThat(downstreamRequestId.get()).startsWith("req_");
        assertThat(exchange.getResponse().getHeaders().getFirst(RequestIdGlobalFilter.HEADER_NAME))
                .isEqualTo(downstreamRequestId.get());
    }

    @Test
    @DisplayName("非法格式的客户端请求编号被替换为新编号")
    void replacesIllegalRequestIdFormat() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/transfers")
                        .header(RequestIdGlobalFilter.HEADER_NAME, "<script>alert(1)</script>")
        );
        AtomicReference<String> downstreamRequestId = new AtomicReference<>();

        filter.filter(exchange, downstream -> captureRequestId(downstream, downstreamRequestId)).block();

        assertThat(downstreamRequestId.get()).startsWith("req_");
        assertThat(exchange.getResponse().getHeaders().getFirst(RequestIdGlobalFilter.HEADER_NAME))
                .isEqualTo(downstreamRequestId.get());
    }

    @Test
    @DisplayName("超长请求编号被替换为新编号")
    void replacesOverlyLongRequestId() {
        String longId = "a".repeat(200);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/transfers")
                        .header(RequestIdGlobalFilter.HEADER_NAME, longId)
        );
        AtomicReference<String> downstreamRequestId = new AtomicReference<>();

        filter.filter(exchange, downstream -> captureRequestId(downstream, downstreamRequestId)).block();

        assertThat(downstreamRequestId.get()).startsWith("req_");
        assertThat(downstreamRequestId.get()).isNotEqualTo(longId);
    }

    @Test
    @DisplayName("请求编号写入 Exchange 属性供异常处理器使用")
    void storesRequestIdAndTraceIdInExchangeAttributes() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health"));

        filter.filter(exchange, downstream -> reactor.core.publisher.Mono.empty()).block();

        assertThat(exchange.getAttribute(RequestIdGlobalFilter.ATTR_REQUEST_ID)).isNotNull();
        assertThat(exchange.getAttribute(RequestIdGlobalFilter.ATTR_REQUEST_ID).toString()).startsWith("req_");
        assertThat(exchange.getAttribute(RequestIdGlobalFilter.ATTR_TRACE_ID)).isNotNull();
    }

    @Test
    @DisplayName("Filter 顺序使用命名常量而非魔法数字")
    void usesNamedOrderConstant() {
        assertThat(filter.getOrder()).isEqualTo(GatewayFilterOrders.REQUEST_CONTEXT);
        assertThat(filter.getOrder()).isNotEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("空请求编号头被替换为新编号")
    void replacesEmptyRequestIdHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health")
                        .header(RequestIdGlobalFilter.HEADER_NAME, "")
        );
        AtomicReference<String> downstreamRequestId = new AtomicReference<>();

        filter.filter(exchange, downstream -> captureRequestId(downstream, downstreamRequestId)).block();

        assertThat(downstreamRequestId.get()).startsWith("req_");
    }

    @Test
    @DisplayName("包含空格的请求编号被替换")
    void replacesRequestIdWithSpaces() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health")
                        .header(RequestIdGlobalFilter.HEADER_NAME, "req test 001")
        );
        AtomicReference<String> downstreamRequestId = new AtomicReference<>();

        filter.filter(exchange, downstream -> captureRequestId(downstream, downstreamRequestId)).block();

        assertThat(downstreamRequestId.get()).startsWith("req_");
    }

    private reactor.core.publisher.Mono<Void> captureRequestId(
            ServerWebExchange exchange,
            AtomicReference<String> downstreamRequestId
    ) {
        downstreamRequestId.set(exchange.getRequest().getHeaders().getFirst(RequestIdGlobalFilter.HEADER_NAME));
        return reactor.core.publisher.Mono.empty();
    }
}
