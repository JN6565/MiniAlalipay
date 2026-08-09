-- 监控 Stream 游标与 Inbox 投影在同一 metrics_db 本地事务内推进；崩溃后重读消息并由 Inbox 去重，避免漏记实时指标。
CREATE TABLE IF NOT EXISTS metrics_db.monitoring_stream_checkpoint (
    consumer_name VARCHAR(64) NOT NULL COMMENT '稳定的监控消费者名称',
    stream_cursor VARCHAR(64) NOT NULL COMMENT '已完成持久化的 Redis Stream 消息 ID，初始为 0-0',
    updated_at DATETIME(3) NOT NULL COMMENT '最后一次成功推进时间',
    PRIMARY KEY (consumer_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='监控 Redis Stream 消费检查点，禁止用其修改资金事实';
