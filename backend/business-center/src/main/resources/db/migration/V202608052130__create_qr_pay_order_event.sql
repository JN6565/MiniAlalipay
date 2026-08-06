-- 动态扫码 SSE 重放只记录最小公开状态，禁止保存二维码原始令牌、会话、账户或确认材料。
-- 保留七天使 Last-Event-ID 能够断线续传；超过保留期由接口返回 EVENT_CURSOR_EXPIRED 并回源订单查询。
CREATE TABLE IF NOT EXISTS business_db.qr_pay_order_event (
    event_id VARCHAR(64) NOT NULL COMMENT 'SSE 事件标识，同时作为 Last-Event-ID 游标',
    qr_order_id CHAR(26) NOT NULL COMMENT '动态扫码来源订单标识',
    transaction_id CHAR(26) NULL COMMENT '统一资金交易标识，受理前可为空',
    status VARCHAR(32) NOT NULL COMMENT '订单公开状态，不代表客户端可自行判定资金成功',
    occurred_at DATETIME(3) NOT NULL COMMENT '状态发生时间',
    retention_until DATETIME(3) NOT NULL COMMENT '事件重放保留截止时间',
    PRIMARY KEY (event_id),
    KEY idx_qr_pay_order_event_replay (qr_order_id, occurred_at, event_id),
    KEY idx_qr_pay_order_event_retention (retention_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
