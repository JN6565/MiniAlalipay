package com.minialalipay.account.application.port;

/** 跨服务用户信息只读端口，用于账本分录展示交易对方信息。 */
public interface UserInfoPort {

    /**
     * 查询用户展示信息；依赖不可用时降级为 null。
     *
     * @param userId 用户 ID
     * @return 用户最小展示信息，查询失败时返回 null
     */
    UserInfo findUserInfo(String userId);

    /** 用户中心返回的最小展示投影。 */
    record UserInfo(String userId, String realName, String nickname, String maskedPhone) {
        /** 优先使用真实姓名，其次昵称，最后脱敏手机号；均缺失时返回空字符串。 */
        public String displayName() {
            if (realName != null && !realName.isBlank()) return realName;
            if (nickname != null && !nickname.isBlank()) return nickname;
            if (maskedPhone != null && !maskedPhone.isBlank()) return maskedPhone;
            return "";
        }
    }
}
