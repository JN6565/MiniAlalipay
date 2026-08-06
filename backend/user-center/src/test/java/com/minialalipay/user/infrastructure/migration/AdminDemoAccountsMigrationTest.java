package com.minialalipay.user.infrastructure.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证 B 端演示账号迁移的角色隔离和凭证存储约束。 */
class AdminDemoAccountsMigrationTest {

    private static final String MIGRATION = "/db/migration/V202608061600__seed_admin_demo_accounts.sql";
    private static final String REMOVE_OBSERVER_MIGRATION = "/db/migration/V202608061700__remove_observer_role.sql";

    /** 初始演示账号和后续角色收敛迁移必须保持密码安全及角色清理顺序。 */
    @Test
    void shouldSeedOneActiveAccountForEachAdminRoleWithBcryptCredentials() throws IOException {
        String sql = readMigration(MIGRATION);

        assertTrue(sql.contains("'OPERATOR'"));
        assertTrue(sql.contains("'OBSERVER'"));
        assertTrue(sql.contains("'ADMIN'"));
        assertEquals(3, countMatches(sql, "'ACTIVE'"));
        assertEquals(3, countMatches(sql, "'$2b$12$"));
        assertEquals(3, countMatches(sql, "NULL, 0, 0, NULL, NULL, 0, 0"));
        assertFalse(sql.contains("password ="));
        assertFalse(sql.contains("loginPassword"));

        String removeObserverSql = readMigration(REMOVE_OBSERVER_MIGRATION);
        assertTrue(removeObserverSql.contains("DELETE FROM user_db.role_assignment"));
        assertTrue(removeObserverSql.contains("WHERE role_code = 'OBSERVER'"));
        assertTrue(removeObserverSql.contains("DELETE FROM user_db.credential"));
        assertTrue(removeObserverSql.contains("DELETE FROM user_db.app_user"));
        assertTrue(removeObserverSql.contains("DROP CHECK ck_role_assignment_role"));
        assertTrue(removeObserverSql.contains("role_code IN ('USER', 'MERCHANT', 'OPERATOR', 'ADMIN')"));
    }

    private String readMigration(String migration) throws IOException {
        var stream = getClass().getResourceAsStream(migration);
        assertNotNull(stream, "Flyway 演示账号迁移必须存在");
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private int countMatches(String content, String literal) {
        Matcher matcher = Pattern.compile(Pattern.quote(literal)).matcher(content);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
