-- 银行卡绑定表迁移；本文件只修改 account_db。
-- 安全约束：只存掩码与 BIN/尾号，禁止存完整卡号、证件号、手机号明文。
-- 重复绑卡（同用户 BIN+尾号已 ACTIVE）与默认卡唯一性由应用层条件校验保证，
-- 因为解绑后允许重绑，唯一索引无法用简单列组合表达。
CREATE TABLE IF NOT EXISTS account_db.bank_card (
    card_id CHAR(26) NOT NULL COMMENT '银行卡 ID，26 位字符串，沿用账户中心既有 ID 约定',
    user_id CHAR(26) NOT NULL COMMENT '所属用户 ID',
    account_id CHAR(26) NOT NULL COMMENT '关联的个人账户 ID',
    bank_code VARCHAR(32) NOT NULL COMMENT '银行编码，如 ICBC、CMB',
    bank_name VARCHAR(64) NOT NULL COMMENT '银行名称',
    card_type VARCHAR(16) NOT NULL COMMENT '卡类型：DEBIT 借记卡，CREDIT 信用卡',
    card_bin CHAR(6) NOT NULL COMMENT '卡号前 6 位 BIN，用于银行识别',
    card_last4 CHAR(4) NOT NULL COMMENT '卡号后 4 位',
    holder_masked VARCHAR(64) NOT NULL COMMENT '持卡人姓名掩码，如 张*三',
    id_card_masked VARCHAR(32) NOT NULL COMMENT '身份证号掩码，如 3301**********1234',
    phone_masked VARCHAR(16) NOT NULL COMMENT '预留手机号掩码，如 138****5678',
    is_default TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否默认卡，同一用户至多一张，应用层保证',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE 已绑定，UNBOUND 已解绑（终态）',
    unbound_at DATETIME(3) NULL COMMENT '解绑时间',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本，默认卡与解绑条件更新使用',
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (card_id),
    KEY idx_bank_card_user_status (user_id, status),
    CONSTRAINT ck_bank_card_type CHECK (card_type IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_bank_card_status CHECK (status IN ('ACTIVE', 'UNBOUND'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
