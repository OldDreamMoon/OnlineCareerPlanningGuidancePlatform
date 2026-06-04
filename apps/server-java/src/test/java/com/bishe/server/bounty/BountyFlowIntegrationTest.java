package com.bishe.server.bounty;

import com.bishe.server.notification.NotificationProperties;
import com.bishe.server.notification.service.NotificationPreferenceCacheService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BountyFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NotificationProperties notificationProperties;

    @Autowired
    private NotificationPreferenceCacheService notificationPreferenceCacheService;

    @Test
    void bountyFlow_shouldSupportCreateListDetailAndSubmit() throws Exception {
        registerUser("ENTERPRISE", "bounty-enterprise@example.com", "Passw0rd!", "EnterpriseOne");
        registerUser("STUDENT", "bounty-student@example.com", "Passw0rd!", "StudentOne");
        String enterpriseToken = loginAndGetAccessToken("bounty-enterprise@example.com", "Passw0rd!");
        String studentToken = loginAndGetAccessToken("bounty-student@example.com", "Passw0rd!");

        MvcResult createTaskResult = mockMvc.perform(post("/api/v1/bounty/tasks")
                        .header("Authorization", "Bearer " + enterpriseToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "前端项目实战",
                                  "description": "完成一个企业展示页并提交说明。",
                                  "rewardDescription": "实习内推机会"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andReturn();

        long taskId = readLong(createTaskResult, "data", "taskId");
        assertThat(taskId).isPositive();

        mockMvc.perform(get("/api/v1/bounty/tasks")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].taskId").value(taskId))
                .andExpect(jsonPath("$.data.records[0].title").value("前端项目实战"));

        mockMvc.perform(get("/api/v1/bounty/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.mySubmission").doesNotExist());

        mockMvc.perform(post("/api/v1/bounty/tasks/{taskId}/submissions", taskId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "contentText": "已完成 Figma 原型与 React 页面实现。",
                                  "attachmentLinks": ["https://github.com/demo/project", "https://demo.example.com"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));

        mockMvc.perform(get("/api/v1/bounty/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mySubmission.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.mySubmission.attachmentLinks[0]").value("https://github.com/demo/project"));
    }

    @Test
    void bountyTaskList_shouldUsePublicCacheAndMergeSubmittedStateAfterCreateSubmission() throws Exception {
        registerUser("ENTERPRISE", "bounty-list-enterprise@example.com", "Passw0rd!", "BountyEnterprise");
        registerUser("STUDENT", "bounty-list-student@example.com", "Passw0rd!", "BountyStudent");
        String enterpriseToken = loginAndGetAccessToken("bounty-list-enterprise@example.com", "Passw0rd!");
        String studentToken = loginAndGetAccessToken("bounty-list-student@example.com", "Passw0rd!");

        long taskId = createTask(enterpriseToken, "缓存悬赏任务", "用于验证悬赏公开列表缓存。", "优先面试");

        mockMvc.perform(get("/api/v1/bounty/tasks")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("page", "1")
                        .param("size", "10")
                        .param("keyword", "缓存悬赏任务"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].taskId").value(taskId))
                .andExpect(jsonPath("$.data.records[0].submissionCount").value(0))
                .andExpect(jsonPath("$.data.records[0].submittedByMe").value(false));

        mockMvc.perform(post("/api/v1/bounty/tasks/{taskId}/submissions", taskId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "contentText": "我已经完成缓存任务提交。",
                                  "attachmentLinks": ["https://demo.example.com/cache-task"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));

        mockMvc.perform(get("/api/v1/bounty/tasks")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("page", "1")
                        .param("size", "10")
                        .param("keyword", "缓存悬赏任务"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].taskId").value(taskId))
                .andExpect(jsonPath("$.data.records[0].submissionCount").value(1))
                .andExpect(jsonPath("$.data.records[0].submittedByMe").value(true));
    }

    @Test
    void bountyTaskList_mineOnlyShouldBypassPublicCache() throws Exception {
        registerUser("ENTERPRISE", "bounty-mine-a@example.com", "Passw0rd!", "MineEnterpriseA");
        registerUser("ENTERPRISE", "bounty-mine-b@example.com", "Passw0rd!", "MineEnterpriseB");
        registerUser("STUDENT", "bounty-mine-student@example.com", "Passw0rd!", "MineStudent");
        String enterpriseAToken = loginAndGetAccessToken("bounty-mine-a@example.com", "Passw0rd!");
        String enterpriseBToken = loginAndGetAccessToken("bounty-mine-b@example.com", "Passw0rd!");
        String studentToken = loginAndGetAccessToken("bounty-mine-student@example.com", "Passw0rd!");

        long taskAId = createTask(enterpriseAToken, "企业 A 的任务", "A 的公开任务。", "A 奖励");
        createTask(enterpriseBToken, "企业 B 的任务", "B 的公开任务。", "B 奖励");

        mockMvc.perform(get("/api/v1/bounty/tasks")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));

        mockMvc.perform(get("/api/v1/bounty/tasks")
                        .header("Authorization", "Bearer " + enterpriseAToken)
                        .param("page", "1")
                        .param("size", "10")
                        .param("mineOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].taskId").value(taskAId))
                .andExpect(jsonPath("$.data.records[0].title").value("企业 A 的任务"));
    }

    @Test
    void bountyTaskDetail_shouldUsePublicCacheAndRefreshAfterSubmission() throws Exception {
        registerUser("ENTERPRISE", "bounty-detail-enterprise@example.com", "Passw0rd!", "DetailEnterprise");
        registerUser("STUDENT", "bounty-detail-student@example.com", "Passw0rd!", "DetailStudent");
        String enterpriseToken = loginAndGetAccessToken("bounty-detail-enterprise@example.com", "Passw0rd!");
        String studentToken = loginAndGetAccessToken("bounty-detail-student@example.com", "Passw0rd!");

        long taskId = createTask(enterpriseToken, "缓存详情任务", "用于验证详情公共主体缓存。", "优先推荐");

        mockMvc.perform(get("/api/v1/bounty/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("缓存详情任务"))
                .andExpect(jsonPath("$.data.mine").value(false))
                .andExpect(jsonPath("$.data.mySubmission").doesNotExist());

        jdbcTemplate.update(
                "UPDATE bounty_tasks SET title = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                "缓存详情任务-数据库直改",
                taskId
        );

        mockMvc.perform(get("/api/v1/bounty/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("缓存详情任务"));

        mockMvc.perform(get("/api/v1/bounty/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + enterpriseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("缓存详情任务"))
                .andExpect(jsonPath("$.data.mine").value(true));

        createSubmission(studentToken, taskId, "我补充了缓存详情验证材料。", "https://demo.example.com/detail-cache");

        mockMvc.perform(get("/api/v1/bounty/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("缓存详情任务-数据库直改"))
                .andExpect(jsonPath("$.data.submissionCount").value(1))
                .andExpect(jsonPath("$.data.mySubmission.status").value("SUBMITTED"));
    }

    @Test
    void bountyReview_shouldSupportPortraitFilteringAndAcceptance() throws Exception {
        String previousApiKey = notificationProperties.getEmail().getResendApiKey();
        String previousFromEmail = notificationProperties.getEmail().getResendFromEmail();
        String previousApiUrl = notificationProperties.getEmail().getResendApiUrl();
        notificationProperties.getEmail().setResendApiKey("test-resend-key");
        notificationProperties.getEmail().setResendFromEmail("notify@test.local");
        notificationProperties.getEmail().setResendApiUrl("http://127.0.0.1:9/emails");

        try {
        registerUser("ENTERPRISE", "review-enterprise@example.com", "Passw0rd!", "ReviewEnterprise");
        registerUser("STUDENT", "review-student-a@example.com", "Passw0rd!", "StudentA");
        registerUser("STUDENT", "review-student-b@example.com", "Passw0rd!", "StudentB");
        String enterpriseToken = loginAndGetAccessToken("review-enterprise@example.com", "Passw0rd!");
        String studentAToken = loginAndGetAccessToken("review-student-a@example.com", "Passw0rd!");
        String studentBToken = loginAndGetAccessToken("review-student-b@example.com", "Passw0rd!");

        long enterpriseUserId = findUserIdByEmail("review-enterprise@example.com");
        long studentAUserId = findUserIdByEmail("review-student-a@example.com");
        long studentBUserId = findUserIdByEmail("review-student-b@example.com");
        enableBountyEmailPreference(studentAUserId);
        enableBountyEmailPreference(studentBUserId);

        long taskId = createTask(enterpriseToken, "企业活动页优化", "提交一个活动页落地方案。", "优先面试机会");
        insertPortraitSnapshot(studentAUserId, """
                [{"code":"COMMUNITY_ACTIVE","label":"社区互动积极","source":"COMMUNITY","confidence":0.92}]
                """);
        insertPortraitSnapshot(studentBUserId, """
                [{"code":"ALGO_BASE","label":"算法基础扎实","source":"PROFILE","confidence":0.88}]
                """);
        insertPostAndLike(studentAUserId, enterpriseUserId);

        long submissionA = createSubmission(studentAToken, taskId, "我完成了活动页高保真稿和实现说明。", "https://demo.example.com/a");
        createSubmission(studentBToken, taskId, "我提交了活动页 React 工程。", "https://demo.example.com/b");

        mockMvc.perform(get("/api/v1/bounty/tasks/{taskId}/submissions", taskId)
                        .header("Authorization", "Bearer " + enterpriseToken)
                        .param("portraitTag", "社区互动积极")
                        .param("minCommunityScore7d", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].studentUserId").value(studentAUserId))
                .andExpect(jsonPath("$.data.records[0].communityScore7d").value(8))
                .andExpect(jsonPath("$.data.records[0].portraitTags[0]").value("社区互动积极"));

        mockMvc.perform(post("/api/v1/bounty/submissions/{submissionId}/review", submissionA)
                        .header("Authorization", "Bearer " + enterpriseToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "ACCEPT",
                                  "contactIntent": "WECHAT",
                                  "comment": "方案完整，进入优先推荐名单。",
                                  "syncEmailReminder": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.taskStatus").value("CLOSED"));

        mockMvc.perform(post("/api/v1/bounty/submissions/{submissionId}/review", submissionA)
                        .header("Authorization", "Bearer " + enterpriseToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "REJECT",
                                  "comment": "重复操作"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BIZ-1001"));

        mockMvc.perform(get("/api/v1/bounty/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.mySubmission.status").value("ACCEPTED"));

        mockMvc.perform(get("/api/v1/bounty/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mySubmission.status").value("REJECTED"));

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + studentAToken)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].type").value("BOUNTY_REVIEWED"));

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + studentBToken)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].type").value("BOUNTY_REVIEWED"));

        Long studentAEmailJobs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_dispatch_jobs WHERE user_id = ? AND channel = 'EMAIL'",
                Long.class,
                studentAUserId
        );
        Long studentBEmailJobs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_dispatch_jobs WHERE user_id = ? AND channel = 'EMAIL'",
                Long.class,
                studentBUserId
        );
        assertThat(studentAEmailJobs).isEqualTo(1L);
        assertThat(studentBEmailJobs).isEqualTo(1L);
        } finally {
            notificationProperties.getEmail().setResendApiKey(previousApiKey);
            notificationProperties.getEmail().setResendFromEmail(previousFromEmail);
            notificationProperties.getEmail().setResendApiUrl(previousApiUrl);
        }
    }

    @Test
    void bountySubmissions_shouldRejectOtherEnterpriseAccess() throws Exception {
        registerUser("ENTERPRISE", "owner-enterprise@example.com", "Passw0rd!", "OwnerEnterprise");
        registerUser("ENTERPRISE", "other-enterprise@example.com", "Passw0rd!", "OtherEnterprise");
        registerUser("STUDENT", "owner-student@example.com", "Passw0rd!", "OwnerStudent");
        String ownerToken = loginAndGetAccessToken("owner-enterprise@example.com", "Passw0rd!");
        String otherToken = loginAndGetAccessToken("other-enterprise@example.com", "Passw0rd!");
        String studentToken = loginAndGetAccessToken("owner-student@example.com", "Passw0rd!");

        long taskId = createTask(ownerToken, "Java 练习题整理", "整理一套适合校招的练习题。", "证书 + 面试机会");
        createSubmission(studentToken, taskId, "已整理题目与答案说明。", "https://example.com/java-task");

        mockMvc.perform(get("/api/v1/bounty/tasks/{taskId}/submissions", taskId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-1004"));
    }

    @Test
    void bountyManage_shouldSupportCloseAndReopenBeforeAcceptance() throws Exception {
        registerUser("ENTERPRISE", "manage-enterprise@example.com", "Passw0rd!", "ManageEnterprise");
        String enterpriseToken = loginAndGetAccessToken("manage-enterprise@example.com", "Passw0rd!");
        long taskId = createTask(enterpriseToken, "运营文案优化", "优化校招活动运营文案。", "优先面试");

        mockMvc.perform(post("/api/v1/bounty/tasks/{taskId}/manage", taskId)
                        .header("Authorization", "Bearer " + enterpriseToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"action\":\"CLOSE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        mockMvc.perform(post("/api/v1/bounty/tasks/{taskId}/manage", taskId)
                        .header("Authorization", "Bearer " + enterpriseToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"action\":\"REOPEN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"));
    }

    @Test
    void bountyTaskUpdate_shouldSupportEditModeAndRejectAcceptedTaskEditing() throws Exception {
        registerUser("ENTERPRISE", "edit-enterprise@example.com", "Passw0rd!", "EditEnterprise");
        registerUser("STUDENT", "edit-student@example.com", "Passw0rd!", "EditStudent");
        String enterpriseToken = loginAndGetAccessToken("edit-enterprise@example.com", "Passw0rd!");
        String studentToken = loginAndGetAccessToken("edit-student@example.com", "Passw0rd!");

        long taskId = createTask(enterpriseToken, "原始任务标题", "原始任务说明", "原始奖励");

        mockMvc.perform(put("/api/v1/bounty/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + enterpriseToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "更新后的任务标题",
                                  "description": "更新后的任务说明，增加了更明确的交付要求。",
                                  "rewardDescription": "更新后的奖励说明",
                                  "deadlineAt": "2026-04-18T15:59:59Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value(taskId))
                .andExpect(jsonPath("$.data.status").value("OPEN"));

        mockMvc.perform(get("/api/v1/bounty/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + enterpriseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("更新后的任务标题"))
                .andExpect(jsonPath("$.data.description").value("更新后的任务说明，增加了更明确的交付要求。"))
                .andExpect(jsonPath("$.data.rewardDescription").value("更新后的奖励说明"))
                .andExpect(jsonPath("$.data.deadlineAt").value(1776527999000L));

        long submissionId = createSubmission(studentToken, taskId, "我根据更新后的要求补齐了任务产出。", "https://example.com/edit-task");

        mockMvc.perform(post("/api/v1/bounty/submissions/{submissionId}/review", submissionId)
                        .header("Authorization", "Bearer " + enterpriseToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "ACCEPT",
                                  "contactIntent": "EMAIL",
                                  "comment": "任务修改后方向更清晰，决定继续推进。"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.taskStatus").value("CLOSED"));

        mockMvc.perform(put("/api/v1/bounty/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + enterpriseToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "中选后再次修改",
                                  "description": "不应允许成功",
                                  "rewardDescription": "不应允许成功",
                                  "deadlineAt": "2026-04-20T15:59:59Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BIZ-1001"));
    }

    @Test
    void bountySubmissionInternalNoteAndContact_shouldBePersistedAndReturned() throws Exception {
        registerUser("ENTERPRISE", "contact-enterprise@example.com", "Passw0rd!", "ContactEnterprise");
        registerUser("STUDENT", "contact-student@example.com", "Passw0rd!", "ContactStudent");
        String enterpriseToken = loginAndGetAccessToken("contact-enterprise@example.com", "Passw0rd!");
        String studentToken = loginAndGetAccessToken("contact-student@example.com", "Passw0rd!");

        long studentUserId = findUserIdByEmail("contact-student@example.com");
        long taskId = createTask(enterpriseToken, "联系方式联调任务", "验证联系方式与内部备注返回。", "结果留痕验证");
        long submissionId = createSubmission(studentToken, taskId, "我已经提交了带联系方式的方案说明。", "https://example.com/contact-task");

        insertStudentContactProfile(
                studentUserId,
                "13800000000",
                "contact_student_wechat",
                """
                        [{"platform":"GITHUB","value":"contact-student"},{"platform":"PORTFOLIO","value":"https://portfolio.example.com/contact-student"}]
                        """
        );
        insertStudentPrivacySettings(
                studentUserId,
                """
                        {"email":{"enterprise":true},"phone":{"enterprise":true},"wechat":{"enterprise":true},"social":{"enterprise":true}}
                        """
        );

        mockMvc.perform(get("/api/v1/bounty/tasks/{taskId}/submissions", taskId)
                        .header("Authorization", "Bearer " + enterpriseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].submissionId").value(submissionId))
                .andExpect(jsonPath("$.data.records[0].updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.records[0].contact.email").value("contact-student@example.com"))
                .andExpect(jsonPath("$.data.records[0].contact.phone").value("13800000000"))
                .andExpect(jsonPath("$.data.records[0].contact.wechat").value("contact_student_wechat"))
                .andExpect(jsonPath("$.data.records[0].contact.socialLinks[0].platform").value("GITHUB"))
                .andExpect(jsonPath("$.data.records[0].contact.socialLinks[0].value").value("contact-student"))
                .andExpect(jsonPath("$.data.records[0].contact.socialLinks[1].platform").value("PORTFOLIO"));

        mockMvc.perform(post("/api/v1/bounty/submissions/{submissionId}/review", submissionId)
                        .header("Authorization", "Bearer " + enterpriseToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "REJECT",
                                  "rejectTemplate": "本次任务方向暂不完全匹配",
                                  "comment": "当前版本先记录学生可见结果说明。",
                                  "actionNote": "这次先不继续推进。"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.comment").value("当前版本先记录学生可见结果说明。"))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/bounty/tasks/{taskId}/submissions", taskId)
                        .header("Authorization", "Bearer " + enterpriseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.data.records[0].reviewComment").value("当前版本先记录学生可见结果说明。"));
    }

    private void registerUser(String role, String email, String password, String displayName) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
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
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
        if ("ENTERPRISE".equals(role)) {
            approveEnterpriseProfile(email, displayName);
        }
    }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{" + "\"email\":\"%s\",\"password\":\"%s\"}" , email, password)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        String accessToken = jsonNode.path("data").path("accessToken").asText();
        assertThat(accessToken).isNotBlank();
        return accessToken;
    }

    private long createTask(String enterpriseToken, String title, String description, String rewardDescription) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/bounty/tasks")
                        .header("Authorization", "Bearer " + enterpriseToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{" +
                                        "\"title\":\"%s\"," +
                                        "\"description\":\"%s\"," +
                                        "\"rewardDescription\":\"%s\"" +
                                        "}",
                                title,
                                description,
                                rewardDescription
                        )))
                .andExpect(status().isOk())
                .andReturn();
        return readLong(result, "data", "taskId");
    }

    private long createSubmission(String studentToken, long taskId, String contentText, String link) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/bounty/tasks/{taskId}/submissions", taskId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{" +
                                        "\"contentText\":\"%s\"," +
                                        "\"attachmentLinks\":[\"%s\"]" +
                                        "}",
                                contentText,
                                link
                        )))
                .andExpect(status().isOk())
                .andReturn();
        return readLong(result, "data", "submissionId");
    }

    private long readLong(MvcResult result, Object... path) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        for (Object segment : path) {
            if (segment instanceof Integer index) {
                node = node.path(index);
            } else {
                node = node.path(String.valueOf(segment));
            }
        }
        return node.asLong();
    }

    private long findUserIdByEmail(String email) {
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        assertThat(userId).isNotNull();
        return userId;
    }

    private void insertPortraitSnapshot(long studentUserId, String portraitTagsJson) {
        jdbcTemplate.update(
                "INSERT INTO student_portrait_snapshots(student_user_id, portrait_tags, evidence, updated_at) VALUES (?, ?, '{}', CURRENT_TIMESTAMP)",
                studentUserId,
                portraitTagsJson
        );
    }

    private void enableBountyEmailPreference(long userId) {
        jdbcTemplate.update(
                """
                INSERT INTO notification_preferences(
                    user_id, category, inbox_enabled, websocket_enabled, browser_popup_enabled,
                    email_enabled, email_urgency_threshold, quiet_hours_json, updated_at
                ) VALUES (?, 'BOUNTY', TRUE, TRUE, TRUE, TRUE, 'HIGH', NULL, CURRENT_TIMESTAMP)
                """,
                userId
        );
        notificationPreferenceCacheService.evictUserPreferencesNow(userId);
    }

    private void insertPostAndLike(long studentUserId, long likerUserId) {
        jdbcTemplate.update(
                "INSERT INTO posts(user_id, title, content, tags, moderation_status, risk_level, is_deleted, created_at, updated_at) VALUES (?, '社区帖子', '贡献分测试帖子', '测试', 'PASS', 'LOW', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                studentUserId
        );
        Long postId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM posts", Long.class);
        assertThat(postId).isNotNull();
        jdbcTemplate.update(
                "INSERT INTO comments(post_id, user_id, content, is_ai, moderation_status, risk_level, is_deleted, created_at, updated_at) VALUES (?, ?, '互动评论', 0, 'PASS', 'LOW', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                postId,
                studentUserId
        );
        jdbcTemplate.update(
                "INSERT INTO post_likes(post_id, user_id, created_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
                postId,
                likerUserId
        );
    }

    private void insertStudentContactProfile(long studentUserId, String phone, String wechat, String socialLinksJson) {
        int updatedRows = jdbcTemplate.update(
                "UPDATE student_profiles SET phone = ?, wechat = ?, social_links_json = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?",
                phone,
                wechat,
                socialLinksJson,
                studentUserId
        );
        if (updatedRows > 0) {
            return;
        }
        jdbcTemplate.update(
                """
                INSERT INTO student_profiles(user_id, phone, wechat, social_links_json, created_at, updated_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                studentUserId,
                phone,
                wechat,
                socialLinksJson
        );
    }

    private void insertStudentPrivacySettings(long studentUserId, String settingsJson) {
        int updatedRows = jdbcTemplate.update(
                "UPDATE student_profile_privacy_settings SET settings_json = ?, updated_at = CURRENT_TIMESTAMP WHERE student_user_id = ?",
                settingsJson,
                studentUserId
        );
        if (updatedRows > 0) {
            return;
        }
        jdbcTemplate.update(
                """
                INSERT INTO student_profile_privacy_settings(student_user_id, settings_json, created_at, updated_at)
                VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                studentUserId,
                settingsJson
        );
    }

    private void approveEnterpriseProfile(String email, String displayName) {
        long userId = findUserIdByEmail(email);
        int updatedRows = jdbcTemplate.update(
                """
                UPDATE enterprise_profiles
                   SET company_name = ?,
                       industry = COALESCE(industry, '待补充'),
                       company_size = COALESCE(company_size, '待补充'),
                       hiring_tags = COALESCE(hiring_tags, '校招,实习'),
                       approval_status = 'APPROVED',
                       updated_at = CURRENT_TIMESTAMP
                 WHERE user_id = ?
                """,
                displayName + " 企业",
                userId
        );
        if (updatedRows > 0) {
            return;
        }
        jdbcTemplate.update(
                """
                INSERT INTO enterprise_profiles(
                    user_id,
                    company_name,
                    industry,
                    company_size,
                    hiring_tags,
                    approval_status,
                    created_at,
                    updated_at
                ) VALUES (?, ?, '待补充', '待补充', '校招,实习', 'APPROVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                userId,
                displayName + " 企业"
        );
    }
}
