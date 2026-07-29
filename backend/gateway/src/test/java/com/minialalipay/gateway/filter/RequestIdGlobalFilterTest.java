package com.minialalipay.gateway.filter;

import com.minialalipay.common.trace.RequestIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdGlobalFilterTest {

    private final RequestIdGlobalFilter filter = new RequestIdGlobalFilter(new RequestIdGenerator());

    @Test
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
    void generatesRequestIdWhenInboundHeaderIsMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/actuator/health"));
        AtomicReference<String> downstreamRequestId = new AtomicReference<>();

        filter.filter(exchange, downstream -> captureRequestId(downstream, downstreamRequestId)).block();

        assertThat(downstreamRequestId.get()).startsWith("req_");
        assertThat(exchange.getResponse().getHeaders().getFirst(RequestIdGlobalFilter.HEADER_NAME))
                .isEqualTo(downstreamRequestId.get());
    }

    private reactor.core.publisher.Mono<Void> captureRequestId(
            ServerWebExchange exchange,
            AtomicReference<String> downstreamRequestId
    ) {
        downstreamRequestId.set(exchange.getRequest().getHeaders().getFirst(RequestIdGlobalFilter.HEADER_NAME));
        return reactor.core.publisher.Mono.empty();
    }
}
