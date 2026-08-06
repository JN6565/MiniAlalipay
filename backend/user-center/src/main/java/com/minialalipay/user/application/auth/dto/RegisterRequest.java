package com.minialalipay.user.application.auth.dto;

/**
 * 注册请求 DTO。
 *
 * <p>应用层的注册请求数据传输对象，用于 {@link com.minialalipay.user.application.auth.AuthService#register} 方法。
 * DTO 对象只在应用层使用，不暴露到领域层或接口层。</p>
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@code loginName} - 登录名（必填，4-20 位，用于登录和唯一识别）</li>
 *   <li>{@code nickname} - 昵称（必填，2-20 位，用于展示和模糊搜索）</li>
 *   <li>{@code loginPassword} - 登录密码（必填，8-32 位，至少包含大小写字母和数字）</li>
 * </ul>
 * </p>
 *
 * <p>校验规则：
 * <ul>
 *   <li>登录名长度 4-20 位，只能包含字母、数字、下划线</li>
 *   <li>昵称长度 2-20 位，不能包含特殊字符</li>
 *   <li>密码长度 8-32 位，至少包含一个大写字母、一个小写字母和一个数字</li>
 * </ul>
 * </p>
 *
 * @see com.minialalipay.user.application.auth.AuthService#register
 */
public record RegisterRequest(
        /**
         * 登录名（必填，4-20 位）。
         * <p>用于登录和唯一识别，系统内唯一。
         * 注册时规范化处理（转小写、去空格）。</p>
         */
        String phoneNumber,

        /** 真实姓名，用于身份展示和转账收款人查询。 */
        String realName,

        /**
         * 昵称（必填，2-20 位）。
         * <p>可重复的展示名称和模糊搜索条件，不要求唯一。</p>
         */
        String nickname,

        /**
         * 登录密码（必填，8-32 位）。
         * <p>至少包含一个大写字母、一个小写字母和一个数字。
         * 存储时使用 BCrypt 强哈希，不存储明文。</p>
         */
        String loginPassword,

        /** 6 位数字支付密码，与登录密码分别哈希保存。 */
        String paymentPassword
) {
}
