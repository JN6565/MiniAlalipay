package com.minialalipay.common.trace;

import java.util.UUID;

public final class RequestIdGenerator {

    public String resolve(String requestId) {
        if (requestId != null && !requestId.isBlank()) {
            return requestId;
        }
        return "req_" + UUID.randomUUID();
    }
}
