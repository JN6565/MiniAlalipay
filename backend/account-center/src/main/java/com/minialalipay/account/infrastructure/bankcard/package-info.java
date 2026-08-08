/**
 * 银行卡基础设施层。
 *
 * <p>职责：以 account_db.bank_card 表实现银行卡仓储端口，
 * 更新统一使用乐观锁 CAS 保护默认卡互斥与解绑终态。
 * 表中只存掩码与 BIN/尾号，实现层禁止输出或记录完整卡号明文。</p>
 */
package com.minialalipay.account.infrastructure.bankcard;
