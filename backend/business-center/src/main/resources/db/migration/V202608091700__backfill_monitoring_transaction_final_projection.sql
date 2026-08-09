-- 最终交易投影在 V202608091600 新建前已经存在交易事实；若不回填，可信运行看板与 T+1 日报会把历史成功交易误显示为零。
-- 历史监控事件缺失 amountFen，故本次一次性修复读取 business-center 自有的业务交易快照与 Outbox 时间戳；日常报表查询仍只读 metrics_db。
-- 条件更新仅接受来源时间更晚或同时间事件号更大的行，保护部署期间消费者已经写入的终态，回填可幂等重试。
INSERT INTO metrics_db.monitoring_transaction_final_projection
        (transaction_id, amount_fen, business_type, status, accepted_at, terminal_at,
         source_occurred_at, source_event_id, updated_at)
SELECT
    fund_tx.transaction_id,
    fund_tx.amount_fen,
    fund_tx.business_type,
    fund_tx.status,
    fund_tx.created_at,
    CASE
        WHEN fund_tx.status IN ('SUCCESS', 'REVERSED', 'CANCELLED')
            THEN COALESCE(latest_event.occurred_at, fund_tx.updated_at)
        ELSE NULL
    END,
    COALESCE(latest_event.occurred_at, fund_tx.updated_at),
    COALESCE(latest_event.event_id, fund_tx.transaction_id),
    COALESCE(latest_event.occurred_at, fund_tx.updated_at)
FROM business_db.fund_transaction AS fund_tx
LEFT JOIN (
    SELECT candidate.transaction_id, candidate.occurred_at, candidate.event_id
    FROM business_db.outbox_event AS candidate
    WHERE candidate.event_type IN ('transaction.accepted', 'transaction.status.changed')
      AND candidate.transaction_id IS NOT NULL
      AND NOT EXISTS (
          SELECT 1
          FROM business_db.outbox_event AS newer
          WHERE newer.transaction_id = candidate.transaction_id
            AND newer.event_type IN ('transaction.accepted', 'transaction.status.changed')
            AND (newer.occurred_at > candidate.occurred_at
                OR (newer.occurred_at = candidate.occurred_at AND newer.event_id > candidate.event_id))
      )
) AS latest_event
    ON latest_event.transaction_id = fund_tx.transaction_id
ON DUPLICATE KEY UPDATE
    amount_fen = CASE
        WHEN VALUES(source_occurred_at) > metrics_db.monitoring_transaction_final_projection.source_occurred_at
          OR (VALUES(source_occurred_at) = metrics_db.monitoring_transaction_final_projection.source_occurred_at AND VALUES(source_event_id) > metrics_db.monitoring_transaction_final_projection.source_event_id)
            THEN VALUES(amount_fen)
        ELSE metrics_db.monitoring_transaction_final_projection.amount_fen
    END,
    business_type = CASE
        WHEN VALUES(source_occurred_at) > metrics_db.monitoring_transaction_final_projection.source_occurred_at
          OR (VALUES(source_occurred_at) = metrics_db.monitoring_transaction_final_projection.source_occurred_at AND VALUES(source_event_id) > metrics_db.monitoring_transaction_final_projection.source_event_id)
            THEN VALUES(business_type)
        ELSE metrics_db.monitoring_transaction_final_projection.business_type
    END,
    status = CASE
        WHEN VALUES(source_occurred_at) > metrics_db.monitoring_transaction_final_projection.source_occurred_at
          OR (VALUES(source_occurred_at) = metrics_db.monitoring_transaction_final_projection.source_occurred_at AND VALUES(source_event_id) > metrics_db.monitoring_transaction_final_projection.source_event_id)
            THEN VALUES(status)
        ELSE metrics_db.monitoring_transaction_final_projection.status
    END,
    accepted_at = COALESCE(metrics_db.monitoring_transaction_final_projection.accepted_at, VALUES(accepted_at)),
    terminal_at = CASE
        WHEN VALUES(source_occurred_at) > metrics_db.monitoring_transaction_final_projection.source_occurred_at
          OR (VALUES(source_occurred_at) = metrics_db.monitoring_transaction_final_projection.source_occurred_at AND VALUES(source_event_id) > metrics_db.monitoring_transaction_final_projection.source_event_id)
            THEN VALUES(terminal_at)
        ELSE metrics_db.monitoring_transaction_final_projection.terminal_at
    END,
    source_occurred_at = CASE
        WHEN VALUES(source_occurred_at) > metrics_db.monitoring_transaction_final_projection.source_occurred_at
          OR (VALUES(source_occurred_at) = metrics_db.monitoring_transaction_final_projection.source_occurred_at AND VALUES(source_event_id) > metrics_db.monitoring_transaction_final_projection.source_event_id)
            THEN VALUES(source_occurred_at)
        ELSE metrics_db.monitoring_transaction_final_projection.source_occurred_at
    END,
    source_event_id = CASE
        WHEN VALUES(source_occurred_at) > metrics_db.monitoring_transaction_final_projection.source_occurred_at
          OR (VALUES(source_occurred_at) = metrics_db.monitoring_transaction_final_projection.source_occurred_at AND VALUES(source_event_id) > metrics_db.monitoring_transaction_final_projection.source_event_id)
            THEN VALUES(source_event_id)
        ELSE metrics_db.monitoring_transaction_final_projection.source_event_id
    END,
    updated_at = CASE
        WHEN VALUES(source_occurred_at) > metrics_db.monitoring_transaction_final_projection.source_occurred_at
          OR (VALUES(source_occurred_at) = metrics_db.monitoring_transaction_final_projection.source_occurred_at AND VALUES(source_event_id) > metrics_db.monitoring_transaction_final_projection.source_event_id)
            THEN VALUES(updated_at)
        ELSE metrics_db.monitoring_transaction_final_projection.updated_at
    END;
