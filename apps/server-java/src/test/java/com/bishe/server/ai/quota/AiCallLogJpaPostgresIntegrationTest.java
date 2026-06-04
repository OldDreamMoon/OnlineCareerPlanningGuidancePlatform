package com.bishe.server.ai.quota;

import com.bishe.server.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 调用日志仓储在 PostgreSQL 下的最小验证。
 */
@DataJpaTest
@Import(AiQuotaRepository.class)
class AiCallLogJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final long USER_ID = 931L;
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 4, 16);

    @Autowired
    private AiQuotaRepository aiQuotaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        insertUser();
        jdbcTemplate.update("DELETE FROM ai_call_logs WHERE user_id = ?", USER_ID);
    }

    @Test
    void aiQuotaRepositoryShouldInsertCallLogAndSumSuccessfulQuotaWeightByDayOnPostgres() {
        long primarySuccessId = aiQuotaRepository.insertCallLog(
                "ai-call-log-pg-001",
                USER_ID,
                "COMMUNITY_REPLY",
                "COMMUNITY_PRE_ANSWER",
                "mock-provider",
                "mock-model",
                "route-community",
                "policy-community",
                123,
                "SUCCESS",
                null,
                120,
                80,
                200,
                15,
                "medium",
                32,
                "BALANCED",
                new BigDecimal("0.123456"),
                2,
                2,
                "summary-1",
                "{\"trace\":1}",
                "FREE"
        );
        long secondarySuccessId = aiQuotaRepository.insertCallLog(
                "ai-call-log-pg-002",
                USER_ID,
                "COMMUNITY_REPLY",
                "COMMUNITY_PRE_ANSWER",
                "mock-provider",
                "mock-model",
                "route-community",
                "policy-community",
                150,
                "SUCCESS",
                null,
                60,
                40,
                100,
                10,
                "low",
                16,
                "LIGHT",
                new BigDecimal("0.010000"),
                1,
                3,
                "summary-2",
                "{\"trace\":2}",
                "FREE"
        );
        long otherSceneSuccessId = aiQuotaRepository.insertCallLog(
                "ai-call-log-pg-003",
                USER_ID,
                "COMMUNITY_REPLY",
                "MENTOR_PREP_SHEET_GENERATE",
                "mock-provider",
                "mock-model",
                "route-mentor",
                "policy-mentor",
                90,
                "SUCCESS",
                null,
                50,
                30,
                80,
                8,
                null,
                null,
                null,
                new BigDecimal("0.020000"),
                0,
                4,
                "summary-3",
                "{\"trace\":3}",
                "FREE"
        );
        long failedId = aiQuotaRepository.insertCallLog(
                "ai-call-log-pg-004",
                USER_ID,
                "COMMUNITY_REPLY",
                "COMMUNITY_PRE_ANSWER",
                "mock-provider",
                "mock-model",
                "route-community",
                "policy-community",
                40,
                "FAILED",
                "AI-9999",
                0,
                0,
                0,
                0,
                null,
                null,
                null,
                BigDecimal.ZERO,
                0,
                99,
                null,
                null,
                "FREE"
        );
        long otherTaskSuccessId = aiQuotaRepository.insertCallLog(
                "ai-call-log-pg-005",
                USER_ID,
                "RESUME",
                "COMMUNITY_PRE_ANSWER",
                "mock-provider",
                "mock-model",
                "route-resume",
                "policy-resume",
                75,
                "SUCCESS",
                null,
                20,
                10,
                30,
                4,
                null,
                null,
                null,
                new BigDecimal("0.030000"),
                0,
                5,
                null,
                null,
                "FREE"
        );
        long previousDaySuccessId = aiQuotaRepository.insertCallLog(
                "ai-call-log-pg-006",
                USER_ID,
                "COMMUNITY_REPLY",
                "COMMUNITY_PRE_ANSWER",
                "mock-provider",
                "mock-model",
                "route-community",
                "policy-community",
                85,
                "SUCCESS",
                null,
                20,
                10,
                30,
                5,
                null,
                null,
                null,
                new BigDecimal("0.040000"),
                0,
                6,
                null,
                null,
                "FREE"
        );

        updateCreatedAt(primarySuccessId, TARGET_DATE, 9, 15);
        updateCreatedAt(secondarySuccessId, TARGET_DATE, 10, 45);
        updateCreatedAt(otherSceneSuccessId, TARGET_DATE, 11, 0);
        updateCreatedAt(failedId, TARGET_DATE, 12, 0);
        updateCreatedAt(otherTaskSuccessId, TARGET_DATE, 13, 30);
        updateCreatedAt(previousDaySuccessId, TARGET_DATE.minusDays(1), 20, 0);

        assertThat(primarySuccessId).isPositive();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT scene_code FROM ai_call_logs WHERE id = ?",
                String.class,
                primarySuccessId
        )).isEqualTo("COMMUNITY_PRE_ANSWER");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT estimated_cost FROM ai_call_logs WHERE id = ?",
                BigDecimal.class,
                primarySuccessId
        )).isEqualByComparingTo("0.123456");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT user_tier FROM ai_call_logs WHERE id = ?",
                String.class,
                primarySuccessId
        )).isEqualTo("FREE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT created_at FROM ai_call_logs WHERE id = ?",
                Timestamp.class,
                primarySuccessId
        )).isNotNull();

        assertThat(aiQuotaRepository.countSuccessfulCallsToday(
                USER_ID,
                "COMMUNITY_REPLY",
                "community_pre_answer",
                TARGET_DATE
        )).isEqualTo(5);
        assertThat(aiQuotaRepository.countSuccessfulCallsToday(
                USER_ID,
                "COMMUNITY_REPLY",
                null,
                TARGET_DATE
        )).isEqualTo(9);
    }

    private void updateCreatedAt(long id, LocalDate date, int hour, int minute) {
        ZoneId zoneId = ZoneId.systemDefault();
        jdbcTemplate.update(
                "UPDATE ai_call_logs SET created_at = ? WHERE id = ?",
                Timestamp.from(date.atTime(hour, minute).atZone(zoneId).toInstant()),
                id
        );
    }

    private void insertUser() {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ?",
                Integer.class,
                USER_ID
        );
        if (existing != null && existing > 0) {
            return;
        }

        jdbcTemplate.update(
                """
                INSERT INTO users(
                    id,
                    email,
                    password_hash,
                    role,
                    tier,
                    status,
                    display_name,
                    real_name,
                    is_deleted,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, 'STUDENT', 'FREE', 'ACTIVE', ?, ?, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                USER_ID,
                "ai-call-log-pg-%s@example.com".formatted(USER_ID),
                "$2a$10$seed",
                "PG Ai Call Log",
                "PG Ai Call Log"
        );
    }
}
