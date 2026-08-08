-- 资金交易表结构对齐（2026-08-07 丢失迁移的重建版本）。
--
-- 背景：本迁移原文件在本地丢失（git 无记录），但已在 business_db 应用；
-- 现依据线上 information_schema 实际结构反推重建，版本号与描述沿用历史记录。
-- 所有语句通过 information_schema 条件判断，对“已对齐”与“未对齐”两种库均幂等可重放。

-- 1. payer_account_id 放宽为可空：统一资金交易在人工复核/补偿等受理前阶段
--    可能尚未确定付款账户映射，强制 NOT NULL 会阻塞这些合法写入。
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='fund_transaction'
       AND COLUMN_NAME='payer_account_id' AND IS_NULLABLE='NO') > 0,
    'ALTER TABLE business_db.fund_transaction MODIFY COLUMN payer_account_id CHAR(26) NULL', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 2. 收款方时间线索引命名统一（与 202608071800 的运维索引命名约定一致）
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='fund_transaction' AND INDEX_NAME='idx_fund_transaction_payee') > 0
    AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='fund_transaction' AND INDEX_NAME='idx_fund_transaction_payee_time') = 0,
    'ALTER TABLE business_db.fund_transaction RENAME INDEX idx_fund_transaction_payee TO idx_fund_transaction_payee_time', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 3. 运维查询补充索引：按业务类型时间线、关联交易回溯、创建时间范围扫描
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='fund_transaction' AND INDEX_NAME='idx_fund_transaction_business_time') = 0,
    'CREATE INDEX idx_fund_transaction_business_time ON business_db.fund_transaction (business_type, created_at)', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='fund_transaction' AND INDEX_NAME='idx_fund_transaction_related') = 0,
    'CREATE INDEX idx_fund_transaction_related ON business_db.fund_transaction (related_transaction_id)', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='fund_transaction' AND INDEX_NAME='idx_fund_transaction_created') = 0,
    'CREATE INDEX idx_fund_transaction_created ON business_db.fund_transaction (created_at)', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
