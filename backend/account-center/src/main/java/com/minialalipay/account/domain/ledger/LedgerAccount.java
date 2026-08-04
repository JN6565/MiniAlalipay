package com.minialalipay.account.domain.ledger;

import java.time.Instant;
import java.util.Objects;

/**
 * 不可变账本科目身份和值对象。
 *
 * <p>科目编码、所有者、分类和正常方向创建后不得修改。阶段三开户只创建普通用户余额负债科目，
 * 其正常方向为贷方。</p>
 */
public final class LedgerAccount {

    private final String ledgerAccountId;
    private final LedgerOwnerType ownerType;
    private final String ownerId;
    private final String accountCode;
    private final String accountType;
    private final LedgerAccountClass accountClass;
    private final LedgerDirection normalDirection;
    private final String currency;
    private final LedgerAccountStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    /** 创建普通用户虚拟余额负债科目。 */
    public static LedgerAccount userBalance(String ledgerAccountId, String userId, String balanceAccountId,
                                            Instant now) {
        return new LedgerAccount(ledgerAccountId, LedgerOwnerType.USER, userId,
                "USER_BALANCE_" + balanceAccountId, "USER_BALANCE_LIABILITY",
                LedgerAccountClass.LIABILITY, LedgerDirection.CREDIT, "CNY",
                LedgerAccountStatus.ACTIVE, now, now);
    }

    /** 从持久化事实重建账本科目。 */
    public LedgerAccount(String ledgerAccountId, LedgerOwnerType ownerType, String ownerId, String accountCode,
                         String accountType, LedgerAccountClass accountClass, LedgerDirection normalDirection,
                         String currency, LedgerAccountStatus status, Instant createdAt, Instant updatedAt) {
        this.ledgerAccountId = requireText(ledgerAccountId, "账本科目 ID 不能为空");
        this.ownerType = Objects.requireNonNull(ownerType, "科目所有者类型不能为空");
        this.ownerId = requireText(ownerId, "科目所有者 ID 不能为空");
        this.accountCode = requireText(accountCode, "科目编码不能为空");
        if (accountCode.length() > 64) throw new IllegalArgumentException("科目编码不能超过 64 个字符");
        this.accountType = requireText(accountType, "科目类型不能为空");
        this.accountClass = Objects.requireNonNull(accountClass, "科目分类不能为空");
        this.normalDirection = Objects.requireNonNull(normalDirection, "正常方向不能为空");
        this.currency = requireText(currency, "币种不能为空");
        this.status = Objects.requireNonNull(status, "科目状态不能为空");
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
        this.updatedAt = Objects.requireNonNull(updatedAt, "更新时间不能为空");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value;
    }

    /** @return 账本科目 ID */ public String getLedgerAccountId() { return ledgerAccountId; }
    /** @return 所有者类型 */ public LedgerOwnerType getOwnerType() { return ownerType; }
    /** @return 所有者 ID */ public String getOwnerId() { return ownerId; }
    /** @return 稳定科目编码 */ public String getAccountCode() { return accountCode; }
    /** @return 业务科目类型 */ public String getAccountType() { return accountType; }
    /** @return 会计科目分类 */ public LedgerAccountClass getAccountClass() { return accountClass; }
    /** @return 正常余额方向 */ public LedgerDirection getNormalDirection() { return normalDirection; }
    /** @return 币种 */ public String getCurrency() { return currency; }
    /** @return 科目状态 */ public LedgerAccountStatus getStatus() { return status; }
    /** @return 创建时间 */ public Instant getCreatedAt() { return createdAt; }
    /** @return 更新时间 */ public Instant getUpdatedAt() { return updatedAt; }
}
