package com.bishe.server.ai.history;

import com.bishe.server.ai.interview.AiInterviewRepository;
import com.bishe.server.ai.quota.AiQuotaRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 历史仓储在 PostgreSQL 下的最小验证。
 */
@DataJpaTest
@Import({
        AiHistoryRepository.class,
        AiQuotaRepository.class,
        AiInterviewRepository.class
})
class AiHistoryJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final long USER_ID = 951L;

    @Autowired
    private AiHistoryRepository aiHistoryRepository;

    @Autowired
    private AiQuotaRepository aiQuotaRepository;

    @Autowired
    private AiInterviewRepository aiInterviewRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        insertUser();
        jdbcTemplate.update("DELETE FROM interview_messages WHERE session_pk IN (SELECT id FROM interview_sessions WHERE student_user_id = ?)", USER_ID);
        jdbcTemplate.update("DELETE FROM interview_sessions WHERE student_user_id = ?", USER_ID);
        jdbcTemplate.update("DELETE FROM ai_call_logs WHERE user_id = ?", USER_ID);
    }

    @Test
    void aiHistoryRepositoryShouldMergePaginateDetailAndSoftDeleteOnPostgres() {
        long resumeRecordId = aiQuotaRepository.insertCallLog(
                "ai-history-pg-001",
                USER_ID,
                "RESUME",
                "RESUME_OPTIMIZE",
                "mock-provider",
                "mock-model",
                "resume-route",
                "resume-policy",
                321,
                "SUCCESS",
                null,
                120,
                80,
                200,
                0,
                null,
                null,
                null,
                new BigDecimal("0.123456"),
                10,
                1,
                "简历优化总结",
                "{\"scoreLabel\":\"优秀\"}",
                "FREE"
        );

        aiInterviewRepository.createSession(
                "is_pg_history_001",
                USER_ID,
                "Backend Engineer",
                "INTERVIEW_TEXT",
                "{\"resume\":true}",
                "{\"difficulty\":\"MEDIUM\"}",
                8,
                15,
                5
        );
        AiInterviewRepository.InterviewSessionRow session = aiInterviewRepository.findSessionBySessionId(USER_ID, "is_pg_history_001")
                .orElseThrow();
        aiInterviewRepository.markSummaryGenerated(
                session.id(),
                88,
                "[\"结构清晰\"]",
                "[\"细节不足\"]",
                "[\"补充数据指标\"]",
                "mock-provider",
                "mock-model",
                4567,
                "MANUAL_SUMMARY",
                false
        );

        updateCreatedAt("ai_call_logs", resumeRecordId, LocalDate.of(2026, 4, 16), 9, 30);
        updateCreatedAt("interview_sessions", session.id(), LocalDate.of(2026, 4, 16), 10, 45);

        List<AiHistoryRepository.AiHistoryRow> history = aiHistoryRepository.findHistory(USER_ID, null, 10, 0);
        assertThat(history).hasSize(2);
        assertThat(history.getFirst().taskType()).isEqualTo("INTERVIEW_TEXT");
        assertThat(history.getFirst().sessionId()).isEqualTo("is_pg_history_001");
        assertThat(history.getFirst().summary()).contains("文本面试").contains("已完成");
        assertThat(history.getLast().id()).isEqualTo(resumeRecordId);
        assertThat(history.getLast().taskType()).isEqualTo("RESUME");
        assertThat(history.getLast().summary()).isEqualTo("简历优化总结");

        assertThat(aiHistoryRepository.findHistory(USER_ID, null, 1, 1))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.id()).isEqualTo(resumeRecordId);
                    assertThat(row.taskType()).isEqualTo("RESUME");
                });

        assertThat(aiHistoryRepository.countHistory(USER_ID, null)).isEqualTo(2);
        assertThat(aiHistoryRepository.countHistory(USER_ID, "resume")).isEqualTo(1);
        assertThat(aiHistoryRepository.countHistory(USER_ID, "INTERVIEW_TEXT")).isEqualTo(1);
        assertThat(aiHistoryRepository.countHistory(USER_ID, "UNKNOWN")).isZero();

        assertThat(aiHistoryRepository.findResumeDetail(USER_ID, resumeRecordId))
                .isPresent()
                .get()
                .satisfies(detail -> {
                    assertThat(detail.resultSummary()).isEqualTo("简历优化总结");
                    assertThat(detail.resultPayloadJson()).contains("scoreLabel");
                    assertThat(detail.chargedPoints()).isEqualTo(10);
                    assertThat(detail.provider()).isEqualTo("mock-provider");
                    assertThat(detail.model()).isEqualTo("mock-model");
                    assertThat(detail.latencyMs()).isEqualTo(321L);
                    assertThat(detail.createdAt()).isNotNull();
                });

        assertThat(aiHistoryRepository.findLatestResumeDetail(USER_ID))
                .isPresent()
                .get()
                .extracting(AiHistoryRepository.ResumeHistoryDetailRow::id)
                .isEqualTo(resumeRecordId);

        assertThat(aiHistoryRepository.softDeleteResumeRecord(USER_ID, resumeRecordId)).isTrue();
        assertThat(aiHistoryRepository.softDeleteResumeRecord(USER_ID, resumeRecordId)).isFalse();
        assertThat(aiHistoryRepository.findLatestResumeDetail(USER_ID)).isEmpty();
        assertThat(aiHistoryRepository.findHistory(USER_ID, null, 10, 0))
                .singleElement()
                .extracting(AiHistoryRepository.AiHistoryRow::taskType)
                .isEqualTo("INTERVIEW_TEXT");
        assertThat(aiHistoryRepository.countHistory(USER_ID, null)).isEqualTo(1);
    }

    private void updateCreatedAt(String tableName, long id, LocalDate date, int hour, int minute) {
        ZoneId zoneId = ZoneId.systemDefault();
        Timestamp timestamp = Timestamp.from(date.atTime(hour, minute).atZone(zoneId).toInstant());
        jdbcTemplate.update(
                "UPDATE " + tableName + " SET created_at = ? WHERE id = ?",
                timestamp,
                id
        );
        if ("interview_sessions".equals(tableName)) {
            jdbcTemplate.update(
                    "UPDATE interview_sessions SET updated_at = ? WHERE id = ?",
                    timestamp,
                    id
            );
        }
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
                "ai-history-pg-%s@example.com".formatted(USER_ID),
                "$2a$10$seed",
                "PG Ai History",
                "PG Ai History"
        );
    }
}
