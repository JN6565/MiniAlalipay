package com.minialalipay.account.domain.ledger;

/** 会计科目分类，决定科目的经济含义而非接口权限。 */
public enum LedgerAccountClass {
    /** 资产类科目，正常余额方向通常为借方。 */
    ASSET,
    /** 负债类科目，用户虚拟余额属于平台对用户的负债。 */
    LIABILITY,
    /** 权益类科目，模拟虚拟资金发行使用该类。 */
    EQUITY
}
