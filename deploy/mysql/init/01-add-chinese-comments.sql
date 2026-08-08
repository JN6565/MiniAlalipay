-- MiniAlalipay 已建表中文注释向前修复脚本。
-- 设计依据：docs/minialalipay/minialalipay-database-design.md。
-- 仅修改表和字段的 COMMENT 元数据，不修改字段类型、默认值、约束、索引或业务数据。
-- MySQL DDL 会隐式提交；执行前应完成备份，并在低流量维护窗口执行。
-- 回滚说明：如必须回滚，只能使用相同字段定义将 COMMENT 置空；通常不应回滚说明性元数据。

SET NAMES utf8mb4;

-- 用户中心数据库：app_user。
ALTER TABLE `user_db`.`app_user`
    COMMENT = '保存用户主体、展示资料和账户级状态，不保存密码或 RBAC 角色',
    MODIFY COLUMN user_id CHAR(26) NOT NULL COMMENT '用户 ULID，跨模块引用用户的稳定标识',
    MODIFY COLUMN login_name VARCHAR(64) NOT NULL COMMENT '规范化登录名，用于登录和唯一识别',
    MODIFY COLUMN nickname VARCHAR(64) NOT NULL COMMENT '可重复的展示名称和模糊搜索条件',
    MODIFY COLUMN phone_tail CHAR(4) NULL COMMENT '手机号尾号，仅用于辅助检索和脱敏展示',
    MODIFY COLUMN id_card VARCHAR(32) NULL COMMENT '身份证号掩码，绑定身份后保存，如 3301**********1234',
    MODIFY COLUMN id_card_hash BINARY(32) NULL COMMENT '身份证号明文哈希，用于绑卡时三要素交叉比对',
    MODIFY COLUMN identity_status VARCHAR(24) NOT NULL COMMENT '演示身份状态，不代表真实 KYC',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '用户物理状态：ACTIVE/LOCKED/CLOSED',
    MODIFY COLUMN version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '用户资料和状态的 CAS 版本',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '用户注册时间',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近资料或状态变更时间';

-- 用户中心数据库：credential。
ALTER TABLE `user_db`.`credential`
    COMMENT = '保存登录密码、支付密码的强哈希以及两套独立的失败锁定状态',
    MODIFY COLUMN user_id CHAR(26) NOT NULL COMMENT '对应 app_user 的一对一凭证主体',
    MODIFY COLUMN login_password_hash VARCHAR(255) NOT NULL COMMENT 'Argon2id 或 BCrypt 登录密码哈希',
    MODIFY COLUMN payment_password_hash VARCHAR(255) NULL COMMENT '独立 6 位支付密码哈希，未设置时为空',
    MODIFY COLUMN login_fail_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '连续登录密码失败次数',
    MODIFY COLUMN pay_fail_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '连续支付密码失败次数',
    MODIFY COLUMN login_lock_until DATETIME(3) NULL COMMENT '登录锁定截止时间',
    MODIFY COLUMN pay_lock_until DATETIME(3) NULL COMMENT '支付验证锁定截止时间',
    MODIFY COLUMN pay_password_version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '支付密码世代号，用于废弃旧授权',
    MODIFY COLUMN version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '凭证状态原子更新的 CAS 版本',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近密码、失败次数或锁定状态更新时间';

-- 用户中心数据库：payment_proof。
ALTER TABLE `user_db`.`payment_proof`
    COMMENT = '保存支付密码验证成功后签发的短期一次性证明，业务库只保存其逻辑引用',
    MODIFY COLUMN proof_id CHAR(26) NOT NULL COMMENT '支付证明 ID',
    MODIFY COLUMN token_digest BINARY(32) NOT NULL COMMENT '原始证明令牌的 HMAC-SHA-256 摘要',
    MODIFY COLUMN user_id CHAR(26) NOT NULL COMMENT '证明所属用户',
    MODIFY COLUMN purpose VARCHAR(32) NOT NULL COMMENT '允许使用证明的确认用途',
    MODIFY COLUMN pay_password_version BIGINT UNSIGNED NOT NULL COMMENT '签发时的支付密码版本',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '取值或格式：ACTIVE/CONSUMED/REVOKED/EXPIRED',
    MODIFY COLUMN expires_at DATETIME(3) NOT NULL COMMENT '证明有效期截止时间',
    MODIFY COLUMN consumed_at DATETIME(3) NULL COMMENT '一次性消费完成时间',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '证明签发时间';

-- 用户中心数据库：contact。
ALTER TABLE `user_db`.`contact`
    COMMENT = '保存由成功转账自动形成的单向常用收款人投影，不表示好友或通讯录关系',
    MODIFY COLUMN owner_user_id CHAR(26) NOT NULL COMMENT '联系人列表所有者',
    MODIFY COLUMN payee_user_id CHAR(26) NOT NULL COMMENT '成功收款的用户',
    MODIFY COLUMN alias VARCHAR(64) NULL COMMENT '所有者设置的联系人备注',
    MODIFY COLUMN success_count BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '与该收款人的成功转账累计次数',
    MODIFY COLUMN last_success_at DATETIME(3) NOT NULL COMMENT '最近一次确定成功的转账时间',
    MODIFY COLUMN pinned BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否由所有者置顶',
    MODIFY COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否从所有者默认列表隐藏',
    MODIFY COLUMN version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '事件累计和用户修改共用的 CAS 版本',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '首次成功转账形成联系人时间',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近统计或用户设置更新时间';

-- 用户中心数据库：role_assignment。
ALTER TABLE `user_db`.`role_assignment`
    COMMENT = '保存 RBAC 角色分配，是系统角色授权的唯一事实来源',
    MODIFY COLUMN user_id CHAR(26) NOT NULL COMMENT '被授权用户',
    MODIFY COLUMN role_code VARCHAR(32) NOT NULL COMMENT '物理约束保留 USER/MERCHANT/OPERATOR/ADMIN/OBSERVER；MERCHANT 为历史保留值，MVP 禁止分配',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '角色授予时间';

-- 用户中心数据库：idempotency_record。
ALTER TABLE `user_db`.`idempotency_record`
    COMMENT = '每个拥有对外创建接口的 Schema 保存请求幂等受理、资源绑定和响应快照',
    MODIFY COLUMN record_id CHAR(26) NOT NULL COMMENT '幂等记录 ID',
    MODIFY COLUMN principal_key VARCHAR(128) NOT NULL COMMENT '登录主体或受信任调用方',
    MODIFY COLUMN api_scope VARCHAR(64) NOT NULL COMMENT '接口或业务动作范围',
    MODIFY COLUMN idempotency_key VARCHAR(64) NOT NULL COMMENT '调用方幂等键',
    MODIFY COLUMN request_digest BINARY(32) NOT NULL COMMENT '规范化请求摘要，检测同键异参',
    MODIFY COLUMN resource_type VARCHAR(32) NULL COMMENT '已创建资源类型',
    MODIFY COLUMN resource_id CHAR(26) NULL COMMENT '已创建资源 ID',
    MODIFY COLUMN response_json JSON NULL COMMENT '可安全重放的脱敏响应快照',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING' COMMENT '取值或格式：PROCESSING/COMPLETED/FAILED',
    MODIFY COLUMN expires_at DATETIME(3) NOT NULL COMMENT '幂等记录保留截止时间',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '首次受理时间',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近处理时间';

-- 用户中心数据库：inbox_event。
ALTER TABLE `user_db`.`inbox_event`
    COMMENT = '每个事件消费者在自己的 Schema 保存消费幂等和接管状态',
    MODIFY COLUMN consumer_name VARCHAR(64) NOT NULL COMMENT '消费者逻辑名称',
    MODIFY COLUMN event_id CHAR(26) NOT NULL COMMENT '被消费事件 ID',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING' COMMENT '取值或格式：PROCESSING/DONE/FAILED',
    MODIFY COLUMN received_at DATETIME(3) NOT NULL COMMENT '首次接收时间',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近接管或完成时间';

-- 用户中心数据库：audit_log。
ALTER TABLE `user_db`.`audit_log`
    COMMENT = '各所有者 Schema 独立保存不可变、脱敏的安全和业务操作证据',
    MODIFY COLUMN audit_id BIGINT UNSIGNED NOT NULL COMMENT '审计流水 ID',
    MODIFY COLUMN actor_type VARCHAR(16) NOT NULL COMMENT '取值或格式：USER/SYSTEM/OPERATOR',
    MODIFY COLUMN actor_id VARCHAR(128) NOT NULL COMMENT '操作者 ID 或系统任务标识',
    MODIFY COLUMN action VARCHAR(64) NOT NULL COMMENT '设置密码、确认交易、人工处置等动作',
    MODIFY COLUMN target_type VARCHAR(32) NOT NULL COMMENT '操作目标类型',
    MODIFY COLUMN target_id VARCHAR(128) NOT NULL COMMENT '操作目标 ID',
    MODIFY COLUMN result_code VARCHAR(32) NOT NULL COMMENT '标准结果码',
    MODIFY COLUMN trace_id CHAR(32) NOT NULL COMMENT '全链路追踪 ID',
    MODIFY COLUMN detail_json JSON NULL COMMENT '脱敏证据和变更摘要',
    MODIFY COLUMN occurred_at DATETIME(3) NOT NULL COMMENT '操作发生时间';

-- 用户中心数据库：outbox_event。
ALTER TABLE `user_db`.`outbox_event`
    COMMENT = '每个产生事实的 Schema 在本地事务中记录待可靠发布事件',
    MODIFY COLUMN event_id CHAR(26) NOT NULL COMMENT '全局事件 ID',
    MODIFY COLUMN aggregate_type VARCHAR(32) NOT NULL COMMENT '事件所属聚合类型',
    MODIFY COLUMN aggregate_id CHAR(26) NOT NULL COMMENT '聚合 ID',
    MODIFY COLUMN aggregate_version BIGINT UNSIGNED NOT NULL COMMENT '产生事件的聚合版本',
    MODIFY COLUMN event_type VARCHAR(64) NOT NULL COMMENT '事件名称',
    MODIFY COLUMN event_version SMALLINT UNSIGNED NOT NULL COMMENT '事件 Schema 版本',
    MODIFY COLUMN business_type VARCHAR(16) NULL COMMENT '资金业务类型',
    MODIFY COLUMN source_type VARCHAR(32) NULL COMMENT '来源订单类型',
    MODIFY COLUMN source_order_id CHAR(26) NULL COMMENT '来源订单 ID',
    MODIFY COLUMN funding_source VARCHAR(16) NULL COMMENT '取值或格式：BALANCE/MINI_CREDIT/SYSTEM_ISSUANCE',
    MODIFY COLUMN transaction_id CHAR(26) NULL COMMENT '关联资金交易',
    MODIFY COLUMN producer VARCHAR(32) NOT NULL COMMENT '发布模块',
    MODIFY COLUMN account_id CHAR(26) NULL COMMENT '受限个人账户路由键',
    MODIFY COLUMN merchant_account_id CHAR(26) NULL COMMENT '历史物理字段名，实际为受限扫码收款账户路由键',
    MODIFY COLUMN user_id_hash BINARY(32) NULL COMMENT '脱敏用户维度',
    MODIFY COLUMN trace_id CHAR(32) NOT NULL COMMENT '全链路追踪 ID',
    MODIFY COLUMN occurred_at DATETIME(3) NOT NULL COMMENT '业务事实发生时间',
    MODIFY COLUMN payload JSON NOT NULL COMMENT '版本化事件载荷',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '取值或格式：PENDING/PUBLISHED/DEAD',
    MODIFY COLUMN retry_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '发布重试次数',
    MODIFY COLUMN next_retry_at DATETIME(3) NULL COMMENT '下次发布时间',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT 'Outbox 行创建时间',
    MODIFY COLUMN published_at DATETIME(3) NULL COMMENT '首次成功发布时间';

-- 账户中心账户与额度数据库：account。
ALTER TABLE `account_db`.`account`
    COMMENT = '保存普通用户虚拟账户的身份、币种与可用状态，不直接保存余额',
    MODIFY COLUMN account_id CHAR(26) NOT NULL COMMENT '虚拟账户 ID',
    MODIFY COLUMN user_id CHAR(26) NOT NULL COMMENT '账户所有者，跨 user_db 逻辑引用',
    MODIFY COLUMN registration_id CHAR(26) NOT NULL COMMENT '用户注册事件对应的开户幂等编号',
    MODIFY COLUMN account_type VARCHAR(16) NOT NULL COMMENT '物理结构兼容 PERSONAL/MERCHANT；MVP 只允许新建 PERSONAL，MERCHANT 为历史保留值',
    MODIFY COLUMN currency CHAR(3) NOT NULL DEFAULT 'CNY' COMMENT '账户币种，MVP 固定人民币',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '取值或格式：ACTIVE/FROZEN/CLOSED',
    MODIFY COLUMN version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '账户状态 CAS 版本',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '开户时间',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近状态更新时间';

