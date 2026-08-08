-- 为会话表增加用户自定义标题列，支持前端"编辑会话名称"功能。
-- title 优先展示用户自定义名称；为 NULL 时前端回退到首条用户消息摘要。
ALTER TABLE agent_db.agent_session
    ADD COLUMN title VARCHAR(100) NULL AFTER summary;
