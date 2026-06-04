package com.bishe.server.ai.interview;

import com.bishe.server.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文本面试仓储在 PostgreSQL 下的最小验证。
 */
@DataJpaTest
@Import(AiInterviewRepository.class)
class AiInterviewJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final long USER_ID = 941L;

    @Autowired
    private AiInterviewRepository aiInterviewRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        insertUser();
        jdbcTemplate.update(
                "DELETE FROM interview_messages WHERE session_pk IN (SELECT id FROM interview_sessions WHERE student_user_id = ?)",
                USER_ID
        );
        jdbcTemplate.update("DELETE FROM interview_sessions WHERE student_user_id = ?", USER_ID);
    }

    @Test
    void aiInterviewRepositoryShouldCreatePersistMessagesAndSummaryOnPostgres() {
        aiInterviewRepository.createSession(
                "is_pg_001",
                USER_ID,
                "Backend Engineer",
                "INTERVIEW_TEXT",
                "{\"resume\":true}",
                "{\"answerMode\":\"LIVE\"}",
                8,
                12,
                5
        );

        AiInterviewRepository.InterviewSessionRow session = aiInterviewRepository.findSessionBySessionId(USER_ID, "is_pg_001")
                .orElseThrow();
        assertThat(session.id()).isPositive();
        assertThat(session.status()).isEqualTo("ACTIVE");
        assertThat(session.replyRoundLimit()).isEqualTo(8);
        assertThat(session.replyRoundUsed()).isZero();
        assertThat(session.summaryGenerated()).isFalse();
        assertThat(session.createdAt()).isNotNull();
        assertThat(session.updatedAt()).isNotNull();

        aiInterviewRepository.insertMessage(
                session.id(),
                "ASSISTANT",
                "先介绍一下你的项目背景。",
                "开场稳定",
                88,
                null
        );
        aiInterviewRepository.insertMessage(
                session.id(),
                "USER",
                "我负责订单中心改造，重点做了缓存治理和慢 SQL 优化。",
                null,
                92,
                "voice/is_pg_001/user-1.webm"
        );
        aiInterviewRepository.incrementReplyRoundUsed(session.id());
        aiInterviewRepository.setReplyRoundUsed(session.id(), 2);

        assertThat(aiInterviewRepository.findMessages(session.id()))
                .hasSize(2)
                .satisfies(messages -> {
                    assertThat(messages.getFirst().senderRole()).isEqualTo("ASSISTANT");
                    assertThat(messages.getFirst().coachFeedback()).isEqualTo("开场稳定");
                    assertThat(messages.getLast().audioObjectKey()).isEqualTo("voice/is_pg_001/user-1.webm");
                });

        aiInterviewRepository.markSummaryGenerated(
                session.id(),
                86,
                "[\"结构清晰\"]",
                "[\"量化细节不足\"]",
                "[\"补充性能指标\"]",
                "mock-provider",
                "mock-model",
                3210,
                "MANUAL_SUMMARY",
                true
        );

        assertThat(aiInterviewRepository.findLatestCompletedSummary(USER_ID))
                .isPresent()
                .get()
                .satisfies(summary -> {
                    assertThat(summary.id()).isEqualTo(session.id());
                    assertThat(summary.status()).isEqualTo("COMPLETED");
                    assertThat(summary.summaryGenerated()).isTrue();
                    assertThat(summary.replyRoundUsed()).isEqualTo(2);
                    assertThat(summary.summaryOverallScore()).isEqualTo(86);
                    assertThat(summary.summaryProvider()).isEqualTo("mock-provider");
                    assertThat(summary.summaryModel()).isEqualTo("mock-model");
                    assertThat(summary.finishReason()).isEqualTo("MANUAL_SUMMARY");
                    assertThat(summary.endedByAi()).isTrue();
                    assertThat(summary.summaryGeneratedAt()).isNotNull();
                });
    }

    @Test
    void aiInterviewRepositoryShouldDeleteMessagesAndSoftDeleteSessionOnPostgres() {
        aiInterviewRepository.createSession(
                "is_pg_002",
                USER_ID,
                "Java Engineer",
                "INTERVIEW_VOICE",
                null,
                "{\"answerMode\":\"VOICE\"}",
                6,
                0,
                3
        );

        AiInterviewRepository.InterviewSessionRow session = aiInterviewRepository.findSessionBySessionId(USER_ID, "is_pg_002")
                .orElseThrow();

        aiInterviewRepository.insertMessage(session.id(), "USER", "这是一条语音转写内容。", 75, "voice/is_pg_002/user-1.webm");
        assertThat(aiInterviewRepository.findMessages(session.id())).hasSize(1);

        aiInterviewRepository.deleteMessages(session.id());
        assertThat(aiInterviewRepository.findMessages(session.id())).isEmpty();

        assertThat(aiInterviewRepository.softDeleteSession(USER_ID, "is_pg_002")).isTrue();
        assertThat(aiInterviewRepository.softDeleteSession(USER_ID, "is_pg_002")).isFalse();
        assertThat(aiInterviewRepository.findSessionBySessionId(USER_ID, "is_pg_002")).isEmpty();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM interview_sessions WHERE session_id = ?",
                String.class,
                "is_pg_002"
        )).isEqualTo("DELETED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT user_deleted_at FROM interview_sessions WHERE session_id = ?",
                java.sql.Timestamp.class,
                "is_pg_002"
        )).isNotNull();
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
                "ai-interview-pg-%s@example.com".formatted(USER_ID),
                "$2a$10$seed",
                "PG Ai Interview",
                "PG Ai Interview"
        );
    }
}
