-- 修复 business-center Flyway 校验和不匹配。
--
-- 背景：V202608071800、V202608072100、V202608072130 三个迁移文件此前丢失后重建，
-- 本地重建版本的内容与远程数据库中已执行的原始版本不同，导致 Flyway 校验和验证失败。
-- 这三个迁移的 SQL 均为幂等设计（使用 information_schema 条件判断），重新执行不会造成副作用。
--
-- 在远程 MySQL 上执行本脚本后，business-center 即可正常启动。

-- 方案：直接从 flyway_schema_history 中删除这三条记录，
-- 让 Flyway 在下次启动时重新执行这些迁移（SQL 幂等可重放）。
DELETE FROM business_db.flyway_schema_history
WHERE version IN ('202608071800', '202608072100', '202608072130');

-- 验证：删除后应返回 0 行（或只剩你希望保留的记录）
SELECT installed_rank, version, description, checksum
FROM business_db.flyway_schema_history
WHERE version IN ('202608071800', '202608072100', '202608072130');
