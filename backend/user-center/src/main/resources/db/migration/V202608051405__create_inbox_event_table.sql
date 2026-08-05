-- Inbox 事件表。
-- 保存事件消费幂等和接管状态，重复事件不得重复修改投影。
-- 参考 docs/minialalipay/minialalipay-database-design.md 第 10.4 节。

CREATE TABLE IF NOT EXISTS user_db.inbox_event (
    consumer_name VARCHAR(64) NOT NULL,
    event_id CHAR(26) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING',
    received_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (consumer_name, event_id),
    KEY idx_inbox_status (status, updated_at),
    CONSTRAINT ck_inbox_status CHECK (status IN ('PROCESSING', 'DONE', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
