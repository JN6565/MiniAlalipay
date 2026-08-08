-- 银行卡注册表：记录用户注册的银行卡（尚未绑定到账户）。
-- 注册时自动生成卡号并保存三要素哈希，绑定时与用户存储身份交叉比对。
-- 安全约束：完整卡号仅在注册时短暂返回给前端，表中保存完整卡号用于模拟环境绑定时匹配。
CREATE TABLE IF NOT EXISTS account_db.bank_card_registration (
    registration_id CHAR(26) NOT NULL COMMENT '注册记录 ID',
    user_id CHAR(26) NOT NULL COMMENT '注册操作人（记录是谁注册的）',
    bank_code VARCHAR(32) NOT NULL COMMENT '银行编码',
    bank_name VARCHAR(64) NOT NULL COMMENT '银行名称',
    card_type VARCHAR(16) NOT NULL COMMENT 'DEBIT/CREDIT',
    card_number VARCHAR(19) NOT NULL COMMENT '自动生成的完整卡号（模拟环境允许）',
    card_bin CHAR(6) NOT NULL COMMENT 'BIN 前 6 位',
    card_last4 CHAR(4) NOT NULL COMMENT '尾号后 4 位',
    holder_name VARCHAR(32) NOT NULL COMMENT '持卡人姓名明文（绑定时用于比对）',
    id_card_hash BINARY(32) NOT NULL COMMENT '身份证号哈希（绑定时用于比对）',
    phone_hash BINARY(32) NOT NULL COMMENT '手机号哈希（绑定时用于比对）',
    status VARCHAR(16) NOT NULL DEFAULT 'REGISTERED' COMMENT 'REGISTERED/BOUND',
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (registration_id),
    KEY idx_bcr_user_status (user_id, status),
    KEY idx_bcr_card_number (card_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- bank_card 表新增 registration_id 列，关联银行卡注册记录。
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA='account_db' AND TABLE_NAME='bank_card' AND COLUMN_NAME='registration_id') = 0,
    'ALTER TABLE account_db.bank_card ADD COLUMN registration_id CHAR(26) NULL COMMENT ''来源注册记录 ID''',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
