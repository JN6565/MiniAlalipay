-- H2 测试 Schema：创建 agent_db 核心表用于 Mapper 集成测试。
-- H2 的 MySQL 兼容模式不支持 JSON、BINARY(32) 等类型，
-- 使用等效类型替代：JSON → CLOB、BINARY(32) → BINARY(32)、VARBINARY → BINARY。

CREATE SCHEMA IF NOT EXISTS AGENT_DB;

CREATE TABLE IF NOT EXISTS AGENT_DB.agent_session (
    session_id CHAR(26) NOT NULL,
    user_id CHAR(26) NOT NULL,
    summary TEXT NULL,
    title VARCHAR(100) NULL,
    slots_json TEXT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    last_active_at TIMESTAMP(3) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (session_id),
    CONSTRAINT ck_agent_session_status CHECK (status IN ('ACTIVE', 'CLOSED', 'EXPIRED'))
);

CREATE TABLE IF NOT EXISTS AGENT_DB.agent_message (
    message_id CHAR(26) NOT NULL,
    session_id CHAR(26) NOT NULL,
    client_message_id VARCHAR(64) NOT NULL,
    role VARCHAR(16) NOT NULL,
    content_redacted TEXT NOT NULL,
    token_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (message_id),
    UNIQUE KEY uk_agent_message_client_role (session_id, client_message_id, role),
    CONSTRAINT fk_agent_message_session FOREIGN KEY (session_id)
        REFERENCES AGENT_DB.agent_session (session_id),
    CONSTRAINT ck_agent_message_role CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM'))
);

CREATE TABLE IF NOT EXISTS AGENT_DB.tool_call_log (
    tool_call_id CHAR(26) NOT NULL,
    session_id CHAR(26) NOT NULL,
    tool_name VARCHAR(64) NOT NULL,
    request_digest BINARY(32) NOT NULL,
    result_code VARCHAR(32) NOT NULL,
    duration_ms INT NOT NULL,
    trace_id CHAR(32) NOT NULL,
    occurred_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (tool_call_id),
    CONSTRAINT fk_tool_call_session FOREIGN KEY (session_id)
        REFERENCES AGENT_DB.agent_session (session_id)
);