-- 账户中心账户与额度数据库：account_balance。
ALTER TABLE `account_db`.`account_balance`
    COMMENT = '保存每个账户唯一一行的实时可用余额和冻结余额，是余额事实表',
    MODIFY COLUMN account_id CHAR(26) NOT NULL COMMENT '对应 account 的余额主体',
    MODIFY COLUMN available_fen BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当前可直接使用的虚拟余额，单位：分',
    MODIFY COLUMN frozen_fen BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'TCC Try 已冻结、尚未确认或释放的金额，单位：分',
    MODIFY COLUMN version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '余额原子修改 CAS 版本',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近余额变化时间';

-- 账户中心账户与额度数据库：freeze_record。
ALTER TABLE `account_db`.`freeze_record`
    COMMENT = '保存余额 TCC 的逐交易冻结事实，为 Confirm、Cancel 和恢复任务提供幂等依据',
    MODIFY COLUMN freeze_id CHAR(26) NOT NULL COMMENT '冻结记录 ID',
    MODIFY COLUMN transaction_id CHAR(26) NOT NULL COMMENT '对应统一资金交易',
    MODIFY COLUMN account_id CHAR(26) NOT NULL COMMENT '被冻结或待入账账户',
    MODIFY COLUMN purpose VARCHAR(24) NOT NULL COMMENT '付款、退款、还款等冻结用途',
    MODIFY COLUMN amount_fen BIGINT UNSIGNED NOT NULL COMMENT '冻结金额，必须大于 0，单位：分',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'FROZEN' COMMENT '取值或格式：FROZEN/CONFIRMED/RELEASED',
    MODIFY COLUMN branch_xid VARCHAR(128) NOT NULL COMMENT '所属 TCC 全局事务 XID',
    MODIFY COLUMN version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态迁移 CAS 版本',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '冻结创建时间',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近状态更新时间';

-- 账户中心账户与额度数据库：credit_account。
ALTER TABLE `account_db`.`credit_account`
    COMMENT = '保存用户 Mini 花呗额度汇总和可用状态，不是余额账户',
    MODIFY COLUMN credit_account_id CHAR(26) NOT NULL COMMENT '信用账户 ID',
    MODIFY COLUMN user_id CHAR(26) NOT NULL COMMENT '信用账户所有者，一个用户最多一行',
    MODIFY COLUMN total_limit_fen BIGINT UNSIGNED NOT NULL DEFAULT 500000 COMMENT '固定 5000 元虚拟授信总额，单位：分',
    MODIFY COLUMN used_fen BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已确认但尚未还清或冲销的额度，单位：分',
    MODIFY COLUMN frozen_fen BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '信用支付 Try 阶段冻结额度，单位：分',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '取值或格式：ACTIVE/SUSPENDED/CLOSED',
    MODIFY COLUMN suspend_reason VARCHAR(32) NULL COMMENT '逾期或人工停用原因',
    MODIFY COLUMN version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '额度和状态 CAS 版本',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '信用账户创建时间',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近额度或状态更新时间';

-- 账户中心账户与额度数据库：credit_freeze。
ALTER TABLE `account_db`.`credit_freeze`
    COMMENT = '保存信用支付 TCC 的逐笔额度冻结事实',
    MODIFY COLUMN credit_freeze_id CHAR(26) NOT NULL COMMENT '额度冻结记录 ID',
    MODIFY COLUMN transaction_id CHAR(26) NOT NULL COMMENT '对应 CREDIT_PAY 交易',
    MODIFY COLUMN credit_account_id CHAR(26) NOT NULL COMMENT '被冻结的信用账户',
    MODIFY COLUMN amount_fen BIGINT UNSIGNED NOT NULL COMMENT '冻结额度，必须大于 0，单位：分',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'FROZEN' COMMENT '取值或格式：FROZEN/CONFIRMED/RELEASED',
    MODIFY COLUMN branch_xid VARCHAR(128) NOT NULL COMMENT '所属 TCC XID',
    MODIFY COLUMN version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态迁移 CAS 版本',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '冻结创建时间',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近状态更新时间';

-- 账户中心账户与额度数据库：tcc_branch。
ALTER TABLE `account_db`.`tcc_branch`
    COMMENT = '每个 TCC 参与者本地保存分支状态、幂等屏障和恢复游标；不同 Schema 不共享表',
    MODIFY COLUMN branch_id CHAR(26) NOT NULL COMMENT '本地分支记录 ID',
    MODIFY COLUMN xid VARCHAR(128) NOT NULL COMMENT '全局 TCC XID',
    MODIFY COLUMN transaction_id CHAR(26) NOT NULL COMMENT '对应资金交易',
    MODIFY COLUMN branch_type VARCHAR(32) NOT NULL COMMENT '余额、额度、应收或账本分支类型',
    MODIFY COLUMN resource_id CHAR(26) NOT NULL COMMENT '分支锁定的本地聚合 ID',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'INIT' COMMENT '取值或格式：INIT/TRIED/CONFIRMED/CANCELLED/MANUAL_REVIEW',
    MODIFY COLUMN rollback_type VARCHAR(16) NULL COMMENT 'NORMAL 或空回滚 EMPTY',
    MODIFY COLUMN barrier_version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '防悬挂屏障版本',
    MODIFY COLUMN retry_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '恢复任务重试次数',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近分支处理时间';

-- 账户中心账户与额度数据库：idempotency_record。
ALTER TABLE `account_db`.`idempotency_record`
    COMMENT = '每个拥有对外创建接口的 Schema 保存请求幂等受理、资源绑定和响应快照',
    MODIFY COLUMN record_id CHAR(26) NOT NULL COMMENT '幂等记录 ID',
    MODIFY COLUMN principal_key VARCHAR(128) NOT NULL COMMENT '登录主体或受信任调用方',
    MODIFY COLUMN api_scope VARCHAR(64) NOT NULL COMMENT '接口或业务动作范围',
    MODIFY COLUMN idempotency_key VARCHAR(64) NOT NULL COMMENT '调用方幂等键',
    MODIFY COLUMN request_digest BINARY(32) NOT NULL COMMENT '规范化请求摘要，检测同键异参',
    MODIFY COLUMN resource_type VARCHAR(32) NULL COMMENT '已创建资源类型',
    MODIFY COLUMN resource_id CHAR(26) NULL COMMENT '已创建资源 ID',
    MODIFY COLUMN response_json JSON NULL COMMENT '可安全重放的脱敏响应快照',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING' COMMENT '取值或格式：PROCESSING/COMPLETED/FAILED',
    MODIFY COLUMN expires_at DATETIME(3) NOT NULL COMMENT '幂等记录保留截止时间',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '首次受理时间',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近处理时间';

-- 账户中心账户与额度数据库：inbox_event。
ALTER TABLE `account_db`.`inbox_event`
    COMMENT = '每个事件消费者在自己的 Schema 保存消费幂等和接管状态',
    MODIFY COLUMN consumer_name VARCHAR(64) NOT NULL COMMENT '消费者逻辑名称',
    MODIFY COLUMN event_id CHAR(26) NOT NULL COMMENT '被消费事件 ID',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING' COMMENT '取值或格式：PROCESSING/DONE/FAILED',
    MODIFY COLUMN received_at DATETIME(3) NOT NULL COMMENT '首次接收时间',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近接管或完成时间';

-- 账户中心账户与额度数据库：audit_log。
ALTER TABLE `account_db`.`audit_log`
    COMMENT = '各所有者 Schema 独立保存不可变、脱敏的安全和业务操作证据',
    MODIFY COLUMN audit_id BIGINT UNSIGNED NOT NULL COMMENT '审计流水 ID',
    MODIFY COLUMN actor_type VARCHAR(16) NOT NULL COMMENT '取值或格式：USER/SYSTEM/OPERATOR',
    MODIFY COLUMN actor_id VARCHAR(128) NOT NULL COMMENT '操作者 ID 或系统任务标识',
    MODIFY COLUMN action VARCHAR(64) NOT NULL COMMENT '设置密码、确认交易、人工处置等动作',
    MODIFY COLUMN target_type VARCHAR(32) NOT NULL COMMENT '操作目标类型',
    MODIFY COLUMN target_id VARCHAR(128) NOT NULL COMMENT '操作目标 ID',
    MODIFY COLUMN result_code VARCHAR(32) NOT NULL COMMENT '标准结果码',
    MODIFY COLUMN trace_id CHAR(32) NOT NULL COMMENT '全链路追踪 ID',
    MODIFY COLUMN detail_json JSON NULL COMMENT '脱敏证据和变更摘要',
    MODIFY COLUMN occurred_at DATETIME(3) NOT NULL COMMENT '操作发生时间';

-- 账户中心账户与额度数据库：outbox_event。
ALTER TABLE `account_db`.`outbox_event`
    COMMENT = '每个产生事实的 Schema 在本地事务中记录待可靠发布事件',
    MODIFY COLUMN event_id CHAR(26) NOT NULL COMMENT '全局事件 ID',
    MODIFY COLUMN aggregate_type VARCHAR(32) NOT NULL COMMENT '事件所属聚合类型',
    MODIFY COLUMN aggregate_id CHAR(26) NOT NULL COMMENT '聚合 ID',
    MODIFY COLUMN aggregate_version BIGINT UNSIGNED NOT NULL COMMENT '产生事件的聚合版本',
    MODIFY COLUMN event_type VARCHAR(64) NOT NULL COMMENT '事件名称',
    MODIFY COLUMN event_version SMALLINT UNSIGNED NOT NULL COMMENT '事件 Schema 版本',
    MODIFY COLUMN business_type VARCHAR(16) NULL COMMENT '资金业务类型',
    MODIFY COLUMN source_type VARCHAR(32) NULL COMMENT '来源订单类型',
    MODIFY COLUMN source_order_id CHAR(26) NULL COMMENT '来源订单 ID',
    MODIFY COLUMN funding_source VARCHAR(16) NULL COMMENT '取值或格式：BALANCE/MINI_CREDIT/SYSTEM_ISSUANCE',
    MODIFY COLUMN transaction_id CHAR(26) NULL COMMENT '关联资金交易',
    MODIFY COLUMN producer VARCHAR(32) NOT NULL COMMENT '发布模块',
    MODIFY COLUMN account_id CHAR(26) NULL COMMENT '受限个人账户路由键',
    MODIFY COLUMN merchant_account_id CHAR(26) NULL COMMENT '历史物理字段名，实际为受限扫码收款账户路由键',
    MODIFY COLUMN user_id_hash BINARY(32) NULL COMMENT '脱敏用户维度',
    MODIFY COLUMN trace_id CHAR(32) NOT NULL COMMENT '全链路追踪 ID',
    MODIFY COLUMN occurred_at DATETIME(3) NOT NULL COMMENT '业务事实发生时间',
    MODIFY COLUMN payload JSON NOT NULL COMMENT '版本化事件载荷',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '取值或格式：PENDING/PUBLISHED/DEAD',
    MODIFY COLUMN retry_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '发布重试次数',
    MODIFY COLUMN next_retry_at DATETIME(3) NULL COMMENT '下次发布时间',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT 'Outbox 行创建时间',
    MODIFY COLUMN published_at DATETIME(3) NULL COMMENT '首次成功发布时间';

-- 账户中心账户与额度数据库：bank_card。
ALTER TABLE `account_db`.`bank_card`
    COMMENT = '银行卡绑定事实，只存 BIN、尾号与掩码值，禁止存完整卡号、证件号、手机号明文',
    MODIFY COLUMN card_id CHAR(26) NOT NULL COMMENT '银行卡 ID',
    MODIFY COLUMN user_id CHAR(26) NOT NULL COMMENT '所属用户 ID',
    MODIFY COLUMN account_id CHAR(26) NOT NULL COMMENT '关联的个人账户 ID',
    MODIFY COLUMN bank_code VARCHAR(32) NOT NULL COMMENT '银行编码，如 ICBC、CMB',
    MODIFY COLUMN bank_name VARCHAR(64) NOT NULL COMMENT '银行名称',
    MODIFY COLUMN card_type VARCHAR(16) NOT NULL COMMENT 'DEBIT 借记卡，CREDIT 信用卡',
    MODIFY COLUMN card_bin CHAR(6) NOT NULL COMMENT '卡号前 6 位 BIN',
    MODIFY COLUMN card_last4 CHAR(4) NOT NULL COMMENT '卡号后 4 位',
    MODIFY COLUMN holder_masked VARCHAR(64) NOT NULL COMMENT '持卡人姓名掩码',
    MODIFY COLUMN id_card_masked VARCHAR(32) NOT NULL COMMENT '身份证号掩码',
    MODIFY COLUMN phone_masked VARCHAR(16) NOT NULL COMMENT '预留手机号掩码',
    MODIFY COLUMN is_default TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否默认卡',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/UNBOUND',
    MODIFY COLUMN unbound_at DATETIME(3) NULL COMMENT '解绑时间',
    MODIFY COLUMN registration_id CHAR(26) NULL COMMENT '来源注册记录 ID',
    MODIFY COLUMN version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '绑定时间',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近变更时间';

