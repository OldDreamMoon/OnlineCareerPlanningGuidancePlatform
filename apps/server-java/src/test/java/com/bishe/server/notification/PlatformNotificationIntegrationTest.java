package com.bishe.server.notification;

import com.bishe.server.notification.model.NotificationCategory;
import com.bishe.server.notification.model.NotificationPriority;
import com.bishe.server.notification.service.NotificationService;
import com.bishe.server.notification.service.PlatformNotificationPublishService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 平台通知系统后端基座集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformNotificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationProperties notificationProperties;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void notificationCenter_shouldSupportInboxPreferenceAndWsTicketFlow() throws Exception {
        registerUser("STUDENT", "notify-student@example.com", "Passw0rd!", "通知同学");
        String token = loginAndGetAccessToken("notify-student@example.com", "Passw0rd!");
        long userId = findUserIdByEmail("notify-student@example.com");

        notificationService.createNotification(
                userId,
                "CONSULT_REPLIED",
                "导师已回复你的咨询，请及时查看订单中的最新内容。",
                "ORD-10001"
        );

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1));

        MvcResult defaultPreferencesResult = mockMvc.perform(get("/api/v1/notifications/preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(4))
                .andReturn();

        JsonNode defaultPreferencesJson = objectMapper.readTree(defaultPreferencesResult.getResponse().getContentAsString());
        List<String> defaultCategories = StreamSupport.stream(defaultPreferencesJson.path("data").path("records").spliterator(), false)
                .map(item -> item.path("category").asText())
                .toList();
        assertThat(defaultCategories).containsExactly("AI_TASK", "CONSULT", "BOUNTY", "COMMUNITY");
        for (JsonNode item : defaultPreferencesJson.path("data").path("records")) {
            assertThat(item.path("emailEnabled").asBoolean()).isFalse();
        }

        MvcResult firstListResult = mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1))
                .andExpect(jsonPath("$.data.actionableCount").value(1))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].type").value("CONSULT_REPLIED"))
                .andExpect(jsonPath("$.data.records[0].category").value("CONSULT"))
                .andExpect(jsonPath("$.data.records[0].title").value("导师已回复你的咨询"))
                .andExpect(jsonPath("$.data.records[0].refType").value("CONSULT_ORDER"))
                .andExpect(jsonPath("$.data.records[0].refId").value("ORD-10001"))
                .andExpect(jsonPath("$.data.records[0].actionCode").value("VIEW_CONSULT_ORDER"))
                .andExpect(jsonPath("$.data.records[0].actionable").value(true))
                .andExpect(jsonPath("$.data.records[0].payload.legacyCompat").value(true))
                .andReturn();

        JsonNode firstListJson = objectMapper.readTree(firstListResult.getResponse().getContentAsString());
        long notificationId = firstListJson.path("data").path("records").get(0).path("id").asLong();
        assertThat(notificationId).isPositive();

        Long eventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_events WHERE type = 'CONSULT_REPLIED' AND source_id = ?",
                Long.class,
                "ORD-10001"
        );
        Long websocketJobCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_dispatch_jobs WHERE user_id = ? AND channel = 'WEBSOCKET'",
                Long.class,
                userId
        );
        assertThat(eventCount).isEqualTo(1L);
        assertThat(websocketJobCount).isEqualTo(1L);

        mockMvc.perform(post("/api/v1/notifications/{notificationId}/read", notificationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true));

        mockMvc.perform(put("/api/v1/notifications/preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "CONSULT",
                                  "websocketEnabled": false,
                                  "browserPopupEnabled": false,
                                  "emailEnabled": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category").value("CONSULT"))
                .andExpect(jsonPath("$.data.websocketEnabled").value(false))
                .andExpect(jsonPath("$.data.browserPopupEnabled").value(false))
                .andExpect(jsonPath("$.data.emailEnabled").value(false));

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderNo", "ORD-10002");
        payload.put("content", "学生已确认并关闭本次咨询订单。");
        notificationService.publish(new PlatformNotificationPublishService.NotificationPublishCommand(
                "CONSULT_CLOSED",
                NotificationCategory.CONSULT,
                "CONSULT_ORDER",
                "ORD-10002",
                null,
                List.of(userId),
                NotificationPriority.NORMAL,
                null,
                "学生已确认并关闭本次咨询订单。",
                null,
                "ORD-10002",
                null,
                payload,
                "platform-notify-test-consult-closed",
                Instant.now()
        ));

        Long totalJobCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_dispatch_jobs WHERE user_id = ?",
                Long.class,
                userId
        );
        Long totalNotificationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ?",
                Long.class,
                userId
        );
        assertThat(totalJobCount).isEqualTo(1L);
        assertThat(totalNotificationCount).isEqualTo(2L);

        mockMvc.perform(get("/api/v1/notifications/preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(4));

        Boolean consultWebsocketEnabled = jdbcTemplate.queryForObject(
                "SELECT websocket_enabled FROM notification_preferences WHERE user_id = ? AND category = 'CONSULT'",
                Boolean.class,
                userId
        );
        assertThat(consultWebsocketEnabled).isFalse();

        mockMvc.perform(post("/api/v1/notifications/ws-ticket")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ticket").isString())
                .andExpect(jsonPath("$.data.wsPath").value("/ws/notifications"))
                .andExpect(jsonPath("$.data.expiresAt").isNumber());

        mockMvc.perform(post("/api/v1/notifications/read-all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    @Test
    void notificationCenter_shouldReturnSanitizedAiTaskPayload() throws Exception {
        registerUser("STUDENT", "notify-ai-payload@example.com", "Passw0rd!", "通知摘要同学");
        String token = loginAndGetAccessToken("notify-ai-payload@example.com", "Passw0rd!");
        long userId = findUserIdByEmail("notify-ai-payload@example.com");

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", "AI-TASK-1001");
        payload.put("taskType", "RESUME");
        payload.put("status", "SUCCEEDED");
        payload.put("sceneCode", "RESUME_OPTIMIZE");
        payload.put("resultSummary", "本次简历诊断已完成，可继续查看优化建议。");
        payload.put("linkedRecordId", 12);

        notificationService.publish(new PlatformNotificationPublishService.NotificationPublishCommand(
                "AI_RESUME_TASK_SUCCEEDED",
                NotificationCategory.AI_TASK,
                "AI_ASYNC_TASK",
                "AI-TASK-1001",
                null,
                List.of(userId),
                NotificationPriority.NORMAL,
                null,
                "你的 AI 简历任务已完成，可前往通知中心查看结果摘要。",
                null,
                "AI-TASK-1001",
                "VIEW_AI_REVIEW_CENTER",
                payload,
                "platform-notify-test-ai-sanitized-payload",
                Instant.now()
        ));

        mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].category").value("AI_TASK"))
                .andExpect(jsonPath("$.data.records[0].payload.taskId").value("AI-TASK-1001"))
                .andExpect(jsonPath("$.data.records[0].payload.taskType").value("RESUME"))
                .andExpect(jsonPath("$.data.records[0].payload.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.records[0].payload.sceneCode").value("RESUME_OPTIMIZE"))
                .andExpect(jsonPath("$.data.records[0].payload.linkedRecordId").value(12))
                .andExpect(jsonPath("$.data.records[0].payload.resultSummary").value("本次简历诊断已完成，可继续查看优化建议。"));
    }

    @Test
    void notificationCenter_shouldSupportCategoryFilterAndActionableSummary() throws Exception {
        registerUser("STUDENT", "notify-category-filter@example.com", "Passw0rd!", "分类筛选同学");
        String token = loginAndGetAccessToken("notify-category-filter@example.com", "Passw0rd!");
        long userId = findUserIdByEmail("notify-category-filter@example.com");

        notificationService.createNotification(
                userId,
                "CONSULT_REPLIED",
                "导师已回复你的咨询，请回到订单继续查看。",
                "ORD-FILTER-1001"
        );
        notificationService.publish(new PlatformNotificationPublishService.NotificationPublishCommand(
                "SYSTEM_ANNOUNCEMENT",
                NotificationCategory.SYSTEM,
                "NOTIFICATION_CENTER",
                null,
                null,
                List.of(userId),
                NotificationPriority.NORMAL,
                null,
                "平台将于今晚进行例行维护，请提前保存你的操作。",
                null,
                null,
                "VIEW_NOTIFICATION_CENTER",
                Map.of("announcementId", "ANN-1001"),
                "platform-notify-test-system-filter",
                Instant.now()
        ));

        mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("category", "CONSULT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(2))
                .andExpect(jsonPath("$.data.actionableCount").value(1))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].category").value("CONSULT"))
                .andExpect(jsonPath("$.data.records[0].actionable").value(true));

        mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("category", "SYSTEM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(2))
                .andExpect(jsonPath("$.data.actionableCount").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].category").value("SYSTEM"))
                .andExpect(jsonPath("$.data.records[0].actionable").value(false));
    }

    @Test
    void notificationCenter_shouldRejectAdminRole() throws Exception {
        insertAdminUser("notify-admin@example.com", "通知管理员", "Passw0rd!");
        String token = loginAndGetAccessToken("notify-admin@example.com", "Passw0rd!");

        mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/notifications/preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/notifications/ws-ticket")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void notificationCenter_shouldSupportBootstrapAndIncrementalSync() throws Exception {
        registerUser("STUDENT", "notify-sync@example.com", "Passw0rd!", "同步同学");
        String token = loginAndGetAccessToken("notify-sync@example.com", "Passw0rd!");
        long userId = findUserIdByEmail("notify-sync@example.com");

        notificationService.createNotification(
                userId,
                "CONSULT_REPLIED",
                "导师已回复你的咨询，请查看最新消息。",
                "ORD-SYNC-1001"
        );
        notificationService.createNotification(
                userId,
                "CONSULT_REPLIED",
                "你的咨询又收到了一条新的处理进展。",
                "ORD-SYNC-1001-FOLLOW"
        );

        MvcResult bootstrapResult = mockMvc.perform(get("/api/v1/notifications/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].content").value("你的咨询又收到了一条新的处理进展。"))
                .andExpect(jsonPath("$.data.hasMore").value(false))
                .andExpect(jsonPath("$.data.unreadCount").value(2))
                .andReturn();

        JsonNode bootstrapData = objectMapper.readTree(bootstrapResult.getResponse().getContentAsString()).path("data");
        long afterId = bootstrapData.path("latestNotificationId").asLong();
        assertThat(afterId).isPositive();

        notificationService.publish(new PlatformNotificationPublishService.NotificationPublishCommand(
                "SYSTEM_ANNOUNCEMENT",
                NotificationCategory.SYSTEM,
                "NOTIFICATION_CENTER",
                "ANN-SYNC-1002",
                null,
                List.of(userId),
                NotificationPriority.NORMAL,
                "同步公告",
                "你重新上线后应主动补齐这条公告。",
                null,
                "ANN-SYNC-1002",
                "VIEW_NOTIFICATION_CENTER",
                Map.of("announcementId", "ANN-SYNC-1002"),
                "platform-notify-test-sync-connected",
                Instant.now()
        ));

        mockMvc.perform(get("/api/v1/notifications/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("afterId", String.valueOf(afterId))
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].title").value("同步公告"))
                .andExpect(jsonPath("$.data.records[0].actionCode").value("VIEW_NOTIFICATION_CENTER"))
                .andExpect(jsonPath("$.data.unreadCount").value(3))
                .andExpect(jsonPath("$.data.latestNotificationId").isNumber())
                .andExpect(jsonPath("$.data.hasMore").value(false));
    }

    @Test
    void notificationPreferences_shouldBeRoleScopedAndSystemAnnouncementShouldIgnoreLegacyRows() throws Exception {
        registerUser("STUDENT", "notify-system-locked@example.com", "Passw0rd!", "系统公告同学");
        String token = loginAndGetAccessToken("notify-system-locked@example.com", "Passw0rd!");
        long userId = findUserIdByEmail("notify-system-locked@example.com");

        jdbcTemplate.update(
                """
                INSERT INTO notification_preferences(
                    user_id, category, inbox_enabled, websocket_enabled, browser_popup_enabled,
                    email_enabled, email_urgency_threshold, quiet_hours_json, updated_at
                ) VALUES (?, 'SYSTEM', TRUE, FALSE, FALSE, FALSE, 'URGENT', '{\"mute\":true}', CURRENT_TIMESTAMP)
                """,
                userId
        );

        MvcResult preferencesResult = mockMvc.perform(get("/api/v1/notifications/preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(4))
                .andReturn();

        JsonNode preferencesJson = objectMapper.readTree(preferencesResult.getResponse().getContentAsString());
        List<String> scopedCategories = StreamSupport.stream(preferencesJson.path("data").path("records").spliterator(), false)
                .map(item -> item.path("category").asText())
                .toList();
        assertThat(scopedCategories)
                .containsExactly("AI_TASK", "CONSULT", "BOUNTY", "COMMUNITY")
                .doesNotContain("SYSTEM", "CERTIFICATION");

        mockMvc.perform(put("/api/v1/notifications/preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "SYSTEM",
                                  "websocketEnabled": false
                                }
                                """))
                .andExpect(status().isBadRequest());

        String previousApiKey = notificationProperties.getEmail().getResendApiKey();
        String previousFromEmail = notificationProperties.getEmail().getResendFromEmail();
        String previousApiUrl = notificationProperties.getEmail().getResendApiUrl();
        notificationProperties.getEmail().setResendApiKey("test-resend-key");
        notificationProperties.getEmail().setResendFromEmail("notify@test.local");
        notificationProperties.getEmail().setResendApiUrl("http://127.0.0.1:9/emails");

        try {
            notificationService.publish(new PlatformNotificationPublishService.NotificationPublishCommand(
                    "SYSTEM_ANNOUNCEMENT",
                    NotificationCategory.SYSTEM,
                    "NOTIFICATION_CENTER",
                    "ANN-LOCKED-1001",
                    null,
                    List.of(userId),
                    NotificationPriority.NORMAL,
                    "平台公告",
                    "今晚 23:00 将进行例行维护，请提前保存你的操作。",
                    null,
                    "ANN-LOCKED-1001",
                    "VIEW_NOTIFICATION_CENTER",
                    Map.of("announcementId", "ANN-LOCKED-1001"),
                    "notify-system-locked-defaults",
                    Instant.now()
            ));

            Long websocketJobCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM notification_dispatch_jobs WHERE user_id = ? AND channel = 'WEBSOCKET'",
                    Long.class,
                    userId
            );
            Long emailJobCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM notification_dispatch_jobs WHERE user_id = ? AND channel = 'EMAIL'",
                    Long.class,
                    userId
            );
            String websocketPayload = jdbcTemplate.queryForObject(
                    "SELECT payload_json FROM notification_dispatch_jobs WHERE user_id = ? AND channel = 'WEBSOCKET' ORDER BY id DESC LIMIT 1",
                    String.class,
                    userId
            );
            Integer websocketMaxAttempts = jdbcTemplate.queryForObject(
                    "SELECT max_attempts FROM notification_dispatch_jobs WHERE user_id = ? AND channel = 'WEBSOCKET' ORDER BY id DESC LIMIT 1",
                    Integer.class,
                    userId
            );

            assertThat(websocketJobCount).isEqualTo(1L);
            assertThat(emailJobCount).isEqualTo(1L);
            assertThat(websocketMaxAttempts).isEqualTo(10);
            assertThat(objectMapper.readTree(websocketPayload).path("browserPopupAllowed").asBoolean()).isTrue();
        } finally {
            notificationProperties.getEmail().setResendApiKey(previousApiKey);
            notificationProperties.getEmail().setResendFromEmail(previousFromEmail);
            notificationProperties.getEmail().setResendApiUrl(previousApiUrl);
        }
    }

    @Test
    void notificationPublishCommand_shouldRespectExplicitEmailRequestFlag() throws Exception {
        registerUser("STUDENT", "notify-email-override@example.com", "Passw0rd!", "邮件覆盖同学");
        long userId = findUserIdByEmail("notify-email-override@example.com");

        jdbcTemplate.update(
                """
                INSERT INTO notification_preferences(
                    user_id, category, inbox_enabled, websocket_enabled, browser_popup_enabled,
                    email_enabled, email_urgency_threshold, quiet_hours_json, updated_at
                ) VALUES (?, 'BOUNTY', TRUE, FALSE, FALSE, TRUE, 'URGENT', NULL, CURRENT_TIMESTAMP)
                """,
                userId
        );

        String previousApiKey = notificationProperties.getEmail().getResendApiKey();
        String previousFromEmail = notificationProperties.getEmail().getResendFromEmail();
        String previousApiUrl = notificationProperties.getEmail().getResendApiUrl();
        notificationProperties.getEmail().setResendApiKey("test-resend-key");
        notificationProperties.getEmail().setResendFromEmail("notify@test.local");
        notificationProperties.getEmail().setResendApiUrl("http://127.0.0.1:9/emails");

        try {
            notificationService.publish(new PlatformNotificationPublishService.NotificationPublishCommand(
                    "BOUNTY_REVIEWED",
                    NotificationCategory.BOUNTY,
                    "BOUNTY_TASK",
                    "TASK-9001",
                    null,
                    List.of(userId),
                    NotificationPriority.NORMAL,
                    null,
                    "这次审核结果只保留站内通知。",
                    null,
                    "TASK-9001",
                    null,
                    Map.of("taskId", "TASK-9001", "reviewStatus", "REJECTED"),
                    Boolean.FALSE,
                    "notify-explicit-email-false",
                    Instant.now()
            ));

            notificationService.publish(new PlatformNotificationPublishService.NotificationPublishCommand(
                    "BOUNTY_REVIEWED",
                    NotificationCategory.BOUNTY,
                    "BOUNTY_TASK",
                    "TASK-9002",
                    null,
                    List.of(userId),
                    NotificationPriority.NORMAL,
                    null,
                    "这次审核结果会同步邮件提醒。",
                    null,
                    "TASK-9002",
                    null,
                    Map.of("taskId", "TASK-9002", "reviewStatus", "ACCEPTED"),
                    Boolean.TRUE,
                    "notify-explicit-email-true",
                    Instant.now()
            ));

            Long emailJobCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM notification_dispatch_jobs WHERE user_id = ? AND channel = 'EMAIL'",
                    Long.class,
                    userId
            );
            Long bountyNotificationCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = 'BOUNTY_REVIEWED'",
                    Long.class,
                    userId
            );

            assertThat(bountyNotificationCount).isEqualTo(2L);
            assertThat(emailJobCount).isEqualTo(1L);
        } finally {
            notificationProperties.getEmail().setResendApiKey(previousApiKey);
            notificationProperties.getEmail().setResendFromEmail(previousFromEmail);
            notificationProperties.getEmail().setResendApiUrl(previousApiUrl);
        }
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

    private long insertAdminUser(String email, String displayName, String password) {
        jdbcTemplate.update(
                """
                INSERT INTO users(
                    email, password_hash, role, tier, status, display_name, real_name,
                    last_login_at, is_deleted, created_at, updated_at
                ) VALUES (?, ?, 'ADMIN', 'FREE', 'ACTIVE', ?, ?, NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                email,
                passwordEncoder.encode(password),
                displayName,
                displayName
        );
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        assertThat(userId).isNotNull();
        return userId;
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
}
