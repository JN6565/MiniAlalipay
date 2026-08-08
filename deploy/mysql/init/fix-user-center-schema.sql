-- user-center 新增身份绑定字段（手动执行版本）。
--
-- 如果 user-center 启动时 Flyway 自动迁移失败，可手动在远程 MySQL 执行本脚本，
-- 然后重新启动 user-center。
--
-- 注意：如果 Flyway 已经成功执行过 V202608081500 迁移，则无需再执行本脚本。
-- 可通过以下 SQL 检查：
--   SELECT * FROM user_db.flyway_schema_history WHERE version = '202608081500';

-- 检查列是否已存在，避免重复添加
SET @sql_id_card = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA='user_db' AND TABLE_NAME='app_user' AND COLUMN_NAME='id_card') = 0,
    'ALTER TABLE user_db.app_user ADD COLUMN id_card VARCHAR(32) NULL COMMENT ''身份证号掩码，绑定身份后保存，如 3301**********1234''',
    'SELECT 1');
PREPARE s FROM @sql_id_card; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql_id_card_hash = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA='user_db' AND TABLE_NAME='app_user' AND COLUMN_NAME='id_card_hash') = 0,
    'ALTER TABLE user_db.app_user ADD COLUMN id_card_hash BINARY(32) NULL COMMENT ''身份证号明文哈希，用于绑卡时三要素交叉比对''',
    'SELECT 1');
PREPARE s FROM @sql_id_card_hash; EXECUTE s; DEALLOCATE PREPARE s;

-- 检查 account_db.bank_card_registration 表是否存在
SET @sql_bcr = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
     WHERE TABLE_SCHEMA='account_db' AND TABLE_NAME='bank_card_registration') = 0,
    'CREATE TABLE account_db.bank_card_registration (
        registration_id CHAR(26) NOT NULL COMMENT ''注册记录 ID'',
        user_id CHAR(26) NOT NULL COMMENT ''注册操作人'',
        bank_code VARCHAR(32) NOT NULL COMMENT ''银行编码'',
        bank_name VARCHAR(64) NOT NULL COMMENT ''银行名称'',
        card_type VARCHAR(16) NOT NULL COMMENT ''DEBIT/CREDIT'',
        card_number VARCHAR(19) NOT NULL COMMENT ''自动生成的完整卡号'',
        card_bin CHAR(6) NOT NULL COMMENT ''BIN 前 6 位'',
        card_last4 CHAR(4) NOT NULL COMMENT ''尾号后 4 位'',
        holder_name VARCHAR(32) NOT NULL COMMENT ''持卡人姓名明文'',
        id_card_hash BINARY(32) NOT NULL COMMENT ''身份证号哈希'',
        phone_hash BINARY(32) NOT NULL COMMENT ''手机号哈希'',
        status VARCHAR(16) NOT NULL DEFAULT ''REGISTERED'' COMMENT ''REGISTERED/BOUND'',
        created_at DATETIME(3) NOT NULL,
        PRIMARY KEY (registration_id),
        KEY idx_bcr_user_status (user_id, status),
        KEY idx_bcr_card_number (card_number)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci',
    'SELECT 1');
PREPARE s FROM @sql_bcr; EXECUTE s; DEALLOCATE PREPARE s;

-- 检查 account_db.bank_card 表是否有 registration_id 列
SET @sql_bc_reg = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA='account_db' AND TABLE_NAME='bank_card' AND COLUMN_NAME='registration_id') = 0,
    'ALTER TABLE account_db.bank_card ADD COLUMN registration_id CHAR(26) NULL COMMENT ''来源注册记录 ID''',
    'SELECT 1');
PREPARE s FROM @sql_bc_reg; EXECUTE s; DEALLOCATE PREPARE s;

-- 验证
SELECT 'user_db.app_user id_card columns' AS check_item,
       COUNT(*) AS column_count
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='user_db' AND TABLE_NAME='app_user'
  AND COLUMN_NAME IN ('id_card', 'id_card_hash');

SELECT 'account_db.bank_card_registration' AS check_item,
       COUNT(*) AS table_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA='account_db' AND TABLE_NAME='bank_card_registration';

SELECT 'account_db.bank_card.registration_id' AS check_item,
       COUNT(*) AS column_count
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='account_db' AND TABLE_NAME='bank_card'
  AND COLUMN_NAME = 'registration_id';

-- ============================================================
-- 以下为 Flyway 历史记录修复（必须执行，否则 user-center / account-center 启动失败）
-- ============================================================
-- 背景：列和表已通过上面的脚本手动添加，但 flyway_schema_history 中没有
-- V202608081500 的记录。Flyway 启动时会尝试重新执行迁移，因对象已存在而失败。
-- 现在迁移文件已改为幂等 SQL，但为避免每次启动都重放，仍需插入历史记录。

-- ========== user-center ==========

-- 第一步：查看当前最大 installed_rank
SELECT MAX(installed_rank) AS max_rank FROM user_db.flyway_schema_history;

-- 第二步：将下面 SQL 中的 installed_rank 改为上面查询结果 + 1，然后执行：
INSERT INTO user_db.flyway_schema_history
  (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success)
VALUES
  (20, '202608081500', 'add id card to app user', 'SQL', 'V202608081500__add_id_card_to_app_user.sql',
   0, 'root', NOW(), 0, 1);

-- 第三步：验证
SELECT installed_rank, version, description, success
FROM user_db.flyway_schema_history WHERE version = '202608081500';

-- ========== account-center ==========

-- 第一步：查看当前最大 installed_rank
SELECT MAX(installed_rank) AS max_rank FROM account_db.flyway_schema_history;

-- 第二步：将下面 SQL 中的 installed_rank 改为上面查询结果 + 1，然后执行：
INSERT INTO account_db.flyway_schema_history
  (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success)
VALUES
  (20, '202608081500', 'create bank card registration table', 'SQL', 'V202608081500__create_bank_card_registration_table.sql',
   0, 'root', NOW(), 0, 1);

-- 第三步：验证
SELECT installed_rank, version, description, success
FROM account_db.flyway_schema_history WHERE version = '202608081500';
