package com.minialalipay.business.application.port;

/**
 * 手动输入短码兑换的尝试限流端口。
 *
 * <p>短码为 8 位数字，必须按主体计数失败尝试以抑制暴力猜测；实现不得记录短码内容本身，只记录主体与尝试计数。</p>
 */
public interface ShortCodeAttemptLimiter {
    /** 校验当前主体是否仍允许兑换短码；超出限制抛出 SHORT_CODE_RATE_LIMITED。 */
    void requireAllowed(String principal);

    /** 记录一次兑换失败尝试，累计失败将触发临时锁定。 */
    void recordFailure(String principal);

    /** 兑换成功后清零主体的失败计数。 */
    void reset(String principal);
}