-- 账户中心账户与额度数据库：bank_card_registration。
ALTER TABLE `account_db`.`bank_card_registration`
    COMMENT = '银行卡注册表，记录用户注册的银行卡（尚未绑定到账户），注册时自动生成卡号并保存三要素哈希',
    MODIFY COLUMN registration_id CHAR(26) NOT NULL COMMENT '注册记录 ID',
    MODIFY COLUMN user_id CHAR(26) NOT NULL COMMENT '注册操作人',
    MODIFY COLUMN bank_code VARCHAR(32) NOT NULL COMMENT '银行编码',
    MODIFY COLUMN bank_name VARCHAR(64) NOT NULL COMMENT '银行名称',
    MODIFY COLUMN card_type VARCHAR(16) NOT NULL COMMENT 'DEBIT/CREDIT',
    MODIFY COLUMN card_number VARCHAR(19) NOT NULL COMMENT '自动生成的完整卡号（模拟环境允许）',
    MODIFY COLUMN card_bin CHAR(6) NOT NULL COMMENT 'BIN 前 6 位',
    MODIFY COLUMN card_last4 CHAR(4) NOT NULL COMMENT '尾号后 4 位',
    MODIFY COLUMN holder_name VARCHAR(32) NOT NULL COMMENT '持卡人姓名明文（绑定时用于比对）',
    MODIFY COLUMN id_card_hash BINARY(32) NOT NULL COMMENT '身份证号哈希（绑定时用于比对）',
    MODIFY COLUMN phone_hash BINARY(32) NOT NULL COMMENT '手机号哈希（绑定时用于比对）',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'REGISTERED' COMMENT 'REGISTERED/BOUND',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '注册时间';

-- 账户中心账本与信用数据库：ledger_account。
ALTER TABLE `ledger_db`.`ledger_account`
    COMMENT = '保存复式账本科目身份、归属、会计分类和正常余额方向',
    MODIFY COLUMN ledger_account_id CHAR(26) NOT NULL COMMENT '账本科目 ID',
    MODIFY COLUMN owner_type VARCHAR(24) NOT NULL COMMENT '物理约束保留 SYSTEM/USER/MERCHANT/CREDIT_ACCOUNT；MVP 不新增 MERCHANT 科目主体',
    MODIFY COLUMN owner_id VARCHAR(64) NOT NULL COMMENT '系统常量或业务聚合 ID',
    MODIFY COLUMN account_code VARCHAR(64) NOT NULL COMMENT '稳定、可审计的唯一科目编码',
    MODIFY COLUMN account_type VARCHAR(32) NOT NULL COMMENT '用户余额负债、信用应收资产、发行权益等业务科目类型',
    MODIFY COLUMN account_class VARCHAR(16) NOT NULL COMMENT '取值或格式：ASSET/LIABILITY/EQUITY',
    MODIFY COLUMN normal_direction VARCHAR(8) NOT NULL COMMENT '正常余额方向 DEBIT/CREDIT',
    MODIFY COLUMN currency CHAR(3) NOT NULL DEFAULT 'CNY' COMMENT '科目币种',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '取值或格式：ACTIVE/CLOSED',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '科目创建时间',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近状态更新时间';

-- 账户中心账本与信用数据库：ledger_voucher。
ALTER TABLE `ledger_db`.`ledger_voucher`
    COMMENT = '保存一笔交易的一组平衡分录及其过账或冲正状态',
    MODIFY COLUMN voucher_id CHAR(26) NOT NULL COMMENT '凭证 ID',
    MODIFY COLUMN transaction_id CHAR(26) NOT NULL COMMENT '对应统一资金交易',
    MODIFY COLUMN voucher_type VARCHAR(24) NOT NULL COMMENT '原始、充值、退款或系统冲正凭证类型',
    MODIFY COLUMN reversal_no SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '同交易同类型的冲正序号',
    MODIFY COLUMN original_voucher_id CHAR(26) NULL COMMENT '冲正凭证引用的原凭证',
    MODIFY COLUMN reversal_reason VARCHAR(32) NULL COMMENT '取值或格式：BUSINESS_REFUND/RECONCILIATION/SYSTEM_CORRECTION',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PREPARED' COMMENT '取值或格式：PREPARED/POSTED/CANCELLED/REVERSED',
    MODIFY COLUMN total_debit_fen BIGINT UNSIGNED NOT NULL COMMENT '凭证预期借方合计，单位：分',
    MODIFY COLUMN total_credit_fen BIGINT UNSIGNED NOT NULL COMMENT '凭证预期贷方合计，单位：分',
    MODIFY COLUMN posted_at DATETIME(3) NULL COMMENT '完成过账时间',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '凭证创建时间';

-- 账户中心账本与信用数据库：ledger_entry。
ALTER TABLE `ledger_db`.`ledger_entry`
    COMMENT = '保存凭证内不可变的逐科目借贷分录',
    MODIFY COLUMN entry_id BIGINT UNSIGNED NOT NULL COMMENT '高频分录 ID',
    MODIFY COLUMN voucher_id CHAR(26) NOT NULL COMMENT '所属凭证',
    MODIFY COLUMN transaction_id CHAR(26) NOT NULL COMMENT '冗余交易 ID，支持链路查询',
    MODIFY COLUMN ledger_account_id CHAR(26) NOT NULL COMMENT '借贷科目',
    MODIFY COLUMN direction VARCHAR(8) NOT NULL COMMENT '取值或格式：DEBIT/CREDIT',
    MODIFY COLUMN amount_fen BIGINT UNSIGNED NOT NULL COMMENT '分录金额，必须大于 0，单位：分',
    MODIFY COLUMN sequence_no SMALLINT UNSIGNED NOT NULL COMMENT '凭证内稳定顺序',
    MODIFY COLUMN memo VARCHAR(255) NULL COMMENT '脱敏分录摘要',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '分录创建时间';

-- 账户中心账本与信用数据库：credit_receivable。
ALTER TABLE `ledger_db`.`credit_receivable`
    COMMENT = '每个信用账户一行，保存未出账、已出账和逾期应收汇总',
    MODIFY COLUMN credit_account_id CHAR(26) NOT NULL COMMENT '对应 account_db.credit_account 的逻辑引用',
    MODIFY COLUMN unbilled_fen BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '尚未进入月账单的未还消费金额，单位：分',
    MODIFY COLUMN billed_fen BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已进入账单的未还金额，单位：分',
    MODIFY COLUMN overdue_fen BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已出账金额中的逾期部分，单位：分',
    MODIFY COLUMN version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '应收汇总 CAS 版本',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近应收变化时间';

-- 账户中心账本与信用数据库：credit_purchase。
ALTER TABLE `ledger_db`.`credit_purchase`
    COMMENT = '保存每笔成功信用消费事实及其还款、退款和出账进度',
    MODIFY COLUMN purchase_id CHAR(26) NOT NULL COMMENT '信用消费事实 ID',
    MODIFY COLUMN credit_transaction_id CHAR(26) NOT NULL COMMENT '原 CREDIT_PAY 资金交易 ID',
    MODIFY COLUMN credit_account_id CHAR(26) NOT NULL COMMENT '消费所属信用账户',
    MODIFY COLUMN qr_order_id CHAR(26) NOT NULL COMMENT '原动态扫码收款订单',
    MODIFY COLUMN merchant_account_id CHAR(26) NOT NULL COMMENT '历史物理字段名，实际指收款普通用户的本人账户',
    MODIFY COLUMN amount_fen BIGINT UNSIGNED NOT NULL COMMENT '原始信用消费金额，单位：分',
    MODIFY COLUMN repaid_fen BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已由还款分配覆盖的金额，单位：分',
    MODIFY COLUMN refunded_fen BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已由经营退款冲销的金额，单位：分',
    MODIFY COLUMN outstanding_fen BIGINT UNSIGNED GENERATED ALWAYS AS (
amount_fen - repaid_fen - refunded_fen
) STORED COMMENT 'amount_fen-repaid_fen-refunded_fen，单位：分',
    MODIFY COLUMN refund_transaction_id CHAR(26) NULL COMMENT '成功信用退款交易 ID',
    MODIFY COLUMN billing_status VARCHAR(16) NOT NULL DEFAULT 'UNBILLED' COMMENT '取值或格式：UNBILLED/BILLED/REPAID/REVERSED',
    MODIFY COLUMN version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '出账、还款和退款 CAS 版本',
    MODIFY COLUMN occurred_at DATETIME(3) NOT NULL COMMENT '信用消费成功时间',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近状态更新时间';

-- 账户中心账本与信用数据库：credit_bill。
ALTER TABLE `ledger_db`.`credit_bill`
    COMMENT = '保存按月生成的信用账单汇总和结清状态',
    MODIFY COLUMN bill_id CHAR(26) NOT NULL COMMENT '账单 ID',
    MODIFY COLUMN credit_account_id CHAR(26) NOT NULL COMMENT '账单所属信用账户',
    MODIFY COLUMN `period` CHAR(7) NOT NULL COMMENT '账期，格式 YYYY-MM',
    MODIFY COLUMN statement_date DATE NOT NULL COMMENT '账单生成业务日期',
    MODIFY COLUMN due_at DATETIME(3) NOT NULL COMMENT '还款到期时间',
    MODIFY COLUMN total_fen BIGINT UNSIGNED NOT NULL COMMENT '账单生成时原始金额，后续不回写减少，单位：分',
    MODIFY COLUMN paid_fen BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已由余额还款结清金额，单位：分',
    MODIFY COLUMN reversed_fen BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已由信用退款冲销金额，单位：分',
    MODIFY COLUMN outstanding_fen BIGINT UNSIGNED NOT NULL COMMENT '当前剩余应还金额，单位：分',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT '取值或格式：OPEN/PARTIALLY_PAID/PAID/OVERDUE',
    MODIFY COLUMN version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '账单状态和金额 CAS 版本',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '账单创建时间',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近还款、退款或状态更新时间';

-- 账户中心账本与信用数据库：credit_bill_item。
ALTER TABLE `ledger_db`.`credit_bill_item`
    COMMENT = '把逐笔信用消费归入唯一账单，并保存该明细的已还和冲销金额',
    MODIFY COLUMN bill_id CHAR(26) NOT NULL COMMENT '所属账单',
    MODIFY COLUMN purchase_id CHAR(26) NOT NULL COMMENT '所属信用消费',
    MODIFY COLUMN amount_fen BIGINT UNSIGNED NOT NULL COMMENT '出账时消费的未还金额，单位：分',
    MODIFY COLUMN allocated_paid_fen BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已分配到该明细的还款金额，单位：分',
    MODIFY COLUMN reversed_fen BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已由退款冲销金额，单位：分',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '取值或格式：ACTIVE/REPAID/REVERSED',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '明细出账时间',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近还款或冲销时间';

-- 账户中心账本与信用数据库：credit_repayment。
ALTER TABLE `ledger_db`.`credit_repayment`
    COMMENT = '保存一次信用还款的确定性业务事实和执行状态',
    MODIFY COLUMN repayment_id CHAR(26) NOT NULL COMMENT '还款事实 ID',
    MODIFY COLUMN repayment_draft_id CHAR(26) NOT NULL COMMENT '业务库还款草稿逻辑引用',
    MODIFY COLUMN transaction_id CHAR(26) NOT NULL COMMENT '对应 CREDIT_REPAY 交易',
    MODIFY COLUMN credit_account_id CHAR(26) NOT NULL COMMENT '被偿还信用账户',
    MODIFY COLUMN amount_fen BIGINT UNSIGNED NOT NULL COMMENT '本次还款总额，单位：分',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING' COMMENT '取值或格式：PROCESSING/SUCCESS/CANCELLED/MANUAL_REVIEW',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '还款受理时间',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近状态更新时间';

