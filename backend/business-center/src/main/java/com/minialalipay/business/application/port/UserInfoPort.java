package com.minialalipay.business.application.port;

/** 跨服务用户信息只读端口，仅提供交易参与者展示所需的最小信息。 */
public interface UserInfoPort {

    /**
     * 查询用户展示信息；依赖不可用时实现应保留 userId，并将名称置空，不能影响交易事实查询。
     *
     * @param userId 用户 ID
     * @return 用户最小展示信息
     */
    UserInfo findUserInfo(String userId);

    /** 用户中心返回的最小展示投影，不包含手机号、证件号等敏感信息。 */
    record UserInfo(String userId, String realName, String nickname, String accountNumber) {
        /** 兼容旧构造：不携带系统账户号的降级投影。 */
        public UserInfo(String userId, String realName, String nickname) {
            this(userId, realName, nickname, null);
        }

        /** 优先使用昵称，其次使用真实姓名；均缺失时为空。 */
        public String displayName() {
            if (nickname != null && !nickname.isBlank()) return nickname;
            return realName == null || realName.isBlank() ? null : realName;
        }
    }
}
