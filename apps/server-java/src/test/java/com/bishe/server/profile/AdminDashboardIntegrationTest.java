package com.bishe.server.profile;

import com.bishe.server.dashboard.AdminWorkbenchCacheService;
import com.bishe.server.dashboard.AdminOperationsDashboardCacheService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端数据看板集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminDashboardIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AdminWorkbenchCacheService adminWorkbenchCacheService;

    @Autowired
    private AdminOperationsDashboardCacheService adminOperationsDashboardCacheService;

    @BeforeEach
    void setUp() {
        adminWorkbenchCacheService.evictAllNow();
        adminOperationsDashboardCacheService.evictAllNow();
    }

    @Test
    void adminDashboard_shouldReturnOperationsMetrics() throws Exception {
        registerUser("MENTOR", "dashboard-mentor@example.com", "Passw0rd!", "DashboardMentor");
        registerUser("STUDENT", "dashboard-s1@example.com", "Passw0rd!", "StudentOne");
        registerUser("STUDENT", "dashboard-s2@example.com", "Passw0rd!", "StudentTwo");
        registerUser("STUDENT", "dashboard-s3@example.com", "Passw0rd!", "StudentThree");
        registerUser("STUDENT", "dashboard-s4@example.com", "Passw0rd!", "StudentFour");

        long mentorUserId = findUserIdByEmail("dashboard-mentor@example.com");
        long student1 = findUserIdByEmail("dashboard-s1@example.com");
        long student2 = findUserIdByEmail("dashboard-s2@example.com");
        long student3 = findUserIdByEmail("dashboard-s3@example.com");
        long student4 = findUserIdByEmail("dashboard-s4@example.com");

        LocalDate currentWeekStartDate = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate previousWeekStartDate = currentWeekStartDate.minusWeeks(1);
        Instant currentWeekStart = Timestamp.valueOf(currentWeekStartDate.atStartOfDay()).toInstant();
        Instant student1CreatedAt = Timestamp.valueOf(currentWeekStartDate.atTime(1, 0)).toInstant();
        Instant student2CreatedAt = Timestamp.valueOf(currentWeekStartDate.atTime(2, 0)).toInstant();
        Instant student3CreatedAt = Timestamp.valueOf(previousWeekStartDate.atTime(0, 1)).toInstant();
        Instant student4CreatedAt = Timestamp.valueOf(previousWeekStartDate.atTime(23, 0)).toInstant();

        updateUserTimeline(student1, student1CreatedAt, currentWeekStart.plus(10, ChronoUnit.HOURS));
        updateUserTimeline(student2, student2CreatedAt, currentWeekStart.plus(11, ChronoUnit.HOURS));
        updateUserTimeline(student3, student3CreatedAt, currentWeekStart.plus(2, ChronoUnit.MINUTES));
        updateUserTimeline(student4, student4CreatedAt, currentWeekStart.plus(3, ChronoUnit.MINUTES));

        insertAiCallLog(student1, "SUCCESS", student1CreatedAt.plus(6, ChronoUnit.HOURS));
        insertAiCallLog(student2, "FAILED", student2CreatedAt.plus(1, ChronoUnit.HOURS));
        insertAiCallLog(student3, "SUCCESS", student3CreatedAt.plus(12, ChronoUnit.HOURS));
        insertAiCallLog(student4, "SUCCESS", student4CreatedAt.plus(10, ChronoUnit.HOURS));

        insertPaidConsultOrder("ORD-DASH-001", student1, mentorUserId, currentWeekStart.plus(12, ChronoUnit.HOURS));

        long post1 = insertPost(student1, "运营看板帖子一", currentWeekStart.plus(13, ChronoUnit.HOURS));
        insertAiComment(post1, mentorUserId, currentWeekStart.plus(14, ChronoUnit.HOURS));
        insertPost(student2, "运营看板帖子二", currentWeekStart.plus(15, ChronoUnit.HOURS));
        insertComment(post1, student3, currentWeekStart.plus(16, ChronoUnit.HOURS));
        insertLike(post1, student2, currentWeekStart.plus(17, ChronoUnit.HOURS));

        insertModerationEvent("PASS", currentWeekStart.plus(4, ChronoUnit.HOURS));
        insertModerationEvent("BLOCK", currentWeekStart.plus(5, ChronoUnit.HOURS));
        insertModerationEvent("PASS", currentWeekStart.plus(6, ChronoUnit.HOURS));
        insertModerationEvent("PASS", currentWeekStart.plus(7, ChronoUnit.HOURS));

        insertClosedReport(student1, "POST", "post-1", 12, currentWeekStart.plus(1, ChronoUnit.HOURS));
        insertClosedReport(student2, "POST", "post-2", 24, currentWeekStart.minus(12, ChronoUnit.HOURS));
        insertClosedReport(student3, "COMMENT", "comment-3", 36, currentWeekStart.minus(18, ChronoUnit.HOURS));

        insertPortraitSnapshot(student1, "[{\"code\":\"COMMUNITY_ACTIVE\",\"label\":\"社区互动积极\"}]");
        insertPortraitSnapshot(student3, "[{\"code\":\"INTERVIEW_ACTIVE\",\"label\":\"模拟面试积极\"}]");

        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");

        mockMvc.perform(get("/api/v1/admin/dashboard/operations")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("period", "week"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.period").value("week"))
                .andExpect(jsonPath("$.data.overview.newStudents").value(2))
                .andExpect(jsonPath("$.data.overview.activatedStudents").value(1))
                .andExpect(jsonPath("$.data.overview.activeStudents").value(4))
                .andExpect(jsonPath("$.data.overview.paidConsultStudents").value(1))
                .andExpect(jsonPath("$.data.overview.aiCalls").value(2))
                .andExpect(jsonPath("$.data.overview.aiSuccessCalls").value(1))
                .andExpect(jsonPath("$.data.overview.newPosts").value(2))
                .andExpect(jsonPath("$.data.overview.aiCoveredPosts").value(1))
                .andExpect(jsonPath("$.data.overview.blockedModerationEvents").value(1))
                .andExpect(jsonPath("$.data.overview.closedReports").value(3))
                .andExpect(jsonPath("$.data.overview.totalStudents").value(4))
                .andExpect(jsonPath("$.data.overview.portraitCompletedStudents").value(2))
                .andExpect(jsonPath("$.data.overview.activeStudents7d").value(4))
                .andExpect(jsonPath("$.data.overview.leaderboardCoveredStudents7d").value(3))
                .andExpect(jsonPath("$.data.activationRate.numerator").value(1))
                .andExpect(jsonPath("$.data.activationRate.denominator").value(2))
                .andExpect(jsonPath("$.data.retention7dRate.numerator").value(1))
                .andExpect(jsonPath("$.data.retention7dRate.denominator").value(2))
                .andExpect(jsonPath("$.data.consultConversionRate.numerator").value(1))
                .andExpect(jsonPath("$.data.consultConversionRate.denominator").value(4))
                .andExpect(jsonPath("$.data.aiSuccessRate.numerator").value(1))
                .andExpect(jsonPath("$.data.aiSuccessRate.denominator").value(2))
                .andExpect(jsonPath("$.data.communityAiCoverageRate.numerator").value(1))
                .andExpect(jsonPath("$.data.communityAiCoverageRate.denominator").value(2))
                .andExpect(jsonPath("$.data.moderationBlockRate.numerator").value(1))
                .andExpect(jsonPath("$.data.moderationBlockRate.denominator").value(4))
                .andExpect(jsonPath("$.data.reportHandleMedianHours.value").value("24h"))
                .andExpect(jsonPath("$.data.reportHandleMedianHours.sampleSize").value(3))
                .andExpect(jsonPath("$.data.portraitCompletenessRate.numerator").value(2))
                .andExpect(jsonPath("$.data.portraitCompletenessRate.denominator").value(4))
                .andExpect(jsonPath("$.data.leaderboardCoverageRate.numerator").value(3))
                .andExpect(jsonPath("$.data.leaderboardCoverageRate.denominator").value(4));
    }

    @Test
    void studentAccessAdminDashboard_shouldReturn403() throws Exception {
        registerUser("STUDENT", "dashboard-forbidden@example.com", "Passw0rd!", "ForbiddenStudent");
        String studentToken = loginAndGetAccessToken("dashboard-forbidden@example.com", "Passw0rd!");

        mockMvc.perform(get("/api/v1/admin/dashboard/operations")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-1004"));
    }

    @Test
    void adminWorkbench_shouldUseRedisCacheAndRefreshAfterReportSubmission() throws Exception {
        registerUser("STUDENT", "dashboard-workbench-author@example.com", "Passw0rd!", "WorkbenchAuthor");
        registerUser("STUDENT", "dashboard-workbench-reporter-1@example.com", "Passw0rd!", "WorkbenchReporterOne");
        registerUser("STUDENT", "dashboard-workbench-reporter-2@example.com", "Passw0rd!", "WorkbenchReporterTwo");

        long authorUserId = findUserIdByEmail("dashboard-workbench-author@example.com");
        long reporterUserId = findUserIdByEmail("dashboard-workbench-reporter-1@example.com");
        String reporterToken = loginAndGetAccessToken("dashboard-workbench-reporter-2@example.com", "Passw0rd!");
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");

        long postId = insertPost(authorUserId, "Workbench 缓存举报目标", Instant.now().minus(1, ChronoUnit.HOURS));

        mockMvc.perform(get("/api/v1/admin/dashboard/workbench")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("hours", "24")
                        .param("timezone", "Asia/Shanghai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.pendingReports").value(0))
                .andExpect(jsonPath("$.data.totalPendingTasks").value(0));

        insertPendingReport(reporterUserId, "POST", String.valueOf(postId), Instant.now().minus(20, ChronoUnit.MINUTES));

        mockMvc.perform(get("/api/v1/admin/dashboard/workbench")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("hours", "24")
                        .param("timezone", "Asia/Shanghai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.pendingReports").value(0))
                .andExpect(jsonPath("$.data.totalPendingTasks").value(0));

        mockMvc.perform(post("/api/v1/community/reports")
                        .header("Authorization", "Bearer " + reporterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetType": "POST",
                                  "targetId": "%s",
                                  "reasonCode": "ABUSE",
                                  "detail": "用于触发管理工作台缓存失效"
                                }
                                """.formatted(postId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        mockMvc.perform(get("/api/v1/admin/dashboard/workbench")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("hours", "24")
                        .param("timezone", "Asia/Shanghai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.pendingReports").value(2))
                .andExpect(jsonPath("$.data.totalPendingTasks").value(2));
    }

    @Test
    void adminOperationsDashboard_shouldUseRedisCacheAndRefreshAfterStudentRegister() throws Exception {
        registerUser("STUDENT", "dashboard-operations-cache-1@example.com", "Passw0rd!", "OpsStudentOne");
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");

        mockMvc.perform(get("/api/v1/admin/dashboard/operations")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("period", "today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.period").value("today"))
                .andExpect(jsonPath("$.data.overview.newStudents").value(1))
                .andExpect(jsonPath("$.data.overview.totalStudents").value(1));

        registerUser("STUDENT", "dashboard-operations-cache-2@example.com", "Passw0rd!", "OpsStudentTwo");

        mockMvc.perform(get("/api/v1/admin/dashboard/operations")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("period", "today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.period").value("today"))
                .andExpect(jsonPath("$.data.overview.newStudents").value(2))
                .andExpect(jsonPath("$.data.overview.totalStudents").value(2));
    }

    private void updateUserTimeline(long userId, Instant createdAt, Instant lastLoginAt) {
        jdbcTemplate.update(
                "UPDATE users SET created_at = ?, updated_at = ?, last_login_at = ? WHERE id = ?",
                Timestamp.from(createdAt),
                Timestamp.from(createdAt),
                Timestamp.from(lastLoginAt),
                userId
        );
    }

    private void insertAiCallLog(long userId, String status, Instant createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_call_logs(trace_id, user_id, task_type, provider, model, latency_ms, status, error_code,
                    request_tokens, response_tokens, total_tokens, estimated_cost, charged_points, quota_weight,
                    result_summary, result_payload_json, user_tier, created_at)
                VALUES (?, ?, 'RESUME', 'MOCK_PROVIDER', 'mock-model', 100, ?, NULL,
                    10, 5, 15, 0, 0, 1, 'summary', '{}', 'FREE', ?)
                """,
                "trace_" + userId + "_" + createdAt.toEpochMilli(),
                userId,
                status,
                Timestamp.from(createdAt)
        );
    }

    private void insertPaidConsultOrder(String orderNo, long studentUserId, long mentorUserId, Instant paidAt) {
        jdbcTemplate.update(
                """
                INSERT INTO consult_orders(order_no, student_user_id, mentor_user_id, amount_fen, status, question_text, paid_at, created_at, updated_at)
                VALUES (?, ?, ?, 9900, 'PAID', '请帮我看看项目表达。', ?, ?, ?)
                """,
                orderNo,
                studentUserId,
                mentorUserId,
                Timestamp.from(paidAt),
                Timestamp.from(paidAt.minus(1, ChronoUnit.HOURS)),
                Timestamp.from(paidAt)
        );
    }

    private long insertPost(long userId, String title, Instant createdAt) {
        jdbcTemplate.update(
                "INSERT INTO posts(user_id, title, content, tags, moderation_status, risk_level, created_at, updated_at) VALUES (?, ?, 'content', 'tag', 'PASS', 'LOW', ?, ?)",
                userId,
                title,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        );
        Long postId = jdbcTemplate.queryForObject("SELECT id FROM posts WHERE title = ?", Long.class, title);
        assertThat(postId).isNotNull();
        return postId;
    }

    private void insertAiComment(long postId, long userId, Instant createdAt) {
        jdbcTemplate.update(
                "INSERT INTO comments(post_id, user_id, content, is_ai, moderation_status, risk_level, created_at, updated_at) VALUES (?, ?, 'ai comment', 1, 'PASS', 'LOW', ?, ?)",
                postId,
                userId,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        );
    }

    private void insertComment(long postId, long userId, Instant createdAt) {
        jdbcTemplate.update(
                "INSERT INTO comments(post_id, user_id, content, is_ai, moderation_status, risk_level, created_at, updated_at) VALUES (?, ?, 'user comment', 0, 'PASS', 'LOW', ?, ?)",
                postId,
                userId,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        );
    }

    private void insertLike(long postId, long userId, Instant createdAt) {
        jdbcTemplate.update(
                "INSERT INTO post_likes(post_id, user_id, created_at) VALUES (?, ?, ?)",
                postId,
                userId,
                Timestamp.from(createdAt)
        );
    }

    private void insertModerationEvent(String action, Instant createdAt) {
        jdbcTemplate.update(
                "INSERT INTO content_moderation_events(trace_id, source_type, target_type, target_id, risk_level, action, reason_code, operator_user_id, created_at) VALUES (?, 'COMMUNITY_POST', 'POST', 'target', 'LOW', ?, 'RULE', 1, ?)",
                "mod_" + createdAt.toEpochMilli(),
                action,
                Timestamp.from(createdAt)
        );
    }

    private void insertClosedReport(long reporterUserId, String targetType, String targetId, long durationHours, Instant createdAt) {
        Instant closedAt = createdAt.plus(durationHours, ChronoUnit.HOURS);
        jdbcTemplate.update(
                """
                INSERT INTO content_reports(reporter_user_id, target_type, target_id, reason_code, detail, status, latest_action, created_at, updated_at, closed_at)
                VALUES (?, ?, ?, 'SPAM', 'detail', 'CLOSED', 'NO_ACTION', ?, ?, ?)
                """,
                reporterUserId,
                targetType,
                targetId,
                Timestamp.from(createdAt),
                Timestamp.from(closedAt),
                Timestamp.from(closedAt)
        );
    }

    private void insertPendingReport(long reporterUserId, String targetType, String targetId, Instant createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO content_reports(reporter_user_id, target_type, target_id, reason_code, detail, status, latest_action, created_at, updated_at, closed_at)
                VALUES (?, ?, ?, 'ABUSE', 'manual cached row', 'PENDING', 'NONE', ?, ?, NULL)
                """,
                reporterUserId,
                targetType,
                targetId,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        );
    }

    private void insertPortraitSnapshot(long studentUserId, String portraitTagsJson) {
        jdbcTemplate.update(
                "INSERT INTO student_portrait_snapshots(student_user_id, portrait_tags, evidence, updated_at) VALUES (?, ?, '{}', CURRENT_TIMESTAMP)",
                studentUserId,
                portraitTagsJson
        );
    }

    private void registerUser(String role, String email, String password, String displayName) throws Exception {
        String body = String.format(
                "{" +
                        "\"role\":\"%s\"," +
                        "\"email\":\"%s\"," +
                        "\"password\":\"%s\"," +
                        "\"displayName\":\"%s\"" +
                        "}",
                role,
                email,
                password,
                displayName
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        String loginBody = String.format(
                "{" +
                        "\"email\":\"%s\"," +
                        "\"password\":\"%s\"" +
                        "}",
                email,
                password
        );

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        String accessToken = jsonNode.path("data").path("accessToken").asText();
        assertThat(accessToken).isNotBlank();
        return accessToken;
    }

    private long findUserIdByEmail(String email) {
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        assertThat(userId).isNotNull();
        return userId;
    }
}
