-- 确认快照补资金来源列（2026-08-07 丢失迁移的重建版本）。
--
-- 背景：本迁移原文件在本地丢失（git 无记录），但已在 business_db 应用；
-- 现依据线上 information_schema 实际结构反推重建，版本号与描述沿用历史记录。
-- 所有语句通过 information_schema 条件判断，对“已对齐”与“未对齐”两种库均幂等可重放。
--
-- 业务含义：confirmation 记录支付密码验证通过后的确认快照，funding_source
-- 固定签名时的资金来源（BALANCE 余额 / MINI_CREDIT 小额信用），
-- 防止确认与支付两个阶段之间资金来源被篡改。

-- 1. 补 funding_source 列：历史确认快照全部来自余额支付，默认值 BALANCE 向前兼容。
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='confirmation' AND COLUMN_NAME='funding_source') = 0,
    'ALTER TABLE business_db.confirmation ADD COLUMN funding_source VARCHAR(16) NOT NULL DEFAULT ''BALANCE'' AFTER pay_password_version', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 2. 收紧取值约束：只允许余额与小额信用两种资金来源，与错误码及领域枚举保持一致。
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='confirmation' AND COLUMN_NAME='funding_source') > 0
    AND (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
     WHERE TABLE_SCHEMA='business_db' AND TABLE_NAME='confirmation'
       AND CONSTRAINT_NAME='ck_confirmation_funding_source' AND CONSTRAINT_TYPE='CHECK') = 0,
    'ALTER TABLE business_db.confirmation ADD CONSTRAINT ck_confirmation_funding_source CHECK (funding_source IN (''BALANCE'',''MINI_CREDIT''))', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
