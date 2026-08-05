package com.minialalipay.account.domain.tcc;

/** TCC Cancel 的回滚事实类型。 */
public enum RollbackType {
    /** Try 已成功后的正常回滚。 */ NORMAL,
    /** Cancel 先于 Try 到达时建立的空回滚屏障。 */ EMPTY
}
