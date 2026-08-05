-- 审计日志表。
-- 保存不可变、脱敏的安全和业务操作证据，只追加不修改不删除。
-- 参考 docs/minialalipay/minialalipay-database-design.md 第 10.5 节。

CREATE TABLE IF NOT EXISTS user_db.audit_log (
    audit_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    actor_type VARCHAR(16) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id VARCHAR(128) NOT NULL,
    result_code VARCHAR(32) NOT NULL,
    trace_id CHAR(32) NOT NULL,
    detail_json JSON NULL,
    occurred_at DATETIME(3) NOT NULL,
    PRIMARY KEY (audit_id),
    KEY idx_audit_actor_time (actor_id, occurred_at),
    KEY idx_audit_target_time (target_type, target_id, occurred_at),
    KEY idx_audit_trace (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
