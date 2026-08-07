package com.minialalipay.user.application.contact.dto;

import java.time.Instant;

/**
 * 联系人对外传输对象。
 *
 * <p>用于接口层返回联系人列表数据，不包含内部实现细节。</p>
 *
 * @param payeeUserId   收款人用户 ID
 * @param alias         备注别名（可空）
 * @param successCount  累计成功转账次数
 * @param lastSuccessAt 最近成功转账时间
 * @param pinned        是否置顶
 * @param payeeNickname        收款人昵称（收款人不存在或非 ACTIVE 时为 null，前端自行降级展示）
 * @param payeeAccountNumber   收款人系统账户号（同上，可空）
 * @param maskedPhone          收款人脱敏手机号，保留前 3 位和后 4 位，中间以 **** 代替（同上，可空）
 * @param phoneTail            收款人手机尾号（4 位），脱敏手机号缺失时的降级展示（同上，可空）
 */
public record ContactDTO(
        String payeeUserId,
        String alias,
        long successCount,
        Instant lastSuccessAt,
        boolean pinned,
        String payeeNickname,
        String payeeAccountNumber,
        String maskedPhone,
        String phoneTail
) {
}
