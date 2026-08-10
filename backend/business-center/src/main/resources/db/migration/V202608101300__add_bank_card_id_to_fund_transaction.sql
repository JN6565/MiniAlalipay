-- 为 fund_transaction 增加银行卡 ID 列，支持银行卡充值/提现交易类型关联具体银行卡。
-- 仅 BANK_CARD_RECHARGE / BANK_CARD_WITHDRAW 类型使用，其余业务类型为 NULL。
ALTER TABLE business_db.fund_transaction
ADD COLUMN bank_card_id CHAR(26) NULL COMMENT '银行卡 ID，仅银行卡充值/提现交易使用'
AFTER related_transaction_id;
