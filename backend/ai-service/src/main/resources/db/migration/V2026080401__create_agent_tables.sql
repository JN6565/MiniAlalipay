-- AI 服务 agent_db 表结构迁移。
-- 表定义与 deploy/mysql/init/00-create-schemas.sql 中的 AI 表保持一致。
-- 使用 CREATE TABLE IF NOT EXISTS 保证与 Docker 初始化脚本共存时不冲突。
-- agent_db 归属 ai-service，保存脱敏会话、消息、工具调用证据和用户偏好。
-- AI 不持有账户写权限，也不能决定资金终态。

-- ============================================================
-- AI 对话会话
-- ============================================================

-- AI 对话会话。结构化槽位仅用于草稿编排，不能替代业务库中的金额、账户或交易事实。
-- 版本号用于 CAS 并发控制；会话超时后自动进入 EXPIRED 状态，未提交草稿也按规则过期。
CREATE TABLE IF NOT EXISTS agent_db.agent_session (
    session_id CHAR(26) NOT NULL,
    user_id CHAR(26) NOT NULL,
    summary TEXT NULL,
    slots_json JSON NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    last_active_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (session_id),
    KEY idx_agent_session_user_active (user_id, last_active_at),
    KEY idx_agent_session_status_active (status, last_active_at),
    CONSTRAINT ck_agent_session_status CHECK (status IN ('ACTIVE', 'CLOSED', 'EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================
-- 脱敏对话消息
-- ============================================================

-- 脱敏对话消息。client_message_id 与角色联合唯一，防止重试重复生成同一回复。
-- content_redacted 为脱敏后的消息正文，原始文本不得进入持久化路径。
CREATE TABLE IF NOT EXISTS agent_db.agent_message (
    message_id CHAR(26) NOT NULL,
    session_id CHAR(26) NOT NULL,
    client_message_id VARCHAR(64) NOT NULL,
    role VARCHAR(16) NOT NULL,
    content_redacted TEXT NOT NULL,
    token_count INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (message_id),
    UNIQUE KEY uk_agent_message_client_role (session_id, client_message_id, role),
    KEY idx_agent_message_session_time (session_id, created_at),
    CONSTRAINT fk_agent_message_session FOREIGN KEY (session_id)
        REFERENCES agent_db.agent_session (session_id),
    CONSTRAINT ck_agent_message_role CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================
-- MCP 工具调用证据
-- ============================================================

-- MCP 工具调用证据。只保存规范化请求摘要和标准结果，不落原始敏感参数。
-- trace_id 关联跨服务调用链路，duration_ms 用于监控和告警。
CREATE TABLE IF NOT EXISTS agent_db.tool_call_log (
    tool_call_id CHAR(26) NOT NULL,
    session_id CHAR(26) NOT NULL,
    tool_name VARCHAR(64) NOT NULL,
    request_digest BINARY(32) NOT NULL,
    result_code VARCHAR(32) NOT NULL,
    duration_ms INT UNSIGNED NOT NULL,
    trace_id CHAR(32) NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    PRIMARY KEY (tool_call_id),
    KEY idx_tool_call_trace_time (trace_id, occurred_at),
    KEY idx_tool_call_session_time (session_id, occurred_at),
    CONSTRAINT fk_tool_call_session FOREIGN KEY (session_id)
        REFERENCES agent_db.agent_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================
-- 用户授权的低敏偏好
-- ============================================================

-- 用户授权的低敏偏好。偏好只能影响候选排序，不能自动选定收款人、金额或跳过确认。
-- value_encrypted 为加密偏好值；consent_version 记录用户同意的版本号。
CREATE TABLE IF NOT EXISTS agent_db.preference (
    preference_id CHAR(26) NOT NULL,
    user_id CHAR(26) NOT NULL,
    preference_type VARCHAR(32) NOT NULL,
    value_encrypted VARBINARY(1024) NOT NULL,
    consent_version VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (preference_id),
    UNIQUE KEY uk_preference_user_type (user_id, preference_type),
    KEY idx_preference_user_status (user_id, status),
    CONSTRAINT ck_preference_status CHECK (status IN ('ACTIVE', 'REVOKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================
-- AI 服务写接口幂等记录
-- ============================================================

-- AI 服务写接口幂等记录，避免重复消息或工具编排造成额外业务副作用。
-- 同键异参必须返回 IDEMPOTENCY_CONFLICT，同键同参返回原结果。
CREATE TABLE IF NOT EXISTS agent_db.idempotency_record (
    record_id CHAR(26) NOT NULL,
    principal_key VARCHAR(128) NOT NULL,
    api_scope VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_digest BINARY(32) NOT NULL,
    resource_type VARCHAR(32) NULL,
    resource_id CHAR(26) NULL,
    response_json JSON NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING',
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (record_id),
    UNIQUE KEY uk_idempotency_scope (principal_key, api_scope, idempotency_key),
    KEY idx_idempotency_recovery (status, updated_at),
    KEY idx_idempotency_expire (expires_at),
    CONSTRAINT ck_idempotency_status CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================
-- AI 服务安全审计日志
-- ============================================================

-- AI 服务审计日志。detail_json 必须脱敏，禁止记录密码、令牌、完整账号或未脱敏对话。
-- trace_id 关联全链路追踪，target_id 为多态引用。
CREATE TABLE IF NOT EXISTS agent_db.audit_log (
    audit_id BIGINT UNSIGNED NOT NULL,
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
    KEY idx_audit_trace (trace_id),
    CONSTRAINT ck_audit_actor_type CHECK (actor_type IN ('USER', 'SYSTEM', 'OPERATOR'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================
-- AI 服务可靠事件表
-- ============================================================

-- AI 服务可靠事件表。会话事实与事件同事务写入，事件载荷不得包含原始敏感信息。
-- aggregate_type + aggregate_id + aggregate_version + event_type 联合唯一，
-- 保证同一聚合版本的同一事件只发布一次。
CREATE TABLE IF NOT EXISTS agent_db.outbox_event (
    event_id CHAR(26) NOT NULL,
    aggregate_type VARCHAR(32) NOT NULL,
    aggregate_id CHAR(26) NOT NULL,
    aggregate_version BIGINT UNSIGNED NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    event_version SMALLINT UNSIGNED NOT NULL,
    business_type VARCHAR(16) NULL,
    source_type VARCHAR(32) NULL,
    source_order_id CHAR(26) NULL,
    funding_source VARCHAR(16) NULL,
    transaction_id CHAR(26) NULL,
    producer VARCHAR(32) NOT NULL,
    account_id CHAR(26) NULL,
    merchant_account_id CHAR(26) NULL,
    user_id_hash BINARY(32) NULL,
    trace_id CHAR(32) NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    retry_count INT UNSIGNED NOT NULL DEFAULT 0,
    next_retry_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    published_at DATETIME(3) NULL,
    PRIMARY KEY (event_id),
    UNIQUE KEY uk_outbox_aggregate_version (
        aggregate_type, aggregate_id, aggregate_version, event_type
    ),
    KEY idx_outbox_publish (status, next_retry_at),
    KEY idx_outbox_transaction_type (transaction_id, event_type),
    KEY idx_outbox_occurred_type (occurred_at, event_type),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'DEAD')),
    CONSTRAINT ck_outbox_funding_source CHECK (
        funding_source IS NULL OR funding_source IN ('BALANCE', 'MINI_CREDIT', 'SYSTEM_ISSUANCE')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