-- 账户中心账本与信用数据库：credit_repayment_allocation。
ALTER TABLE `ledger_db`.`credit_repayment_allocation`
    COMMENT = '保存还款按逾期账单、普通账单和未出账消费的一级分配顺序',
    MODIFY COLUMN repayment_id CHAR(26) NOT NULL COMMENT '所属还款',
    MODIFY COLUMN sequence_no SMALLINT UNSIGNED NOT NULL COMMENT '分配执行顺序',
    MODIFY COLUMN target_type VARCHAR(24) NOT NULL COMMENT '取值或格式：OVERDUE_BILL/BILL/UNBILLED_PURCHASE',
    MODIFY COLUMN target_id CHAR(26) NOT NULL COMMENT '账单 ID 或未出账消费 ID',
    MODIFY COLUMN amount_fen BIGINT UNSIGNED NOT NULL COMMENT '分配到该一级目标的金额，单位：分',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '分配快照创建时间';

-- 账户中心账本与信用数据库：credit_repayment_allocation_detail。
ALTER TABLE `ledger_db`.`credit_repayment_allocation_detail`
    COMMENT = '把账单级还款分配展开到具体消费，保证 Confirm 使用不可变快照',
    MODIFY COLUMN repayment_id CHAR(26) NOT NULL COMMENT '所属还款',
    MODIFY COLUMN sequence_no SMALLINT UNSIGNED NOT NULL COMMENT '对应一级分配序号',
    MODIFY COLUMN detail_no SMALLINT UNSIGNED NOT NULL COMMENT '一级分配内明细顺序',
    MODIFY COLUMN purchase_id CHAR(26) NOT NULL COMMENT '实际被偿还消费',
    MODIFY COLUMN bill_id CHAR(26) NULL COMMENT '已出账消费所属账单，未出账时为空',
    MODIFY COLUMN amount_fen BIGINT UNSIGNED NOT NULL COMMENT '分配到该消费的金额，单位：分',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '明细快照创建时间';

-- 账户中心账本与信用数据库：credit_job_run。
ALTER TABLE `ledger_db`.`credit_job_run`
    COMMENT = '保存出账与到期检查任务的幂等运行、游标、恢复和审计信息',
    MODIFY COLUMN run_id CHAR(26) NOT NULL COMMENT '任务运行 ID',
    MODIFY COLUMN job_type VARCHAR(16) NOT NULL COMMENT '取值或格式：STATEMENT/DUE_CHECK',
    MODIFY COLUMN business_date DATE NOT NULL COMMENT 'Asia/Shanghai 业务日期',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '取值或格式：PENDING/RUNNING/SUCCESS/FAILED/MANUAL_REVIEW',
    MODIFY COLUMN cursor_credit_account_id CHAR(26) NULL COMMENT '分批续跑的最后信用账户游标',
    MODIFY COLUMN trigger_type VARCHAR(16) NOT NULL COMMENT '取值或格式：SCHEDULED/MANUAL',
    MODIFY COLUMN triggered_by_user_id CHAR(26) NULL COMMENT '手动触发管理员，定时任务为空',
    MODIFY COLUMN request_digest BINARY(32) NOT NULL COMMENT '触发参数摘要，识别同键异参',
    MODIFY COLUMN retry_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '恢复重试次数',
    MODIFY COLUMN error_code VARCHAR(32) NULL COMMENT '最近一次失败原因',
    MODIFY COLUMN version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态和游标 CAS 版本',
    MODIFY COLUMN started_at DATETIME(3) NULL COMMENT '首次开始执行时间',
    MODIFY COLUMN completed_at DATETIME(3) NULL COMMENT '成功或人工终结时间',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '运行记录创建时间',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近任务心跳或状态更新时间';

-- 账户中心账本与信用数据库：tcc_branch。
ALTER TABLE `ledger_db`.`tcc_branch`
    COMMENT = '每个 TCC 参与者本地保存分支状态、幂等屏障和恢复游标；不同 Schema 不共享表',
    MODIFY COLUMN branch_id CHAR(26) NOT NULL COMMENT '本地分支记录 ID',
    MODIFY COLUMN xid VARCHAR(128) NOT NULL COMMENT '全局 TCC XID',
    MODIFY COLUMN transaction_id CHAR(26) NOT NULL COMMENT '对应资金交易',
    MODIFY COLUMN branch_type VARCHAR(32) NOT NULL COMMENT '余额、额度、应收或账本分支类型',
    MODIFY COLUMN resource_id CHAR(26) NOT NULL COMMENT '分支锁定的本地聚合 ID',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'INIT' COMMENT '取值或格式：INIT/TRIED/CONFIRMED/CANCELLED/MANUAL_REVIEW',
    MODIFY COLUMN rollback_type VARCHAR(16) NULL COMMENT 'NORMAL 或空回滚 EMPTY',
    MODIFY COLUMN barrier_version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '防悬挂屏障版本',
    MODIFY COLUMN retry_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '恢复任务重试次数',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近分支处理时间';

-- 账户中心账本与信用数据库：reconciliation_diff。
ALTER TABLE `ledger_db`.`reconciliation_diff`
    COMMENT = '保存交易、账户、信用和账本之间的对账差异证据及处置状态',
    MODIFY COLUMN diff_id CHAR(26) NOT NULL COMMENT '对账差异 ID',
    MODIFY COLUMN biz_date DATE NOT NULL COMMENT '差异所属业务日期',
    MODIFY COLUMN transaction_id CHAR(26) NOT NULL COMMENT '关联资金交易',
    MODIFY COLUMN diff_type VARCHAR(32) NOT NULL COMMENT '余额、额度、应收或账本差异类型',
    MODIFY COLUMN expected_json JSON NOT NULL COMMENT '预期值及计算证据',
    MODIFY COLUMN actual_json JSON NOT NULL COMMENT '实际值及来源证据',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT '取值或格式：OPEN/PROCESSING/RESOLVED/IGNORED',
    MODIFY COLUMN manual_case_id CHAR(26) NULL COMMENT '业务库人工工单逻辑引用',
    MODIFY COLUMN trace_id CHAR(32) NOT NULL COMMENT '关联链路 ID',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '差异发现时间',
    MODIFY COLUMN resolved_at DATETIME(3) NULL COMMENT '处置完成时间';

-- 账户中心账本与信用数据库：outbox_event。
ALTER TABLE `ledger_db`.`outbox_event`
    COMMENT = '每个产生事实的 Schema 在本地事务中记录待可靠发布事件',
    MODIFY COLUMN event_id CHAR(26) NOT NULL COMMENT '全局事件 ID',
    MODIFY COLUMN aggregate_type VARCHAR(32) NOT NULL COMMENT '事件所属聚合类型',
    MODIFY COLUMN aggregate_id CHAR(26) NOT NULL COMMENT '聚合 ID',
    MODIFY COLUMN aggregate_version BIGINT UNSIGNED NOT NULL COMMENT '产生事件的聚合版本',
    MODIFY COLUMN event_type VARCHAR(64) NOT NULL COMMENT '事件名称',
    MODIFY COLUMN event_version SMALLINT UNSIGNED NOT NULL COMMENT '事件 Schema 版本',
    MODIFY COLUMN business_type VARCHAR(16) NULL COMMENT '资金业务类型',
    MODIFY COLUMN source_type VARCHAR(32) NULL COMMENT '来源订单类型',
    MODIFY COLUMN source_order_id CHAR(26) NULL COMMENT '来源订单 ID',
    MODIFY COLUMN funding_source VARCHAR(16) NULL COMMENT '取值或格式：BALANCE/MINI_CREDIT/SYSTEM_ISSUANCE',
    MODIFY COLUMN transaction_id CHAR(26) NULL COMMENT '关联资金交易',
    MODIFY COLUMN producer VARCHAR(32) NOT NULL COMMENT '发布模块',
    MODIFY COLUMN account_id CHAR(26) NULL COMMENT '受限个人账户路由键',
    MODIFY COLUMN merchant_account_id CHAR(26) NULL COMMENT '历史物理字段名，实际为受限扫码收款账户路由键',
    MODIFY COLUMN user_id_hash BINARY(32) NULL COMMENT '脱敏用户维度',
    MODIFY COLUMN trace_id CHAR(32) NOT NULL COMMENT '全链路追踪 ID',
    MODIFY COLUMN occurred_at DATETIME(3) NOT NULL COMMENT '业务事实发生时间',
    MODIFY COLUMN payload JSON NOT NULL COMMENT '版本化事件载荷',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '取值或格式：PENDING/PUBLISHED/DEAD',
    MODIFY COLUMN retry_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '发布重试次数',
    MODIFY COLUMN next_retry_at DATETIME(3) NULL COMMENT '下次发布时间',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT 'Outbox 行创建时间',
    MODIFY COLUMN published_at DATETIME(3) NULL COMMENT '首次成功发布时间';

-- 业务中心数据库：recharge_policy。
ALTER TABLE `business_db`.`recharge_policy`
    COMMENT = '保存模拟充值的单笔、单日金额和次数策略版本',
    MODIFY COLUMN policy_id CHAR(26) NOT NULL COMMENT '策略版本 ID',
    MODIFY COLUMN policy_code VARCHAR(32) NOT NULL COMMENT '稳定策略编码',
    MODIFY COLUMN single_limit_fen BIGINT UNSIGNED NOT NULL COMMENT '单笔充值上限，单位：分',
    MODIFY COLUMN daily_limit_fen BIGINT UNSIGNED NOT NULL COMMENT '单用户单日金额上限，单位：分',
    MODIFY COLUMN daily_count_limit INT UNSIGNED NOT NULL COMMENT '单用户单日次数上限',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '取值或格式：ACTIVE/INACTIVE',
    MODIFY COLUMN active_slot TINYINT GENERATED ALWAYS AS (
CASE WHEN status = 'ACTIVE' THEN 1 ELSE NULL END
) STORED COMMENT '活动策略唯一占位键',
    MODIFY COLUMN version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '策略版本号，订单保存其快照版本',
    MODIFY COLUMN effective_at DATETIME(3) NOT NULL COMMENT '策略生效时间',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '策略创建时间',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近状态更新时间';

-- 业务中心数据库：recharge_daily_usage。
ALTER TABLE `business_db`.`recharge_daily_usage`
    COMMENT = '按用户和业务日期保存充值额度的处理中预占与成功累计',
    MODIFY COLUMN user_id CHAR(26) NOT NULL COMMENT '充值用户',
    MODIFY COLUMN business_date DATE NOT NULL COMMENT 'Asia/Shanghai 业务日期',
    MODIFY COLUMN processing_fen BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '在途充值占用金额，单位：分',
    MODIFY COLUMN success_fen BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当日成功充值金额，单位：分',
    MODIFY COLUMN processing_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '在途充值次数',
    MODIFY COLUMN success_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当日成功充值次数',
    MODIFY COLUMN version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '并发额度预占 CAS 版本',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近预占或结算时间';

-- 业务中心数据库：recharge_order。
ALTER TABLE `business_db`.`recharge_order`
    COMMENT = '保存一次受控模拟充值的来源订单和策略快照',
    MODIFY COLUMN recharge_order_id CHAR(26) NOT NULL COMMENT '充值订单 ID',
    MODIFY COLUMN user_id CHAR(26) NOT NULL COMMENT '发起充值用户',
    MODIFY COLUMN target_account_id CHAR(26) NOT NULL COMMENT '本人入账账户',
    MODIFY COLUMN amount_fen BIGINT UNSIGNED NOT NULL COMMENT '充值金额，范围 1..5000000，单位：分',
    MODIFY COLUMN business_date DATE NOT NULL COMMENT '计入日额度的业务日期',
    MODIFY COLUMN channel VARCHAR(16) NOT NULL DEFAULT 'SIMULATED' COMMENT '固定模拟渠道',
    MODIFY COLUMN policy_id CHAR(26) NOT NULL COMMENT '受理时使用的策略',
    MODIFY COLUMN policy_version BIGINT UNSIGNED NOT NULL COMMENT '策略快照版本',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'CREATED' COMMENT '取值或格式：CREATED/PROCESSING/SUCCESS/REJECTED/CANCELLED/MANUAL_REVIEW',
    MODIFY COLUMN transaction_id CHAR(26) NULL COMMENT '受理后生成的充值交易',
    MODIFY COLUMN reject_reason_code VARCHAR(32) NULL COMMENT '受理前拒绝原因',
    MODIFY COLUMN version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '订单状态 CAS 版本',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '订单创建时间',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近状态更新时间',
    MODIFY COLUMN completed_at DATETIME(3) NULL COMMENT '成功或取消完成时间';

