package com.minialalipay.account.infrastructure;

import com.minialalipay.account.domain.credit.CreditJobRun;
import com.minialalipay.account.domain.credit.CreditJobRunRepository;
import com.minialalipay.account.domain.credit.CreditJobTriggerType;
import com.minialalipay.account.domain.credit.CreditJobType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 校验信用任务执行记录仓储与 {@code ledger_db.credit_job_run} 表结构一致：
 * 全字段（含 run_id/completed_at/error_code/trigger_type/request_digest）往返持久化、
 * 唯一键幂等续跑与乐观锁 CAS 冲突处理。
 */
@SpringBootTest(properties = {"spring.cloud.nacos.discovery.enabled=false"})
@Transactional
class CreditJobRunRepositoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-07T08:00:00Z");
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 7, 1);
    /** 与表列 CHAR(26) 等长的测试标识。 */
    private static final String RUN_ID_1 = idOf("run-");
    private static final String RUN_ID_2 = idOf("run1-");
    private static final String RUN_ID_3 = idOf("run2-");
    private static final String USER_ID = idOf("user-");
    private static final String CURSOR_ID = idOf("cursor-");

    private static String idOf(String prefix) {
        return prefix + "0".repeat(26 - prefix.length());
    }

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private CreditJobRunRepository jobRunRepository;

    @BeforeEach
    void createTables() {
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS ledger_db");
        jdbcTemplate.execute("DROP TABLE IF EXISTS ledger_db.credit_job_run");
        // 与 V202608050900__create_credit_tables.sql 中的 credit_job_run 结构保持一致。
        jdbcTemplate.execute("CREATE TABLE ledger_db.credit_job_run ("
                + "run_id CHAR(26) PRIMARY KEY, "
                + "job_type VARCHAR(16) NOT NULL, "
                + "business_date DATE NOT NULL, "
                + "status VARCHAR(16) NOT NULL DEFAULT 'PENDING', "
                + "cursor_credit_account_id CHAR(26) NULL, "
                + "trigger_type VARCHAR(16) NOT NULL, "
                + "triggered_by_user_id CHAR(26) NULL, "
                + "request_digest BINARY(32) NOT NULL, "
                + "retry_count INT NOT NULL DEFAULT 0, "
                + "error_code VARCHAR(32) NULL, "
                + "version BIGINT NOT NULL DEFAULT 0, "
                + "started_at TIMESTAMP NULL, "
                + "completed_at TIMESTAMP NULL, "
                + "created_at TIMESTAMP NOT NULL, "
                + "updated_at TIMESTAMP NOT NULL, "
                + "UNIQUE uk_credit_job_type_date (job_type, business_date))");
    }

    /** 与 CreditJobService.requestDigestOf 相同的规范化摘要。 */
    private byte[] digest() {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(("STATEMENT|" + BUSINESS_DATE + "|MANUAL|" + USER_ID)
                            .getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }

    @Test
    void 全字段往返持久化且CAS更新递增版本() {
        byte[] requestDigest = digest();
        CreditJobRun jobRun = new CreditJobRun(
                RUN_ID_1, CreditJobType.STATEMENT, BUSINESS_DATE,
                CreditJobTriggerType.MANUAL, USER_ID, requestDigest, NOW);

        jobRunRepository.save(jobRun);
        jobRun.start(NOW);
        jobRunRepository.save(jobRun);
        jobRun.succeed(CURSOR_ID, NOW.plusSeconds(2));
        jobRunRepository.save(jobRun);

        // 唯一键保证重复保存走更新而非重复插入。
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_db.credit_job_run", Integer.class)).isEqualTo(1);

        CreditJobRun reloaded = jobRunRepository
                .findByJobTypeAndBusinessDate(CreditJobType.STATEMENT.name(), BUSINESS_DATE)
                .orElseThrow();
        assertThat(reloaded.getStatus().name()).isEqualTo("SUCCESS");
        assertThat(reloaded.getStartedAt()).isEqualTo(NOW);
        assertThat(reloaded.getCompletedAt()).isEqualTo(NOW.plusSeconds(2));
        assertThat(reloaded.getCursorCreditAccountId()).isEqualTo(CURSOR_ID);
        assertThat(reloaded.getTriggerType()).isEqualTo(CreditJobTriggerType.MANUAL);
        assertThat(reloaded.getTriggeredByUserId()).isEqualTo(USER_ID);
        assertThat(reloaded.getRequestDigest()).isEqualTo(requestDigest);
        assertThat(reloaded.getRetryCount()).isZero();
        assertThat(reloaded.getErrorCode()).isNull();
        assertThat(reloaded.getVersion()).isEqualTo(2L);
    }

    @Test
    void 失败与乐观锁冲突处理() {
        CreditJobRun jobRun = new CreditJobRun(
                RUN_ID_2, CreditJobType.DUE_CHECK, BUSINESS_DATE,
                CreditJobTriggerType.MANUAL, USER_ID, digest(), NOW);
        jobRunRepository.save(jobRun);

        CreditJobRun concurrent = jobRunRepository
                .findByJobTypeAndBusinessDate(CreditJobType.DUE_CHECK.name(), BUSINESS_DATE)
                .orElseThrow();
        jobRun.start(NOW);
        jobRunRepository.save(jobRun); // 版本 0 -> 1

        concurrent.start(NOW.plusSeconds(1));
        assertThatThrownBy(() -> jobRunRepository.save(concurrent))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("乐观锁冲突");
    }

    @Test
    void 失败后重跑重置状态并递增重试次数() {
        CreditJobRun jobRun = new CreditJobRun(
                RUN_ID_3, CreditJobType.STATEMENT, BUSINESS_DATE,
                CreditJobTriggerType.MANUAL, USER_ID, digest(), NOW);
        jobRun.start(NOW);
        jobRunRepository.save(jobRun);
        jobRun.fail("INTERNAL_ERROR", NOW.plusSeconds(1));
        jobRunRepository.save(jobRun);

        jobRun.restart(NOW.plusSeconds(5));
        jobRunRepository.save(jobRun);

        CreditJobRun reloaded = jobRunRepository
                .findByJobTypeAndBusinessDate(CreditJobType.STATEMENT.name(), BUSINESS_DATE)
                .orElseThrow();
        assertThat(reloaded.getStatus().name()).isEqualTo("RUNNING");
        assertThat(reloaded.getStartedAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(reloaded.getCompletedAt()).isNull();
        assertThat(reloaded.getErrorCode()).isNull();
        assertThat(reloaded.getRetryCount()).isEqualTo(1);

        // 重跑成功后进入终态，仍保持同业务日期单条记录。
        jobRun.succeed(null, NOW.plusSeconds(6));
        jobRunRepository.save(jobRun);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_db.credit_job_run", Integer.class)).isEqualTo(1);
        assertThat(jobRunRepository
                .findByJobTypeAndBusinessDate(CreditJobType.STATEMENT.name(), BUSINESS_DATE)
                .orElseThrow().getStatus().name()).isEqualTo("SUCCESS");
    }

    @Test
    void 已成功任务不可重跑() {
        CreditJobRun jobRun = new CreditJobRun(
                RUN_ID_3, CreditJobType.STATEMENT, BUSINESS_DATE,
                CreditJobTriggerType.MANUAL, USER_ID, digest(), NOW);
        jobRun.start(NOW);
        jobRunRepository.save(jobRun);
        jobRun.succeed(null, NOW.plusSeconds(1));
        jobRunRepository.save(jobRun);

        assertThatThrownBy(() -> jobRun.restart(NOW.plusSeconds(5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不可重跑");
    }
}
