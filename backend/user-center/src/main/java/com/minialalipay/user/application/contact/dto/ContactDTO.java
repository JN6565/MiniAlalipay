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
 */
public record ContactDTO(
        String payeeUserId,
        String alias,
        long successCount,
        Instant lastSuccessAt,
        boolean pinned
) {
}
