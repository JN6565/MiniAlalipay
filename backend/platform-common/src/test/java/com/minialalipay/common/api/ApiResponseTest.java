package com.minialalipay.common.api;

import com.minialalipay.common.error.CommonErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void successUsesStableEnvelopeAndPreservesRequestId() {
        ApiResponse<String> response = ApiResponse.success("已受理", "req-001", "trace-001");

        assertThat(response.code()).isEqualTo(CommonErrorCode.SUCCESS.code());
        assertThat(response.message()).isEqualTo(CommonErrorCode.SUCCESS.message());
        assertThat(response.requestId()).isEqualTo("req-001");
        assertThat(response.traceId()).isEqualTo("trace-001");
        assertThat(response.data()).isEqualTo("已受理");
    }

    @Test
    void failureDoesNotExposeData() {
        ApiResponse<Void> response = ApiResponse.failure(
                CommonErrorCode.INVALID_REQUEST,
                "req-002",
                "trace-002"
        );

        assertThat(response.code()).isEqualTo("COMMON_INVALID_REQUEST");
        assertThat(response.message()).isEqualTo("请求参数不合法");
        assertThat(response.requestId()).isEqualTo("req-002");
        assertThat(response.traceId()).isEqualTo("trace-002");
        assertThat(response.data()).isNull();
    }
}
