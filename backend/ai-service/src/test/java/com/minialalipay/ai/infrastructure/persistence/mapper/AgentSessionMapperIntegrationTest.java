package com.minialalipay.ai.infrastructure.persistence.mapper;

import com.minialalipay.ai.infrastructure.persistence.po.AgentSessionPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgentSessionMapper 集成测试。
 *
 * <p>使用 H2 内存库验证 Mapper SQL 的正确性：
 * 插入、按 ID 查询、活跃会话查询和 CAS 更新。</p>
 */
@MybatisTest
@ActiveProfiles("test")
@Sql(scripts = "classpath:db/test-schema.sql",
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class AgentSessionMapperIntegrationTest {

    @Autowired
    private AgentSessionMapper mapper;

    private static final String SESSION_ID = "01J5Q000000000000000000001";
    private static final String USER_ID = "01J5Q000000000000000000002";
    private static final Instant NOW = Instant.parse("2026-08-04T06:00:00Z");

    @BeforeEach
    void setUp() {
        // 每个测试前插入一条基准会话
        AgentSessionPO po = new AgentSessionPO(
                SESSION_ID, USER_ID, null, null, null,
                "ACTIVE", 0L, NOW, NOW);
        mapper.insert(po);
    }

    @Test
    void shouldInsertAndFindSession() {
        AgentSessionPO found = mapper.findBySessionId(SESSION_ID);

        assertThat(found).isNotNull();
        assertThat(found.getSessionId()).isEqualTo(SESSION_ID);
        assertThat(found.getUserId()).isEqualTo(USER_ID);
        assertThat(found.getStatus()).isEqualTo("ACTIVE");
        assertThat(found.getVersion()).isZero();
    }

    @Test
    void shouldReturnNullForNonExistentSession() {
        AgentSessionPO found = mapper.findBySessionId("NONEXIST00000000000000000000");
        assertThat(found).isNull();
    }

    @Test
    void shouldFindActiveSessionsByUserId() {
        // 插入第二活跃会话
        AgentSessionPO po2 = new AgentSessionPO(
                "01J5Q000000000000000000003", USER_ID, null, null, null,
                "ACTIVE", 0L, NOW.plusSeconds(60), NOW);
        mapper.insert(po2);

        List<AgentSessionPO> active = mapper.findActiveByUserId(USER_ID);
        assertThat(active).hasSize(2);
        // 按 last_active_at DESC 排序
        assertThat(active.get(0).getSessionId()).isEqualTo("01J5Q000000000000000000003");
    }

    @Test
    void shouldExcludeNonActiveSessionsFromActiveQuery() {
        // 插入已关闭会话
        AgentSessionPO closed = new AgentSessionPO(
                "01J5Q000000000000000000004", USER_ID, null, null, null,
                "CLOSED", 0L, NOW, NOW);
        mapper.insert(closed);

        List<AgentSessionPO> active = mapper.findActiveByUserId(USER_ID);
        assertThat(active).hasSize(1);
        assertThat(active.get(0).getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void shouldUpdateSessionByCas() {
        AgentSessionPO update = new AgentSessionPO(
                SESSION_ID, USER_ID, "压缩摘要", null, "{}",
                "ACTIVE", 0L, NOW.plusSeconds(120), NOW);

        int rows = mapper.updateByCas(update);
        assertThat(rows).isEqualTo(1);

        AgentSessionPO found = mapper.findBySessionId(SESSION_ID);
        assertThat(found.getSummary()).isEqualTo("压缩摘要");
        assertThat(found.getVersion()).isEqualTo(1L);
    }

    @Test
    void shouldFailCasUpdateOnVersionMismatch() {
        AgentSessionPO update = new AgentSessionPO(
                SESSION_ID, USER_ID, "过期版本", null, null,
                "ACTIVE", 999L, NOW, NOW); // 版本号不匹配

        int rows = mapper.updateByCas(update);
        assertThat(rows).isZero();
    }
}