-- 业务中心数据库：refund_order。
ALTER TABLE `business_db`.`refund_order`
    COMMENT = '保存动态扫码收款订单创建用户对成功扫码支付发起的全额虚拟退款尝试',
    MODIFY COLUMN refund_order_id CHAR(26) NOT NULL COMMENT '退款尝试 ID',
    MODIFY COLUMN original_transaction_id CHAR(26) NOT NULL COMMENT '原 QR_PAY/CREDIT_PAY 交易',
    MODIFY COLUMN merchant_user_id CHAR(26) NOT NULL COMMENT '历史物理字段名，实际指发起退款的原收款普通用户',
    MODIFY COLUMN merchant_account_id CHAR(26) NOT NULL COMMENT '历史物理字段名，实际指原收款用户的本人账户',
    MODIFY COLUMN payer_user_id CHAR(26) NOT NULL COMMENT '原付款用户',
    MODIFY COLUMN payer_account_id CHAR(26) NOT NULL COMMENT '原付款余额或信用账户映射',
    MODIFY COLUMN original_business_type VARCHAR(16) NOT NULL COMMENT '取值或格式：QR_PAY/CREDIT_PAY',
    MODIFY COLUMN funding_source VARCHAR(16) NOT NULL COMMENT '取值或格式：BALANCE/MINI_CREDIT',
    MODIFY COLUMN amount_fen BIGINT UNSIGNED NOT NULL COMMENT '必须等于原交易全额，单位：分',
    MODIFY COLUMN reason_code VARCHAR(32) NOT NULL COMMENT '收款用户退款原因',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'CREATED' COMMENT '取值或格式：CREATED/PROCESSING/SUCCESS/REJECTED/CANCELLED/MANUAL_REVIEW',
    MODIFY COLUMN active_original_key CHAR(26) GENERATED ALWAYS AS (
CASE
WHEN status IN ('CREATED', 'PROCESSING', 'SUCCESS', 'MANUAL_REVIEW')
THEN original_transaction_id
ELSE NULL
END
) STORED COMMENT '有效或成功退款占位键',
    MODIFY COLUMN transaction_id CHAR(26) NULL COMMENT '对应 REFUND 资金交易',
    MODIFY COLUMN version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '退款状态 CAS 版本',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '退款申请时间',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近状态更新时间',
    MODIFY COLUMN completed_at DATETIME(3) NULL COMMENT '退款完成时间';

-- 业务中心数据库：transfer_draft。
ALTER TABLE `business_db`.`transfer_draft`
    COMMENT = '保存传统或 AI 转账在确认前的服务端可编辑草稿',
    MODIFY COLUMN draft_id CHAR(26) NOT NULL COMMENT '转账草稿 ID',
    MODIFY COLUMN payer_user_id CHAR(26) NOT NULL COMMENT '当前登录付款用户',
    MODIFY COLUMN payee_user_id CHAR(26) NOT NULL COMMENT '明确选择的收款用户',
    MODIFY COLUMN payer_account_id CHAR(26) NOT NULL COMMENT '服务端派生付款账户',
    MODIFY COLUMN payee_account_id CHAR(26) NOT NULL COMMENT '服务端派生收款账户',
    MODIFY COLUMN amount_fen BIGINT UNSIGNED NOT NULL COMMENT '转账金额 1..5000000，单位：分',
    MODIFY COLUMN remark VARCHAR(128) NULL COMMENT '用户备注',
    MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '取值或格式：DRAFT/VALIDATED/PENDING_CONFIRMATION/SUBMITTED/EXPIRED',
    MODIFY COLUMN version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '草稿编辑 CAS 版本',
    MODIFY COLUMN expires_at DATETIME(3) NOT NULL COMMENT '草稿失效时间',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '草稿创建时间',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近编辑时间';

-- 业务中心数据库：credit_repayment_draft。
ALTER TABLE `business_db`.`credit_repayment_draft`
    COMMENT = '保存信用还款确认前的金额、付款账户和服务端分配预览',
    MODIFY COLUMN repayment_draft_id CHAR(26) NOT NULL COMMENT '还款草稿 ID',
    MODIFY COLUMN user_id CHAR(26) NOT NULL COMMENT '还款用户',
    MODIFY COLUMN credit_account_id CHAR(26) NOT NULL COMMENT '本人信用账户',
    MODIFY COLUMN payer_account_id CHAR(26) NOT NULL COMMENT '本人余额付款账户',
    MODIFY COLUMN amount_fen BIGINT UNSIGNED NOT NULL COMMENT '还款金额，单位：分',
    MODIFY COLUMN allocation_snapshot JSON NOT NULL COMMENT '服务端生成的只读分配预览',
    MODIFY COLUMN allocation_hash BINARY(32) NOT NULL COMMENT '分配预览摘要，绑定确认上下文',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT '取值或格式：DRAFT/CONFIRMED/CONSUMED/EXPIRED',
    MODIFY COLUMN version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '草稿状态 CAS 版本',
    MODIFY COLUMN expires_at DATETIME(3) NOT NULL COMMENT '草稿有效期',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '草稿创建时间',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近状态更新时间';

-- 业务中心数据库：qr_pay_order。
ALTER TABLE `business_db`.`qr_pay_order`
    COMMENT = '保存普通用户为本人账户创建的动态扫码收款订单、付款选择、支付和退款状态',
    MODIFY COLUMN qr_order_id CHAR(26) NOT NULL COMMENT '扫码订单 ID',
    MODIFY COLUMN merchant_user_id CHAR(26) NOT NULL COMMENT '历史物理字段名，实际指创建订单的普通用户',
    MODIFY COLUMN merchant_account_id CHAR(26) NOT NULL COMMENT '历史物理字段名，实际指该用户本人的收款账户',
    MODIFY COLUMN payer_user_id CHAR(26) NULL COMMENT '扫码后绑定的付款用户',
    MODIFY COLUMN transaction_id CHAR(26) NULL COMMENT '支付资金交易',
    MODIFY COLUMN amount_fen BIGINT UNSIGNED NOT NULL COMMENT '订单金额 1..5000000，单位：分',
    MODIFY COLUMN subject VARCHAR(128) NULL COMMENT '商品或收款说明',
    MODIFY COLUMN funding_source VARCHAR(16) NULL COMMENT '取值或格式：BALANCE/MINI_CREDIT',
    MODIFY COLUMN refunded_fen BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '成功退款金额，仅 0 或全额，单位：分',
    MODIFY COLUMN refund_status VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT '取值或格式：NONE/PROCESSING/SUCCESS/MANUAL_REVIEW',
    MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'CREATED' COMMENT '扫码订单完整状态机状态',
    MODIFY COLUMN version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '订单状态 CAS 版本',
    MODIFY COLUMN scanned_at DATETIME(3) NULL COMMENT '首次有效扫码时间',
    MODIFY COLUMN confirmed_at DATETIME(3) NULL COMMENT '付款确认时间',
    MODIFY COLUMN expires_at DATETIME(3) NOT NULL COMMENT '订单失效时间',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '订单创建时间',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近状态更新时间';

-- 业务中心数据库：qr_pay_token。
ALTER TABLE `business_db`.`qr_pay_token`
    COMMENT = '保存动态码的一次性令牌和 H5 会话绑定，防止二维码重放',
    MODIFY COLUMN token_digest BINARY(32) NOT NULL COMMENT '二维码原始令牌摘要',
    MODIFY COLUMN qr_order_id CHAR(26) NOT NULL COMMENT '对应扫码订单',
    MODIFY COLUMN bootstrap_session_hash BINARY(32) NOT NULL COMMENT 'Web 引导会话摘要',
    MODIFY COLUMN h5_session_id CHAR(26) NULL COMMENT '首个合法 H5 会话绑定',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '取值或格式：ACTIVE/CONSUMED/REVOKED/EXPIRED',
    MODIFY COLUMN expires_at DATETIME(3) NOT NULL COMMENT '五分钟有效期截止时间',
    MODIFY COLUMN consumed_at DATETIME(3) NULL COMMENT '消费时间',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '令牌创建时间';

-- 业务中心数据库：personal_collection_code。
ALTER TABLE `business_db`.`personal_collection_code`
    COMMENT = '保存普通用户长期个人收款码及唯一活动码占位',
    MODIFY COLUMN code_id CHAR(26) NOT NULL COMMENT '个人码 ID',
    MODIFY COLUMN owner_user_id CHAR(26) NOT NULL COMMENT '个人码所有者',
    MODIFY COLUMN payee_account_id CHAR(26) NOT NULL COMMENT '固定收款账户',
    MODIFY COLUMN token_digest BINARY(32) NOT NULL COMMENT '码令牌摘要',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '取值或格式：ACTIVE/REVOKED',
    MODIFY COLUMN active_owner_key CHAR(26) GENERATED ALWAYS AS (
CASE WHEN status = 'ACTIVE' THEN owner_user_id ELSE NULL END
) STORED COMMENT '活动时等于所有者 ID',
    MODIFY COLUMN version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态 CAS 版本',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近更新时间',
    MODIFY COLUMN revoked_at DATETIME(3) NULL COMMENT '撤销时间';

-- 业务中心数据库：collection_request。
ALTER TABLE `business_db`.`collection_request`
    COMMENT = '保存普通用户创建的固定金额一次性收款请求',
    MODIFY COLUMN request_id CHAR(26) NOT NULL COMMENT '固定请求 ID',
    MODIFY COLUMN requester_user_id CHAR(26) NOT NULL COMMENT '请求创建人',
    MODIFY COLUMN payee_account_id CHAR(26) NOT NULL COMMENT '收款账户',
    MODIFY COLUMN token_digest BINARY(32) NOT NULL COMMENT '请求码令牌摘要',
    MODIFY COLUMN amount_fen BIGINT UNSIGNED NOT NULL COMMENT '固定金额 1..5000000，单位：分',
    MODIFY COLUMN subject VARCHAR(50) NULL COMMENT '收款说明',
    MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT '取值或格式：OPEN/PROCESSING/SUCCESS/CANCELLED/EXPIRED/MANUAL_REVIEW',
    MODIFY COLUMN active_order_id CHAR(26) NULL COMMENT '当前抢占付款尝试',
    MODIFY COLUMN transaction_id CHAR(26) NULL COMMENT '最终成功交易',
    MODIFY COLUMN cancel_requested_at DATETIME(3) NULL COMMENT '在途期间的取消意图时间',
    MODIFY COLUMN version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '请求抢占 CAS 版本',
    MODIFY COLUMN expires_at DATETIME(3) NOT NULL COMMENT '请求失效时间',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近状态更新时间';

-- 业务中心数据库：collection_order。
ALTER TABLE `business_db`.`collection_order`
    COMMENT = '保存个人码或固定请求的每次 H5 付款尝试',
    MODIFY COLUMN order_id CHAR(26) NOT NULL COMMENT '尝试订单 ID',
    MODIFY COLUMN mode VARCHAR(24) NOT NULL COMMENT '取值或格式：PERSONAL_QR/FIXED_REQUEST',
    MODIFY COLUMN code_id CHAR(26) NULL COMMENT '个人码模式来源',
    MODIFY COLUMN request_id CHAR(26) NULL COMMENT '固定请求模式来源',
    MODIFY COLUMN payer_user_id CHAR(26) NOT NULL COMMENT '付款用户',
    MODIFY COLUMN payer_account_id CHAR(26) NOT NULL COMMENT '付款余额账户',
    MODIFY COLUMN payee_user_id CHAR(26) NOT NULL COMMENT '收款用户',
    MODIFY COLUMN payee_account_id CHAR(26) NOT NULL COMMENT '收款账户',
    MODIFY COLUMN h5_session_id CHAR(26) NOT NULL COMMENT 'H5 会话唯一标识',
    MODIFY COLUMN amount_fen BIGINT UNSIGNED NOT NULL COMMENT '付款金额，单位：分',
    MODIFY COLUMN subject VARCHAR(50) NULL COMMENT '收款说明快照',
    MODIFY COLUMN funding_source VARCHAR(16) NOT NULL DEFAULT 'BALANCE' COMMENT '强制余额支付',
    MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '单次个人收款订单状态',
    MODIFY COLUMN transaction_id CHAR(26) NULL COMMENT '对应 TRANSFER 交易',
    MODIFY COLUMN version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '订单状态 CAS 版本',
    MODIFY COLUMN expires_at DATETIME(3) NOT NULL COMMENT '尝试失效时间',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近状态更新时间';

-- 业务中心数据库：confirmation_subject。
ALTER TABLE `business_db`.`confirmation_subject`
    COMMENT = '保存每个待确认业务主体当前唯一确认上下文',
    MODIFY COLUMN subject_type VARCHAR(24) NOT NULL COMMENT '草稿或订单类型',
    MODIFY COLUMN subject_id CHAR(26) NOT NULL COMMENT '待确认主体 ID',
    MODIFY COLUMN current_confirmation_id CHAR(26) NOT NULL COMMENT '当前活动确认 ID',
    MODIFY COLUMN version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '更换确认上下文的 CAS 版本',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近签发时间';

