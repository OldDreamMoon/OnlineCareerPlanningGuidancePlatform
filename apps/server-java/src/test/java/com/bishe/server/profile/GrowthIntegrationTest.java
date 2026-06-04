package com.bishe.server.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 成长中心最小闭环测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GrowthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider;

    @BeforeEach
    void clearGrowthCaches() {
        StringRedisTemplate stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
        if (stringRedisTemplate == null) {
            return;
        }
        Set<String> keys = stringRedisTemplate.keys("growth:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Test
    void dailyTasks_shouldAutoCompleteResumeTaskFromAsyncSubmission() throws Exception {
        registerUser("STUDENT", "growth@example.com", "Passw0rd!", "GrowthAlice");
        long userId = findUserIdByEmail("growth@example.com");
        String studentToken = loginAndGetAccessToken("growth@example.com", "Passw0rd!");

        mockMvc.perform(post("/api/v1/growth/checkin")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.streak").value(1))
                .andExpect(jsonPath("$.data.pointsEarned").value(10))
                .andExpect(jsonPath("$.data.basePointsEarned").value(10))
                .andExpect(jsonPath("$.data.bonusPointsEarned").value(0))
                .andExpect(jsonPath("$.data.newBalance").value(10));

        mockMvc.perform(post("/api/v1/growth/checkin")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BIZ-1301"));

        insertResumeAsyncTask(userId, "resume-task-" + userId, LocalDateTime.now().withHour(10).withMinute(15).withSecond(0).withNano(0));

        JsonNode firstTasks = readDataNode(mockMvc.perform(get("/api/v1/growth/tasks/daily")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andReturn());
        assertThat(findTaskByCode(firstTasks, "TASK_RESUME_OPTIMIZE").path("completed").asBoolean()).isTrue();

        JsonNode secondTasks = readDataNode(mockMvc.perform(get("/api/v1/growth/tasks/daily")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(findTaskByCode(secondTasks, "TASK_RESUME_OPTIMIZE").path("completed").asBoolean()).isTrue();

        mockMvc.perform(get("/api/v1/growth/points/ledger")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(20))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[0].reasonCode").value("TASK_RESUME_OPTIMIZE"))
                .andExpect(jsonPath("$.data.records[1].reasonCode").value("CHECKIN"));
    }

    @Test
    void pointsLedger_shouldAutoCompleteSkillTaskFromProgressUpdate() throws Exception {
        registerUser("STUDENT", "growth-skill@example.com", "Passw0rd!", "GrowthSkill");
        long userId = findUserIdByEmail("growth-skill@example.com");
        String studentToken = loginAndGetAccessToken("growth-skill@example.com", "Passw0rd!");

        insertSkillProgress(userId, "programming_language_foundations", "LEARNING", LocalDateTime.now().withHour(11).withMinute(20).withSecond(0).withNano(0));

        mockMvc.perform(get("/api/v1/growth/points/ledger")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(8))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].reasonCode").value("TASK_SKILL_PROGRESS"));

        JsonNode tasks = readDataNode(mockMvc.perform(get("/api/v1/growth/tasks/daily")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(findTaskByCode(tasks, "TASK_SKILL_PROGRESS").path("completed").asBoolean()).isTrue();
    }

    @Test
    void dailyTasks_shouldAutoCompleteCommunityTaskFromPostActivity() throws Exception {
        registerUser("STUDENT", "growth-community@example.com", "Passw0rd!", "GrowthCommunity");
        long userId = findUserIdByEmail("growth-community@example.com");
        String studentToken = loginAndGetAccessToken("growth-community@example.com", "Passw0rd!");

        insertCommunityPost(userId, LocalDateTime.now().withHour(14).withMinute(5).withSecond(0).withNano(0));

        JsonNode tasks = readDataNode(mockMvc.perform(get("/api/v1/growth/tasks/daily")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(findTaskByCode(tasks, "TASK_COMMUNITY_INTERACT").path("completed").asBoolean()).isTrue();

        mockMvc.perform(get("/api/v1/growth/points/ledger")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(6))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].reasonCode").value("TASK_COMMUNITY_INTERACT"));
    }

    @Test
    void checkin_shouldGrantMilestoneBonusWhenStreakHitsRewardRule() throws Exception {
        registerUser("STUDENT", "growth-streak@example.com", "Passw0rd!", "GrowthStreak");
        long userId = findUserIdByEmail("growth-streak@example.com");
        String studentToken = loginAndGetAccessToken("growth-streak@example.com", "Passw0rd!");

        LocalDate today = LocalDate.now();
        insertCheckin(userId, today.minusDays(2), 1, 10);
        insertCheckin(userId, today.minusDays(1), 2, 10);
        insertLedger(userId, 10, "CHECKIN", 10, today.minusDays(2));
        insertLedger(userId, 10, "CHECKIN", 20, today.minusDays(1));

        mockMvc.perform(post("/api/v1/growth/checkin")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.streak").value(3))
                .andExpect(jsonPath("$.data.basePointsEarned").value(10))
                .andExpect(jsonPath("$.data.bonusPointsEarned").value(6))
                .andExpect(jsonPath("$.data.pointsEarned").value(16))
                .andExpect(jsonPath("$.data.newBalance").value(36))
                .andExpect(jsonPath("$.data.triggeredReward.rewardCode").value("CHECKIN_STREAK_3"))
                .andExpect(jsonPath("$.data.triggeredReward.streakDays").value(3))
                .andExpect(jsonPath("$.data.triggeredReward.bonusPoints").value(6));

        mockMvc.perform(get("/api/v1/growth/points/ledger")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(36))
                .andExpect(jsonPath("$.data.records[0].reasonCode").value("CHECKIN_STREAK_3"))
                .andExpect(jsonPath("$.data.records[0].deltaPoints").value(6))
                .andExpect(jsonPath("$.data.records[1].reasonCode").value("CHECKIN"))
                .andExpect(jsonPath("$.data.records[1].deltaPoints").value(10));
    }

    @Test
    void checkinOverview_shouldExposeCalendarDaysAndNextReward() throws Exception {
        registerUser("STUDENT", "growth-overview@example.com", "Passw0rd!", "GrowthOverview");
        long userId = findUserIdByEmail("growth-overview@example.com");
        String studentToken = loginAndGetAccessToken("growth-overview@example.com", "Passw0rd!");

        LocalDate today = LocalDate.now();
        updateUserCreatedAt(userId, today.minusDays(6).atTime(9, 0));
        insertCheckin(userId, today.minusDays(2), 1, 10);
        insertCheckin(userId, today.minusDays(1), 2, 10);

        mockMvc.perform(get("/api/v1/growth/checkin/overview")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.signedInToday").value(false))
                .andExpect(jsonPath("$.data.growthJourneyDays").value(7))
                .andExpect(jsonPath("$.data.currentStreak").value(2))
                .andExpect(jsonPath("$.data.checkedInDays.length()").value(2))
                .andExpect(jsonPath("$.data.checkedInDays[0]").value(today.minusDays(2).getDayOfMonth()))
                .andExpect(jsonPath("$.data.checkedInDays[1]").value(today.minusDays(1).getDayOfMonth()))
                .andExpect(jsonPath("$.data.basePointsPerDay").value(10))
                .andExpect(jsonPath("$.data.rewardRules[0].rewardCode").value("CHECKIN_STREAK_3"))
                .andExpect(jsonPath("$.data.nextReward.rewardCode").value("CHECKIN_STREAK_3"))
                .andExpect(jsonPath("$.data.nextReward.remainingDays").value(1));
    }

    @Test
    void checkinOverview_shouldUseCacheAndRefreshAfterCheckin() throws Exception {
        registerUser("STUDENT", "growth-overview-cache@example.com", "Passw0rd!", "GrowthOverviewCache");
        long userId = findUserIdByEmail("growth-overview-cache@example.com");
        String studentToken = loginAndGetAccessToken("growth-overview-cache@example.com", "Passw0rd!");

        mockMvc.perform(get("/api/v1/growth/checkin/overview")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.signedInToday").value(false))
                .andExpect(jsonPath("$.data.growthJourneyDays").value(1));

        updateUserCreatedAt(userId, LocalDate.now().minusDays(10).atTime(9, 0));

        mockMvc.perform(get("/api/v1/growth/checkin/overview")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.signedInToday").value(false))
                .andExpect(jsonPath("$.data.growthJourneyDays").value(1));

        mockMvc.perform(post("/api/v1/growth/checkin")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.streak").value(1));

        mockMvc.perform(get("/api/v1/growth/checkin/overview")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.signedInToday").value(true))
                .andExpect(jsonPath("$.data.growthJourneyDays").value(11))
                .andExpect(jsonPath("$.data.currentStreak").value(1));
    }

    @Test
    void dailyTasks_shouldUseCacheAndRefreshAfterSkillProgressUpdate() throws Exception {
        registerUser("STUDENT", "growth-daily-skill-cache@example.com", "Passw0rd!", "GrowthDailySkill");
        long userId = findUserIdByEmail("growth-daily-skill-cache@example.com");
        String studentToken = loginAndGetAccessToken("growth-daily-skill-cache@example.com", "Passw0rd!");

        JsonNode firstTasks = readDataNode(mockMvc.perform(get("/api/v1/growth/tasks/daily")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(findTaskByCode(firstTasks, "TASK_SKILL_PROGRESS").path("completed").asBoolean()).isFalse();

        insertSkillProgress(userId, "programming_language_foundations", "LEARNING", LocalDateTime.now().withHour(9).withMinute(15).withSecond(0).withNano(0));

        JsonNode cachedTasks = readDataNode(mockMvc.perform(get("/api/v1/growth/tasks/daily")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(findTaskByCode(cachedTasks, "TASK_SKILL_PROGRESS").path("completed").asBoolean()).isFalse();

        mockMvc.perform(post("/api/v1/skills/progress")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nodeId": "programming_language_foundations",
                                  "targetStatus": "MASTERED"
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode refreshedTasks = readDataNode(mockMvc.perform(get("/api/v1/growth/tasks/daily")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(findTaskByCode(refreshedTasks, "TASK_SKILL_PROGRESS").path("completed").asBoolean()).isTrue();
    }

    @Test
    void dailyTasks_shouldUseCacheAndRefreshAfterResumeAsyncSubmit() throws Exception {
        registerUser("STUDENT", "growth-daily-resume-cache@example.com", "Passw0rd!", "GrowthDailyResume");
        long userId = findUserIdByEmail("growth-daily-resume-cache@example.com");
        String studentToken = loginAndGetAccessToken("growth-daily-resume-cache@example.com", "Passw0rd!");

        JsonNode firstTasks = readDataNode(mockMvc.perform(get("/api/v1/growth/tasks/daily")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(findTaskByCode(firstTasks, "TASK_RESUME_OPTIMIZE").path("completed").asBoolean()).isFalse();

        insertResumeAsyncTask(userId, "growth-cache-prewarm-" + userId, LocalDateTime.now().withHour(10).withMinute(15).withSecond(0).withNano(0));

        JsonNode cachedTasks = readDataNode(mockMvc.perform(get("/api/v1/growth/tasks/daily")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(findTaskByCode(cachedTasks, "TASK_RESUME_OPTIMIZE").path("completed").asBoolean()).isFalse();

        mockMvc.perform(post("/api/v1/ai/resume/tasks")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetRole": "Backend Engineer",
                                  "targetContext": "校招正式批",
                                  "jobDescription": "负责核心后端服务研发，要求熟悉 Java、MySQL、Redis 与性能优化。",
                                  "resumeText": "负责用户增长平台后端接口开发，重构缓存链路后接口延迟降低 30%，支撑日活 10 万。"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.taskType").value("RESUME"));

        JsonNode refreshedTasks = readDataNode(mockMvc.perform(get("/api/v1/growth/tasks/daily")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(findTaskByCode(refreshedTasks, "TASK_RESUME_OPTIMIZE").path("completed").asBoolean()).isTrue();
    }

    @Test
    void adminGrantPoints_shouldAppendLedgerForStudent() throws Exception {
        registerUser("STUDENT", "growth-points@example.com", "Passw0rd!", "GrowthPoints");
        long userId = findUserIdByEmail("growth-points@example.com");
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");
        String studentToken = loginAndGetAccessToken("growth-points@example.com", "Passw0rd!");

        mockMvc.perform(get("/api/v1/growth/points/ledger")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(0))
                .andExpect(jsonPath("$.data.total").value(0));

        String body = String.format(
                "{" +
                        "\"userId\":%d," +
                        "\"points\":30," +
                        "\"reasonCode\":\"TEST_TOPUP\"" +
                        "}",
                userId
        );

        mockMvc.perform(post("/api/v1/admin/growth/points/grant")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("points granted"))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.deltaPoints").value(30))
                .andExpect(jsonPath("$.data.newBalance").value(30))
                .andExpect(jsonPath("$.data.reasonCode").value("TEST_TOPUP"));

        mockMvc.perform(get("/api/v1/growth/points/ledger")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(30))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].reasonCode").value("TEST_TOPUP"))
                .andExpect(jsonPath("$.data.records[0].deltaPoints").value(30));
    }

    @Test
    void growthEndpoints_shouldRejectMentorAccess() throws Exception {
        registerUser("MENTOR", "mentor-growth@example.com", "Passw0rd!", "MentorGrowth");
        String mentorToken = loginAndGetAccessToken("mentor-growth@example.com", "Passw0rd!");

        mockMvc.perform(get("/api/v1/growth/tasks/daily")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-1004"));
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

    private JsonNode readDataNode(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private JsonNode findTaskByCode(JsonNode tasks, String taskCode) {
        for (JsonNode task : tasks) {
            if (taskCode.equals(task.path("taskCode").asText())) {
                return task;
            }
        }
        return objectMapper.createObjectNode();
    }

    private void insertCheckin(long userId, LocalDate checkinDate, int streakCount, int pointsEarned) {
        jdbcTemplate.update(
                "INSERT INTO checkins(student_user_id, checkin_date, streak_count, points_earned, created_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)",
                userId,
                checkinDate,
                streakCount,
                pointsEarned
        );
    }

    private void insertLedger(long userId, int deltaPoints, String reasonCode, int balanceAfter, LocalDate createdDate) {
        jdbcTemplate.update(
                "INSERT INTO points_ledger(student_user_id, delta_points, reason_code, balance_after, created_at) VALUES (?, ?, ?, ?, ?)",
                userId,
                deltaPoints,
                reasonCode,
                balanceAfter,
                java.sql.Timestamp.valueOf(createdDate.atTime(8, 0))
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
                    user_id, title, content, tags, scenario_code, resolved_status, moderation_status,
                    risk_level, last_moderation_event_id, is_deleted, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'GENERAL_HELP', 'OPEN', 'PASS', 'LOW', NULL, 0, ?, ?)
                """,
                userId,
                "今天的成长记录",
                "把简历和面试中的心得整理成一条社区分享。",
                "成长,复盘",
                timestamp,
                timestamp
        );
    }

    private void updateUserCreatedAt(long userId, LocalDateTime createdAt) {
        jdbcTemplate.update(
                "UPDATE users SET created_at = ?, updated_at = ? WHERE id = ?",
                Timestamp.valueOf(createdAt),
                Timestamp.valueOf(createdAt),
                userId
        );
    }
}
