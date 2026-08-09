package com.minialalipay.user.application.contact.dto;

import java.time.Instant;

/**
 * 联系人对外传输对象。
 *
 * <p>用于接口层返回联系人列表数据，不包含内部实现细节。
 * 收款人展示信息均在服务边界脱敏：姓名为脱敏展示名，手机号仅下发脱敏形式。</p>
 *
 * @param payeeUserId   收款人用户 ID
 * @param payeeName     收款人脱敏展示名（真实姓名优先，未绑定身份时降级昵称；保留首字符其余星号）；收款人不存在时为 ***
 * @param accountNumber 收款人系统账户号；收款人不存在时为空字符串
 * @param alias         备注别名（可空）
 * @param successCount  累计成功转账次数
 * @param lastSuccessAt 最近成功转账时间
 * @param pinned        是否置顶
 * @param maskedPhone   脱敏手机号（保留前 3 位和后 4 位，中间以 **** 代替，如 138****9150）；收款人不存在时为 null
 * @param phoneTail     手机尾号（4 位），脱敏手机号缺失时的降级展示；收款人不存在时为 null
 */
public record ContactDTO(
        String payeeUserId,
        String payeeName,
        String accountNumber,
        String alias,
        long successCount,
        Instant lastSuccessAt,
        boolean pinned,
        String maskedPhone,
        String phoneTail
) {
}
