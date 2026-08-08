package com.minialalipay.account.domain.bankcard;

/**
 * 用户中心身份校验端口。
 *
 * <p>account-center 注册银行卡与绑卡时通过该端口调用 user-center 的内部三要素校验接口，
 * 确保输入的姓名、身份证、手机号与用户绑定的身份完全一致。</p>
 */
public interface UserCenterIdentityPort {

    /**
     * 三要素交叉校验结果，供应用层映射为对外错误码。
     *
     * <ul>
     *   <li>{@code MATCHED}：三要素与用户绑定身份完全一致，允许继续注册/绑卡</li>
     *   <li>{@code IDENTITY_NOT_BOUND}：用户尚未绑定身份信息（含用户不存在），
     *       对应错误码 {@code IDENTITY_NOT_BOUND}，前端应引导用户先绑定身份</li>
     *   <li>{@code MISMATCH}：已绑定身份但任一要素不一致，
     *       对应错误码 {@code IDENTITY_MISMATCH}，统一提示不区分具体字段</li>
     *   <li>{@code SERVICE_UNAVAILABLE}：user-center 不可连接或响应异常；
     *       三要素校验是资金安全前置关卡，校验无法完成时必须拒绝请求，禁止放行</li>
     * </ul>
     */
    enum VerifyResult {
        /** 三要素全部匹配。 */
        MATCHED,
        /** 用户未绑定身份信息。 */
        IDENTITY_NOT_BOUND,
        /** 已绑定身份但三要素不一致。 */
        MISMATCH,
        /** 校验服务不可用。 */
        SERVICE_UNAVAILABLE
    }

    /**
     * 三要素交叉校验：比对持卡人姓名、身份证号、手机号与用户绑定身份是否完全一致。
     *
     * @param userId 用户 ID
     * @param holderName 持卡人姓名
     * @param idCard 身份证号明文
     * @param phone 手机号明文
     * @return 校验结果枚举，失败原因的区分见枚举说明
     */
    VerifyResult verifyThreeElements(String userId, String holderName, String idCard, String phone);
}