-- 业务中心数据库：confirmation。
ALTER TABLE `business_db`.`confirmation`
    COMMENT = '保存一次确认令牌、业务快照摘要和支付密码证明版本',
    MODIFY COLUMN confirmation_id CHAR(26) NOT NULL COMMENT '确认上下文 ID',
    MODIFY COLUMN token_digest BINARY(32) NOT NULL COMMENT '原始确认令牌摘要',
    MODIFY COLUMN subject_type VARCHAR(24) NOT NULL COMMENT '被确认主体类型',
    MODIFY COLUMN subject_id CHAR(26) NOT NULL COMMENT '被确认主体 ID',
    MODIFY COLUMN subject_hash BINARY(32) NOT NULL COMMENT '金额、账户、版本等不可变快照摘要',
    MODIFY COLUMN payer_user_id CHAR(26) NOT NULL COMMENT '执行确认用户',
    MODIFY COLUMN payment_proof_id CHAR(26) NOT NULL COMMENT '用户库支付证明逻辑引用',
    MODIFY COLUMN pay_password_version BIGINT UNSIGNED NOT NULL COMMENT '签发时支付密码版本',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '取值或格式：ACTIVE/CONSUMED/REVOKED/EXPIRED',
    MODIFY COLUMN active_subject_key VARCHAR(64) GENERATED ALWAYS AS (
CASE
WHEN status = 'ACTIVE' THEN CONCAT(subject_type, ':', subject_id)
ELSE NULL
END
) STORED COMMENT '活动确认主体唯一占位',
    MODIFY COLUMN expires_at DATETIME(3) NOT NULL COMMENT '确认有效期',
    MODIFY COLUMN consumed_at DATETIME(3) NULL COMMENT '一次性消费时间',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '签发时间';

-- 业务中心数据库：risk_decision。
ALTER TABLE `business_db`.`risk_decision`
    COMMENT = '保存一次可审计风险判断及规则版本',
    MODIFY COLUMN decision_id CHAR(26) NOT NULL COMMENT '风险决策 ID',
    MODIFY COLUMN subject_type VARCHAR(24) NOT NULL COMMENT '被评估主体类型',
    MODIFY COLUMN subject_id CHAR(26) NOT NULL COMMENT '被评估主体 ID',
    MODIFY COLUMN transaction_id CHAR(26) NULL COMMENT '交易创建后关联的交易 ID',
    MODIFY COLUMN rule_version VARCHAR(32) NOT NULL COMMENT '风控规则版本',
    MODIFY COLUMN risk_level VARCHAR(16) NOT NULL COMMENT '风险等级',
    MODIFY COLUMN action VARCHAR(16) NOT NULL COMMENT '取值或格式：PASS/REJECT/REVIEW',
    MODIFY COLUMN reason_code VARCHAR(32) NOT NULL COMMENT '标准风险原因码',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '决策时间';

-- 业务中心数据库：manual_case。
ALTER TABLE `business_db`.`manual_case`
    COMMENT = '保存异常资金、风控或对账的人工处理工单',
    MODIFY COLUMN case_id CHAR(26) NOT NULL COMMENT '工单 ID',
    MODIFY COLUMN case_type VARCHAR(32) NOT NULL COMMENT '工单业务类别',
    MODIFY COLUMN subject_type VARCHAR(24) NOT NULL COMMENT '异常主体类型',
    MODIFY COLUMN subject_id CHAR(26) NOT NULL COMMENT '异常主体 ID',
    MODIFY COLUMN transaction_id CHAR(26) NULL COMMENT '关联交易',
    MODIFY COLUMN reason_code VARCHAR(32) NOT NULL COMMENT '转人工原因',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT '取值或格式：OPEN/PROCESSING/RESOLVED/CLOSED',
    MODIFY COLUMN active_subject_key VARCHAR(64) GENERATED ALWAYS AS (
CASE
WHEN status IN ('OPEN', 'PROCESSING') THEN CONCAT(subject_type, ':', subject_id)
ELSE NULL
END
) STORED COMMENT '活动工单主体唯一占位',
    MODIFY COLUMN operator_id CHAR(26) NULL COMMENT '当前处理人',
    MODIFY COLUMN version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '抢单和状态 CAS 版本',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '工单创建时间',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近处理时间';

-- 业务中心数据库：fund_transaction。
ALTER TABLE `business_db`.`fund_transaction`
    COMMENT = '统一承载所有已受理资金业务，是业务状态与 TCC 协调的主聚合',
    MODIFY COLUMN transaction_id CHAR(26) NOT NULL COMMENT '统一资金交易 ID',
    MODIFY COLUMN business_type VARCHAR(16) NOT NULL COMMENT '取值或格式：TRANSFER/QR_PAY/CREDIT_PAY/CREDIT_REPAY/RECHARGE/REFUND',
    MODIFY COLUMN source_type VARCHAR(32) NOT NULL COMMENT '草稿或订单来源类型',
    MODIFY COLUMN source_order_id CHAR(26) NOT NULL COMMENT '来源对象 ID',
    MODIFY COLUMN initiator_user_id CHAR(26) NOT NULL COMMENT '原始登录发起人，恢复任务沿用',
    MODIFY COLUMN payer_account_id CHAR(26) NULL COMMENT '付款账户；充值为空',
    MODIFY COLUMN payee_account_id CHAR(26) NOT NULL COMMENT '收款或入账账户',
    MODIFY COLUMN funding_source VARCHAR(16) NOT NULL COMMENT '取值或格式：BALANCE/MINI_CREDIT/SYSTEM_ISSUANCE',
    MODIFY COLUMN related_transaction_id CHAR(26) NULL COMMENT '退款关联原支付，其余为空',
    MODIFY COLUMN amount_fen BIGINT UNSIGNED NOT NULL COMMENT '交易金额 1..5000000，单位：分',
    MODIFY COLUMN idempotency_key VARCHAR(64) NOT NULL COMMENT '调用方幂等键',
    MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'PROCESSING' COMMENT '取值或格式：PROCESSING/COMPENSATING/MANUAL_REVIEW/SUCCESS/REVERSED/CANCELLED',
    MODIFY COLUMN risk_level VARCHAR(16) NOT NULL COMMENT '受理时风险等级',
    MODIFY COLUMN trace_id CHAR(32) NOT NULL COMMENT '全链路追踪 ID',
    MODIFY COLUMN version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '交易状态 CAS 版本',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '交易受理时间',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近状态更新时间';

-- 业务中心数据库：tcc_global。
ALTER TABLE `business_db`.`tcc_global`
    COMMENT = 'business_db 保存一笔资金交易的 TCC 全局协调状态和恢复计划',
    MODIFY COLUMN transaction_id CHAR(26) NOT NULL COMMENT '被协调资金交易',
    MODIFY COLUMN xid VARCHAR(128) NOT NULL COMMENT '全局 TCC XID',
    MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'PROCESSING' COMMENT '取值或格式：PROCESSING/COMMITTING/ROLLING_BACK/SUCCESS/CANCELLED/MANUAL_REVIEW',
    MODIFY COLUMN retry_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '全局恢复重试次数',
    MODIFY COLUMN next_retry_at DATETIME(3) NULL COMMENT '下次允许恢复时间',
    MODIFY COLUMN started_at DATETIME(3) NOT NULL COMMENT '全局事务开始时间',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近协调状态更新时间';

-- 业务中心数据库：idempotency_record。
ALTER TABLE `business_db`.`idempotency_record`
    COMMENT = '每个拥有对外创建接口的 Schema 保存请求幂等受理、资源绑定和响应快照',
    MODIFY COLUMN record_id CHAR(26) NOT NULL COMMENT '幂等记录 ID',
    MODIFY COLUMN principal_key VARCHAR(128) NOT NULL COMMENT '登录主体或受信任调用方',
    MODIFY COLUMN api_scope VARCHAR(64) NOT NULL COMMENT '接口或业务动作范围',
    MODIFY COLUMN idempotency_key VARCHAR(64) NOT NULL COMMENT '调用方幂等键',
    MODIFY COLUMN request_digest BINARY(32) NOT NULL COMMENT '规范化请求摘要，检测同键异参',
    MODIFY COLUMN resource_type VARCHAR(32) NULL COMMENT '已创建资源类型',
    MODIFY COLUMN resource_id CHAR(26) NULL COMMENT '已创建资源 ID',
    MODIFY COLUMN response_json JSON NULL COMMENT '可安全重放的脱敏响应快照',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING' COMMENT '取值或格式：PROCESSING/COMPLETED/FAILED',
    MODIFY COLUMN expires_at DATETIME(3) NOT NULL COMMENT '幂等记录保留截止时间',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '首次受理时间',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近处理时间';

-- 业务中心数据库：inbox_event。
ALTER TABLE `business_db`.`inbox_event`
    COMMENT = '每个事件消费者在自己的 Schema 保存消费幂等和接管状态',
    MODIFY COLUMN consumer_name VARCHAR(64) NOT NULL COMMENT '消费者逻辑名称',
    MODIFY COLUMN event_id CHAR(26) NOT NULL COMMENT '被消费事件 ID',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING' COMMENT '取值或格式：PROCESSING/DONE/FAILED',
    MODIFY COLUMN received_at DATETIME(3) NOT NULL COMMENT '首次接收时间',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近接管或完成时间';

-- 业务中心数据库：audit_log。
ALTER TABLE `business_db`.`audit_log`
    COMMENT = '各所有者 Schema 独立保存不可变、脱敏的安全和业务操作证据',
    MODIFY COLUMN audit_id BIGINT UNSIGNED NOT NULL COMMENT '审计流水 ID',
    MODIFY COLUMN actor_type VARCHAR(16) NOT NULL COMMENT '取值或格式：USER/SYSTEM/OPERATOR',
    MODIFY COLUMN actor_id VARCHAR(128) NOT NULL COMMENT '操作者 ID 或系统任务标识',
    MODIFY COLUMN action VARCHAR(64) NOT NULL COMMENT '设置密码、确认交易、人工处置等动作',
    MODIFY COLUMN target_type VARCHAR(32) NOT NULL COMMENT '操作目标类型',
    MODIFY COLUMN target_id VARCHAR(128) NOT NULL COMMENT '操作目标 ID',
    MODIFY COLUMN result_code VARCHAR(32) NOT NULL COMMENT '标准结果码',
    MODIFY COLUMN trace_id CHAR(32) NOT NULL COMMENT '全链路追踪 ID',
    MODIFY COLUMN detail_json JSON NULL COMMENT '脱敏证据和变更摘要',
    MODIFY COLUMN occurred_at DATETIME(3) NOT NULL COMMENT '操作发生时间';

-- 业务中心数据库：outbox_event。
ALTER TABLE `business_db`.`outbox_event`
    COMMENT = '每个产生事实的 Schema 在本地事务中记录待可靠发布事件',
    MODIFY COLUMN event_id CHAR(26) NOT NULL COMMENT '全局事件 ID',
    MODIFY COLUMN aggregate_type VARCHAR(32) NOT NULL COMMENT '事件所属聚合类型',
    MODIFY COLUMN aggregate_id CHAR(26) NOT NULL COMMENT '聚合 ID',
    MODIFY COLUMN aggregate_version BIGINT UNSIGNED NOT NULL COMMENT '产生事件的聚合版本',
    MODIFY COLUMN event_type VARCHAR(64) NOT NULL COMMENT '事件名称',
    MODIFY COLUMN event_version SMALLINT UNSIGNED NOT NULL COMMENT '事件 Schema 版本',
    MODIFY COLUMN business_type VARCHAR(16) NULL COMMENT '资金业务类型',
    MODIFY COLUMN source_type VARCHAR(32) NULL COMMENT '来源订单类型',
    MODIFY COLUMN source_order_id CHAR(26) NULL COMMENT '来源订单 ID',
    MODIFY COLUMN funding_source VARCHAR(16) NULL COMMENT '取值或格式：BALANCE/MINI_CREDIT/SYSTEM_ISSUANCE',
    MODIFY COLUMN transaction_id CHAR(26) NULL COMMENT '关联资金交易',
    MODIFY COLUMN producer VARCHAR(32) NOT NULL COMMENT '发布模块',
    MODIFY COLUMN account_id CHAR(26) NULL COMMENT '受限个人账户路由键',
    MODIFY COLUMN merchant_account_id CHAR(26) NULL COMMENT '历史物理字段名，实际为受限扫码收款账户路由键',
    MODIFY COLUMN user_id_hash BINARY(32) NULL COMMENT '脱敏用户维度',
    MODIFY COLUMN trace_id CHAR(32) NOT NULL COMMENT '全链路追踪 ID',
    MODIFY COLUMN occurred_at DATETIME(3) NOT NULL COMMENT '业务事实发生时间',
    MODIFY COLUMN payload JSON NOT NULL COMMENT '版本化事件载荷',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '取值或格式：PENDING/PUBLISHED/DEAD',
    MODIFY COLUMN retry_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '发布重试次数',
    MODIFY COLUMN next_retry_at DATETIME(3) NULL COMMENT '下次发布时间',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT 'Outbox 行创建时间',
    MODIFY COLUMN published_at DATETIME(3) NULL COMMENT '首次成功发布时间';

