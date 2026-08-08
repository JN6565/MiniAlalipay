package com.minialalipay.account.domain.bankcard;

/**
 * 银行卡绑定状态。
 *
 * <p>状态流转：绑卡成功进入 ACTIVE；解绑后进入 UNBOUND。
 * UNBOUND 为终态，不可重新激活；同一张实体卡解绑后如需再使用，
 * 必须重新走完整绑卡流程生成新的绑定记录。
 */
public enum BankCardStatus {
    /** 已绑定：可用于设默认、解绑，后续阶段可作为资金来源。 */
    ACTIVE,
    /** 已解绑（终态）：不再出现在用户卡列表，禁止任何后续操作。 */
    UNBOUND
}
