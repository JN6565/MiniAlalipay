-- 为个人收款码增加 Mini 花呗商户收款开关。
-- 该开关默认关闭；关闭时个人码和固定金额收款请求只能通过余额或银行卡收款。
ALTER TABLE business_db.personal_collection_code
    ADD COLUMN credit_collection_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已开通 Mini 花呗商户收款码'
        AFTER token_digest;

CREATE INDEX idx_personal_collection_code_credit_enabled
    ON business_db.personal_collection_code (owner_user_id, credit_collection_enabled, status);
