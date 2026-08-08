-- 为 agent_message 表增加消息类型和工具名称列，支持区分文本回复和工具结果消息。
-- 历史消息恢复时，前端可根据 kind 重建工具结果卡片（余额、额度、交易记录等）。
-- tool_name 仅在 kind = 'TOOL_RESULT' 时有值，记录产生该结果的工具名称。

ALTER TABLE agent_db.agent_message
    ADD COLUMN kind VARCHAR(16) NOT NULL DEFAULT 'TEXT' COMMENT '消息类型：TEXT（文本回复）/ TOOL_RESULT（工具结果）',
    ADD COLUMN tool_name VARCHAR(64) NULL COMMENT '工具名称，仅 TOOL_RESULT 类型有值';
