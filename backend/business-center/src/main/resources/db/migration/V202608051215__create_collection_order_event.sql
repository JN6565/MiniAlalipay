-- C2C SSE 只能重放已持久化的最小公开状态事件；表中严禁保存账户、会话、原始令牌、确认令牌或支付证明。
CREATE TABLE IF NOT EXISTS business_db.collection_order_event (
    event_id VARCHAR(64) NOT NULL,
    order_id CHAR(26) NULL,
    request_id CHAR(26) NOT NULL,
    transaction_id CHAR(26) NULL,
    status VARCHAR(32) NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    retention_until DATETIME(3) NOT NULL,
    PRIMARY KEY (event_id),
    KEY idx_collection_order_event_request_replay (request_id, event_id),
    KEY idx_collection_order_event_retention (retention_until),
    CONSTRAINT ck_collection_order_event_status CHECK (
        status IN ('OPEN','PENDING_CONFIRMATION','PROCESSING','SUCCESS','CANCELLED','EXPIRED','MANUAL_REVIEW')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 已存在的固定请求在首次升级后也必须能发送权威快照；补录仅含请求当前公开状态。
INSERT IGNORE INTO business_db.collection_order_event
    (event_id, order_id, request_id, transaction_id, status, occurred_at, retention_until)
SELECT CONCAT('legacy:', request_id, ':', version), active_order_id, request_id, transaction_id,
       CASE status
           WHEN 'RESERVED' THEN 'PENDING_CONFIRMATION'
           WHEN 'CLOSED' THEN 'CANCELLED'
           ELSE status
       END,
       updated_at, DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 7 DAY)
FROM business_db.collection_request;

-- 固定请求一旦进入统一交易受理或终态，状态与文档定义保持一致；RESERVED 只保留为受理前内部仲裁状态。
ALTER TABLE business_db.collection_request DROP CHECK ck_collection_request_status;
-- 历史 CLOSED 是受理前取消的旧命名，先扩展检查约束后统一为对外可见的 CANCELLED。
UPDATE business_db.collection_request SET status = 'CANCELLED' WHERE status = 'CLOSED';
ALTER TABLE business_db.collection_request
    ADD CONSTRAINT ck_collection_request_status CHECK (
        status IN ('OPEN','RESERVED','PROCESSING','SUCCESS','CANCELLED','EXPIRED','MANUAL_REVIEW')
    );
