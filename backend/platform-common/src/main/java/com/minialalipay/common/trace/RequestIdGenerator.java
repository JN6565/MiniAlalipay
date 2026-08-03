package com.minialalipay.common.trace;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 解析客户端请求编号；缺失或格式不安全时生成服务端编号。
 */
public final class RequestIdGenerator {

    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    /**
     * 返回可安全传播和记录的请求编号。
     *
     * @param requestId 客户端传入的请求编号
     * @return 合法的原请求编号，或新生成的请求编号
     */
    public String resolve(String requestId) {
        if (requestId != null && REQUEST_ID_PATTERN.matcher(requestId).matches()) {
            return requestId;
        }
        return "req_" + UUID.randomUUID();
    }
}
