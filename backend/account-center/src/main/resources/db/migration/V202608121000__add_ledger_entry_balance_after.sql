-- 账本分录增加「交易后余额」展示列。
--
-- 设计目的：C 端账户明细页每笔交易需展示「交易后剩余余额」。该值是展示事实而非记账事实，
-- 由过账时（LedgerRepositoryImpl.postAndAppendOutbox）根据账户当前余额回填，
-- 因此列允许 NULL：非个人账户分录、以及本迁移执行前的存量分录均无值，展示层回退为不显示。
--
-- 不做存量回填的原因：回填需要联查 account_db.account_balance 取当前余额，
-- 而迁移规范禁止一条迁移跨 Schema 联查；存量数据缺口不影响资金正确性，仅影响历史明细的余额展示。
ALTER TABLE ledger_db.ledger_entry
    ADD COLUMN balance_after_fen BIGINT UNSIGNED NULL COMMENT '该分录所属用户账户在交易完成后的可用余额（分），仅展示用，过账时回填，存量与系统账户分录为 NULL' AFTER amount_fen;
