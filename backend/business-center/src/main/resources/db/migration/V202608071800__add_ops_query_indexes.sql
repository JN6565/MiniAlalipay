-- 运维查询索引命名统一与补充（2026-08-07 丢失迁移的重建版本）。
--
-- 背景：本迁移原文件在本地丢失（git 无记录），但已在 business_db 应用；
-- 现依据线上 information_schema 实际结构反推重建，版本号与描述沿用历史记录。
-- 所有语句通过 information_schema 条件判断，对“已对齐”与“未对齐”两种库均幂等可重放。
--
-- 命名约定：运维/查询索引统一为 业务键_time / 业务键_expire / 状态_recovery 风格，
-- 便于按“谁在什么时间窗口查什么状态”定位索引。

-- 1. transfer_draft：按付款人与状态查询草稿、过期扫描
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='transfer_draft' AND INDEX_NAME='idx_transfer_draft_owner') > 0
    AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='transfer_draft' AND INDEX_NAME='idx_transfer_draft_payer_status_time') = 0,
    'ALTER TABLE business_db.transfer_draft RENAME INDEX idx_transfer_draft_owner TO idx_transfer_draft_payer_status_time', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='transfer_draft' AND INDEX_NAME='idx_transfer_draft_expiry') > 0
    AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='transfer_draft' AND INDEX_NAME='idx_transfer_draft_status_expire') = 0,
    'ALTER TABLE business_db.transfer_draft RENAME INDEX idx_transfer_draft_expiry TO idx_transfer_draft_status_expire', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 2. confirmation：按付款人查活跃确认快照、过期扫描
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='confirmation' AND INDEX_NAME='idx_confirmation_owner') > 0
    AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='confirmation' AND INDEX_NAME='idx_confirmation_payer_status_version') = 0,
    'ALTER TABLE business_db.confirmation RENAME INDEX idx_confirmation_owner TO idx_confirmation_payer_status_version', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='confirmation' AND INDEX_NAME='idx_confirmation_expiry') > 0
    AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='confirmation' AND INDEX_NAME='idx_confirmation_status_expire') = 0,
    'ALTER TABLE business_db.confirmation RENAME INDEX idx_confirmation_expiry TO idx_confirmation_status_expire', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 3. idempotency_record：恢复扫描与过期清理
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='idempotency_record' AND INDEX_NAME='idx_idempotency_status') > 0
    AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='idempotency_record' AND INDEX_NAME='idx_idempotency_recovery') = 0,
    'ALTER TABLE business_db.idempotency_record RENAME INDEX idx_idempotency_status TO idx_idempotency_recovery', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='idempotency_record' AND INDEX_NAME='idx_idempotency_expiry') > 0
    AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='idempotency_record' AND INDEX_NAME='idx_idempotency_expire') = 0,
    'ALTER TABLE business_db.idempotency_record RENAME INDEX idx_idempotency_expiry TO idx_idempotency_expire', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 4. outbox_event：唯一键与发布/查询索引命名去 business_ 前缀，补按发生时间的事件检索索引
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='outbox_event' AND INDEX_NAME='uk_business_outbox_version') > 0
    AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='outbox_event' AND INDEX_NAME='uk_outbox_aggregate_version') = 0,
    'ALTER TABLE business_db.outbox_event RENAME INDEX uk_business_outbox_version TO uk_outbox_aggregate_version', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='outbox_event' AND INDEX_NAME='idx_business_outbox_publish') > 0
    AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='outbox_event' AND INDEX_NAME='idx_outbox_publish') = 0,
    'ALTER TABLE business_db.outbox_event RENAME INDEX idx_business_outbox_publish TO idx_outbox_publish', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='outbox_event' AND INDEX_NAME='idx_business_outbox_transaction') > 0
    AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='outbox_event' AND INDEX_NAME='idx_outbox_transaction_type') = 0,
    'ALTER TABLE business_db.outbox_event RENAME INDEX idx_business_outbox_transaction TO idx_outbox_transaction_type', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='outbox_event' AND INDEX_NAME='idx_outbox_occurred_type') = 0,
    'CREATE INDEX idx_outbox_occurred_type ON business_db.outbox_event (occurred_at, event_type)', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 5. collection_order：收款订单四个查询路径索引命名统一
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='collection_order' AND INDEX_NAME='idx_collection_order_request') > 0
    AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='collection_order' AND INDEX_NAME='idx_collection_order_request_status') = 0,
    'ALTER TABLE business_db.collection_order RENAME INDEX idx_collection_order_request TO idx_collection_order_request_status', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='collection_order' AND INDEX_NAME='idx_collection_order_code') > 0
    AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='collection_order' AND INDEX_NAME='idx_collection_order_code_status') = 0,
    'ALTER TABLE business_db.collection_order RENAME INDEX idx_collection_order_code TO idx_collection_order_code_status', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='collection_order' AND INDEX_NAME='idx_collection_order_payer') > 0
    AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='collection_order' AND INDEX_NAME='idx_collection_order_payer_time') = 0,
    'ALTER TABLE business_db.collection_order RENAME INDEX idx_collection_order_payer TO idx_collection_order_payer_time', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='collection_order' AND INDEX_NAME='idx_collection_order_payee') > 0
    AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='collection_order' AND INDEX_NAME='idx_collection_order_payee_time') = 0,
    'ALTER TABLE business_db.collection_order RENAME INDEX idx_collection_order_payee TO idx_collection_order_payee_time', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 6. collection_request：固定收款请求过期扫描与收款方时间线查询
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='collection_request' AND INDEX_NAME='idx_collection_request_expiry') > 0
    AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='collection_request' AND INDEX_NAME='idx_collection_request_status_expire') = 0,
    'ALTER TABLE business_db.collection_request RENAME INDEX idx_collection_request_expiry TO idx_collection_request_status_expire', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='collection_request' AND INDEX_NAME='idx_collection_request_owner') > 0
    AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='collection_request' AND INDEX_NAME='idx_collection_request_owner_time') = 0,
    'ALTER TABLE business_db.collection_request RENAME INDEX idx_collection_request_owner TO idx_collection_request_owner_time', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 7. personal_collection_code：个人码持有者时间线与状态扫描
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='personal_collection_code' AND INDEX_NAME='idx_personal_collection_code_owner') > 0
    AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='personal_collection_code' AND INDEX_NAME='idx_personal_collection_code_owner_time') = 0,
    'ALTER TABLE business_db.personal_collection_code RENAME INDEX idx_personal_collection_code_owner TO idx_personal_collection_code_owner_time', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='personal_collection_code' AND INDEX_NAME='idx_personal_collection_code_status') > 0
    AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='personal_collection_code' AND INDEX_NAME='idx_personal_collection_code_status_updated') = 0,
    'ALTER TABLE business_db.personal_collection_code RENAME INDEX idx_personal_collection_code_status TO idx_personal_collection_code_status_updated', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 8. qr_pay_token / qr_pay_order：扫码令牌过期扫描与订单状态索引命名统一
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='qr_pay_token' AND INDEX_NAME='idx_qr_pay_token_expiry') > 0
    AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='qr_pay_token' AND INDEX_NAME='idx_qr_pay_token_status_expire') = 0,
    'ALTER TABLE business_db.qr_pay_token RENAME INDEX idx_qr_pay_token_expiry TO idx_qr_pay_token_status_expire', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='qr_pay_order' AND INDEX_NAME='idx_qr_pay_order_expiry') > 0
    AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='qr_pay_order' AND INDEX_NAME='idx_qr_pay_order_status_expire') = 0,
    'ALTER TABLE business_db.qr_pay_order RENAME INDEX idx_qr_pay_order_expiry TO idx_qr_pay_order_status_expire', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- qr_pay_order 线上结构以 merchant_account_id 表达收款方（基线脚本建表），
-- 旧 payee 索引依赖的列在基线库中不存在：旧索引存在即删除；仅当 merchant 列存在且新索引缺失时创建。
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='qr_pay_order' AND INDEX_NAME='idx_qr_pay_order_payee') > 0,
    'ALTER TABLE business_db.qr_pay_order DROP INDEX idx_qr_pay_order_payee', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='qr_pay_order' AND COLUMN_NAME='merchant_account_id') > 0
    AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='qr_pay_order' AND INDEX_NAME='idx_qr_pay_order_merchant_status_time') = 0,
    'CREATE INDEX idx_qr_pay_order_merchant_status_time ON business_db.qr_pay_order (merchant_account_id, status, created_at, qr_order_id)', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
