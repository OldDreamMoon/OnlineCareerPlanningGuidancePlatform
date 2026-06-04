package com.bishe.server.mentor;

import com.bishe.server.mentor.repository.MentorRecommendationRepository;
import com.bishe.server.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(MentorRecommendationRepository.class)
class MentorRecommendationJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final long STUDENT_USER_ID = 4301L;
    private static final long MENTOR_USER_ID = 4302L;

    @Autowired
    private MentorRecommendationRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM mentor_recommendation_events");
        jdbcTemplate.update("DELETE FROM mentor_recommendation_runs");
        jdbcTemplate.update("DELETE FROM recommendation_embedding_vectors WHERE entity_id IN (?, ?)", STUDENT_USER_ID, MENTOR_USER_ID);
        jdbcTemplate.update("DELETE FROM mentor_recommendation_snapshots WHERE mentor_user_id = ?", MENTOR_USER_ID);
        jdbcTemplate.update("DELETE FROM student_recommendation_snapshots WHERE student_user_id = ?", STUDENT_USER_ID);
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", STUDENT_USER_ID, MENTOR_USER_ID);
    }

    @Test
    void repositoryShouldUpsertRecommendationArtifactsOnPostgres() {
        insertUser(STUDENT_USER_ID, "STUDENT", "pg-recommend-student@example.com", "PgRecommendStudent");
        insertUser(MENTOR_USER_ID, "MENTOR", "pg-recommend-mentor@example.com", "PgRecommendMentor");

        repository.saveStudentSnapshot(new MentorRecommendationRepository.StudentRecommendationSnapshotCommand(
                STUDENT_USER_ID,
                "student snapshot one",
                "前端开发工程师",
                "[\"React\"]",
                "[\"表达\"]",
                101L,
                "前端开发工程师",
                "summary-one",
                "[\"优化项目\"]",
                "session-1",
                "前端开发工程师",
                "[\"表达弱项\"]",
                "[\"补强\"]",
                "{\"portraitSignalLevel\":\"HIGH\"}",
                "student-hash-1"
        ));
        repository.saveStudentSnapshot(new MentorRecommendationRepository.StudentRecommendationSnapshotCommand(
                STUDENT_USER_ID,
                "student snapshot two",
                "全栈开发工程师",
                "[\"React\",\"TypeScript\"]",
                "[\"表达\",\"项目\"]",
                102L,
                "全栈开发工程师",
                "summary-two",
                "[\"强化项目量化\"]",
                "session-2",
                "全栈开发工程师",
                "[\"沟通弱项\"]",
                "[\"补强项目表达\"]",
                "{\"portraitSignalLevel\":\"MEDIUM\"}",
                "student-hash-2"
        ));

        Integer studentSnapshotCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM student_recommendation_snapshots WHERE student_user_id = ?",
                Integer.class,
                STUDENT_USER_ID
        );
        String studentHash = jdbcTemplate.queryForObject(
                "SELECT content_hash FROM student_recommendation_snapshots WHERE student_user_id = ?",
                String.class,
                STUDENT_USER_ID
        );
        assertThat(studentSnapshotCount).isEqualTo(1);
        assertThat(normalizeFixedChar(studentHash)).isEqualTo("student-hash-2");

        repository.saveMentorSnapshot(new MentorRecommendationRepository.MentorRecommendationSnapshotCommand(
                MENTOR_USER_ID,
                "mentor snapshot one",
                "[\"前端工程化\"]",
                "[\"简历诊断\"]",
                88,
                19900,
                true,
                "mentor-hash-1"
        ));
        repository.saveMentorSnapshot(new MentorRecommendationRepository.MentorRecommendationSnapshotCommand(
                MENTOR_USER_ID,
                "mentor snapshot two",
                "[\"前端工程化\",\"React\"]",
                "[\"简历诊断\",\"项目表达\"]",
                92,
                22900,
                false,
                "mentor-hash-2"
        ));

        Integer mentorSnapshotCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mentor_recommendation_snapshots WHERE mentor_user_id = ?",
                Integer.class,
                MENTOR_USER_ID
        );
        String mentorHash = jdbcTemplate.queryForObject(
                "SELECT content_hash FROM mentor_recommendation_snapshots WHERE mentor_user_id = ?",
                String.class,
                MENTOR_USER_ID
        );
        assertThat(mentorSnapshotCount).isEqualTo(1);
        assertThat(normalizeFixedChar(mentorHash)).isEqualTo("mentor-hash-2");

        repository.saveEmbeddingVector(new MentorRecommendationRepository.EmbeddingVectorCommand(
                "STUDENT",
                STUDENT_USER_ID,
                "text-embedding-3-small",
                3,
                "[0.1,0.2,0.3]",
                "embed-hash-1"
        ));
        repository.saveEmbeddingVector(new MentorRecommendationRepository.EmbeddingVectorCommand(
                "STUDENT",
                STUDENT_USER_ID,
                "text-embedding-3-small",
                4,
                "[0.4,0.5,0.6,0.7]",
                "embed-hash-2"
        ));

        Integer embeddingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM recommendation_embedding_vectors WHERE entity_type = 'STUDENT' AND entity_id = ? AND model_code = ?",
                Integer.class,
                STUDENT_USER_ID,
                "text-embedding-3-small"
        );
        assertThat(embeddingCount).isEqualTo(1);
        assertThat(repository.findEmbeddingVector("STUDENT", STUDENT_USER_ID, "text-embedding-3-small"))
                .isPresent()
                .get()
                .satisfies(row -> {
                    assertThat(row.vectorDim()).isEqualTo(4);
                    assertThat(row.vectorJson()).isEqualTo("[0.4,0.5,0.6,0.7]");
                    assertThat(normalizeFixedChar(row.contentHash())).isEqualTo("embed-hash-2");
                });

        long runId = repository.createRecommendationRun(new MentorRecommendationRepository.RecommendationRunCommand(
                STUDENT_USER_ID,
                "简历诊断",
                "亮点拆解",
                "前端工程化",
                "{\"available\":true}",
                "text-embedding-3-small",
                "rerank-v1",
                false,
                5,
                2,
                "[" + MENTOR_USER_ID + "]",
                "basis-summary"
        ));
        assertThat(runId).isPositive();

        repository.appendRecommendationEvent(new MentorRecommendationRepository.RecommendationEventCommand(
                runId,
                MENTOR_USER_ID,
                "RECALL",
                1,
                0.918231,
                "{\"reason\":\"skill\"}"
        ));

        Integer eventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mentor_recommendation_events WHERE run_id = ? AND mentor_user_id = ?",
                Integer.class,
                runId,
                MENTOR_USER_ID
        );
        BigDecimal storedScore = jdbcTemplate.queryForObject(
                "SELECT score FROM mentor_recommendation_events WHERE run_id = ? AND mentor_user_id = ?",
                BigDecimal.class,
                runId,
                MENTOR_USER_ID
        );
        assertThat(eventCount).isEqualTo(1);
        assertThat(storedScore).isEqualByComparingTo("0.918231");
    }

    @Test
    void repositoryShouldPurgeOldRecommendationRunsInBatchesOnPostgres() {
        insertUser(STUDENT_USER_ID, "STUDENT", "pg-recommend-retention-student@example.com", "PgRecommendRetentionStudent");
        insertUser(MENTOR_USER_ID, "MENTOR", "pg-recommend-retention-mentor@example.com", "PgRecommendRetentionMentor");

        long oldRunId = repository.createRecommendationRun(new MentorRecommendationRepository.RecommendationRunCommand(
                STUDENT_USER_ID,
                "简历诊断",
                "旧 run",
                "前端工程化",
                "{\"available\":true}",
                "text-embedding-3-small",
                "rerank-v1",
                false,
                5,
                1,
                "[" + MENTOR_USER_ID + "]",
                "old-basis"
        ));
        repository.appendRecommendationEvent(new MentorRecommendationRepository.RecommendationEventCommand(
                oldRunId,
                MENTOR_USER_ID,
                "RECALL",
                1,
                0.81,
                "{\"reason\":\"old\"}"
        ));

        long recentRunId = repository.createRecommendationRun(new MentorRecommendationRepository.RecommendationRunCommand(
                STUDENT_USER_ID,
                "面试准备",
                "新 run",
                "系统设计",
                "{\"available\":false}",
                "text-embedding-3-small",
                "rerank-v1",
                false,
                4,
                1,
                "[" + MENTOR_USER_ID + "]",
                "recent-basis"
        ));
        repository.appendRecommendationEvent(new MentorRecommendationRepository.RecommendationEventCommand(
                recentRunId,
                MENTOR_USER_ID,
                "RERANK",
                1,
                0.93,
                "{\"reason\":\"recent\"}"
        ));

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        jdbcTemplate.update(
                "UPDATE mentor_recommendation_runs SET created_at = ? WHERE id = ?",
                Timestamp.from(now.minus(40, ChronoUnit.DAYS)),
                oldRunId
        );
        jdbcTemplate.update(
                "UPDATE mentor_recommendation_runs SET created_at = ? WHERE id = ?",
                Timestamp.from(now.minus(5, ChronoUnit.DAYS)),
                recentRunId
        );

        List<Long> firstBatch = repository.findRunIdsBefore(now.minus(30, ChronoUnit.DAYS), 1);
        assertThat(firstBatch).containsExactly(oldRunId);

        long deletedEvents = repository.deleteEventsByRunIds(firstBatch);
        long deletedRuns = repository.deleteRunsByIds(firstBatch);
        assertThat(deletedEvents).isEqualTo(1L);
        assertThat(deletedRuns).isEqualTo(1L);

        Integer remainingOldRun = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mentor_recommendation_runs WHERE id = ?",
                Integer.class,
                oldRunId
        );
        Integer remainingRecentRun = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mentor_recommendation_runs WHERE id = ?",
                Integer.class,
                recentRunId
        );
        Integer remainingRecentEvents = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mentor_recommendation_events WHERE run_id = ?",
                Integer.class,
                recentRunId
        );
        assertThat(remainingOldRun).isZero();
        assertThat(remainingRecentRun).isEqualTo(1);
        assertThat(remainingRecentEvents).isEqualTo(1);
    }

    private void insertUser(long userId, String role, String email, String displayName) {
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
                ) VALUES (?, ?, ?, ?, 'FREE', 'ACTIVE', ?, ?, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                userId,
                email,
                "$2a$10$seed",
                role,
                displayName,
                displayName
        );
    }

    private String normalizeFixedChar(String value) {
        return value == null ? null : value.stripTrailing();
    }
}
