package com.bishe.server.consult;

import com.bishe.server.consult.service.ConsultAfterSalesService;
import com.bishe.server.mentor.service.MentorDashboardCacheService;
import com.bishe.server.mentor.service.MentorPublicDetailCacheService;
import com.bishe.server.mentor.service.MentorPublicListCacheService;
import com.bishe.server.mentor.schedule.service.MentorPublicScheduleCacheService;
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

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 咨询订单、通知与导师工作台闭环集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConsultFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ConsultAfterSalesService consultAfterSalesService;

    @Autowired
    private MentorPublicDetailCacheService mentorPublicDetailCacheService;

    @Autowired
    private MentorDashboardCacheService mentorDashboardCacheService;

    @Autowired
    private MentorPublicListCacheService mentorPublicListCacheService;

    @Autowired
    private MentorPublicScheduleCacheService mentorPublicScheduleCacheService;

    @BeforeEach
    void setUp() {
        mentorPublicListCacheService.evictAllNow();
        jdbcTemplate.queryForList("SELECT user_id FROM mentor_profiles", Long.class)
                .forEach(mentorUserId -> {
                    mentorPublicDetailCacheService.evictNow(mentorUserId);
                    mentorDashboardCacheService.evictNow(mentorUserId);
                    mentorPublicScheduleCacheService.evictNow(mentorUserId);
                });
    }

    @Test
    void consultFlow_shouldSupportAnsweredCloseNotificationsDashboardAndReview() throws Exception {
        long mentorUserId = registerUser("MENTOR", "consult-mentor@example.com", "Passw0rd!", "MentorConsult");
        long studentUserId = registerUser("STUDENT", "consult-student@example.com", "Passw0rd!", "StudentConsult");
        String mentorToken = loginAndGetAccessToken("consult-mentor@example.com", "Passw0rd!");
        String studentToken = loginAndGetAccessToken("consult-student@example.com", "Passw0rd!");

        mockMvc.perform(put("/api/v1/profiles/students/me")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobStatus": "积极冲刺春招",
                                  "schoolName": "华东理工大学",
                                  "major": "计算机科学与技术",
                                  "grade": "大四",
                                  "gpa": "3.8 / 4.0",
                                  "targetPosition": "Java 后端开发",
                                  "skillTags": ["Spring Boot", "MySQL", "系统设计"],
                                  "selfIntro": "希望通过导师咨询更快收口简历表达与项目深度。"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated").value(true));

        mockMvc.perform(put("/api/v1/mentor/profile")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expertiseTags": ["Java后端", "系统设计"],
                                  "bio": "专注校招求职辅导与简历诊断。",
                                  "suitableFor": "适合已经有 1-2 段项目经历、准备冲刺后端实习的同学。",
                                  "notSuitableFor": "不适合希望从零开始系统学习 Java 语法的同学。",
                                  "prepMaterials": "请提前准备最新简历、目标岗位 JD 与最担心被问到的问题。",
                                  "replyRhythm": "工作日晚间统一回复，周末可补充长文本建议。",
                                  "priceFen": 8800,
                                  "available": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priceFen").value(8800))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.expertiseTags[0]").value("Java后端"));

        mockMvc.perform(get("/api/v1/mentors")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("available", "true")
                        .param("expertise", "Java")
                        .param("keyword", "校招求职"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].userId").value(mentorUserId));

        mockMvc.perform(get("/api/v1/mentors/{mentorUserId}", mentorUserId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(mentorUserId))
                .andExpect(jsonPath("$.data.priceFen").value(8800))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.suitableFor").value("适合已经有 1-2 段项目经历、准备冲刺后端实习的同学。"))
                .andExpect(jsonPath("$.data.notSuitableFor").value("不适合希望从零开始系统学习 Java 语法的同学。"))
                .andExpect(jsonPath("$.data.prepMaterials").value("请提前准备最新简历、目标岗位 JD 与最担心被问到的问题。"))
                .andExpect(jsonPath("$.data.replyRhythm").value("工作日晚间统一回复，周末可补充长文本建议。"));

        String createOrderBody = String.format(
                "{" +
                        "\"mentorUserId\":%d," +
                        "\"questionText\":\"我的简历适合投递 Java 后端实习吗？\"" +
                        "}",
                mentorUserId
        );

        MvcResult createOrderResult = mockMvc.perform(post("/api/v1/consult/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createOrderBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andReturn();

        String orderNo = objectMapper.readTree(createOrderResult.getResponse().getContentAsString())
                .path("data")
                .path("orderNo")
                .asText();
        assertThat(orderNo).startsWith("ORD");

        mockMvc.perform(get("/api/v1/consult/orders/{orderNo}", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andExpect(jsonPath("$.data.autoCancelAt").isNotEmpty());

        mockMvc.perform(post("/api/v1/pay/orders/{orderNo}/create", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAYING"))
                .andExpect(jsonPath("$.data.paymentMode").value("MOCK"));

        mockMvc.perform(post("/api/v1/pay/mock/orders/{orderNo}/success", orderNo)
                        .header("Authorization", "Bearer " + studentToken)
                        .param("reason", "TEST_FLOW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.alreadyProcessed").value(false));

        mockMvc.perform(get("/api/v1/consult/orders/{orderNo}", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.autoCancelAt").isEmpty());

        mockMvc.perform(get("/api/v1/consult/orders/{orderNo}", orderNo)
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentProfile.jobStatus").value("积极冲刺春招"))
                .andExpect(jsonPath("$.data.studentProfile.schoolName").value("华东理工大学"))
                .andExpect(jsonPath("$.data.studentProfile.major").value("计算机科学与技术"))
                .andExpect(jsonPath("$.data.studentProfile.targetPosition").value("Java 后端开发"))
                .andExpect(jsonPath("$.data.studentProfile.skillTags[0]").value("Spring Boot"));

        MvcResult mentorNotificationResult = mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1))
                .andReturn();
        JsonNode mentorNotifications = objectMapper.readTree(mentorNotificationResult.getResponse().getContentAsString()).path("data");
        assertThat(mentorNotifications.path("records").get(0).path("type").asText()).isEqualTo("CONSULT_PAID");
        assertThat(mentorNotifications.path("records").get(0).path("refId").asText()).isEqualTo(orderNo);

        mockMvc.perform(post("/api/v1/consult/orders/{orderNo}/messages", orderNo)
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageText\":\"建议你把项目亮点改成结果导向，并补充性能指标。\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("ANSWERED"));

        mockMvc.perform(get("/api/v1/consult/orders/{orderNo}", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ANSWERED"));

        MvcResult studentNotificationResult = mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1))
                .andReturn();
        JsonNode studentNotifications = objectMapper.readTree(studentNotificationResult.getResponse().getContentAsString()).path("data");
        long notificationId = studentNotifications.path("records").get(0).path("id").asLong();
        assertThat(studentNotifications.path("records").get(0).path("type").asText()).isEqualTo("CONSULT_REPLIED");
        assertThat(studentNotifications.path("records").get(0).path("refId").asText()).isEqualTo(orderNo);

        mockMvc.perform(post("/api/v1/notifications/{notificationId}/read", notificationId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true));

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        mockMvc.perform(post("/api/v1/consult/orders/{orderNo}/close", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        mockMvc.perform(post("/api/v1/consult/orders/{orderNo}/review", orderNo)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"comment\":\"导师建议很具体，可直接执行。\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rating").value(5));

        MvcResult mentorNotificationAfterCloseResult = mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(3))
                .andReturn();
        JsonNode mentorNotificationsAfterClose = objectMapper.readTree(mentorNotificationAfterCloseResult.getResponse().getContentAsString()).path("data");
        assertThat(mentorNotificationsAfterClose.path("records").get(0).path("type").asText()).isEqualTo("CONSULT_REVIEWED");
        assertThat(mentorNotificationsAfterClose.path("records").get(1).path("type").asText()).isEqualTo("CONSULT_CLOSED");
        assertThat(mentorNotificationsAfterClose.path("records").get(2).path("type").asText()).isEqualTo("CONSULT_PAID");

        mockMvc.perform(get("/api/v1/mentors/{mentorUserId}", mentorUserId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOrders").value(1))
                .andExpect(jsonPath("$.data.avgRating").value(5.00))
                .andExpect(jsonPath("$.data.recentReviews.length()").value(1))
                .andExpect(jsonPath("$.data.recentReviews[0].orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.recentReviews[0].studentDisplayName").value("StudentConsult"))
                .andExpect(jsonPath("$.data.recentReviews[0].rating").value(5))
                .andExpect(jsonPath("$.data.recentReviews[0].comment").value("导师建议很具体，可直接执行。"));

        mockMvc.perform(get("/api/v1/mentor/dashboard")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingPaidCount").value(0))
                .andExpect(jsonPath("$.data.answeredCount").value(0))
                .andExpect(jsonPath("$.data.closedCount").value(1))
                .andExpect(jsonPath("$.data.totalRevenueFen").value(8800))
                .andExpect(jsonPath("$.data.totalOrders").value(1))
                .andExpect(jsonPath("$.data.avgRating").value(5.00));

        mockMvc.perform(get("/api/v1/consult/orders/{orderNo}/messages", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(2));

        assertThat(studentUserId).isPositive();
    }

    @Test
    void mentorDetail_shouldRefreshAfterMentorUpdatesProfile() throws Exception {
        long mentorUserId = registerUser("MENTOR", "mentor-cache-profile@example.com", "Passw0rd!", "MentorCacheProfile");
        registerUser("STUDENT", "student-cache-profile@example.com", "Passw0rd!", "StudentCacheProfile");
        String mentorToken = loginAndGetAccessToken("mentor-cache-profile@example.com", "Passw0rd!");
        String studentToken = loginAndGetAccessToken("student-cache-profile@example.com", "Passw0rd!");

        mockMvc.perform(put("/api/v1/mentor/profile")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bio": "第一版导师简介",
                                  "prepMaterials": "先准备第一版资料。",
                                  "priceFen": 6800,
                                  "available": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priceFen").value(6800));

        mockMvc.perform(get("/api/v1/mentors/{mentorUserId}", mentorUserId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bio").value("第一版导师简介"))
                .andExpect(jsonPath("$.data.priceFen").value(6800));

        mockMvc.perform(put("/api/v1/mentor/profile")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bio": "第二版导师简介",
                                  "prepMaterials": "请准备最新简历、目标岗位 JD 与项目复盘。",
                                  "priceFen": 9200,
                                  "available": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priceFen").value(9200));

        mockMvc.perform(get("/api/v1/mentors/{mentorUserId}", mentorUserId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bio").value("第二版导师简介"))
                .andExpect(jsonPath("$.data.prepMaterials").value("请准备最新简历、目标岗位 JD 与项目复盘。"))
                .andExpect(jsonPath("$.data.priceFen").value(9200));
    }

    @Test
    void mentorDashboard_shouldRefreshAfterOrderStatusTransitions() throws Exception {
        long mentorUserId = registerUser("MENTOR", "mentor-dashboard-cache@example.com", "Passw0rd!", "MentorDashboardCache");
        registerUser("STUDENT", "student-dashboard-cache@example.com", "Passw0rd!", "StudentDashboardCache");
        String mentorToken = loginAndGetAccessToken("mentor-dashboard-cache@example.com", "Passw0rd!");
        String studentToken = loginAndGetAccessToken("student-dashboard-cache@example.com", "Passw0rd!");

        mockMvc.perform(get("/api/v1/mentor/dashboard")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingPaidCount").value(0))
                .andExpect(jsonPath("$.data.answeredCount").value(0))
                .andExpect(jsonPath("$.data.closedCount").value(0));

        String createOrderBody = String.format(
                "{" +
                        "\"mentorUserId\":%d," +
                        "\"questionText\":\"想请你帮我看一下后端项目表达。\"" +
                        "}",
                mentorUserId
        );
        MvcResult createOrderResult = mockMvc.perform(post("/api/v1/consult/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createOrderBody))
                .andExpect(status().isOk())
                .andReturn();
        String orderNo = objectMapper.readTree(createOrderResult.getResponse().getContentAsString())
                .path("data")
                .path("orderNo")
                .asText();

        mockMvc.perform(post("/api/v1/pay/orders/{orderNo}/create", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/pay/mock/orders/{orderNo}/success", orderNo)
                        .header("Authorization", "Bearer " + studentToken)
                        .param("reason", "DASHBOARD_CACHE_TEST"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/mentor/dashboard")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingPaidCount").value(1))
                .andExpect(jsonPath("$.data.answeredCount").value(0))
                .andExpect(jsonPath("$.data.recentOrders[0].orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.recentOrders[0].status").value("PAID"));

        mockMvc.perform(post("/api/v1/consult/orders/{orderNo}/messages", orderNo)
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageText\":\"建议你把项目亮点改成结果导向。\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("ANSWERED"));

        mockMvc.perform(get("/api/v1/mentor/dashboard")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingPaidCount").value(0))
                .andExpect(jsonPath("$.data.answeredCount").value(1))
                .andExpect(jsonPath("$.data.recentOrders[0].orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.recentOrders[0].status").value("ANSWERED"));

        mockMvc.perform(post("/api/v1/consult/orders/{orderNo}/close", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        mockMvc.perform(get("/api/v1/mentor/dashboard")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingPaidCount").value(0))
                .andExpect(jsonPath("$.data.answeredCount").value(0))
                .andExpect(jsonPath("$.data.closedCount").value(1))
                .andExpect(jsonPath("$.data.recentOrders[0].orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.recentOrders[0].status").value("CLOSED"));
    }

    @Test
    void mentorReplyDraft_shouldGenerateAiDraftAndRecordCallLog() throws Exception {
        long mentorUserId = registerUser("MENTOR", "consult-mentor-ai@example.com", "Passw0rd!", "MentorAiDraft");
        long studentUserId = registerUser("STUDENT", "consult-student-ai@example.com", "Passw0rd!", "StudentAiDraft");
        String mentorToken = loginAndGetAccessToken("consult-mentor-ai@example.com", "Passw0rd!");
        String studentToken = loginAndGetAccessToken("consult-student-ai@example.com", "Passw0rd!");

        mockMvc.perform(put("/api/v1/profiles/students/me")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobStatus": "正在冲刺秋招",
                                  "schoolName": "上海交通大学",
                                  "major": "软件工程",
                                  "grade": "研二",
                                  "targetPosition": "Java 后端开发",
                                  "skillTags": ["Spring Boot", "MySQL", "Redis"],
                                  "selfIntro": "希望重点提升项目表达和咨询沟通效率。"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated").value(true));

        mockMvc.perform(put("/api/v1/mentor/profile")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expertiseTags": ["Java后端", "项目表达"],
                                  "bio": "擅长校招后端项目表达与咨询答复拆解。",
                                  "priceFen": 9900,
                                  "available": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priceFen").value(9900));

        MvcResult createOrderResult = mockMvc.perform(post("/api/v1/consult/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {
                                  "mentorUserId": %d,
                                  "sceneCode": "RESUME_DIAGNOSIS",
                                  "sourcePage": "MENTOR_MARKETPLACE",
                                  "questionText": "我想把后端项目写得更有结果感，也想知道咨询时第一条应该怎么问。",
                                  "problemSummary": "项目表达偏流水账，不知道该如何开场说明自己的诉求。",
                                  "coreQuestions": ["如何突出个人贡献", "如何让导师快速理解背景"],
                                  "expectedOutcomes": ["明确项目表达重点", "拿到下一条沟通框架"],
                                  "selectedMaterialTypes": ["RESUME"],
                                  "questionPayload": {
                                    "primaryConcern": "项目经历像流水账，亮点不够集中",
                                    "background": "有两段 Java 后端项目，但每次沟通都说不清楚重点",
                                    "expectedHelp": "希望先给我一版导师会怎么回复的框架"
                                  }
                                }
                                """).formatted(mentorUserId)))
                .andExpect(status().isOk())
                .andReturn();
        String orderNo = objectMapper.readTree(createOrderResult.getResponse().getContentAsString()).path("data").path("orderNo").asText();

        mockMvc.perform(post("/api/v1/pay/orders/{orderNo}/create", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAYING"));

        mockMvc.perform(post("/api/v1/pay/mock/orders/{orderNo}/success", orderNo)
                        .header("Authorization", "Bearer " + studentToken)
                        .param("reason", "MENTOR_AI_DRAFT_TEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"));

        mockMvc.perform(post("/api/v1/consult/orders/{orderNo}/ai-reply-draft", orderNo)
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "instruction": "更直接一些，先给出可执行框架"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("generated"))
                .andExpect(jsonPath("$.data.draftReply").isNotEmpty())
                .andExpect(jsonPath("$.data.generationMode").value("GENERATE_FROM_CONTEXT"))
                .andExpect(jsonPath("$.data.appliedInstruction").value("更直接一些，先给出可执行框架"))
                .andExpect(jsonPath("$.data.aiMeta.taskType").value("MENTOR_REPLY_DRAFT"))
                .andExpect(jsonPath("$.data.moderation.action").value("PASS"));

        mockMvc.perform(post("/api/v1/consult/orders/{orderNo}/ai-reply-draft", orderNo)
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentDraft": "我先看了你的问题，建议我们先聚焦项目表达。",
                                  "instruction": "语气更温和一些"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draftReply").isNotEmpty())
                .andExpect(jsonPath("$.data.generationMode").value("POLISH_EXISTING"))
                .andExpect(jsonPath("$.data.appliedInstruction").value("语气更温和一些"))
                .andExpect(jsonPath("$.data.aiMeta.taskType").value("MENTOR_REPLY_DRAFT"))
                .andExpect(jsonPath("$.data.moderation.action").value("PASS"));

        mockMvc.perform(post("/api/v1/consult/orders/{orderNo}/ai-reply-draft", orderNo)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        Integer mentorAiDraftCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_call_logs WHERE task_type = 'COMMUNITY_REPLY' AND status = 'SUCCESS' AND user_id = ?",
                Integer.class,
                mentorUserId
        );
        assertThat(mentorAiDraftCount).isEqualTo(2);
        assertThat(studentUserId).isPositive();
    }

    @Test
    void mentorList_shouldSupportKeywordSearchAndExistingFilters() throws Exception {
        long mentorUserId = registerMentor(
                "mentor-search@example.com",
                "Passw0rd!",
                "SearchMentor",
                "求职实验室",
                "后端导师"
        );
        registerUser("STUDENT", "mentor-search-student@example.com", "Passw0rd!", "SearchStudent");
        String mentorToken = loginAndGetAccessToken("mentor-search@example.com", "Passw0rd!");
        String studentToken = loginAndGetAccessToken("mentor-search-student@example.com", "Passw0rd!");

        mockMvc.perform(put("/api/v1/mentor/profile")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName": "求职实验室",
                                  "jobTitle": "后端导师",
                                  "expertiseTags": ["简历优化", "Java后端"],
                                  "serviceScenes": ["简历诊断", "校招投递策略"],
                                  "bio": "擅长校招简历优化与后端项目表达拆解。",
                                  "priceFen": 7200,
                                  "available": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true));

        mockMvc.perform(get("/api/v1/mentors")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("keyword", "简历优化")
                        .param("available", "true")
                        .param("scene", "简历诊断")
                        .param("maxPrice", "8000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].userId").value(mentorUserId))
                .andExpect(jsonPath("$.data.records[0].displayName").value("SearchMentor"))
                .andExpect(jsonPath("$.data.records[0].companyName").value("求职实验室"))
                .andExpect(jsonPath("$.data.records[0].jobTitle").value("后端导师"))
                .andExpect(jsonPath("$.data.records[0].serviceScenes[0]").value("简历诊断"));

        mockMvc.perform(get("/api/v1/mentors")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("keyword", "不存在的关键词"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void mentorMarketplace_shouldSupportRecommendationsFavoritesAndPrepSheetGeneration() throws Exception {
        long mentorUserId = registerMentor(
                "mentor-marketplace@example.com",
                "Passw0rd!",
                "MarketplaceMentor",
                "字节跳动",
                "高级前端工程师"
        );
        long studentUserId = registerUser("STUDENT", "mentor-marketplace-student@example.com", "Passw0rd!", "MarketplaceStudent");
        String mentorToken = loginAndGetAccessToken("mentor-marketplace@example.com", "Passw0rd!");
        String studentToken = loginAndGetAccessToken("mentor-marketplace-student@example.com", "Passw0rd!");

        mockMvc.perform(put("/api/v1/profiles/students/me")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetPosition": "前端开发工程师",
                                  "skillTags": ["React", "TypeScript"],
                                  "selfIntro": "希望重点优化简历和项目表达。"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated").value(true))
                .andExpect(jsonPath("$.data.portraitRefreshTriggered").value(true));

        mockMvc.perform(put("/api/v1/mentor/profile")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName": "字节跳动",
                                  "jobTitle": "高级前端工程师",
                                  "avatarUrl": "https://example.com/marketplace-mentor.png",
                                  "expertiseTags": ["前端工程化", "React"],
                                  "serviceScenes": ["简历诊断", "项目表达"],
                                  "bio": "擅长前端项目亮点拆解与简历诊断。",
                                  "priceFen": 19900,
                                  "available": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyName").value("字节跳动"))
                .andExpect(jsonPath("$.data.jobTitle").value("高级前端工程师"))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://example.com/marketplace-mentor.png"))
                .andExpect(jsonPath("$.data.serviceScenes[0]").value("简历诊断"));

        mockMvc.perform(get("/api/v1/mentors/recommendations")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("scene", "简历诊断")
                        .param("keyword", "亮点拆解"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scene").value("简历诊断"))
                .andExpect(jsonPath("$.data.records[0].userId").value(mentorUserId))
                .andExpect(jsonPath("$.data.records[0].companyName").value("字节跳动"))
                .andExpect(jsonPath("$.data.records[0].favorited").value(false))
                .andExpect(jsonPath("$.data.records[0].reasons.length()").value(3));

        Integer studentSnapshotCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM student_recommendation_snapshots WHERE student_user_id = ?",
                Integer.class,
                studentUserId
        );
        assertThat(studentSnapshotCount).isEqualTo(1);
        String studentSignalFlags = jdbcTemplate.queryForObject(
                "SELECT signal_flags_json FROM student_recommendation_snapshots WHERE student_user_id = ?",
                String.class,
                studentUserId
        );
        assertThat(studentSignalFlags).contains("portraitStrengthTags");
        assertThat(studentSignalFlags).contains("portraitSignalLevel");

        Integer mentorSnapshotCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mentor_recommendation_snapshots WHERE mentor_user_id = ?",
                Integer.class,
                mentorUserId
        );
        assertThat(mentorSnapshotCount).isEqualTo(1);

        Integer studentEmbeddingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM recommendation_embedding_vectors WHERE entity_type = 'STUDENT' AND entity_id = ?",
                Integer.class,
                studentUserId
        );
        Integer mentorEmbeddingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM recommendation_embedding_vectors WHERE entity_type = 'MENTOR' AND entity_id = ?",
                Integer.class,
                mentorUserId
        );
        assertThat(studentEmbeddingCount).isEqualTo(1);
        assertThat(mentorEmbeddingCount).isEqualTo(1);

        Integer recommendationRunCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mentor_recommendation_runs WHERE student_user_id = ?",
                Integer.class,
                studentUserId
        );
        assertThat(recommendationRunCount).isEqualTo(1);
        String topMentorUserIdsJson = jdbcTemplate.queryForObject(
                "SELECT top_mentor_user_ids_json FROM mentor_recommendation_runs WHERE student_user_id = ? ORDER BY id DESC LIMIT 1",
                String.class,
                studentUserId
        );
        assertThat(topMentorUserIdsJson).contains(Long.toString(mentorUserId));

        Long recommendationRunId = jdbcTemplate.queryForObject(
                "SELECT id FROM mentor_recommendation_runs WHERE student_user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class,
                studentUserId
        );
        Integer recommendationEventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mentor_recommendation_events WHERE run_id = ? AND mentor_user_id = ?",
                Integer.class,
                recommendationRunId,
                mentorUserId
        );
        assertThat(recommendationEventCount).isGreaterThanOrEqualTo(2);

        mockMvc.perform(post("/api/v1/mentors/{mentorUserId}/favorite", mentorUserId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mentorUserId").value(mentorUserId))
                .andExpect(jsonPath("$.data.favorited").value(true))
                .andExpect(jsonPath("$.data.totalFavorites").value(1));

        mockMvc.perform(get("/api/v1/mentors/favorites")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.mentorUserIds[0]").value(mentorUserId));

        mockMvc.perform(get("/api/v1/mentors")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("favorited", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].userId").value(mentorUserId))
                .andExpect(jsonPath("$.data.records[0].favorited").value(true));

        mockMvc.perform(get("/api/v1/mentors/{mentorUserId}", mentorUserId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(mentorUserId))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://example.com/marketplace-mentor.png"))
                .andExpect(jsonPath("$.data.serviceScenes[0]").value("简历诊断"))
                .andExpect(jsonPath("$.data.favorited").value(true));

        mockMvc.perform(post("/api/v1/mentors/prep-sheet/generate")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mentorUserId": %d,
                                  "scene": "简历诊断"
                                }
                                """.formatted(mentorUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mentorUserId").value(mentorUserId))
                .andExpect(jsonPath("$.data.scene").value("简历诊断"))
                .andExpect(jsonPath("$.data.targetPosition").value("前端开发工程师"))
                .andExpect(jsonPath("$.data.coreQuestions.length()").value(3))
                .andExpect(jsonPath("$.data.suggestedMaterials[0]").value("我的最新简历"));

        mockMvc.perform(delete("/api/v1/mentors/{mentorUserId}/favorite", mentorUserId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favorited").value(false))
                .andExpect(jsonPath("$.data.totalFavorites").value(0));
    }

    @Test
    void mentorList_shouldRefreshAfterProfileUpdateAndFavoriteToggle() throws Exception {
        long mentorUserId = registerMentor(
                "mentor-list-cache@example.com",
                "Passw0rd!",
                "ListCacheMentor",
                "缓存导师工作室",
                "缓存导师"
        );
        registerUser("STUDENT", "mentor-list-cache-student@example.com", "Passw0rd!", "ListCacheStudent");
        String mentorToken = loginAndGetAccessToken("mentor-list-cache@example.com", "Passw0rd!");
        String studentToken = loginAndGetAccessToken("mentor-list-cache-student@example.com", "Passw0rd!");

        mockMvc.perform(get("/api/v1/mentors")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("keyword", "列表缓存实验"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(put("/api/v1/mentor/profile")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName": "缓存导师工作室",
                                  "jobTitle": "缓存导师",
                                  "expertiseTags": ["列表缓存实验", "前端工程化"],
                                  "serviceScenes": ["简历诊断", "项目表达"],
                                  "bio": "用于验证导师广场公开列表缓存刷新。",
                                  "priceFen": 6600,
                                  "available": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyName").value("缓存导师工作室"));

        mockMvc.perform(get("/api/v1/mentors")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("keyword", "列表缓存实验"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].userId").value(mentorUserId))
                .andExpect(jsonPath("$.data.records[0].favorited").value(false));

        mockMvc.perform(post("/api/v1/mentors/{mentorUserId}/favorite", mentorUserId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favorited").value(true));

        mockMvc.perform(get("/api/v1/mentors")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("keyword", "列表缓存实验"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].userId").value(mentorUserId))
                .andExpect(jsonPath("$.data.records[0].favorited").value(true));

        mockMvc.perform(delete("/api/v1/mentors/{mentorUserId}/favorite", mentorUserId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favorited").value(false));

        mockMvc.perform(get("/api/v1/mentors")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("keyword", "列表缓存实验"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].userId").value(mentorUserId))
                .andExpect(jsonPath("$.data.records[0].favorited").value(false));
    }

    @Test
    void consultFlow_shouldReserveMentorScheduleSlotAndPersistAppointmentSnapshot() throws Exception {
        long mentorUserId = registerUser("MENTOR", "consult-mentor-schedule@example.com", "Passw0rd!", "MentorSchedule");
        registerUser("STUDENT", "consult-student-schedule@example.com", "Passw0rd!", "StudentSchedule");
        String mentorToken = loginAndGetAccessToken("consult-mentor-schedule@example.com", "Passw0rd!");
        String studentToken = loginAndGetAccessToken("consult-student-schedule@example.com", "Passw0rd!");

        mockMvc.perform(put("/api/v1/mentor/profile")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expertiseTags": ["求职规划", "简历修改"],
                                  "bio": "提供带预约时段的咨询辅导。",
                                  "priceFen": 6600,
                                  "available": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true));

        String slotStartAt = "2099-03-01T09:00:00Z";
        String slotEndAt = "2099-03-01T10:00:00Z";
        long slotStartAtEpochMillis = Instant.parse(slotStartAt).toEpochMilli();
        long slotEndAtEpochMillis = Instant.parse(slotEndAt).toEpochMilli();
        MvcResult createSlotResult = mockMvc.perform(post("/api/v1/mentor/schedule/slots")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"startAt\":\"%s\",\"endAt\":\"%s\"}", slotStartAt, slotEndAt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                .andReturn();
        long slotId = objectMapper.readTree(createSlotResult.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .asLong();
        assertThat(slotId).isPositive();

        mockMvc.perform(get("/api/v1/mentor/schedule/slots")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("mentorUserId", String.valueOf(mentorUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(slotId))
                .andExpect(jsonPath("$.data.records[0].startAt").value(slotStartAtEpochMillis));

        String createOrderBody = String.format(
                "{" +
                        "\"mentorUserId\":%d," +
                        "\"questionText\":\"我想约一个固定时段聊简历投递策略。\"," +
                        "\"scheduleSlotId\":%d" +
                        "}",
                mentorUserId,
                slotId
        );

        MvcResult createOrderResult = mockMvc.perform(post("/api/v1/consult/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createOrderBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andExpect(jsonPath("$.data.appointmentStartAt").value(slotStartAtEpochMillis))
                .andExpect(jsonPath("$.data.appointmentEndAt").value(slotEndAtEpochMillis))
                .andReturn();
        String orderNo = objectMapper.readTree(createOrderResult.getResponse().getContentAsString())
                .path("data")
                .path("orderNo")
                .asText();
        Long orderId = jdbcTemplate.queryForObject(
                "SELECT id FROM consult_orders WHERE order_no = ?",
                Long.class,
                orderNo
        );
        assertThat(orderId).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT booked_order_id FROM mentor_schedule_slots WHERE id = ?",
                Long.class,
                slotId
        )).isEqualTo(orderId);

        mockMvc.perform(get("/api/v1/mentor/schedule/slots/me")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(slotId))
                .andExpect(jsonPath("$.data.records[0].status").value("BOOKED"))
                .andExpect(jsonPath("$.data.records[0].bookedOrderNo").value(orderNo));

        mockMvc.perform(get("/api/v1/mentor/schedule/slots")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("mentorUserId", String.valueOf(mentorUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(0));

        mockMvc.perform(get("/api/v1/consult/orders/{orderNo}", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appointmentStartAt").value(slotStartAtEpochMillis))
                .andExpect(jsonPath("$.data.appointmentEndAt").value(slotEndAtEpochMillis));

        mockMvc.perform(get("/api/v1/consult/orders")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].appointmentStartAt").value(slotStartAtEpochMillis))
                .andExpect(jsonPath("$.data.records[0].appointmentEndAt").value(slotEndAtEpochMillis));
    }

    @Test
    void mentorPublicSchedule_shouldRefreshAfterSlotMutations() throws Exception {
        long mentorUserId = registerUser("MENTOR", "consult-mentor-schedule-cache@example.com", "Passw0rd!", "MentorScheduleCache");
        registerUser("STUDENT", "consult-student-schedule-cache@example.com", "Passw0rd!", "StudentScheduleCache");
        String mentorToken = loginAndGetAccessToken("consult-mentor-schedule-cache@example.com", "Passw0rd!");
        String studentToken = loginAndGetAccessToken("consult-student-schedule-cache@example.com", "Passw0rd!");

        mockMvc.perform(put("/api/v1/mentor/profile")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expertiseTags": ["预约缓存", "排期联调"],
                                  "bio": "用于验证公开排期缓存刷新。",
                                  "priceFen": 6100,
                                  "available": true
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/mentor/schedule/slots")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("mentorUserId", String.valueOf(mentorUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(0));

        String slotStartAt = "2099-03-05T09:00:00Z";
        String slotEndAt = "2099-03-05T10:00:00Z";
        MvcResult createSlotResult = mockMvc.perform(post("/api/v1/mentor/schedule/slots")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"startAt\":\"%s\",\"endAt\":\"%s\"}", slotStartAt, slotEndAt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                .andReturn();
        long slotId = objectMapper.readTree(createSlotResult.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .asLong();

        mockMvc.perform(get("/api/v1/mentor/schedule/slots")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("mentorUserId", String.valueOf(mentorUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(slotId));

        String createOrderBody = String.format(
                """
                        {
                          "mentorUserId": %d,
                          "questionText": "请测试公开排期缓存是否会在预约后刷新。",
                          "scheduleSlotId": %d
                        }
                        """,
                mentorUserId,
                slotId
        );
        MvcResult createOrderResult = mockMvc.perform(post("/api/v1/consult/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createOrderBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andReturn();
        String orderNo = objectMapper.readTree(createOrderResult.getResponse().getContentAsString())
                .path("data")
                .path("orderNo")
                .asText();

        mockMvc.perform(get("/api/v1/mentor/schedule/slots")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("mentorUserId", String.valueOf(mentorUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(0));

        mockMvc.perform(post("/api/v1/consult/orders/{orderNo}/cancel", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"));

        mockMvc.perform(get("/api/v1/mentor/schedule/slots")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("mentorUserId", String.valueOf(mentorUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(slotId))
                .andExpect(jsonPath("$.data.records[0].status").value("AVAILABLE"));

        mockMvc.perform(delete("/api/v1/mentor/schedule/slots/{slotId}", slotId)
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/mentor/schedule/slots")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("mentorUserId", String.valueOf(mentorUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(0));
    }

    @Test
    void consultFlow_shouldCancelUnpaidOrderAndReleaseReservedSlot() throws Exception {
        long mentorUserId = registerUser("MENTOR", "consult-mentor-cancel@example.com", "Passw0rd!", "MentorCancel");
        registerUser("STUDENT", "consult-student-cancel@example.com", "Passw0rd!", "StudentCancel");
        String mentorToken = loginAndGetAccessToken("consult-mentor-cancel@example.com", "Passw0rd!");
        String studentToken = loginAndGetAccessToken("consult-student-cancel@example.com", "Passw0rd!");

        mockMvc.perform(put("/api/v1/mentor/profile")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expertiseTags": ["时间管理", "求职咨询"],
                                  "bio": "支持预约制咨询。",
                                  "priceFen": 5200,
                                  "available": true
                                }
                                """))
                .andExpect(status().isOk());

        String slotStartAt = "2099-03-02T09:00:00Z";
        String slotEndAt = "2099-03-02T10:00:00Z";
        MvcResult createSlotResult = mockMvc.perform(post("/api/v1/mentor/schedule/slots")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"startAt\":\"%s\",\"endAt\":\"%s\"}", slotStartAt, slotEndAt)))
                .andExpect(status().isOk())
                .andReturn();
        long slotId = objectMapper.readTree(createSlotResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        String createOrderBody = String.format(
                "{" +
                        "\"mentorUserId\":%d," +
                        "\"questionText\":\"我想先下单再决定是否支付。\"," +
                        "\"scheduleSlotId\":%d" +
                        "}",
                mentorUserId,
                slotId
        );
        MvcResult createOrderResult = mockMvc.perform(post("/api/v1/consult/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createOrderBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andReturn();
        String orderNo = objectMapper.readTree(createOrderResult.getResponse().getContentAsString()).path("data").path("orderNo").asText();

        mockMvc.perform(post("/api/v1/consult/orders/{orderNo}/cancel", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"));

        mockMvc.perform(get("/api/v1/consult/orders/{orderNo}", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"));

        mockMvc.perform(get("/api/v1/mentor/schedule/slots")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("mentorUserId", String.valueOf(mentorUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(slotId))
                .andExpect(jsonPath("$.data.records[0].status").value("AVAILABLE"));

        MvcResult mentorNotificationResult = mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1))
                .andReturn();
        JsonNode mentorNotifications = objectMapper.readTree(mentorNotificationResult.getResponse().getContentAsString()).path("data");
        assertThat(mentorNotifications.path("records").get(0).path("type").asText()).isEqualTo("CONSULT_CANCELED");
        assertThat(mentorNotifications.path("records").get(0).path("refId").asText()).isEqualTo(orderNo);

        mockMvc.perform(post("/api/v1/pay/orders/{orderNo}/create", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BIZ-1001"));
    }

    @Test
    void consultFlow_shouldAutoCancelTimedOutOrderAndReleaseReservedSlot() throws Exception {
        long mentorUserId = registerUser("MENTOR", "consult-mentor-auto-timeout@example.com", "Passw0rd!", "MentorTimeout");
        registerUser("STUDENT", "consult-student-auto-timeout@example.com", "Passw0rd!", "StudentTimeout");
        String mentorToken = loginAndGetAccessToken("consult-mentor-auto-timeout@example.com", "Passw0rd!");
        String studentToken = loginAndGetAccessToken("consult-student-auto-timeout@example.com", "Passw0rd!");

        mockMvc.perform(put("/api/v1/mentor/profile")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expertiseTags": ["预约咨询"],
                                  "bio": "支持排期回收测试。",
                                  "priceFen": 7000,
                                  "available": true
                                }
                                """))
                .andExpect(status().isOk());

        String slotStartAt = "2099-03-03T09:00:00Z";
        String slotEndAt = "2099-03-03T10:00:00Z";
        MvcResult createSlotResult = mockMvc.perform(post("/api/v1/mentor/schedule/slots")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"startAt\":\"%s\",\"endAt\":\"%s\"}", slotStartAt, slotEndAt)))
                .andExpect(status().isOk())
                .andReturn();
        long slotId = objectMapper.readTree(createSlotResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        String createOrderBody = String.format(
                "{" +
                        "\"mentorUserId\":%d," +
                        "\"questionText\":\"这是一条用于超时回收的订单。\"," +
                        "\"scheduleSlotId\":%d" +
                        "}",
                mentorUserId,
                slotId
        );
        MvcResult createOrderResult = mockMvc.perform(post("/api/v1/consult/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createOrderBody))
                .andExpect(status().isOk())
                .andReturn();
        String orderNo = objectMapper.readTree(createOrderResult.getResponse().getContentAsString()).path("data").path("orderNo").asText();

        Instant expiredAt = Instant.now().minusSeconds(31 * 60L);
        jdbcTemplate.update(
                "UPDATE consult_orders SET created_at = ?, updated_at = ? WHERE order_no = ?",
                Timestamp.from(expiredAt),
                Timestamp.from(expiredAt),
                orderNo
        );

        mockMvc.perform(get("/api/v1/consult/orders/{orderNo}", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"));

        mockMvc.perform(get("/api/v1/mentor/schedule/slots")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("mentorUserId", String.valueOf(mentorUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(slotId))
                .andExpect(jsonPath("$.data.records[0].status").value("AVAILABLE"));

        MvcResult studentNotificationResult = mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1))
                .andReturn();
        JsonNode studentNotifications = objectMapper.readTree(studentNotificationResult.getResponse().getContentAsString()).path("data");
        assertThat(studentNotifications.path("records").get(0).path("type").asText()).isEqualTo("CONSULT_TIMEOUT_CANCELED");
        assertThat(studentNotifications.path("records").get(0).path("refId").asText()).isEqualTo(orderNo);

        MvcResult mentorNotificationResult = mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1))
                .andReturn();
        JsonNode mentorNotifications = objectMapper.readTree(mentorNotificationResult.getResponse().getContentAsString()).path("data");
        assertThat(mentorNotifications.path("records").get(0).path("type").asText()).isEqualTo("CONSULT_TIMEOUT_CANCELED");
        assertThat(mentorNotifications.path("records").get(0).path("refId").asText()).isEqualTo(orderNo);
    }


    @Test
    void consultFlow_shouldSupportAdminRefundAndReleaseUpcomingSlot() throws Exception {
        long mentorUserId = registerUser("MENTOR", "consult-mentor-refund@example.com", "Passw0rd!", "MentorRefund");
        registerUser("STUDENT", "consult-student-refund@example.com", "Passw0rd!", "StudentRefund");
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");
        String mentorToken = loginAndGetAccessToken("consult-mentor-refund@example.com", "Passw0rd!");
        String studentToken = loginAndGetAccessToken("consult-student-refund@example.com", "Passw0rd!");

        mockMvc.perform(put("/api/v1/mentor/profile")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expertiseTags": ["职业规划", "模拟面试"],
                                  "bio": "支持带预约时段的售后退款测试。",
                                  "priceFen": 9900,
                                  "available": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priceFen").value(9900));

        String slotStartAt = "2099-04-01T09:00:00Z";
        String slotEndAt = "2099-04-01T10:00:00Z";
        MvcResult createSlotResult = mockMvc.perform(post("/api/v1/mentor/schedule/slots")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"startAt\":\"%s\",\"endAt\":\"%s\"}", slotStartAt, slotEndAt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                .andReturn();
        long slotId = objectMapper.readTree(createSlotResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        String createOrderBody = String.format(
                "{" +
                        "\"mentorUserId\":%d," +
                        "\"questionText\":\"如果我想退款，平台会如何处理？\"," +
                        "\"scheduleSlotId\":%d" +
                        "}",
                mentorUserId,
                slotId
        );

        MvcResult createOrderResult = mockMvc.perform(post("/api/v1/consult/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createOrderBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andReturn();
        String orderNo = objectMapper.readTree(createOrderResult.getResponse().getContentAsString()).path("data").path("orderNo").asText();

        mockMvc.perform(post("/api/v1/pay/orders/{orderNo}/create", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAYING"));

        mockMvc.perform(post("/api/v1/pay/mock/orders/{orderNo}/success", orderNo)
                        .header("Authorization", "Bearer " + studentToken)
                        .param("reason", "REFUND_TEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"));

        mockMvc.perform(post("/api/v1/consult/orders/{orderNo}/messages", orderNo)
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageText\":\"可以的，我先给你答复，然后管理员可做售后处理。\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("ANSWERED"));

        mockMvc.perform(post("/api/v1/consult/orders/{orderNo}/close", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        mockMvc.perform(post("/api/v1/consult/orders/{orderNo}/review", orderNo)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":4,\"comment\":\"先完成后退款测试。\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rating").value(4));

        mockMvc.perform(get("/api/v1/mentors")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("keyword", "售后退款测试"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].userId").value(mentorUserId))
                .andExpect(jsonPath("$.data.records[0].totalOrders").value(1))
                .andExpect(jsonPath("$.data.records[0].avgRating").value(4.00));

        mockMvc.perform(post("/api/v1/admin/consult/orders/{orderNo}/refund", orderNo)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"学生申请退款，管理员核验后同意\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.slotReleased").value(true))
                .andExpect(jsonPath("$.data.reviewRemoved").value(true));

        mockMvc.perform(get("/api/v1/admin/consult/orders?status=REFUNDED")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.records[0].status").value("REFUNDED"));

        mockMvc.perform(get("/api/v1/admin/consult/orders/{orderNo}", orderNo)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.refund.reason").value("学生申请退款，管理员核验后同意"))
                .andExpect(jsonPath("$.data.refund.slotReleased").value(true))
                .andExpect(jsonPath("$.data.refund.reviewRemoved").value(true));

        mockMvc.perform(get("/api/v1/consult/orders/{orderNo}", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.review").doesNotExist());

        mockMvc.perform(get("/api/v1/mentor/schedule/slots")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("mentorUserId", String.valueOf(mentorUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value(slotId))
                .andExpect(jsonPath("$.data.records[0].status").value("AVAILABLE"));

        mockMvc.perform(get("/api/v1/mentor/dashboard")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.closedCount").value(0))
                .andExpect(jsonPath("$.data.totalRevenueFen").value(0))
                .andExpect(jsonPath("$.data.totalOrders").value(0))
                .andExpect(jsonPath("$.data.avgRating").value(0.00));

        mockMvc.perform(get("/api/v1/mentors/{mentorUserId}", mentorUserId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOrders").value(0))
                .andExpect(jsonPath("$.data.avgRating").value(0.00))
                .andExpect(jsonPath("$.data.recentReviews.length()").value(0));

        mockMvc.perform(get("/api/v1/mentors")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("keyword", "售后退款测试"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].userId").value(mentorUserId))
                .andExpect(jsonPath("$.data.records[0].totalOrders").value(0))
                .andExpect(jsonPath("$.data.records[0].avgRating").value(0.00));
    }

    @Test
    void consultFlow_shouldSupportStudentAfterSalesRequestAndAdminApproval() throws Exception {
        long mentorUserId = registerUser("MENTOR", "consult-mentor-after-sales@example.com", "Passw0rd!", "MentorAfterSales");
        registerUser("STUDENT", "consult-student-after-sales@example.com", "Passw0rd!", "StudentAfterSales");
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");
        String mentorToken = loginAndGetAccessToken("consult-mentor-after-sales@example.com", "Passw0rd!");
        String studentToken = loginAndGetAccessToken("consult-student-after-sales@example.com", "Passw0rd!");

        mockMvc.perform(put("/api/v1/mentor/profile")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expertiseTags": ["售后协商", "简历诊断"],
                                  "bio": "用于学生售后申请与管理员审核测试。",
                                  "priceFen": 9200,
                                  "available": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priceFen").value(9200));

        String slotStartAt = "2099-05-01T09:00:00Z";
        String slotEndAt = "2099-05-01T10:00:00Z";
        MvcResult createSlotResult = mockMvc.perform(post("/api/v1/mentor/schedule/slots")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"startAt":"%s","endAt":"%s"}
                                """, slotStartAt, slotEndAt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                .andReturn();
        long slotId = objectMapper.readTree(createSlotResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        String createOrderBody = String.format(
                """
                        {
                          "mentorUserId": %d,
                          "questionText": "如果沟通后仍不适合，我可以申请售后吗？",
                          "scheduleSlotId": %d
                        }
                        """,
                mentorUserId,
                slotId
        );

        MvcResult createOrderResult = mockMvc.perform(post("/api/v1/consult/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createOrderBody))
                .andExpect(status().isOk())
                .andReturn();
        String orderNo = objectMapper.readTree(createOrderResult.getResponse().getContentAsString()).path("data").path("orderNo").asText();

        mockMvc.perform(post("/api/v1/pay/orders/{orderNo}/create", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAYING"));

        mockMvc.perform(post("/api/v1/pay/mock/orders/{orderNo}/success", orderNo)
                        .header("Authorization", "Bearer " + studentToken)
                        .param("reason", "AFTER_SALES_TEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"));

        mockMvc.perform(post("/api/v1/consult/orders/{orderNo}/messages", orderNo)
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"messageText":"我先给你一版建议，如果仍不合适可以走平台售后。"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("ANSWERED"));

        mockMvc.perform(post("/api/v1/consult/orders/{orderNo}/close", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        mockMvc.perform(post("/api/v1/consult/orders/{orderNo}/review", orderNo)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":3,"comment":"先走完整售后链测试。"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rating").value(3));

        MvcResult createAfterSalesResult = mockMvc.perform(post("/api/v1/consult/orders/{orderNo}/after-sales/requests", orderNo)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"学生认为本次服务不匹配，申请退款"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.requestType").value("REFUND"))
                .andReturn();
        long requestId = objectMapper.readTree(createAfterSalesResult.getResponse().getContentAsString()).path("data").path("requestId").asLong();

        mockMvc.perform(get("/api/v1/consult/orders/{orderNo}", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.afterSalesRequests[0].id").value(requestId))
                .andExpect(jsonPath("$.data.afterSalesRequests[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.afterSalesRequests[0].reason").value("学生认为本次服务不匹配，申请退款"));

        mockMvc.perform(get("/api/v1/admin/consult/after-sales/requests")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "PENDING")
                        .param("keyword", orderNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].requestId").value(requestId))
                .andExpect(jsonPath("$.data.records[0].orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.records[0].status").value("PENDING"));

        mockMvc.perform(post("/api/v1/admin/consult/after-sales/requests/{requestId}/review", requestId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approved":true,"reviewNote":"核验通过，按平台规则退款"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.refund.status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.refund.reason").value("学生认为本次服务不匹配，申请退款"));

        mockMvc.perform(get("/api/v1/admin/consult/orders/{orderNo}", orderNo)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.refund.reason").value("学生认为本次服务不匹配，申请退款"))
                .andExpect(jsonPath("$.data.afterSalesRequests[0].id").value(requestId))
                .andExpect(jsonPath("$.data.afterSalesRequests[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.data.afterSalesRequests[0].reviewNote").value("核验通过，按平台规则退款"));

        mockMvc.perform(get("/api/v1/consult/orders/{orderNo}", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.review").doesNotExist())
                .andExpect(jsonPath("$.data.afterSalesRequests[0].status").value("APPROVED"));

        mockMvc.perform(get("/api/v1/mentor/schedule/slots")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("mentorUserId", String.valueOf(mentorUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value(slotId))
                .andExpect(jsonPath("$.data.records[0].status").value("AVAILABLE"));

        mockMvc.perform(get("/api/v1/mentor/dashboard")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.closedCount").value(0))
                .andExpect(jsonPath("$.data.totalRevenueFen").value(0))
                .andExpect(jsonPath("$.data.totalOrders").value(0))
                .andExpect(jsonPath("$.data.avgRating").value(0.00));
    }

    @Test
    void consultFlow_shouldKeepAdminOrderDetailReadOnlyWhenMentorReplyTimedOut() throws Exception {
        long mentorUserId = registerUser("MENTOR", "consult-mentor-timeout-readonly@example.com", "Passw0rd!", "MentorTimeoutReadonly");
        registerUser("STUDENT", "consult-student-timeout-readonly@example.com", "Passw0rd!", "StudentTimeoutReadonly");
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");
        String mentorToken = loginAndGetAccessToken("consult-mentor-timeout-readonly@example.com", "Passw0rd!");
        String studentToken = loginAndGetAccessToken("consult-student-timeout-readonly@example.com", "Passw0rd!");

        mockMvc.perform(put("/api/v1/mentor/profile")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expertiseTags": ["超时只读", "详情页"],
                                  "bio": "用于验证管理员详情查询不应触发自动退款。",
                                  "priceFen": 7600,
                                  "available": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priceFen").value(7600));

        String slotStartAt = "2099-06-02T09:00:00Z";
        String slotEndAt = "2099-06-02T10:00:00Z";
        MvcResult createSlotResult = mockMvc.perform(post("/api/v1/mentor/schedule/slots")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"startAt":"%s","endAt":"%s"}
                                """, slotStartAt, slotEndAt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                .andReturn();
        long slotId = objectMapper.readTree(createSlotResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        String createOrderBody = String.format(
                """
                        {
                          "mentorUserId": %d,
                          "questionText": "请验证管理员查看详情不会自动触发导师超时退款。",
                          "scheduleSlotId": %d
                        }
                        """,
                mentorUserId,
                slotId
        );

        MvcResult createOrderResult = mockMvc.perform(post("/api/v1/consult/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createOrderBody))
                .andExpect(status().isOk())
                .andReturn();
        String orderNo = objectMapper.readTree(createOrderResult.getResponse().getContentAsString()).path("data").path("orderNo").asText();

        mockMvc.perform(post("/api/v1/pay/orders/{orderNo}/create", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAYING"));

        mockMvc.perform(post("/api/v1/pay/mock/orders/{orderNo}/success", orderNo)
                        .header("Authorization", "Bearer " + studentToken)
                        .param("reason", "TIMEOUT_DETAIL_READONLY_TEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"));

        Instant expiredPaidAt = Instant.now().minusSeconds(49 * 3600L);
        jdbcTemplate.update(
                "UPDATE consult_orders SET paid_at = ?, updated_at = ? WHERE order_no = ?",
                Timestamp.from(expiredPaidAt),
                Timestamp.from(expiredPaidAt),
                orderNo
        );

        mockMvc.perform(get("/api/v1/admin/consult/orders/{orderNo}", orderNo)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.mentorReplyDeadlineAt").isNotEmpty())
                .andExpect(jsonPath("$.data.refund").doesNotExist())
                .andExpect(jsonPath("$.data.afterSalesRequests").isArray())
                .andExpect(jsonPath("$.data.afterSalesRequests.length()").value(0));
    }

    @Test
    void consultFlow_shouldAutoRefundWhenMentorReplyTimedOut() throws Exception {
        long mentorUserId = registerUser("MENTOR", "consult-mentor-timeout@example.com", "Passw0rd!", "MentorTimeout");
        registerUser("STUDENT", "consult-student-timeout@example.com", "Passw0rd!", "StudentTimeout");
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");
        String mentorToken = loginAndGetAccessToken("consult-mentor-timeout@example.com", "Passw0rd!");
        String studentToken = loginAndGetAccessToken("consult-student-timeout@example.com", "Passw0rd!");

        mockMvc.perform(put("/api/v1/mentor/profile")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expertiseTags": ["超时场景", "自动处理"],
                                  "bio": "用于导师超时未答自动处理测试。",
                                  "priceFen": 8800,
                                  "available": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priceFen").value(8800));

        String slotStartAt = "2099-06-01T09:00:00Z";
        String slotEndAt = "2099-06-01T10:00:00Z";
        MvcResult createSlotResult = mockMvc.perform(post("/api/v1/mentor/schedule/slots")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"startAt":"%s","endAt":"%s"}
                                """, slotStartAt, slotEndAt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                .andReturn();
        long slotId = objectMapper.readTree(createSlotResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        String createOrderBody = String.format(
                """
                        {
                          "mentorUserId": %d,
                          "questionText": "请测试导师超时未答时系统是否会自动退款。",
                          "scheduleSlotId": %d
                        }
                        """,
                mentorUserId,
                slotId
        );

        MvcResult createOrderResult = mockMvc.perform(post("/api/v1/consult/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createOrderBody))
                .andExpect(status().isOk())
                .andReturn();
        String orderNo = objectMapper.readTree(createOrderResult.getResponse().getContentAsString()).path("data").path("orderNo").asText();

        mockMvc.perform(post("/api/v1/pay/orders/{orderNo}/create", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAYING"));

        mockMvc.perform(post("/api/v1/pay/mock/orders/{orderNo}/success", orderNo)
                        .header("Authorization", "Bearer " + studentToken)
                        .param("reason", "TIMEOUT_AUTO_REFUND_TEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"));

        mockMvc.perform(get("/api/v1/consult/orders/{orderNo}", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.mentorReplyDeadlineAt").isNotEmpty());

        Instant expiredPaidAt = Instant.now().minusSeconds(49 * 3600L);
        jdbcTemplate.update(
                "UPDATE consult_orders SET paid_at = ?, updated_at = ? WHERE order_no = ?",
                Timestamp.from(expiredPaidAt),
                Timestamp.from(expiredPaidAt),
                orderNo
        );

        assertThat(consultAfterSalesService.reconcileTimedOutMentorReplyOrders()).isGreaterThanOrEqualTo(1);

        mockMvc.perform(get("/api/v1/consult/orders/{orderNo}", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.afterSalesRequests[0].autoTriggered").value(true))
                .andExpect(jsonPath("$.data.afterSalesRequests[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.data.afterSalesRequests[0].reviewNote").value("系统自动审批：导师超时未答"));

        mockMvc.perform(get("/api/v1/admin/consult/orders/{orderNo}", orderNo)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.refund.reason").value("导师在规定时限内未正式答复，系统已自动发起售后退款"))
                .andExpect(jsonPath("$.data.afterSalesRequests[0].autoTriggered").value(true));

        mockMvc.perform(get("/api/v1/mentor/schedule/slots")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("mentorUserId", String.valueOf(mentorUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value(slotId))
                .andExpect(jsonPath("$.data.records[0].status").value("AVAILABLE"));
    }

    @Test
    void consultFlow_shouldSupportAdminPaymentReconciliationAndManualHandling() throws Exception {
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");
        long mentorUserId = registerUser("MENTOR", "consult-mentor-recon@example.com", "Passw0rd!", "MentorRecon");
        registerUser("STUDENT", "consult-student-recon@example.com", "Passw0rd!", "StudentRecon");
        String mentorToken = loginAndGetAccessToken("consult-mentor-recon@example.com", "Passw0rd!");
        String studentToken = loginAndGetAccessToken("consult-student-recon@example.com", "Passw0rd!");

        mockMvc.perform(put("/api/v1/mentor/profile")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expertiseTags": ["支付联调", "异常处理"],
                                  "bio": "用于支付对账与人工处理测试。",
                                  "priceFen": 7300,
                                  "available": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priceFen").value(7300));

        String firstCreateOrderBody = String.format(
                "{" +
                        "\"mentorUserId\":%d," +
                        "\"questionText\":\"请帮我测试未支付异常单处理。\"" +
                        "}",
                mentorUserId
        );

        MvcResult firstCreateOrderResult = mockMvc.perform(post("/api/v1/consult/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstCreateOrderBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andReturn();
        String staleOrderNo = objectMapper.readTree(firstCreateOrderResult.getResponse().getContentAsString()).path("data").path("orderNo").asText();

        mockMvc.perform(post("/api/v1/pay/orders/{orderNo}/create", staleOrderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAYING"));

        Instant staleCreatedAt = Instant.now().minusSeconds(7200);
        jdbcTemplate.update(
                "UPDATE consult_orders SET created_at = ?, updated_at = ? WHERE order_no = ?",
                Timestamp.from(staleCreatedAt),
                Timestamp.from(staleCreatedAt),
                staleOrderNo
        );

        mockMvc.perform(get("/api/v1/admin/payments/reconciliation")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("reconciliationStatus", "REVIEW_REQUIRED")
                        .param("keyword", staleOrderNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].orderNo").value(staleOrderNo))
                .andExpect(jsonPath("$.data.records[0].reconciliationStatus").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.data.records[0].issueTags[0]").value("UNPAID_STUCK"));

        mockMvc.perform(get("/api/v1/admin/payments/reconciliation/{orderNo}", staleOrderNo)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("PAYING"))
                .andExpect(jsonPath("$.data.issueTags[0]").value("UNPAID_STUCK"))
                .andExpect(jsonPath("$.data.recommendedActions[0]").value("CANCEL_UNPAID"));

        mockMvc.perform(post("/api/v1/admin/payments/reconciliation/{orderNo}/handle", staleOrderNo)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"action\":\"CANCEL_UNPAID\"," +
                                "\"note\":\"管理员核对后取消未支付异常单\"" +
                                "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("CANCELED"))
                .andExpect(jsonPath("$.data.reconciliationStatus").value("MANUALLY_RESOLVED"));

        mockMvc.perform(get("/api/v1/admin/payments/reconciliation/{orderNo}", staleOrderNo)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("CANCELED"))
                .andExpect(jsonPath("$.data.reconciliationStatus").value("MANUALLY_RESOLVED"))
                .andExpect(jsonPath("$.data.latestManualHandling.action").value("CANCEL_UNPAID"));

        String secondCreateOrderBody = String.format(
                "{" +
                        "\"mentorUserId\":%d," +
                        "\"questionText\":\"请帮我测试支付成功未落单的人工补记。\"" +
                        "}",
                mentorUserId
        );

        MvcResult secondCreateOrderResult = mockMvc.perform(post("/api/v1/consult/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondCreateOrderBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andReturn();
        String pendingSuccessOrderNo = objectMapper.readTree(secondCreateOrderResult.getResponse().getContentAsString()).path("data").path("orderNo").asText();

        mockMvc.perform(post("/api/v1/pay/orders/{orderNo}/create", pendingSuccessOrderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAYING"));

        jdbcTemplate.update(
                "INSERT INTO payment_records(order_no, channel, mode, provider_trade_no, amount_fen, status, idempotency_key, raw_callback, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                pendingSuccessOrderNo,
                "ALIPAY",
                "SANDBOX",
                "ALI-RECON-" + pendingSuccessOrderNo,
                7300,
                "SUCCESS",
                "ALI-RECON-" + pendingSuccessOrderNo,
                "MANUAL_TEST_CALLBACK"
        );

        mockMvc.perform(get("/api/v1/admin/payments/reconciliation/{orderNo}", pendingSuccessOrderNo)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("PAYING"))
                .andExpect(jsonPath("$.data.issueTags[0]").value("SUCCESS_NOT_APPLIED"))
                .andExpect(jsonPath("$.data.recommendedActions[0]").value("MARK_PAID"));

        mockMvc.perform(post("/api/v1/admin/payments/reconciliation/{orderNo}/handle", pendingSuccessOrderNo)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"action\":\"MARK_PAID\"," +
                                "\"note\":\"管理员根据支付记录人工补记成功\"" +
                                "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("PAID"))
                .andExpect(jsonPath("$.data.reconciliationStatus").value("MANUALLY_RESOLVED"));

        mockMvc.perform(get("/api/v1/admin/payments/reconciliation/{orderNo}", pendingSuccessOrderNo)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("PAID"))
                .andExpect(jsonPath("$.data.reconciliationStatus").value("MANUALLY_RESOLVED"))
                .andExpect(jsonPath("$.data.latestManualHandling.action").value("MARK_PAID"));

        String thirdCreateOrderBody = String.format(
                "{" +
                        "\"mentorUserId\":%d," +
                        "\"questionText\":\"请帮我测试外部退款确认后的对账状态同步。\"" +
                        "}",
                mentorUserId
        );

        MvcResult thirdCreateOrderResult = mockMvc.perform(post("/api/v1/consult/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(thirdCreateOrderBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andReturn();
        String refundedOrderNo = objectMapper.readTree(thirdCreateOrderResult.getResponse().getContentAsString()).path("data").path("orderNo").asText();

        Instant refundedPaidAt = Instant.now().minusSeconds(3600);
        jdbcTemplate.update(
                "UPDATE consult_orders SET status = 'REFUNDED', paid_at = ?, updated_at = ? WHERE order_no = ?",
                Timestamp.from(refundedPaidAt),
                Timestamp.from(refundedPaidAt),
                refundedOrderNo
        );
        jdbcTemplate.update(
                "INSERT INTO payment_records(order_no, channel, mode, provider_trade_no, amount_fen, status, idempotency_key, raw_callback, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                refundedOrderNo,
                "ALIPAY",
                "SANDBOX",
                "ALI-RECON-SUCCESS-" + refundedOrderNo,
                7300,
                "SUCCESS",
                "ALI-RECON-SUCCESS-" + refundedOrderNo,
                "MANUAL_TEST_SUCCESS",
                Timestamp.from(refundedPaidAt),
                Timestamp.from(refundedPaidAt)
        );
        jdbcTemplate.update(
                "INSERT INTO payment_records(order_no, channel, mode, provider_trade_no, amount_fen, status, idempotency_key, raw_callback, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                refundedOrderNo,
                "ALIPAY",
                "SANDBOX",
                "ALI-RECON-REFUND-" + refundedOrderNo,
                7300,
                "REFUND_SUCCESS",
                "ALI-RECON-REFUND-" + refundedOrderNo,
                "MANUAL_TEST_REFUND"
        );

        mockMvc.perform(get("/api/v1/admin/payments/reconciliation")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("reconciliationStatus", "REVIEW_REQUIRED")
                        .param("keyword", refundedOrderNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].orderNo").value(refundedOrderNo))
                .andExpect(jsonPath("$.data.records[0].reconciliationStatus").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.data.records[0].issueTags[0]").value("REFUND_EXTERNAL_PENDING"));

        mockMvc.perform(get("/api/v1/admin/payments/reconciliation/{orderNo}", refundedOrderNo)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.data.reconciliationStatus").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.data.issueTags[0]").value("REFUND_EXTERNAL_PENDING"));

        mockMvc.perform(post("/api/v1/admin/payments/reconciliation/{orderNo}/handle", refundedOrderNo)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"action\":\"CONFIRM_EXTERNAL_REFUND\"," +
                                "\"note\":\"管理员已核对外部退款到账\"" +
                                "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.data.reconciliationStatus").value("MANUALLY_RESOLVED"));

        mockMvc.perform(get("/api/v1/admin/payments/reconciliation/{orderNo}", refundedOrderNo)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.data.reconciliationStatus").value("MANUALLY_RESOLVED"))
                .andExpect(jsonPath("$.data.latestManualHandling.action").value("CONFIRM_EXTERNAL_REFUND"));

        mockMvc.perform(get("/api/v1/admin/payments/reconciliation")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("reconciliationStatus", "MANUALLY_RESOLVED")
                        .param("keyword", refundedOrderNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].orderNo").value(refundedOrderNo))
                .andExpect(jsonPath("$.data.records[0].reconciliationStatus").value("MANUALLY_RESOLVED"));

        mockMvc.perform(get("/api/v1/admin/payments/reconciliation")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("reconciliationStatus", "REVIEW_REQUIRED")
                        .param("keyword", refundedOrderNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    
    @Test
    void consultEndpoints_shouldRejectMentorBrowseUnpaidReplyAndPrematureClose() throws Exception {
        long mentorUserId = registerUser("MENTOR", "consult-mentor-2@example.com", "Passw0rd!", "MentorNoPay");
        registerUser("STUDENT", "consult-student-2@example.com", "Passw0rd!", "StudentNoPay");
        String mentorToken = loginAndGetAccessToken("consult-mentor-2@example.com", "Passw0rd!");
        String studentToken = loginAndGetAccessToken("consult-student-2@example.com", "Passw0rd!");

        mockMvc.perform(get("/api/v1/mentors")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-1004"));

        String createOrderBody = String.format(
                "{" +
                        "\"mentorUserId\":%d," +
                        "\"questionText\":\"想请你帮我看看项目表达。\"" +
                        "}",
                mentorUserId
        );

        MvcResult createOrderResult = mockMvc.perform(post("/api/v1/consult/orders")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createOrderBody))
                .andExpect(status().isOk())
                .andReturn();

        String orderNo = objectMapper.readTree(createOrderResult.getResponse().getContentAsString())
                .path("data")
                .path("orderNo")
                .asText();

        mockMvc.perform(post("/api/v1/consult/orders/{orderNo}/messages", orderNo)
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageText\":\"未支付前不能答复。\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BIZ-1001"));

        mockMvc.perform(post("/api/v1/consult/orders/{orderNo}/close", orderNo)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BIZ-1001"));
    }

    @Test
    void consultEndpoints_shouldReturnMentorWorkbenchAndAdminOrdersAfterJpaMigration() throws Exception {
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");
        long mentorUserId = registerUser(
                "MENTOR",
                "consult-workbench-endpoint-mentor@example.com",
                "Passw0rd!",
                "Workbench Endpoint Mentor"
        );
        long expiringStudentUserId = registerUser(
                "STUDENT",
                "consult-workbench-endpoint-expiring@example.com",
                "Passw0rd!",
                "Workbench Expiring Student"
        );
        long answeredStudentUserId = registerUser(
                "STUDENT",
                "consult-workbench-endpoint-answered@example.com",
                "Passw0rd!",
                "Workbench Answered Student"
        );
        long refundedStudentUserId = registerUser(
                "STUDENT",
                "consult-workbench-endpoint-refunded@example.com",
                "Passw0rd!",
                "Workbench Refunded Student"
        );
        long afterSalesStudentUserId = registerUser(
                "STUDENT",
                "consult-workbench-endpoint-after-sales@example.com",
                "Passw0rd!",
                "Workbench After Sales Student"
        );
        String mentorToken = loginAndGetAccessToken("consult-workbench-endpoint-mentor@example.com", "Passw0rd!");

        int replyTimeoutHours = consultAfterSalesService.getMentorReplyTimeoutHours();
        Instant now = Instant.now();
        Instant expiringPaidAt = now.minusSeconds(Math.max(replyTimeoutHours - 2, 1) * 3600L);
        String expiringOrderNo = "H2-CONSULT-WB-001";
        String answeredOrderNo = "H2-CONSULT-WB-002";
        String refundedOrderNo = "H2-CONSULT-WB-003";
        String afterSalesOrderNo = "H2-CONSULT-WB-004";

        insertConsultOrderRecord(
                expiringOrderNo,
                expiringStudentUserId,
                mentorUserId,
                "PAID",
                16800,
                "即将超时的导师订单",
                expiringPaidAt.minusSeconds(1800),
                expiringPaidAt,
                null
        );
        insertConsultOrderRecord(
                answeredOrderNo,
                answeredStudentUserId,
                mentorUserId,
                "ANSWERED",
                12800,
                "已答复等待学生确认",
                now.minusSeconds(8 * 3600),
                now.minusSeconds(7 * 3600),
                null
        );
        insertConsultOrderRecord(
                refundedOrderNo,
                refundedStudentUserId,
                mentorUserId,
                "REFUNDED",
                18800,
                "管理员已退款的工作台订单",
                now.minusSeconds(10 * 3600),
                now.minusSeconds(9 * 3600),
                now.minusSeconds(8 * 3600)
        );
        insertConsultOrderRecord(
                afterSalesOrderNo,
                afterSalesStudentUserId,
                mentorUserId,
                "CLOSED",
                13800,
                "已完成但仍有售后申请的订单",
                now.minusSeconds(6 * 3600),
                now.minusSeconds(5 * 3600),
                now.minusSeconds(4 * 3600)
        );

        insertPaymentRecordRecord(expiringOrderNo, "ALIPAY", "SUCCESS", now.minusSeconds(30 * 60));
        insertPaymentRecordRecord(answeredOrderNo, "ALIPAY", "SUCCESS", now.minusSeconds(7 * 3600));
        insertPaymentRecordRecord(refundedOrderNo, "WECHAT", "SUCCESS", now.minusSeconds(9 * 3600));
        insertAfterSalesRequestRecord(afterSalesOrderNo, afterSalesStudentUserId, "PENDING", "希望继续申诉");

        mockMvc.perform(get("/api/v1/consult/orders/mentor-workbench")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.pendingReplyCount").value(1))
                .andExpect(jsonPath("$.data.summary.expiringSoonCount").value(1))
                .andExpect(jsonPath("$.data.summary.waitingConfirmationCount").value(1))
                .andExpect(jsonPath("$.data.summary.afterSalesImpactCount").value(2))
                .andExpect(jsonPath("$.data.records[0].orderNo").value(expiringOrderNo))
                .andExpect(jsonPath("$.data.records[0].paymentMode").value("ALIPAY"))
                .andExpect(jsonPath("$.data.records[1].orderNo").value(refundedOrderNo))
                .andExpect(jsonPath("$.data.records[2].orderNo").value(afterSalesOrderNo))
                .andExpect(jsonPath("$.data.records[2].afterSalesImpact").value(true))
                .andExpect(jsonPath("$.data.records[2].pendingAfterSales").value(true))
                .andExpect(jsonPath("$.data.records[2].latestAfterSalesStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.availablePaymentModes[0]").value("ALIPAY"))
                .andExpect(jsonPath("$.data.availablePaymentModes[1]").value("WECHAT"));

        mockMvc.perform(get("/api/v1/admin/consult/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("keyword", "Workbench Endpoint Mentor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.records[0].mentorDisplayName").value("Workbench Endpoint Mentor"));

        mockMvc.perform(get("/api/v1/admin/consult/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("keyword", "Workbench Endpoint Mentor")
                        .param("status", "REFUNDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].orderNo").value(refundedOrderNo))
                .andExpect(jsonPath("$.data.records[0].paymentMode").value("WECHAT"));
    }

    private void insertConsultOrderRecord(
            String orderNo,
            long studentUserId,
            long mentorUserId,
            String status,
            int amountFen,
            String questionText,
            Instant createdAt,
            Instant paidAt,
            Instant closedAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO consult_orders(
                    order_no,
                    student_user_id,
                    mentor_user_id,
                    scene_code,
                    source_page,
                    amount_fen,
                    status,
                    question_text,
                    appointment_start_at,
                    appointment_end_at,
                    paid_at,
                    closed_at,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, 'PROJECT_REVIEW', 'MENTOR_WORKBENCH', ?, ?, ?, NULL, NULL, ?, ?, ?, ?)
                """,
                orderNo,
                studentUserId,
                mentorUserId,
                amountFen,
                status,
                questionText,
                paidAt == null ? null : Timestamp.from(paidAt),
                closedAt == null ? null : Timestamp.from(closedAt),
                Timestamp.from(createdAt),
                Timestamp.from(createdAt.plusSeconds(60))
        );
    }

    private void insertPaymentRecordRecord(String orderNo, String mode, String status, Instant createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO payment_records(
                    order_no,
                    channel,
                    mode,
                    provider_trade_no,
                    amount_fen,
                    status,
                    idempotency_key,
                    raw_callback,
                    created_at,
                    updated_at
                ) VALUES (?, 'ALIPAY', ?, ?, 100, ?, ?, '{}', ?, ?)
                """,
                orderNo,
                mode,
                "FLOW-" + orderNo + "-" + mode,
                status,
                "FLOW-" + orderNo + "-" + status,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        );
    }

    private void insertAfterSalesRequestRecord(String orderNo, long requesterUserId, String status, String reason) {
        jdbcTemplate.update(
                """
                INSERT INTO consult_after_sales_requests(
                    order_no,
                    requester_user_id,
                    request_type,
                    status,
                    reason,
                    auto_triggered,
                    created_at,
                    updated_at
                ) VALUES (?, ?, 'REFUND', ?, ?, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                orderNo,
                requesterUserId,
                status,
                reason
        );
    }

    private long registerUser(String role, String email, String password, String displayName) throws Exception {
        return registerUser(role, email, password, displayName, null, null, null);
    }

    private long registerMentor(
            String email,
            String password,
            String displayName,
            String companyName,
            String jobTitle
    ) throws Exception {
        return registerUser("MENTOR", email, password, displayName, null, companyName, jobTitle);
    }

    private long registerUser(
            String role,
            String email,
            String password,
            String displayName,
            String realName,
            String companyName,
            String jobTitle
    ) throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode payload = objectMapper.createObjectNode();
        payload.put("role", role);
        payload.put("email", email);
        payload.put("password", password);
        payload.put("displayName", displayName);
        if (realName != null) {
            payload.put("realName", realName);
        }
        if (companyName != null) {
            payload.put("companyName", companyName);
        }
        if (jobTitle != null) {
            payload.put("jobTitle", jobTitle);
        }
        String body = payload.toString();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        long userId = jsonNode.path("data").path("userId").asLong();
        if ("MENTOR".equals(role)) {
            jdbcTemplate.update(
                    "UPDATE mentor_profiles SET approval_status = 'APPROVED', updated_at = CURRENT_TIMESTAMP WHERE user_id = ?",
                    userId
            );
        }
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
        String accessToken = jsonNode.path("data").path("accessToken").asText();
        assertThat(accessToken).isNotBlank();
        return accessToken;
    }
}
