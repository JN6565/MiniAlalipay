/**
 * 复式账本持久化适配器。
 *
 * <p>独占读写 ledger_db 的科目、凭证和分录；分录只允许新增和查询，禁止提供更新或删除入口。</p>
 */
package com.minialalipay.account.infrastructure.ledger;
