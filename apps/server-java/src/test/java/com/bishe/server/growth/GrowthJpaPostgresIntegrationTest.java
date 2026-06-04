package com.bishe.server.growth;

import com.bishe.server.growth.repository.GrowthRepository;
import com.bishe.server.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(GrowthRepository.class)
class GrowthJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final long STUDENT_USER_ID = 5101L;

    @Autowired
    private GrowthRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM points_ledger WHERE student_user_id = ?", STUDENT_USER_ID);
        jdbcTemplate.update("DELETE FROM checkins WHERE student_user_id = ?", STUDENT_USER_ID);
        jdbcTemplate.update("DELETE FROM ai_async_task_events WHERE user_id = ?", STUDENT_USER_ID);
        jdbcTemplate.update("DELETE FROM ai_async_task_jobs WHERE user_id = ?", STUDENT_USER_ID);
        jdbcTemplate.update("DELETE FROM ai_call_logs WHERE user_id = ?", STUDENT_USER_ID);
        jdbcTemplate.update("DELETE FROM comments WHERE user_id = ?", STUDENT_USER_ID);
        jdbcTemplate.update("DELETE FROM posts WHERE user_id = ?", STUDENT_USER_ID);
        jdbcTemplate.update("DELETE FROM skill_progress WHERE student_user_id = ?", STUDENT_USER_ID);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", STUDENT_USER_ID);
    }

    @Test
    void repositoryShouldSupportGrowthQueriesOnPostgres() {
        insertUser(STUDENT_USER_ID, "STUDENT", "growth-pg@example.com", "GrowthPg");

        assertThat(repository.existsStudentUser(STUDENT_USER_ID)).isTrue();
        assertThat(repository.findStudentJourneyStartDate(STUDENT_USER_ID)).contains(LocalDate.now());

        repository.lockStudentUser(STUDENT_USER_ID);

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        repository.insertCheckin(STUDENT_USER_ID, yesterday, 1, 10);
        repository.insertCheckin(STUDENT_USER_ID, today, 2, 10);

        assertThat(repository.existsCheckin(STUDENT_USER_ID, today)).isTrue();
        assertThat(repository.findLatestCheckin(STUDENT_USER_ID))
                .isPresent()
                .get()
                .satisfies(row -> {
                    assertThat(row.checkinDate()).isEqualTo(today);
                    assertThat(row.streakCount()).isEqualTo(2);
                    assertThat(row.pointsEarned()).isEqualTo(10);
                });
        assertThat(repository.findCheckinsBetween(STUDENT_USER_ID, yesterday, today))
                .extracting(GrowthRepository.CheckinRow::checkinDate)
                .containsExactly(yesterday, today);

        List<GrowthRepository.DailyTaskRow> tasks = repository.findActiveDailyTasks();
        assertThat(tasks).extracting(GrowthRepository.DailyTaskRow::taskCode)
                .contains("TASK_RESUME_OPTIMIZE", "TASK_SKILL_PROGRESS", "TASK_COMMUNITY_INTERACT");
        assertThat(repository.findActiveDailyTask(tasks.getFirst().id())).isPresent();
        assertThat(repository.findEnabledCheckinRewardRules())
                .extracting(GrowthRepository.CheckinRewardRuleRow::rewardCode)
                .startsWith("CHECKIN_STREAK_3", "CHECKIN_STREAK_7", "CHECKIN_STREAK_14");

        int balanceAfterCheckin = repository.appendPointsLedger(STUDENT_USER_ID, 10, "CHECKIN");
        int balanceAfterTask = repository.appendPointsLedger(STUDENT_USER_ID, 8, "TASK_SKILL_PROGRESS");
        assertThat(balanceAfterCheckin).isEqualTo(10);
        assertThat(balanceAfterTask).isEqualTo(18);
        assertThat(repository.getCurrentBalance(STUDENT_USER_ID)).isEqualTo(18);
        assertThat(repository.hasLedgerReasonOnDate(STUDENT_USER_ID, "CHECKIN", today)).isTrue();

        Set<String> reasons = repository.findLedgerReasonsOnDate(
                STUDENT_USER_ID,
                List.of("CHECKIN", "TASK_SKILL_PROGRESS", "TASK_COMMUNITY_INTERACT"),
                today
        );
        assertThat(reasons).containsExactlyInAnyOrder("CHECKIN", "TASK_SKILL_PROGRESS");
        assertThat(repository.findLedgerRecords(STUDENT_USER_ID, 10, 0))
                .extracting(GrowthRepository.PointsLedgerRow::reasonCode)
                .containsExactly("TASK_SKILL_PROGRESS", "CHECKIN");
        assertThat(repository.countLedgerRecords(STUDENT_USER_ID)).isEqualTo(2L);

        insertResumeAsyncTask(STUDENT_USER_ID, "growth-pg-task", LocalDateTime.now().withHour(10).withMinute(0).withSecond(0).withNano(0));
        assertThat(repository.hasResumeOptimizeActivityOnDate(STUDENT_USER_ID, today)).isTrue();

        insertSkillProgress(STUDENT_USER_ID, "programming_language_foundations", "LEARNING", LocalDateTime.now().withHour(11).withMinute(0).withSecond(0).withNano(0));
        assertThat(repository.hasSkillProgressActivityOnDate(STUDENT_USER_ID, today)).isTrue();

        insertCommunityPost(STUDENT_USER_ID, LocalDateTime.now().withHour(12).withMinute(0).withSecond(0).withNano(0));
        assertThat(repository.hasCommunityInteractionOnDate(STUDENT_USER_ID, today)).isTrue();
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

    private void insertResumeAsyncTask(long userId, String taskId, LocalDateTime createdAt) {
        Timestamp timestamp = Timestamp.valueOf(createdAt);
        jdbcTemplate.update(
                """
                INSERT INTO ai_async_task_jobs(
                    task_id, user_id, task_type, scene_code, route_code, execution_mode, status,
                    max_attempts, next_run_at, queued_at, created_at, updated_at
                ) VALUES (?, ?, 'RESUME', 'RESUME_OPTIMIZE', 'SYSTEM_RESUME_OPTIMIZE', 'ASYNC_JOB', 'PENDING', 3, ?, ?, ?, ?)
                """,
                taskId,
                userId,
                timestamp,
                timestamp,
                timestamp,
                timestamp
        );
    }

    private void insertSkillProgress(long userId, String nodeCode, String progressStatus, LocalDateTime updatedAt) {
        jdbcTemplate.update(
                "INSERT INTO skill_progress(student_user_id, node_code, progress_status, updated_at) VALUES (?, ?, ?, ?)",
                userId,
                nodeCode,
                progressStatus,
                Timestamp.valueOf(updatedAt)
        );
    }

    private void insertCommunityPost(long userId, LocalDateTime createdAt) {
        Timestamp timestamp = Timestamp.valueOf(createdAt);
        jdbcTemplate.update(
                """
                INSERT INTO posts(
                    user_id, title, content, moderation_status, is_deleted, created_at, updated_at
                ) VALUES (?, ?, ?, 'PASS', FALSE, ?, ?)
                """,
                userId,
                "Growth PG Test Post",
                "growth repository postgres fixture",
                timestamp,
                timestamp
        );
    }
}