-- AI 服务数据库：agent_session。
ALTER TABLE `agent_db`.`agent_session`
    COMMENT = '保存一次用户 AI 对话会话的脱敏摘要、结构化槽位和生命周期',
    MODIFY COLUMN session_id CHAR(26) NOT NULL COMMENT 'AI 会话 ID',
    MODIFY COLUMN user_id CHAR(26) NOT NULL COMMENT '会话所属用户',
    MODIFY COLUMN summary TEXT NULL COMMENT '上下文压缩后的脱敏摘要',
    MODIFY COLUMN slots_json JSON NULL COMMENT '当前意图的结构化槽位，不含可信资金终态',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '取值或格式：ACTIVE/CLOSED/EXPIRED',
    MODIFY COLUMN version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '会话上下文 CAS 版本',
    MODIFY COLUMN last_active_at DATETIME(3) NOT NULL COMMENT '最近消息或工具调用时间',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '会话创建时间';

-- AI 服务数据库：agent_message。
ALTER TABLE `agent_db`.`agent_message`
    COMMENT = '保存用户和助手的脱敏消息，用于恢复对话和审计 AI 解释',
    MODIFY COLUMN message_id CHAR(26) NOT NULL COMMENT '消息 ID',
    MODIFY COLUMN session_id CHAR(26) NOT NULL COMMENT '所属 AI 会话',
    MODIFY COLUMN client_message_id VARCHAR(64) NOT NULL COMMENT '客户端消息幂等键',
    MODIFY COLUMN role VARCHAR(16) NOT NULL COMMENT '取值或格式：USER/ASSISTANT/SYSTEM',
    MODIFY COLUMN content_redacted TEXT NOT NULL COMMENT '脱敏消息正文',
    MODIFY COLUMN token_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '上下文预算和成本统计',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '消息创建时间';

-- AI 服务数据库：tool_call_log。
ALTER TABLE `agent_db`.`tool_call_log`
    COMMENT = '保存 AI/MCP 工具调用的摘要、结果、耗时和 Trace 证据',
    MODIFY COLUMN tool_call_id CHAR(26) NOT NULL COMMENT '工具调用 ID',
    MODIFY COLUMN session_id CHAR(26) NOT NULL COMMENT '发起调用的 AI 会话',
    MODIFY COLUMN tool_name VARCHAR(64) NOT NULL COMMENT '工具契约名称',
    MODIFY COLUMN request_digest BINARY(32) NOT NULL COMMENT '脱敏规范化请求摘要',
    MODIFY COLUMN result_code VARCHAR(32) NOT NULL COMMENT '标准工具结果码',
    MODIFY COLUMN duration_ms INT UNSIGNED NOT NULL COMMENT '调用耗时毫秒',
    MODIFY COLUMN trace_id CHAR(32) NOT NULL COMMENT '跨服务 Trace ID',
    MODIFY COLUMN occurred_at DATETIME(3) NOT NULL COMMENT '调用发生时间';

-- AI 服务数据库：preference。
ALTER TABLE `agent_db`.`preference`
    COMMENT = '在用户明确同意后保存低敏 AI 偏好，不保存可跳过确认的资金决策',
    MODIFY COLUMN preference_id CHAR(26) NOT NULL COMMENT '偏好记录 ID',
    MODIFY COLUMN user_id CHAR(26) NOT NULL COMMENT '偏好所属用户',
    MODIFY COLUMN preference_type VARCHAR(32) NOT NULL COMMENT '常用备注、展示偏好等类型',
    MODIFY COLUMN value_encrypted VARBINARY(1024) NOT NULL COMMENT '加密偏好值',
    MODIFY COLUMN consent_version VARCHAR(16) NOT NULL COMMENT '用户同意版本',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '取值或格式：ACTIVE/REVOKED',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '首次同意保存时间',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近修改或撤销时间';

-- AI 服务数据库：idempotency_record。
ALTER TABLE `agent_db`.`idempotency_record`
    COMMENT = '每个拥有对外创建接口的 Schema 保存请求幂等受理、资源绑定和响应快照',
    MODIFY COLUMN record_id CHAR(26) NOT NULL COMMENT '幂等记录 ID',
    MODIFY COLUMN principal_key VARCHAR(128) NOT NULL COMMENT '登录主体或受信任调用方',
    MODIFY COLUMN api_scope VARCHAR(64) NOT NULL COMMENT '接口或业务动作范围',
    MODIFY COLUMN idempotency_key VARCHAR(64) NOT NULL COMMENT '调用方幂等键',
    MODIFY COLUMN request_digest BINARY(32) NOT NULL COMMENT '规范化请求摘要，检测同键异参',
    MODIFY COLUMN resource_type VARCHAR(32) NULL COMMENT '已创建资源类型',
    MODIFY COLUMN resource_id CHAR(26) NULL COMMENT '已创建资源 ID',
    MODIFY COLUMN response_json JSON NULL COMMENT '可安全重放的脱敏响应快照',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING' COMMENT '取值或格式：PROCESSING/COMPLETED/FAILED',
    MODIFY COLUMN expires_at DATETIME(3) NOT NULL COMMENT '幂等记录保留截止时间',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT '首次受理时间',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近处理时间';

-- AI 服务数据库：audit_log。
ALTER TABLE `agent_db`.`audit_log`
    COMMENT = '各所有者 Schema 独立保存不可变、脱敏的安全和业务操作证据',
    MODIFY COLUMN audit_id BIGINT UNSIGNED NOT NULL COMMENT '审计流水 ID',
    MODIFY COLUMN actor_type VARCHAR(16) NOT NULL COMMENT '取值或格式：USER/SYSTEM/OPERATOR',
    MODIFY COLUMN actor_id VARCHAR(128) NOT NULL COMMENT '操作者 ID 或系统任务标识',
    MODIFY COLUMN action VARCHAR(64) NOT NULL COMMENT '设置密码、确认交易、人工处置等动作',
    MODIFY COLUMN target_type VARCHAR(32) NOT NULL COMMENT '操作目标类型',
    MODIFY COLUMN target_id VARCHAR(128) NOT NULL COMMENT '操作目标 ID',
    MODIFY COLUMN result_code VARCHAR(32) NOT NULL COMMENT '标准结果码',
    MODIFY COLUMN trace_id CHAR(32) NOT NULL COMMENT '全链路追踪 ID',
    MODIFY COLUMN detail_json JSON NULL COMMENT '脱敏证据和变更摘要',
    MODIFY COLUMN occurred_at DATETIME(3) NOT NULL COMMENT '操作发生时间';

-- AI 服务数据库：outbox_event。
ALTER TABLE `agent_db`.`outbox_event`
    COMMENT = '每个产生事实的 Schema 在本地事务中记录待可靠发布事件',
    MODIFY COLUMN event_id CHAR(26) NOT NULL COMMENT '全局事件 ID',
    MODIFY COLUMN aggregate_type VARCHAR(32) NOT NULL COMMENT '事件所属聚合类型',
    MODIFY COLUMN aggregate_id CHAR(26) NOT NULL COMMENT '聚合 ID',
    MODIFY COLUMN aggregate_version BIGINT UNSIGNED NOT NULL COMMENT '产生事件的聚合版本',
    MODIFY COLUMN event_type VARCHAR(64) NOT NULL COMMENT '事件名称',
    MODIFY COLUMN event_version SMALLINT UNSIGNED NOT NULL COMMENT '事件 Schema 版本',
    MODIFY COLUMN business_type VARCHAR(16) NULL COMMENT '资金业务类型',
    MODIFY COLUMN source_type VARCHAR(32) NULL COMMENT '来源订单类型',
    MODIFY COLUMN source_order_id CHAR(26) NULL COMMENT '来源订单 ID',
    MODIFY COLUMN funding_source VARCHAR(16) NULL COMMENT '取值或格式：BALANCE/MINI_CREDIT/SYSTEM_ISSUANCE',
    MODIFY COLUMN transaction_id CHAR(26) NULL COMMENT '关联资金交易',
    MODIFY COLUMN producer VARCHAR(32) NOT NULL COMMENT '发布模块',
    MODIFY COLUMN account_id CHAR(26) NULL COMMENT '受限个人账户路由键',
    MODIFY COLUMN merchant_account_id CHAR(26) NULL COMMENT '历史物理字段名，实际为受限扫码收款账户路由键',
    MODIFY COLUMN user_id_hash BINARY(32) NULL COMMENT '脱敏用户维度',
    MODIFY COLUMN trace_id CHAR(32) NOT NULL COMMENT '全链路追踪 ID',
    MODIFY COLUMN occurred_at DATETIME(3) NOT NULL COMMENT '业务事实发生时间',
    MODIFY COLUMN payload JSON NOT NULL COMMENT '版本化事件载荷',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '取值或格式：PENDING/PUBLISHED/DEAD',
    MODIFY COLUMN retry_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '发布重试次数',
    MODIFY COLUMN next_retry_at DATETIME(3) NULL COMMENT '下次发布时间',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL COMMENT 'Outbox 行创建时间',
    MODIFY COLUMN published_at DATETIME(3) NULL COMMENT '首次成功发布时间';

-- 监控指标与统计投影数据库：inbox_event。
ALTER TABLE `metrics_db`.`inbox_event`
    COMMENT = '每个事件消费者在自己的 Schema 保存消费幂等和接管状态',
    MODIFY COLUMN consumer_name VARCHAR(64) NOT NULL COMMENT '消费者逻辑名称',
    MODIFY COLUMN event_id CHAR(26) NOT NULL COMMENT '被消费事件 ID',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING' COMMENT '取值或格式：PROCESSING/DONE/FAILED',
    MODIFY COLUMN received_at DATETIME(3) NOT NULL COMMENT '首次接收时间',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近接管或完成时间';

-- 监控指标与统计投影数据库：quarantined_event。
ALTER TABLE `metrics_db`.`quarantined_event`
    COMMENT = '隔离 Schema 不兼容、字段缺失或口径无法判定的事件，避免污染指标',
    MODIFY COLUMN consumer_name VARCHAR(64) NOT NULL COMMENT '隔离事件的消费者',
    MODIFY COLUMN event_id CHAR(26) NOT NULL COMMENT '原事件 ID',
    MODIFY COLUMN reason_code VARCHAR(32) NOT NULL COMMENT '隔离原因',
    MODIFY COLUMN schema_version SMALLINT UNSIGNED NOT NULL COMMENT '输入事件版本',
    MODIFY COLUMN payload JSON NOT NULL COMMENT '原始脱敏载荷',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT '取值或格式：OPEN/REPROCESSED/IGNORED',
    MODIFY COLUMN quarantined_at DATETIME(3) NOT NULL COMMENT '隔离时间',
    MODIFY COLUMN resolved_at DATETIME(3) NULL COMMENT '重放或忽略时间';

