-- 日报交易查询索引（从远程库反推重建）：
-- 本迁移原由队友分支于 2026-08-09 10:15 应用到远程 business_db 但从未提交 git，
-- 现依据 information_schema.STATISTICS 中实际存在的索引反推重建，使历史表可解析、
-- 新环境（空库）也能得到与远程库一致的索引结构。
--
-- 说明：
-- 1. fund_transaction 的 (created_at, status, business_type) 复合索引服务 T+1 日报
--    按业务日期范围 + 终态 + 业务类型聚合交易；
-- 2. analytics_event 的三个 (维度, occurred_at) 索引服务日报按账户/业务类型/商户维度
--    回放分析事件。
-- 3. MySQL 不支持 CREATE INDEX IF NOT EXISTS，按仓库既有惯例先查 information_schema
--    再动态执行，远程库已存在同名索引时为空操作，保证幂等可重放。

SET @idx_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'business_db' AND TABLE_NAME = 'fund_transaction'
      AND INDEX_NAME = 'idx_fund_transaction_report_time_status'
);
SET @idx_sql = IF(@idx_exists = 0,
    'CREATE INDEX idx_fund_transaction_report_time_status ON business_db.fund_transaction (created_at, status, business_type)',
    'SELECT 1');
PREPARE idx_stmt FROM @idx_sql; EXECUTE idx_stmt; DEALLOCATE PREPARE idx_stmt;

SET @idx_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'metrics_db' AND TABLE_NAME = 'analytics_event'
      AND INDEX_NAME = 'idx_analytics_event_account_time'
);
SET @idx_sql = IF(@idx_exists = 0,
    'CREATE INDEX idx_analytics_event_account_time ON metrics_db.analytics_event (account_id, occurred_at)',
    'SELECT 1');
PREPARE idx_stmt FROM @idx_sql; EXECUTE idx_stmt; DEALLOCATE PREPARE idx_stmt;

SET @idx_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'metrics_db' AND TABLE_NAME = 'analytics_event'
      AND INDEX_NAME = 'idx_analytics_event_business_time'
);
SET @idx_sql = IF(@idx_exists = 0,
    'CREATE INDEX idx_analytics_event_business_time ON metrics_db.analytics_event (business_type, occurred_at)',
    'SELECT 1');
PREPARE idx_stmt FROM @idx_sql; EXECUTE idx_stmt; DEALLOCATE PREPARE idx_stmt;

SET @idx_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'metrics_db' AND TABLE_NAME = 'analytics_event'
      AND INDEX_NAME = 'idx_analytics_event_merchant_time'
);
SET @idx_sql = IF(@idx_exists = 0,
    'CREATE INDEX idx_analytics_event_merchant_time ON metrics_db.analytics_event (merchant_account_id, occurred_at)',
    'SELECT 1');
PREPARE idx_stmt FROM @idx_sql; EXECUTE idx_stmt; DEALLOCATE PREPARE idx_stmt;
