package com.bishe.server.notification;

import com.bishe.server.ai.gateway.AiExecutionMode;
import com.bishe.server.ai.gateway.task.AiAsyncTaskService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 平台通知系统业务接入测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformNotificationBusinessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AiAsyncTaskService aiAsyncTaskService;

    @Test
    void aiAsyncTaskLifecycle_shouldCreatePlatformNotifications() throws Exception {
        registerUser("STUDENT", "notify-ai-student@example.com", "Passw0rd!", "AI 通知同学");
        long userId = findUserIdByEmail("notify-ai-student@example.com");

        AiAsyncTaskService.TaskTicket successTicket = aiAsyncTaskService.submitTask(new AiAsyncTaskService.SubmitTaskCommand(
                userId,
                "RESUME",
                "RESUME_REVIEW",
                "resume-async-task-test",
                AiExecutionMode.ASYNC_JOB.name(),
                "mock-provider",
                "MOCK",
                "mock-economy-model",
                "resume_async_test",
                1,
                "{}",
                "{}",
                "{}",
                "{}",
                2
        ));
        long successJobId = findAsyncTaskJobId(successTicket.taskId());
        aiAsyncTaskService.markSucceeded(successJobId, "简历复盘已生成", "{\"ok\":true}");

        AiAsyncTaskService.TaskTicket failedTicket = aiAsyncTaskService.submitTask(new AiAsyncTaskService.SubmitTaskCommand(
                userId,
                "RESUME",
                "RESUME_REVIEW",
                "resume-async-task-test",
                AiExecutionMode.ASYNC_JOB.name(),
                "mock-provider",
                "MOCK",
                "mock-economy-model",
                "resume_async_test",
                1,
                "{}",
                "{}",
                "{}",
                "{}",
                2
        ));
        long failedJobId = findAsyncTaskJobId(failedTicket.taskId());
        aiAsyncTaskService.markFailed(failedJobId, "AI-FAILED", "mock execution failed");

        Long successNotificationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = 'AI_RESUME_TASK_SUCCEEDED'",
                Long.class,
                userId
        );
        Long failedNotificationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = 'AI_RESUME_TASK_FAILED'",
                Long.class,
                userId
        );
        assertThat(successNotificationCount).isEqualTo(1L);
        assertThat(failedNotificationCount).isEqualTo(1L);
    }

    @Test
    void certificationReviewAndAdminAnnouncement_shouldCreatePlatformNotifications() throws Exception {
        registerUser("MENTOR", "notify-mentor-cert@example.com", "Passw0rd!", "认证导师");
        long mentorUserId = findUserIdByEmail("notify-mentor-cert@example.com");
        long submissionId = insertCertificationSubmission(mentorUserId, "MENTOR", "林老师", "字节跳动", "前端导师");
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");

        mockMvc.perform(post("/api/v1/admin/users/{userId}/certification-review", mentorUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approvalStatus": "REJECTED",
                                  "reviewNote": "请补充更清晰的工牌或在职证明照片后重新提交。"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentSubmission.submissionId").value(submissionId))
                .andExpect(jsonPath("$.data.currentSubmission.status").value("REJECTED"));

        Long certificationNotificationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = 'CERTIFICATION_RESUBMIT_REQUIRED'",
                Long.class,
                mentorUserId
        );
        assertThat(certificationNotificationCount).isEqualTo(1L);

        registerUser("STUDENT", "notify-announcement-student@example.com", "Passw0rd!", "公告学生");
        registerUser("ENTERPRISE", "notify-announcement-enterprise@example.com", "Passw0rd!", "公告企业");
        long studentUserId = findUserIdByEmail("notify-announcement-student@example.com");
        long enterpriseUserId = findUserIdByEmail("notify-announcement-enterprise@example.com");
        Long expectedAnnouncementTargetCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                  FROM users
                 WHERE is_deleted = 0
                   AND status = 'ACTIVE'
                   AND role IN ('STUDENT', 'ENTERPRISE')
                """,
                Long.class
        );
        assertThat(expectedAnnouncementTargetCount).isNotNull();

        mockMvc.perform(post("/api/v1/admin/notifications/announcements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "系统公告测试",
                                  "content": "今晚 23:00 将进行一次短时维护，请提前保存进度。",
                                  "targetRoles": ["STUDENT", "ENTERPRISE"],
                                  "priority": "HIGH",
                                  "actionCode": "VIEW_NOTIFICATION_CENTER"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notificationCount").value(expectedAnnouncementTargetCount))
                .andExpect(jsonPath("$.data.eventId").isString());

        Long studentAnnouncementCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = 'SYSTEM_ANNOUNCEMENT'",
                Long.class,
                studentUserId
        );
        Long enterpriseAnnouncementCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = 'SYSTEM_ANNOUNCEMENT'",
                Long.class,
                enterpriseUserId
        );
        assertThat(studentAnnouncementCount).isEqualTo(1L);
        assertThat(enterpriseAnnouncementCount).isEqualTo(1L);
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
                .andExpect(status().isOk());
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
        return jsonNode.path("data").path("accessToken").asText();
    }

    private long findUserIdByEmail(String email) {
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        assertThat(userId).isNotNull();
        return userId;
    }

    private long findAsyncTaskJobId(String taskId) {
        Long jobId = jdbcTemplate.queryForObject("SELECT id FROM ai_async_task_jobs WHERE task_id = ?", Long.class, taskId);
        assertThat(jobId).isNotNull();
        return jobId;
    }

    private long insertCertificationSubmission(long userId, String role, String realName, String companyName, String jobTitle) {
        Long mentorProfileCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mentor_profiles WHERE user_id = ?",
                Long.class,
                userId
        );
        assertThat(mentorProfileCount).isNotNull();
        if (mentorProfileCount > 0) {
            jdbcTemplate.update(
                    """
                    UPDATE mentor_profiles
                       SET company_name = ?,
                           job_title = ?,
                           approval_status = 'PENDING',
                           updated_at = CURRENT_TIMESTAMP
                     WHERE user_id = ?
                    """,
                    companyName,
                    jobTitle,
                    userId
            );
        } else {
            jdbcTemplate.update(
                    "INSERT INTO mentor_profiles(user_id, company_name, job_title, approval_status, created_at, updated_at) VALUES (?, ?, ?, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    userId,
                    companyName,
                    jobTitle
            );
        }
        jdbcTemplate.update(
                """
                INSERT INTO certification_submissions(
                    user_id, user_role, real_name, company_name, job_title, status, is_current, submitted_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 'PENDING', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                userId,
                role,
                realName,
                companyName,
                jobTitle
        );
        Long submissionId = jdbcTemplate.queryForObject(
                "SELECT id FROM certification_submissions WHERE user_id = ? AND is_current = 1 ORDER BY id DESC LIMIT 1",
                Long.class,
                userId
        );
        assertThat(submissionId).isNotNull();
        jdbcTemplate.update(
                """
                INSERT INTO certification_submission_assets(
                    submission_id, storage_bucket, object_key, original_filename, content_type, size_bytes, lifecycle_status, created_at, updated_at
                ) VALUES (?, 'test-bucket', ?, 'mentor-proof.pdf', 'application/pdf', 128, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                submissionId,
                "cert/test/" + submissionId + ".pdf"
        );
        return submissionId;
    }
}
