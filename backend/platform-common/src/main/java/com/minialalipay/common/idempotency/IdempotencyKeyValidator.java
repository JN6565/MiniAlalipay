package com.minialalipay.common.idempotency;

import java.util.regex.Pattern;

/**
 * 校验对外写接口使用的幂等键格式，不承担业务幂等记录的持久化职责。
 */
public final class IdempotencyKeyValidator {

    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{16,64}");

    /**
     * 判断幂等键是否符合公共 HTTP 契约。
     *
     * @param key 待校验幂等键
     * @return 符合契约时返回 true
     */
    public boolean isValid(String key) {
        return key != null && KEY_PATTERN.matcher(key).matches();
    }
}
