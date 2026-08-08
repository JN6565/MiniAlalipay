package com.minialalipay.account.domain.bankcard;

/**
 * 用户中心身份校验端口。
 *
 * <p>account-center 绑卡时通过该端口调用 user-center 的内部三要素校验接口，
 * 确保绑卡输入的姓名、身份证、手机号与用户存储身份完全一致。</p>
 */
public interface UserCenterIdentityPort {

    /**
     * 三要素交叉校验：比对持卡人姓名、身份证号、手机号与用户存储信息是否完全一致。
     *
     * @param userId 用户 ID
     * @param holderName 持卡人姓名
     * @param idCard 身份证号明文
     * @param phone 手机号明文
     * @return 是否全部匹配
     */
    boolean verifyThreeElements(String userId, String holderName, String idCard, String phone);
}
