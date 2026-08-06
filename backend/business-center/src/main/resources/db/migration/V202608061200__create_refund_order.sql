-- 受控退款来源订单：对本人已成功的动态扫码交易发起受控全额虚拟退款。
-- 表结构与共享库 refund_order 保持一致：商户（原收款方）发起、支持 QR_PAY/CREDIT_PAY 原业务类型
-- 与 BALANCE/MINI_CREDIT 资金来源；active_original_key 生成列保证"活跃原交易"至多一个退款订单，
-- 失败/取消后允许对同一原交易重新发起退款。
-- 订单只管理创建与受理前推进，终态必须由统一 REFUND 交易终态发布器回填。
CREATE TABLE IF NOT EXISTS business_db.refund_order (
    refund_order_id CHAR(26) NOT NULL COMMENT '退款订单 ID',
    original_transaction_id CHAR(26) NOT NULL COMMENT '原 QR_PAY/CREDIT_PAY 交易',
    merchant_user_id CHAR(26) NOT NULL COMMENT '历史遗留字段；实际指向发起退款的原收款方普通用户',
    merchant_account_id CHAR(26) NOT NULL COMMENT '历史遗留字段；实际指向原收款用户的本体账户',
    payer_user_id CHAR(26) NOT NULL COMMENT '原付款人用户',
    payer_account_id CHAR(26) NOT NULL COMMENT '原付款人本体账户映射',
    original_business_type VARCHAR(16) NOT NULL COMMENT '取值方式：QR_PAY/CREDIT_PAY',
    funding_source VARCHAR(16) NOT NULL COMMENT '取值方式：BALANCE/MINI_CREDIT',
    amount_fen BIGINT UNSIGNED NOT NULL COMMENT '退款金额等于原交易全额，单位分',
    reason_code VARCHAR(32) NOT NULL COMMENT '收款用户退款原因',
    status VARCHAR(16) NOT NULL DEFAULT 'CREATED' COMMENT '取值方式：CREATED/PROCESSING/SUCCESS/REJECTED/CANCELLED/MANUAL_REVIEW',
    active_original_key CHAR(26) GENERATED ALWAYS AS (
        CASE WHEN status IN ('CREATED','PROCESSING','SUCCESS','MANUAL_REVIEW') THEN original_transaction_id ELSE NULL END
    ) STORED COMMENT '有效成功退款占位键',
    transaction_id CHAR(26) NULL COMMENT '对应 REFUND 资金单',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '退款状态 CAS 版本',
    created_at DATETIME(3) NOT NULL COMMENT '退款创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '退款状态更新时间',
    completed_at DATETIME(3) NULL COMMENT '退款完成时间',
    PRIMARY KEY (refund_order_id),
    UNIQUE KEY uk_refund_order_transaction (transaction_id),
    UNIQUE KEY uk_refund_order_active_original (active_original_key),
    KEY idx_refund_order_original_time (original_transaction_id, created_at),
    KEY idx_refund_order_merchant_status_time (merchant_account_id, status, created_at),
    CONSTRAINT ck_refund_order_amount CHECK (amount_fen BETWEEN 1 AND 5000000),
    CONSTRAINT ck_refund_order_business_type CHECK (original_business_type IN ('QR_PAY','CREDIT_PAY')),
    CONSTRAINT ck_refund_order_funding_source CHECK (funding_source IN ('BALANCE','MINI_CREDIT')),
    CONSTRAINT ck_refund_order_status CHECK (status IN ('CREATED','PROCESSING','SUCCESS','REJECTED','CANCELLED','MANUAL_REVIEW'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='对动态扫码收款订单对应的用户已成功扫码支付发起全额受控退款申请';
