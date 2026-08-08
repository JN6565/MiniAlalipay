package com.minialalipay.account.domain.bankcard;

/**
 * 银行卡绑定状态。
 *
 * <p>状态流转：绑卡成功进入 ACTIVE；解绑后进入 UNBOUND。
 * UNBOUND 是绑定记录的终态，不可重新激活；同一张实体卡解绑后如需再使用，
 * 必须重新走完整绑卡流程生成新的绑定记录（解绑时会同步释放对应的
 * 银行卡注册记录，使其回到可绑定状态）。
 */
public enum BankCardStatus {
    /** 已绑定：可用于设默认、解绑，后续阶段可作为资金来源。 */
    ACTIVE,
    /** 已解绑（绑定记录终态）：不再出现在用户卡列表，禁止任何后续操作；实体卡可经注册记录重新绑卡。 */
    UNBOUND
}
