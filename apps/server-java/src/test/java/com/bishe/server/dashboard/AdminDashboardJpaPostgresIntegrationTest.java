package com.bishe.server.dashboard;

import com.bishe.server.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(AdminDashboardRepository.class)
class AdminDashboardJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final long MENTOR_USER_ID = 2101L;
    private static final long STUDENT_1 = 2102L;
    private static final long STUDENT_2 = 2103L;
    private static final long STUDENT_3 = 2104L;
    private static final long STUDENT_4 = 2105L;

    @Autowired
    private AdminDashboardRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM comments WHERE user_id IN (?, ?, ?, ?, ?)", MENTOR_USER_ID, STUDENT_1, STUDENT_2, STUDENT_3, STUDENT_4);
        jdbcTemplate.update("DELETE FROM posts WHERE user_id IN (?, ?, ?, ?)", STUDENT_1, STUDENT_2, STUDENT_3, STUDENT_4);
        jdbcTemplate.update("DELETE FROM consult_orders WHERE student_user_id IN (?, ?, ?, ?) OR mentor_user_id = ?", STUDENT_1, STUDENT_2, STUDENT_3, STUDENT_4, MENTOR_USER_ID);
        jdbcTemplate.update("DELETE FROM ai_call_logs WHERE user_id IN (?, ?, ?, ?)", STUDENT_1, STUDENT_2, STUDENT_3, STUDENT_4);
        jdbcTemplate.update("DELETE FROM content_reports WHERE reporter_user_id IN (?, ?, ?, ?)", STUDENT_1, STUDENT_2, STUDENT_3, STUDENT_4);
        jdbcTemplate.update("DELETE FROM content_moderation_events WHERE trace_id LIKE 'pg_dashboard_%'");
        jdbcTemplate.update("DELETE FROM student_portrait_snapshots WHERE student_user_id IN (?, ?, ?, ?)", STUDENT_1, STUDENT_2, STUDENT_3, STUDENT_4);
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?, ?, ?, ?)", MENTOR_USER_ID, STUDENT_1, STUDENT_2, STUDENT_3, STUDENT_4);
    }

    @Test
    void repositoryShouldAggregateOperationsMetricsOnPostgres() {
        long baselineTotalStudents = repository.countTotalStudents();
        long baselinePortraitCompletedStudents = repository.countPortraitCompletedStudents();

        LocalDate currentWeekStartDate = LocalDate.of(2030, 1, 7).with(DayOfWeek.MONDAY);
        LocalDate previousWeekStartDate = currentWeekStartDate.minusWeeks(1);
        Instant currentWeekStart = at(currentWeekStartDate, 0, 0);
        Instant windowEnd = currentWeekStartDate.plusDays(6).atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant();

        Instant student1CreatedAt = at(currentWeekStartDate, 1, 0);
        Instant student2CreatedAt = at(currentWeekStartDate, 2, 0);
        Instant student3CreatedAt = at(previousWeekStartDate, 0, 1);
        Instant student4CreatedAt = at(previousWeekStartDate, 23, 0);

        insertUser(MENTOR_USER_ID, "MENTOR", "dashboard-mentor-pg@example.com", "Pg Mentor", currentWeekStart.minus(2, ChronoUnit.DAYS), currentWeekStart.minus(2, ChronoUnit.DAYS));
        insertUser(STUDENT_1, "STUDENT", "dashboard-s1-pg@example.com", "Pg Student One", student1CreatedAt, currentWeekStart.plus(10, ChronoUnit.HOURS));
        insertUser(STUDENT_2, "STUDENT", "dashboard-s2-pg@example.com", "Pg Student Two", student2CreatedAt, currentWeekStart.plus(11, ChronoUnit.HOURS));
        insertUser(STUDENT_3, "STUDENT", "dashboard-s3-pg@example.com", "Pg Student Three", student3CreatedAt, currentWeekStart.plus(2, ChronoUnit.MINUTES));
        insertUser(STUDENT_4, "STUDENT", "dashboard-s4-pg@example.com", "Pg Student Four", student4CreatedAt, currentWeekStart.plus(3, ChronoUnit.MINUTES));

        insertAiCallLog(STUDENT_1, "SUCCESS", student1CreatedAt.plus(6, ChronoUnit.HOURS));
        insertAiCallLog(STUDENT_2, "FAILED", student2CreatedAt.plus(1, ChronoUnit.HOURS));
        insertAiCallLog(STUDENT_3, "SUCCESS", student3CreatedAt.plus(12, ChronoUnit.HOURS));
        insertAiCallLog(STUDENT_4, "SUCCESS", student4CreatedAt.plus(10, ChronoUnit.HOURS));

        insertPaidConsultOrder("PG-ORDER-001", STUDENT_1, MENTOR_USER_ID, currentWeekStart.plus(12, ChronoUnit.HOURS));

        long post1 = insertPost(STUDENT_1, currentWeekStart.plus(13, ChronoUnit.HOURS));
        insertAiComment(post1, MENTOR_USER_ID, currentWeekStart.plus(14, ChronoUnit.HOURS));
        insertPost(STUDENT_2, currentWeekStart.plus(15, ChronoUnit.HOURS));
        insertComment(post1, STUDENT_3, currentWeekStart.plus(16, ChronoUnit.HOURS));

        insertModerationEvent("PASS", currentWeekStart.plus(4, ChronoUnit.HOURS));
        insertModerationEvent("BLOCK", currentWeekStart.plus(5, ChronoUnit.HOURS));
        insertModerationEvent("PASS", currentWeekStart.plus(6, ChronoUnit.HOURS));
        insertModerationEvent("PASS", currentWeekStart.plus(7, ChronoUnit.HOURS));

        insertClosedReport(STUDENT_1, "POST", "pg-post-1", 12, currentWeekStart.plus(1, ChronoUnit.HOURS));
        insertClosedReport(STUDENT_2, "POST", "pg-post-2", 24, currentWeekStart.minus(12, ChronoUnit.HOURS));
        insertClosedReport(STUDENT_3, "COMMENT", "pg-comment-3", 36, currentWeekStart.minus(18, ChronoUnit.HOURS));

        insertPortraitSnapshot(STUDENT_1, "[{\"code\":\"COMMUNITY_ACTIVE\"}]");
        insertPortraitSnapshot(STUDENT_3, "[{\"code\":\"INTERVIEW_ACTIVE\"}]");

        Timestamp startAt = Timestamp.from(currentWeekStart);
        Timestamp endAt = Timestamp.from(windowEnd);

        assertThat(repository.findStudentLifecycleRows(startAt, endAt))
                .extracting(AdminDashboardRepository.StudentLifecycleRow::userId)
                .containsExactly(STUDENT_1, STUDENT_2);

        Map<Long, Instant> firstSuccessMap = repository.findFirstSuccessfulAiCallAt(List.of(STUDENT_1, STUDENT_2, STUDENT_3, STUDENT_4));
        assertThat(firstSuccessMap)
                .containsKeys(STUDENT_1, STUDENT_3, STUDENT_4)
                .doesNotContainKey(STUDENT_2);
        assertThat(firstSuccessMap.get(STUDENT_1)).isEqualTo(student1CreatedAt.plus(6, ChronoUnit.HOURS));

        assertThat(repository.findActiveStudentIds(startAt, endAt))
                .containsExactly(STUDENT_1, STUDENT_2, STUDENT_3, STUDENT_4);
        assertThat(repository.findPaidConsultStudentIds(startAt, endAt))
                .containsExactly(STUDENT_1);

        assertThat(repository.summarizeAiCalls(startAt, endAt))
                .satisfies(summary -> {
                    assertThat(summary.totalCount()).isEqualTo(2);
                    assertThat(summary.successCount()).isEqualTo(1);
                });

        assertThat(repository.summarizeCommunityAiCoverage(startAt, endAt))
                .satisfies(summary -> {
                    assertThat(summary.totalPosts()).isEqualTo(2);
                    assertThat(summary.aiCoveredPosts()).isEqualTo(1);
                });

        assertThat(repository.summarizeModeration(startAt, endAt))
                .satisfies(summary -> {
                    assertThat(summary.totalCount()).isEqualTo(4);
                    assertThat(summary.blockedCount()).isEqualTo(1);
                });

        assertThat(repository.listClosedReportDurationsHours(startAt, endAt))
                .containsExactly(24D, 12D, 36D);

        assertThat(repository.countTotalStudents()).isEqualTo(baselineTotalStudents + 4);
        assertThat(repository.countPortraitCompletedStudents()).isEqualTo(baselinePortraitCompletedStudents + 2);
        assertThat(repository.countActiveStudentsSince(Timestamp.from(currentWeekStart.minus(1, ChronoUnit.HOURS)))).isEqualTo(4);
    }

    private Instant at(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant();
    }

    private void insertUser(long userId, String role, String email, String displayName, Instant createdAt, Instant lastLoginAt) {
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
                    last_login_at,
                    is_deleted,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, 'FREE', 'ACTIVE', ?, ?, ?, FALSE, ?, ?)
                """,
                userId,
                email,
                "$2a$10$seed",
                role,
                displayName,
                displayName,
                lastLoginAt == null ? null : Timestamp.from(lastLoginAt),
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        );
    }

    private void insertAiCallLog(long userId, String status, Instant createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_call_logs(
                    trace_id,
                    user_id,
                    task_type,
                    scene_code,
                    provider,
                    model,
                    route_code,
                    route_policy_code,
                    latency_ms,
                    status,
                    error_code,
                    request_tokens,
                    response_tokens,
                    total_tokens,
                    thoughts_tokens,
                    reasoning_effort,
                    thinking_budget,
                    thinking_level,
                    estimated_cost,
                    charged_points,
                    quota_weight,
                    result_summary,
                    result_payload_json,
                    user_deleted_at,
                    user_tier,
                    created_at
                ) VALUES (?, ?, 'RESUME', 'RESUME_OPTIMIZE', 'MOCK_PROVIDER', 'mock-model', 'route', 'policy', 100, ?, NULL, 10, 5, 15, 0, 'LOW', 64, 'BALANCED', 0, 0, 1, 'summary', '{}', NULL, 'FREE', ?)
                """,
                "pg_dashboard_ai_" + userId + "_" + createdAt.toEpochMilli(),
                userId,
                status,
                Timestamp.from(createdAt)
        );
    }

    private void insertPaidConsultOrder(String orderNo, long studentUserId, long mentorUserId, Instant paidAt) {
        jdbcTemplate.update(
                """
                INSERT INTO consult_orders(order_no, student_user_id, mentor_user_id, amount_fen, status, question_text, paid_at, created_at, updated_at)
                VALUES (?, ?, ?, 9900, 'PAID', 'dashboard pg consult', ?, ?, ?)
                """,
                orderNo,
                studentUserId,
                mentorUserId,
                Timestamp.from(paidAt),
                Timestamp.from(paidAt.minus(1, ChronoUnit.HOURS)),
                Timestamp.from(paidAt)
        );
    }

    private long insertPost(long userId, Instant createdAt) {
        jdbcTemplate.update(
                "INSERT INTO posts(user_id, moderation_status, is_deleted, created_at) VALUES (?, 'PASS', FALSE, ?)",
                userId,
                Timestamp.from(createdAt)
        );
        Long postId = jdbcTemplate.queryForObject(
                "SELECT id FROM posts WHERE user_id = ? AND created_at = ?",
                Long.class,
                userId,
                Timestamp.from(createdAt)
        );
        assertThat(postId).isNotNull();
        return postId;
    }

    private void insertAiComment(long postId, long userId, Instant createdAt) {
        jdbcTemplate.update(
                "INSERT INTO comments(post_id, user_id, is_ai, moderation_status, is_deleted, created_at) VALUES (?, ?, TRUE, 'PASS', FALSE, ?)",
                postId,
                userId,
                Timestamp.from(createdAt)
        );
    }

    private void insertComment(long postId, long userId, Instant createdAt) {
        jdbcTemplate.update(
                "INSERT INTO comments(post_id, user_id, is_ai, moderation_status, is_deleted, created_at) VALUES (?, ?, FALSE, 'PASS', FALSE, ?)",
                postId,
                userId,
                Timestamp.from(createdAt)
        );
    }

    private void insertModerationEvent(String action, Instant createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO content_moderation_events(
                    trace_id,
                    source_type,
                    target_type,
                    target_id,
                    risk_level,
                    action,
                    reason_code,
                    operator_user_id,
                    created_at
                ) VALUES (?, 'COMMUNITY_POST', 'POST', 'target', 'LOW', ?, 'RULE', ?, ?)
                """,
                "pg_dashboard_" + createdAt.toEpochMilli(),
                action,
                STUDENT_1,
                Timestamp.from(createdAt)
        );
    }

    private void insertClosedReport(long reporterUserId, String targetType, String targetId, long durationHours, Instant createdAt) {
        Instant closedAt = createdAt.plus(durationHours, ChronoUnit.HOURS);
        jdbcTemplate.update(
                """
                INSERT INTO content_reports(
                    reporter_user_id,
                    target_type,
                    target_id,
                    reason_code,
                    detail,
                    status,
                    latest_action,
                    created_at,
                    updated_at,
                    closed_at
                ) VALUES (?, ?, ?, 'SPAM', 'detail', 'CLOSED', 'NO_ACTION', ?, ?, ?)
                """,
                reporterUserId,
                targetType,
                targetId,
                Timestamp.from(createdAt),
                Timestamp.from(closedAt),
                Timestamp.from(closedAt)
        );
    }

    private void insertPortraitSnapshot(long studentUserId, String portraitTagsJson) {
        jdbcTemplate.update(
                "INSERT INTO student_portrait_snapshots(student_user_id, portrait_tags, evidence, updated_at) VALUES (?, ?, '{}', CURRENT_TIMESTAMP)",
                studentUserId,
                portraitTagsJson
        );
    }
}
