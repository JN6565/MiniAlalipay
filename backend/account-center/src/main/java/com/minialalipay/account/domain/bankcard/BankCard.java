package com.minialalipay.account.domain.bankcard;

import com.minialalipay.common.error.BusinessException;

import java.time.Instant;

/**
 * 银行卡绑定聚合根。
 *
 * <p>一次绑卡生成一条聚合记录，保护以下不变量：
 * <ul>
 *   <li>只保存掩码值与 BIN/尾号，聚合内禁止出现完整卡号、证件号或手机号明文；</li>
 *   <li>状态只能 ACTIVE → UNBOUND 单向流转，UNBOUND 为终态，解绑后不可重新激活；</li>
 *   <li>已解绑的卡禁止任何后续操作（设默认、再次解绑都必须拒绝）。</li>
 * </ul>
 * 默认卡互斥（同一用户至多一张 ACTIVE 默认卡）属于跨聚合不变量，
 * 由应用服务在事务内用条件更新保证。</p>
 */
public class BankCard {

    private final String cardId;
    private final String userId;
    private final String accountId;
    private final String bankCode;
    private final String bankName;
    private final BankCardType cardType;
    private final String cardBin;
    private final String cardLast4;
    private final String holderMasked;
    private final String idCardMasked;
    private final String phoneMasked;
    private boolean isDefault;
    private BankCardStatus status;
    private Instant unboundAt;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;

    /** 仓储重建构造器：字段语义与数据库列一一对应。 */
    public BankCard(String cardId, String userId, String accountId, String bankCode, String bankName,
                    BankCardType cardType, String cardBin, String cardLast4,
                    String holderMasked, String idCardMasked, String phoneMasked,
                    boolean isDefault, BankCardStatus status, Instant unboundAt,
                    long version, Instant createdAt, Instant updatedAt) {
        this.cardId = cardId;
        this.userId = userId;
        this.accountId = accountId;
        this.bankCode = bankCode;
        this.bankName = bankName;
        this.cardType = cardType;
        this.cardBin = cardBin;
        this.cardLast4 = cardLast4;
        this.holderMasked = holderMasked;
        this.idCardMasked = idCardMasked;
        this.phoneMasked = phoneMasked;
        this.isDefault = isDefault;
        this.status = status;
        this.unboundAt = unboundAt;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 绑卡工厂：用完整卡号与四要素明文创建 ACTIVE 绑定。
     *
     * <p>明文参数在方法返回后不得被任何调用方保留；聚合只保存
     * BIN、尾号与掩码值。是否默认卡由应用服务根据「首张卡自动默认」规则传入。</p>
     *
     * @param cardId 新生成的银行卡 ID（26 位字符串）
     * @param userId 所属用户 ID
     * @param accountId 关联的个人账户 ID
     * @param bankInfo BIN 字典识别出的发卡行信息
     * @param fullCardNumber 完整卡号明文，只取 BIN 与尾号
     * @param holderName 持卡人姓名明文，落库前掩码
     * @param idCard 身份证号明文，落库前掩码
     * @param phone 预留手机号明文，落库前掩码
     * @param isDefault 是否设为默认卡
     * @param now 绑定时间
     * @return ACTIVE 状态的银行卡聚合
     */
    public static BankCard bind(String cardId, String userId, String accountId,
                                BankCardNumber.BankCardInfo bankInfo, String fullCardNumber,
                                String holderName, String idCard, String phone,
                                boolean isDefault, Instant now) {
        return new BankCard(cardId, userId, accountId, bankInfo.bankCode(), bankInfo.bankName(),
                bankInfo.cardType(), fullCardNumber.substring(0, 6),
                fullCardNumber.substring(fullCardNumber.length() - 4),
                SensitiveMask.maskName(holderName), SensitiveMask.maskIdCard(idCard),
                SensitiveMask.maskPhone(phone), isDefault, BankCardStatus.ACTIVE, null,
                0L, now, now);
    }

    /** 设为默认卡；只允许 ACTIVE 状态，重复设置视为幂等空操作。 */
    public void markDefault(Instant now) {
        requireActive();
        this.isDefault = true;
        this.updatedAt = now;
    }

    /** 取消默认卡标记；用于设默认事务内先清旧默认再置新，保证至多一张默认卡。 */
    public void clearDefault(Instant now) {
        requireActive();
        this.isDefault = false;
        this.updatedAt = now;
    }

    /**
     * 解绑：状态置为 UNBOUND 终态并记录解绑时间。
     *
     * @throws BusinessException 已是 UNBOUND 终态时抛出 BANK_CARD_ALREADY_UNBOUND
     */
    public void unbind(Instant now) {
        requireActive();
        this.status = BankCardStatus.UNBOUND;
        this.isDefault = false;
        this.unboundAt = now;
        this.updatedAt = now;
    }

    /** 非 ACTIVE 状态禁止任何变更操作，统一拒绝以保护终态不可逆。 */
    private void requireActive() {
        if (status != BankCardStatus.ACTIVE) {
            throw new BusinessException(BankCardErrorCode.BANK_CARD_ALREADY_UNBOUND);
        }
    }

    public String getCardId() { return cardId; }
    public String getUserId() { return userId; }
    public String getAccountId() { return accountId; }
    public String getBankCode() { return bankCode; }
    public String getBankName() { return bankName; }
    public BankCardType getCardType() { return cardType; }
    public String getCardBin() { return cardBin; }
    public String getCardLast4() { return cardLast4; }
    public String getHolderMasked() { return holderMasked; }
    public String getIdCardMasked() { return idCardMasked; }
    public String getPhoneMasked() { return phoneMasked; }
    public boolean isDefault() { return isDefault; }
    public BankCardStatus getStatus() { return status; }
    public Instant getUnboundAt() { return unboundAt; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /** 仓储 CAS 更新成功后同步内存版本号，避免后续操作基于过期版本。 */
    public void updateVersion(long newVersion) {
        this.version = newVersion;
    }
}