-- 监控指标与统计投影数据库：analytics_event。
ALTER TABLE `metrics_db`.`analytics_event`
    COMMENT = '把业务事件标准化为普通用户本人统计和平台运营指标可共同消费的分析事实',
    MODIFY COLUMN event_id CHAR(26) NOT NULL COMMENT '原事件 ID，兼作去重键',
    MODIFY COLUMN event_type VARCHAR(64) NOT NULL COMMENT '标准分析事件类型',
    MODIFY COLUMN event_version SMALLINT UNSIGNED NOT NULL COMMENT '输入事件 Schema 版本',
    MODIFY COLUMN business_type VARCHAR(16) NULL COMMENT '资金业务类型',
    MODIFY COLUMN source_type VARCHAR(32) NULL COMMENT '来源订单类型',
    MODIFY COLUMN source_order_id CHAR(26) NULL COMMENT '来源订单 ID',
    MODIFY COLUMN funding_source VARCHAR(16) NULL COMMENT '余额、信用或发行来源',
    MODIFY COLUMN transaction_id CHAR(26) NULL COMMENT '当前交易 ID',
    MODIFY COLUMN original_transaction_id CHAR(26) NULL COMMENT '退款关联原交易',
    MODIFY COLUMN account_id CHAR(26) NULL COMMENT '受限个人投影路由键',
    MODIFY COLUMN merchant_account_id CHAR(26) NULL COMMENT '历史物理字段名，实际为受限扫码收款账户投影路由键',
    MODIFY COLUMN account_id_hash BINARY(32) NULL COMMENT '脱敏个人维度',
    MODIFY COLUMN merchant_account_id_hash BINARY(32) NULL COMMENT '历史物理字段名，实际为脱敏收款账户维度',
    MODIFY COLUMN direction VARCHAR(16) NULL COMMENT '取值或格式：INCOME/EXPENSE/NEUTRAL',
    MODIFY COLUMN stat_category VARCHAR(32) NULL COMMENT '收入、支出、充值、还款或退款类别',
    MODIFY COLUMN amount_fen BIGINT UNSIGNED NULL COMMENT '确定终态统计金额，单位：分',
    MODIFY COLUMN occurred_at DATETIME(3) NOT NULL COMMENT '业务事实发生时间',
    MODIFY COLUMN definition_version INT UNSIGNED NOT NULL COMMENT '指标口径版本',
    MODIFY COLUMN dimensions_json JSON NULL COMMENT '扩展脱敏维度',
    MODIFY COLUMN metrics_json JSON NULL COMMENT '扩展指标值',
    MODIFY COLUMN trace_id CHAR(32) NOT NULL COMMENT '链路追踪 ID';

-- 监控指标与统计投影数据库：personal_cashflow_daily。
ALTER TABLE `metrics_db`.`personal_cashflow_daily`
    COMMENT = '按个人账户和日期保存收支、余额资金流、信用消费、还款与退款投影',
    MODIFY COLUMN account_id CHAR(26) NOT NULL COMMENT '个人账户 ID',
    MODIFY COLUMN stat_date DATE NOT NULL COMMENT '统计日期',
    MODIFY COLUMN definition_version INT UNSIGNED NOT NULL COMMENT '指标口径版本',
    MODIFY COLUMN transfer_income_fen BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '收到成功转账收入，单位：分',
    MODIFY COLUMN transfer_expense_fen BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '主动转账支出，单位：分',
    MODIFY COLUMN balance_payment_fen BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '余额扫码消费支出，单位：分',
    MODIFY COLUMN credit_consumption_fen BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Mini 花呗消费金额，单位：分',
    MODIFY COLUMN credit_repayment_fen BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '信用偿债资金流，单位：分',
    MODIFY COLUMN recharge_inflow_fen BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '模拟充值资金流入，非收入，单位：分',
    MODIFY COLUMN refund_fen BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '经营退款冲减金额，单位：分',
    MODIFY COLUMN success_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '确定终态交易次数',
    MODIFY COLUMN quality_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '取值或格式：PENDING/PASSED/FAILED',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近投影更新时间';

-- 监控指标与统计投影数据库：personal_counterparty_stat。
ALTER TABLE `metrics_db`.`personal_counterparty_stat`
    COMMENT = '按个人账户、交易对象和周期保存收支分布',
    MODIFY COLUMN account_id CHAR(26) NOT NULL COMMENT '本人账户',
    MODIFY COLUMN counterparty_account_id CHAR(26) NOT NULL COMMENT '交易对方账户',
    MODIFY COLUMN period_type VARCHAR(8) NOT NULL COMMENT '取值或格式：DAY/MONTH',
    MODIFY COLUMN period_start DATE NOT NULL COMMENT '周期起始日期',
    MODIFY COLUMN income_fen BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '来自该对象的收入，单位：分',
    MODIFY COLUMN expense_fen BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '向该对象的支出，单位：分',
    MODIFY COLUMN success_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '成功交易次数',
    MODIFY COLUMN last_success_at DATETIME(3) NULL COMMENT '最近成功交易时间',
    MODIFY COLUMN definition_version INT UNSIGNED NOT NULL COMMENT '口径版本',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近投影时间';

-- 监控指标与统计投影数据库：merchant_business_daily。
ALTER TABLE `metrics_db`.`merchant_business_daily`
    COMMENT = '表名为已部署历史物理名称，实际按普通用户本人收款账户和日期保存动态扫码订单状态、收款方式、退款和净收款投影',
    MODIFY COLUMN merchant_account_id CHAR(26) NOT NULL COMMENT '历史物理字段名，实际为普通用户本人收款账户 ID',
    MODIFY COLUMN stat_date DATE NOT NULL COMMENT '统计日期',
    MODIFY COLUMN definition_version INT UNSIGNED NOT NULL COMMENT '口径版本',
    MODIFY COLUMN success_order_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '成功扫码订单数',
    MODIFY COLUMN failed_order_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已明确失败订单数',
    MODIFY COLUMN processing_order_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当前处理中订单数',
    MODIFY COLUMN success_receipt_fen BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '毛成功收款金额，单位：分',
    MODIFY COLUMN balance_receipt_fen BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '余额支付收款金额，单位：分',
    MODIFY COLUMN credit_receipt_fen BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '信用支付收款金额，单位：分',
    MODIFY COLUMN refund_fen BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '成功经营退款金额，单位：分',
    MODIFY COLUMN net_receipt_fen BIGINT NOT NULL DEFAULT 0 COMMENT '毛收款减退款后的净额，单位：分',
    MODIFY COLUMN quality_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '取值或格式：PENDING/PASSED/FAILED',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近投影时间';

-- 监控指标与统计投影数据库：merchant_reconciliation_daily。
ALTER TABLE `metrics_db`.`merchant_reconciliation_daily`
    COMMENT = '表名为已部署历史物理名称，实际保存普通用户扫码收款日订单净额与账本净额的核对结果',
    MODIFY COLUMN merchant_account_id CHAR(26) NOT NULL COMMENT '历史物理字段名，实际为被对账的普通用户本人收款账户',
    MODIFY COLUMN biz_date DATE NOT NULL COMMENT '对账业务日期',
    MODIFY COLUMN definition_version INT UNSIGNED NOT NULL COMMENT '对账口径版本',
    MODIFY COLUMN successful_order_fen BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '成功支付毛额，单位：分',
    MODIFY COLUMN successful_refund_fen BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '成功经营退款额，单位：分',
    MODIFY COLUMN expected_net_fen BIGINT NOT NULL COMMENT '订单毛额减退款额，单位：分',
    MODIFY COLUMN ledger_net_fen BIGINT NOT NULL COMMENT '账本收款账户净变动，单位：分',
    MODIFY COLUMN diff_fen BIGINT NOT NULL COMMENT '账本净额减预期净额，单位：分',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '取值或格式：PENDING/MATCHED/DIFF/RESOLVED',
    MODIFY COLUMN reconciliation_diff_id CHAR(26) NULL COMMENT '差异证据逻辑引用',
    MODIFY COLUMN checked_at DATETIME(3) NOT NULL COMMENT '对账完成时间';

-- 监控指标与统计投影数据库：metric_definition。
ALTER TABLE `metrics_db`.`metric_definition`
    COMMENT = '版本化保存指标名称、公式、维度和负责团队',
    MODIFY COLUMN metric_code VARCHAR(64) NOT NULL COMMENT '稳定指标编码',
    MODIFY COLUMN version INT UNSIGNED NOT NULL COMMENT '指标口径版本',
    MODIFY COLUMN name VARCHAR(128) NOT NULL COMMENT '指标展示名称',
    MODIFY COLUMN formula TEXT NOT NULL COMMENT '可审计计算公式',
    MODIFY COLUMN dimensions_json JSON NOT NULL COMMENT '允许维度及定义',
    MODIFY COLUMN owner_id CHAR(26) NOT NULL COMMENT '指标负责人或团队 ID',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '取值或格式：DRAFT/ACTIVE/RETIRED',
    MODIFY COLUMN effective_at DATETIME(3) NOT NULL COMMENT '口径生效时间';

-- 监控指标与统计投影数据库：minute_metric。
ALTER TABLE `metrics_db`.`minute_metric`
    COMMENT = '保存实时看板使用的分钟级指标桶',
    MODIFY COLUMN metric_code VARCHAR(64) NOT NULL COMMENT '指标编码',
    MODIFY COLUMN bucket_at DATETIME(3) NOT NULL COMMENT '分钟桶起始时间',
    MODIFY COLUMN dimension_hash BINARY(32) NOT NULL COMMENT '规范化维度摘要',
    MODIFY COLUMN definition_version INT UNSIGNED NOT NULL COMMENT '指标口径版本',
    MODIFY COLUMN dimensions_json JSON NOT NULL COMMENT '脱敏维度值',
    MODIFY COLUMN value_decimal DECIMAL(24,6) NOT NULL COMMENT '指标数值',
    MODIFY COLUMN quality_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '取值或格式：PENDING/PASSED/FAILED',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近聚合时间';

-- 监控指标与统计投影数据库：daily_metric。
ALTER TABLE `metrics_db`.`daily_metric`
    COMMENT = '保存 T+1 和趋势查询使用的日级通用指标',
    MODIFY COLUMN metric_code VARCHAR(64) NOT NULL COMMENT '指标编码',
    MODIFY COLUMN business_date DATE NOT NULL COMMENT '指标业务日期',
    MODIFY COLUMN dimension_hash BINARY(32) NOT NULL COMMENT '规范化维度摘要',
    MODIFY COLUMN definition_version INT UNSIGNED NOT NULL COMMENT '指标口径版本',
    MODIFY COLUMN dimensions_json JSON NOT NULL COMMENT '脱敏维度值',
    MODIFY COLUMN value_decimal DECIMAL(24,6) NOT NULL COMMENT '日指标数值',
    MODIFY COLUMN quality_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '取值或格式：PENDING/PASSED/FAILED',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近计算时间';

-- 监控指标与统计投影数据库：quality_result。
ALTER TABLE `metrics_db`.`quality_result`
    COMMENT = '保存每个数据任务、日期和质量规则的执行证据与门禁结果',
    MODIFY COLUMN result_id CHAR(26) NOT NULL COMMENT '质量检查结果 ID',
    MODIFY COLUMN task_code VARCHAR(64) NOT NULL COMMENT '数据任务编码',
    MODIFY COLUMN data_date DATE NOT NULL COMMENT '被检查数据日期',
    MODIFY COLUMN rule_code VARCHAR(64) NOT NULL COMMENT '质量规则编码',
    MODIFY COLUMN status VARCHAR(16) NOT NULL COMMENT '取值或格式：PASSED/FAILED',
    MODIFY COLUMN expected_value DECIMAL(24,6) NULL COMMENT '预期值',
    MODIFY COLUMN actual_value DECIMAL(24,6) NULL COMMENT '实际值',
    MODIFY COLUMN evidence_json JSON NOT NULL COMMENT '差异样本和链路证据',
    MODIFY COLUMN checked_at DATETIME(3) NOT NULL COMMENT '检查完成时间';

-- 监控指标与统计投影数据库：monitor_alert。
ALTER TABLE `metrics_db`.`monitor_alert`
    COMMENT = '保存监控告警、证据、负责人和完整处置生命周期',
    MODIFY COLUMN alert_id CHAR(26) NOT NULL COMMENT '告警 ID',
    MODIFY COLUMN rule_code VARCHAR(64) NOT NULL COMMENT '触发规则编码',
    MODIFY COLUMN severity VARCHAR(8) NOT NULL COMMENT '取值或格式：P0/P1/P2',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT '取值或格式：OPEN/ACKNOWLEDGED/RESOLVED/CLOSED',
    MODIFY COLUMN subject_id VARCHAR(128) NOT NULL COMMENT '告警主体 ID',
    MODIFY COLUMN evidence_json JSON NOT NULL COMMENT '指标、交易和 Trace 证据',
    MODIFY COLUMN assignee_id CHAR(26) NULL COMMENT '当前处理人',
    MODIFY COLUMN opened_at DATETIME(3) NOT NULL COMMENT '告警打开时间',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL COMMENT '最近处置时间',
    MODIFY COLUMN closed_at DATETIME(3) NULL COMMENT '人工关闭时间';

-- 验证结果必须均为 0；否则说明仍有表或字段未补充中文注释。
SELECT COUNT(*) AS missing_table_comment_count
FROM information_schema.tables
WHERE table_schema IN ('account_db', 'agent_db', 'business_db', 'ledger_db', 'metrics_db', 'user_db')
  AND table_type = 'BASE TABLE'
  AND table_comment = '';

SELECT COUNT(*) AS missing_column_comment_count
FROM information_schema.columns
WHERE table_schema IN ('account_db', 'agent_db', 'business_db', 'ledger_db', 'metrics_db', 'user_db')
  AND column_comment = '';
