package com.bishe.server.notification;

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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理员通知运营台集成回归测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminNotificationOpsIntegrationTest {

    private static final String DEAD_JOB_ID = "admin-notify-dead-job-001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void adminNotificationOps_shouldSupportOverviewAnnouncementListAndRetry() throws Exception {
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");
        NotificationOverviewMetrics baselineOverview = getOverview(adminToken);

        registerUser("STUDENT", "admin-notify-student@example.com", "Passw0rd!", "通知治理学生");
        String studentToken = loginAndGetAccessToken("admin-notify-student@example.com", "Passw0rd!");
        long studentUserId = findUserIdByEmail("admin-notify-student@example.com");

        AnnouncementPublishResult publishResult = publishAnnouncement(adminToken);
        long notificationId = findNotificationId(publishResult.eventId(), studentUserId);
        insertDeadDispatchJob(notificationId, publishResult.eventId(), studentUserId);

        NotificationOverviewMetrics afterSetupOverview = getOverview(adminToken);
        assertThat(afterSetupOverview.announcementCount()).isEqualTo(baselineOverview.announcementCount() + 1);
        assertThat(afterSetupOverview.announcementsLast7Days()).isEqualTo(baselineOverview.announcementsLast7Days() + 1);
        assertThat(afterSetupOverview.deadJobCount()).isEqualTo(baselineOverview.deadJobCount() + 1);

        mockMvc.perform(get("/api/v1/admin/notifications/announcements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.records[?(@.eventId=='%s')].title".formatted(publishResult.eventId()))
                        .value(contains("管理员通知运营公告测试")))
                .andExpect(jsonPath("$.data.records[?(@.eventId=='%s')].deliveryChannels".formatted(publishResult.eventId()))
                        .value(contains(List.of("IN_APP"))))
                .andExpect(jsonPath("$.data.records[?(@.eventId=='%s')].actionCode".formatted(publishResult.eventId()))
                        .value(contains("VIEW_NOTIFICATION_CENTER")));

        mockMvc.perform(get("/api/v1/admin/notifications/dispatch-jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("page", "1")
                        .param("size", "10")
                        .param("status", "DEAD")
                        .param("channel", "EMAIL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.records[?(@.jobId=='%s')].status".formatted(DEAD_JOB_ID)).value(contains("DEAD")))
                .andExpect(jsonPath("$.data.records[?(@.jobId=='%s')].userId".formatted(DEAD_JOB_ID)).value(contains((int) studentUserId)));

        mockMvc.perform(post("/api/v1/admin/notifications/dispatch-jobs/{jobId}/retry", DEAD_JOB_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("dispatch job requeued"))
                .andExpect(jsonPath("$.data.jobId").value(DEAD_JOB_ID))
                .andExpect(jsonPath("$.data.status").value("RETRY_WAIT"))
                .andExpect(jsonPath("$.data.nextRunAt").isNumber());

        NotificationOverviewMetrics afterRetryOverview = getOverview(adminToken);
        assertThat(afterRetryOverview.deadJobCount()).isEqualTo(afterSetupOverview.deadJobCount() - 1);
        assertThat(afterRetryOverview.retryJobCount()).isGreaterThanOrEqualTo(afterSetupOverview.retryJobCount() + 1);

        mockMvc.perform(get("/api/v1/admin/notifications/dispatch-jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("page", "1")
                        .param("size", "10")
                        .param("status", "RETRY_WAIT")
                        .param("channel", "EMAIL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.jobId=='%s')].status".formatted(DEAD_JOB_ID)).value(contains("RETRY_WAIT")));

        mockMvc.perform(get("/api/v1/admin/notifications/overview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-1004"));
    }

    @Test
    void adminNotificationOps_shouldPurgeOldTerminalDispatchJobs() throws Exception {
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");

        registerUser("STUDENT", "admin-notify-purge@example.com", "Passw0rd!", "通知清理学生");
        long studentUserId = findUserIdByEmail("admin-notify-purge@example.com");

        AnnouncementPublishResult publishResult = publishAnnouncement(adminToken);
        long notificationId = findNotificationId(publishResult.eventId(), studentUserId);
        insertDeadDispatchJob(notificationId, publishResult.eventId(), studentUserId);
        long dispatchJobRowId = findDispatchJobRowId(DEAD_JOB_ID);
        insertDispatchAttempt(dispatchJobRowId);

        MvcResult purgeResult = mockMvc.perform(post("/api/v1/admin/notifications/dispatch-jobs/purge-terminal")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("olderThanHours", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("terminal dispatch jobs purged"))
                .andExpect(jsonPath("$.data.deletedCount").isNumber())
                .andExpect(jsonPath("$.data.deletedBeforeAt").isNumber())
                .andReturn();

        long deletedCount = objectMapper.readTree(purgeResult.getResponse().getContentAsString())
                .path("data")
                .path("deletedCount")
                .asLong();
        assertThat(deletedCount).isGreaterThanOrEqualTo(1L);

        Long remainingJobCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_dispatch_jobs WHERE job_id = ?",
                Long.class,
                DEAD_JOB_ID
        );
        Long remainingAttemptCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_dispatch_attempts WHERE job_id = ?",
                Long.class,
                dispatchJobRowId
        );
        assertThat(remainingJobCount).isZero();
        assertThat(remainingAttemptCount).isZero();
    }

    @Test
    void adminNotificationOps_shouldExposeEmailChannelWhenAnnouncementRequestsEmail() throws Exception {
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");
        registerUser("STUDENT", "admin-notify-email@example.com", "Passw0rd!", "通知邮件学生");

        AnnouncementPublishResult publishResult = publishAnnouncement(adminToken, true);

        mockMvc.perform(get("/api/v1/admin/notifications/announcements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.records[?(@.eventId=='%s')].deliveryChannels".formatted(publishResult.eventId()))
                        .value(contains(List.of("IN_APP", "EMAIL"))));
    }

    private void registerUser(String role, String email, String password, String displayName) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "%s",
                                  "email": "%s",
                                  "password": "%s",
                                  "displayName": "%s"
                                }
                                """.formatted(role, email, password, displayName)))
                .andExpect(status().isOk());
    }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();
        assertThat(accessToken).isNotBlank();
        return accessToken;
    }

    private NotificationOverviewMetrics getOverview(String adminToken) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/admin/notifications/overview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return new NotificationOverviewMetrics(
                data.path("announcementCount").asLong(),
                data.path("announcementsLast7Days").asLong(),
                data.path("retryJobCount").asLong(),
                data.path("deadJobCount").asLong()
        );
    }

    private AnnouncementPublishResult publishAnnouncement(String adminToken) throws Exception {
        return publishAnnouncement(adminToken, false);
    }

    private AnnouncementPublishResult publishAnnouncement(String adminToken, boolean emailRequested) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/notifications/announcements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "管理员通知运营公告测试",
                                  "content": "用于验证通知运营台公告列表与概览统计。",
                                  "targetRoles": ["STUDENT"],
                                  "priority": "HIGH",
                                  "actionCode": "VIEW_NOTIFICATION_CENTER",
                                  "emailRequested": %s
                                }
                                """.formatted(emailRequested)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("system announcement published"))
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        String eventId = data.path("eventId").asText();
        long notificationCount = data.path("notificationCount").asLong();
        assertThat(eventId).isNotBlank();
        assertThat(notificationCount).isPositive();
        return new AnnouncementPublishResult(eventId, notificationCount);
    }

    private long findUserIdByEmail(String email) {
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        assertThat(userId).isNotNull();
        return userId;
    }

    private long findNotificationId(String eventId, long userId) {
        Long notificationId = jdbcTemplate.queryForObject(
                """
                SELECT id
                  FROM notifications
                 WHERE event_id = ?
                   AND user_id = ?
                 ORDER BY id DESC
                 LIMIT 1
                """,
                Long.class,
                eventId,
                userId
        );
        assertThat(notificationId).isNotNull();
        return notificationId;
    }

    private void insertDeadDispatchJob(long notificationId, String eventId, long userId) {
        jdbcTemplate.update(
                """
                INSERT INTO notification_dispatch_jobs(
                    job_id,
                    notification_id,
                    event_id,
                    user_id,
                    channel,
                    status,
                    attempt_count,
                    max_attempts,
                    next_run_at,
                    failed_at,
                    error_code,
                    error_message,
                    payload_json,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, 'EMAIL', 'DEAD', 3, 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?, '{}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                DEAD_JOB_ID,
                notificationId,
                eventId,
                userId,
                "NOTIFY-EMAIL-500",
                "模拟邮件派发失败"
        );
    }

    private long findDispatchJobRowId(String jobId) {
        Long dispatchJobRowId = jdbcTemplate.queryForObject(
                "SELECT id FROM notification_dispatch_jobs WHERE job_id = ?",
                Long.class,
                jobId
        );
        assertThat(dispatchJobRowId).isNotNull();
        return dispatchJobRowId;
    }

    private void insertDispatchAttempt(long dispatchJobRowId) {
        jdbcTemplate.update(
                """
                INSERT INTO notification_dispatch_attempts(
                    job_id,
                    attempt_no,
                    status,
                    request_snapshot_json,
                    response_snapshot_json,
                    error_code,
                    error_message,
                    latency_ms,
                    created_at
                )
                VALUES (?, 1, 'DEAD', '{}', '{}', 'NOTIFY-EMAIL-500', '模拟邮件派发失败', 12, CURRENT_TIMESTAMP)
                """,
                dispatchJobRowId
        );
    }

    private record NotificationOverviewMetrics(
            long announcementCount,
            long announcementsLast7Days,
            long retryJobCount,
            long deadJobCount
    ) {
    }

    private record AnnouncementPublishResult(
            String eventId,
            long notificationCount
    ) {
    }
}
