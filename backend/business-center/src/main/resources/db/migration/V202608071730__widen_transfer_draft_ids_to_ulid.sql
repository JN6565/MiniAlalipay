-- 历史数据库中的转账草稿 ID 字段曾为 14 位，无法保存系统统一的 26 位 ULID。
-- 本向前迁移只扩容字段，不改变已有数据和主键语义，避免新建草稿时发生截断。
ALTER TABLE business_db.transfer_draft
    MODIFY COLUMN draft_id CHAR(26) NOT NULL,
    MODIFY COLUMN payer_user_id CHAR(26) NOT NULL,
    MODIFY COLUMN payee_user_id CHAR(26) NOT NULL,
    MODIFY COLUMN payer_account_id CHAR(26) NOT NULL,
    MODIFY COLUMN payee_account_id CHAR(26) NOT NULL;
