package com.bishe.server.profile;

import com.bishe.server.certification.service.CertificationReviewListCacheService;
import com.bishe.server.community.service.CommunityPostListCacheService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 内容治理最小闭环测试：社区审查、举报、后台处置与频控。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:governanceintegration;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GovernanceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CommunityPostListCacheService communityPostListCacheService;

    @Autowired
    private CertificationReviewListCacheService certificationReviewListCacheService;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        communityPostListCacheService.evictAllNow();
        certificationReviewListCacheService.evictAllNow();
        deleteCacheKeys(
                "community:posts:detail:*",
                "community:posts:comments:*",
                "certification:reviews:detail:*"
        );
    }

    private void deleteCacheKeys(String... patterns) {
        if (stringRedisTemplate == null || patterns == null) {
            return;
        }
        for (String pattern : patterns) {
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            Set<String> keys = stringRedisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        }
    }

    @Test
    void communityReviewQueue_shouldSupportSubmitAndApprove() throws Exception {
        registerUser("MENTOR", "review-mentor@example.com", "Passw0rd!", "ReviewMentor");
        registerUser("STUDENT", "review-student@example.com", "Passw0rd!", "ReviewStudent");
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");
        String mentorToken = loginAndGetAccessToken("review-mentor@example.com", "Passw0rd!");
        String studentToken = loginAndGetAccessToken("review-student@example.com", "Passw0rd!");

        createSensitiveTerm(adminToken, "灰测待审词A", "MEDIUM", "REVIEW", "COMMUNITY_POST");

        MvcResult createPostResult = mockMvc.perform(post("/api/v1/community/posts")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "灰测待审词A 的讨论",
                                  "content": "这是一条应该进入待审队列的帖子内容。",
                                  "tags": ["治理测试"]
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("MOD-1003"))
                .andExpect(jsonPath("$.data.moderation.action").value("REVIEW"))
                .andReturn();

        long postId = readLong(createPostResult, "data", "postId");
        assertThat(postId).isPositive();

        mockMvc.perform(get("/api/v1/community/posts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        MvcResult reviewQueueResult = mockMvc.perform(get("/api/v1/admin/content/review-queue")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].targetType").value("POST"))
                .andReturn();

        long reviewItemId = readLong(reviewQueueResult, "data", "records", 0, "itemId");
        assertThat(reviewItemId).isPositive();

        mockMvc.perform(post("/api/v1/admin/content/review-queue/" + reviewItemId + "/decision")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "comment": "人工审核通过"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        mockMvc.perform(get("/api/v1/community/posts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].postId").value(postId));

        mockMvc.perform(get("/api/v1/admin/content/audit-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("actionType", "CONTENT_REVIEW_DECISION")
                        .param("targetType", "POST")
                        .param("targetId", String.valueOf(postId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].actionType").value("CONTENT_REVIEW_DECISION"))
                .andExpect(jsonPath("$.data.records[0].targetType").value("POST"))
                .andExpect(jsonPath("$.data.records[0].targetId").value(String.valueOf(postId)))
                .andExpect(jsonPath("$.data.records[0].detailJson").value(org.hamcrest.Matchers.containsString("APPROVE")));
    }

    @Test
    void commentBlockAndReportRestore_shouldWork() throws Exception {
        registerUser("MENTOR", "gov-mentor@example.com", "Passw0rd!", "GovMentor");
        registerUser("STUDENT", "gov-student@example.com", "Passw0rd!", "GovStudent");
        registerUser("MENTOR", "gov-reporter2@example.com", "Passw0rd!", "GovReporter2");
        registerUser("ENTERPRISE", "gov-reporter3@example.com", "Passw0rd!", "GovReporter3");
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");
        String mentorToken = loginAndGetAccessToken("gov-mentor@example.com", "Passw0rd!");
        String studentToken = loginAndGetAccessToken("gov-student@example.com", "Passw0rd!");
        String reporter2Token = loginAndGetAccessToken("gov-reporter2@example.com", "Passw0rd!");
        String reporter3Token = loginAndGetAccessToken("gov-reporter3@example.com", "Passw0rd!");

        createSensitiveTerm(adminToken, "屏蔽评论词B", "HIGH", "BLOCK", "COMMUNITY_COMMENT");

        long postId = createPost(mentorToken, "治理正常帖", "这是一条用于举报恢复流程的正常帖子。");

        mockMvc.perform(post("/api/v1/community/posts/" + postId + "/comments")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "这里包含 屏蔽评论词B ，应该被拦截。"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MOD-1001"))
                .andExpect(jsonPath("$.data.moderation.action").value("BLOCK"));

        long reportId = submitReport(studentToken, "POST", String.valueOf(postId), "ABUSE", "第一位举报人");
        submitReport(reporter2Token, "POST", String.valueOf(postId), "ABUSE", "第二位举报人");
        submitReport(reporter3Token, "POST", String.valueOf(postId), "ABUSE", "第三位举报人");

        mockMvc.perform(get("/api/v1/community/posts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/api/v1/admin/content/reports")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].reportCount").value(3))
                .andExpect(jsonPath("$.data.records[0].contentPostId").value(String.valueOf(postId)))
                .andExpect(jsonPath("$.data.records[0].contentTitle").value("治理正常帖"))
                .andExpect(jsonPath("$.data.records[0].contentBody").value("这是一条用于举报恢复流程的正常帖子。"))
                .andExpect(jsonPath("$.data.records[0].latestAction").value("NONE"));

        mockMvc.perform(post("/api/v1/admin/content/reports/" + reportId + "/decision")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "ACCEPTED",
                                  "action": "RESTORE",
                                  "comment": "经核查恢复展示"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        mockMvc.perform(get("/api/v1/community/posts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].postId").value(postId));

        mockMvc.perform(get("/api/v1/admin/content/reports")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.records[0].latestAction").value("RESTORE"));

        mockMvc.perform(get("/api/v1/admin/content/reports/" + reportId + "/actions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].decision").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.records[0].action").value("RESTORE"))
                .andExpect(jsonPath("$.data.records[0].comment").value("经核查恢复展示"));

        mockMvc.perform(get("/api/v1/community/reports/mine")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].status").value("ACCEPTED"));

        mockMvc.perform(get("/api/v1/admin/content/audit-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("actionType", "REPORT_DECISION")
                        .param("targetType", "POST")
                        .param("targetId", String.valueOf(postId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].actionType").value("REPORT_DECISION"))
                .andExpect(jsonPath("$.data.records[0].targetId").value(String.valueOf(postId)))
                .andExpect(jsonPath("$.data.records[0].detailJson").value(org.hamcrest.Matchers.containsString("RESTORE")));

        mockMvc.perform(get("/api/v1/admin/content/audit-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("actionType", "AUTO_HIDE")
                        .param("targetType", "POST")
                        .param("targetId", String.valueOf(postId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].actionType").value("AUTO_HIDE"))
                .andExpect(jsonPath("$.data.records[0].operatorUserId").value(0))
                .andExpect(jsonPath("$.data.records[0].operatorDisplayName").value("系统"));
    }

    @Test
    void reportDecision_shouldEvictPostDetailCacheAfterTakeDown() throws Exception {
        registerUser("MENTOR", "detail-cache-mentor@example.com", "Passw0rd!", "DetailCacheMentor");
        registerUser("STUDENT", "detail-cache-student@example.com", "Passw0rd!", "DetailCacheStudent");
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");
        String mentorToken = loginAndGetAccessToken("detail-cache-mentor@example.com", "Passw0rd!");
        String studentToken = loginAndGetAccessToken("detail-cache-student@example.com", "Passw0rd!");

        long postId = createPost(mentorToken, "详情缓存治理测试帖", "这是一条用于验证详情缓存失效的正常帖子。");

        mockMvc.perform(get("/api/v1/community/posts/{postId}", postId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postId").value(postId));

        long reportId = submitReport(studentToken, "POST", String.valueOf(postId), "ABUSE", "验证治理后详情缓存失效");

        mockMvc.perform(post("/api/v1/admin/content/reports/" + reportId + "/decision")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "ACCEPTED",
                                  "action": "TAKE_DOWN",
                                  "comment": "下架测试帖"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        mockMvc.perform(get("/api/v1/community/posts/{postId}", postId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BIZ-1002"));
    }

    @Test
    void reviewDecision_shouldEvictCachedCommentListAfterApprove() throws Exception {
        registerUser("MENTOR", "comment-review-mentor@example.com", "Passw0rd!", "CommentReviewMentor");
        registerUser("STUDENT", "comment-review-student@example.com", "Passw0rd!", "CommentReviewStudent");
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");
        String mentorToken = loginAndGetAccessToken("comment-review-mentor@example.com", "Passw0rd!");
        String studentToken = loginAndGetAccessToken("comment-review-student@example.com", "Passw0rd!");

        createSensitiveTerm(adminToken, "评论待审缓存词D", "MEDIUM", "REVIEW", "COMMUNITY_COMMENT");
        long postId = createPost(mentorToken, "评论缓存治理测试帖", "用于验证评论列表缓存在审核通过后刷新。");

        MvcResult firstDetailResult = mockMvc.perform(get("/api/v1/community/posts/{postId}", postId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode firstDetail = objectMapper.readTree(firstDetailResult.getResponse().getContentAsString()).path("data");
        int initialCommentCount = firstDetail.path("commentCount").asInt();
        int initialCommentsSize = firstDetail.path("comments").size();

        mockMvc.perform(post("/api/v1/community/posts/" + postId + "/comments")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "这条评论包含评论待审缓存词D，需要进入待审。"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("MOD-1003"))
                .andExpect(jsonPath("$.data.moderation.action").value("REVIEW"));

        MvcResult reviewQueueResult = mockMvc.perform(get("/api/v1/admin/content/review-queue")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        long reviewItemId = readLong(reviewQueueResult, "data", "records", 0, "itemId");
        assertThat(reviewItemId).isPositive();

        mockMvc.perform(post("/api/v1/admin/content/review-queue/" + reviewItemId + "/decision")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "comment": "允许展示"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        mockMvc.perform(get("/api/v1/community/posts/{postId}", postId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commentCount").value(initialCommentCount + 1))
                .andExpect(jsonPath("$.data.comments.length()").value(initialCommentsSize + 1))
                .andExpect(jsonPath("$.data.comments[*].content").value(org.hamcrest.Matchers.hasItem("这条评论包含评论待审缓存词D，需要进入待审。")));
    }

    @Test
    void reviewQueueDetail_shouldReturnCommentContentAndParentPostContext() throws Exception {
        registerUser("MENTOR", "review-detail-mentor@example.com", "Passw0rd!", "ReviewDetailMentor");
        registerUser("STUDENT", "review-detail-student@example.com", "Passw0rd!", "ReviewDetailStudent");
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");
        String mentorToken = loginAndGetAccessToken("review-detail-mentor@example.com", "Passw0rd!");
        String studentToken = loginAndGetAccessToken("review-detail-student@example.com", "Passw0rd!");

        createSensitiveTerm(adminToken, "灰测评论词C", "MEDIUM", "REVIEW", "COMMUNITY_COMMENT");
        long postId = createPost(mentorToken, "评论待审父帖", "这是评论待审场景下的父帖正文。");

        MvcResult createCommentResult = mockMvc.perform(post("/api/v1/community/posts/" + postId + "/comments")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "这条评论包含灰测评论词C，需要进入待审。"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("MOD-1003"))
                .andExpect(jsonPath("$.data.moderation.action").value("REVIEW"))
                .andReturn();

        long commentId = readLong(createCommentResult, "data", "commentId");
        assertThat(commentId).isPositive();

        MvcResult reviewQueueResult = mockMvc.perform(get("/api/v1/admin/content/review-queue")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].targetType").value("COMMENT"))
                .andReturn();

        long reviewItemId = readLong(reviewQueueResult, "data", "records", 0, "itemId");
        assertThat(reviewItemId).isPositive();

        mockMvc.perform(get("/api/v1/admin/content/review-queue/" + reviewItemId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.itemId").value(reviewItemId))
                .andExpect(jsonPath("$.data.targetType").value("COMMENT"))
                .andExpect(jsonPath("$.data.targetId").value(String.valueOf(commentId)))
                .andExpect(jsonPath("$.data.contentBody").value("这条评论包含灰测评论词C，需要进入待审。"))
                .andExpect(jsonPath("$.data.postId").value(String.valueOf(postId)))
                .andExpect(jsonPath("$.data.postTitle").value("评论待审父帖"))
                .andExpect(jsonPath("$.data.postBody").value("这是评论待审场景下的父帖正文。"))
                .andExpect(jsonPath("$.data.authorDisplayName").value("ReviewDetailStudent"))
                .andExpect(jsonPath("$.data.authorRole").value("STUDENT"));
    }

    @Test
    void adminShouldReadHiddenPostAndCommentForGovernanceJump() throws Exception {
        registerUser("MENTOR", "admin-hidden-post-mentor@example.com", "Passw0rd!", "HiddenPostMentor");
        long studentUserId = registerUserAndGetId("STUDENT", "admin-hidden-post-student@example.com", "Passw0rd!", "HiddenPostStudent");
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");
        String mentorToken = loginAndGetAccessToken("admin-hidden-post-mentor@example.com", "Passw0rd!");

        createSensitiveTerm(adminToken, "管理员原帖核查词E", "MEDIUM", "REVIEW", "COMMUNITY_POST");

        MvcResult createPostResult = mockMvc.perform(post("/api/v1/community/posts")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "管理员原帖核查测试帖",
                                  "content": "正文中包含 管理员原帖核查词E ，会进入待审。",
                                  "tags": ["治理联查"]
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("MOD-1003"))
                .andExpect(jsonPath("$.data.moderation.action").value("REVIEW"))
                .andReturn();

        long postId = readLong(createPostResult, "data", "postId");
        jdbcTemplate.update(
                """
                INSERT INTO comments(post_id, user_id, content, is_ai, moderation_status, risk_level, last_moderation_event_id, is_deleted, created_at, updated_at)
                VALUES (?, ?, ?, 0, 'REVIEW', 'MEDIUM', NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                postId,
                studentUserId,
                "这条待审评论用于验证管理员可直接查看隐藏评论原文。"
        );

        mockMvc.perform(get("/api/v1/community/posts/{postId}", postId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postId").value(postId))
                .andExpect(jsonPath("$.data.moderationStatus").value("REVIEW"))
                .andExpect(jsonPath("$.data.title").value("管理员原帖核查测试帖"))
                .andExpect(jsonPath("$.data.comments[*].content").value(org.hamcrest.Matchers.hasItem("这条待审评论用于验证管理员可直接查看隐藏评论原文。")));
    }

    @Test
    void sensitiveTerms_shouldSupportUpdate() throws Exception {
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");
        long termId = createSensitiveTermAndGetId(adminToken, "旧敏感词", "OTHER", "HIGH", "BLOCK", "COMMUNITY_POST");

        mockMvc.perform(put("/api/v1/admin/content/sensitive-terms/" + termId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "term": "新敏感词",
                                  "termType": "POLITICS",
                                  "riskLevel": "MEDIUM",
                                  "action": "REVIEW",
                                  "sourceScope": "AI_OUTPUT",
                                  "whitelist": true,
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.termId").value(termId))
                .andExpect(jsonPath("$.data.term").value("新敏感词"))
                .andExpect(jsonPath("$.data.termType").value("POLITICS"))
                .andExpect(jsonPath("$.data.riskLevel").value("MEDIUM"))
                .andExpect(jsonPath("$.data.action").value("REVIEW"))
                .andExpect(jsonPath("$.data.sourceScope").value("AI_OUTPUT"))
                .andExpect(jsonPath("$.data.whitelist").value(true))
                .andExpect(jsonPath("$.data.enabled").value(false));

        mockMvc.perform(get("/api/v1/admin/content/sensitive-terms")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].termId").value(termId))
                .andExpect(jsonPath("$.data.records[0].term").value("新敏感词"))
                .andExpect(jsonPath("$.data.records[0].termType").value("POLITICS"))
                .andExpect(jsonPath("$.data.records[0].riskLevel").value("MEDIUM"))
                .andExpect(jsonPath("$.data.records[0].action").value("REVIEW"))
                .andExpect(jsonPath("$.data.records[0].sourceScope").value("AI_OUTPUT"))
                .andExpect(jsonPath("$.data.records[0].whitelist").value(true))
                .andExpect(jsonPath("$.data.records[0].enabled").value(false));
    }

    @Test
    void sensitiveTerms_shouldSupportBatchStatusImportExportAndDelete() throws Exception {
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");
        long termA = createSensitiveTermAndGetId(adminToken, "政治词A", "POLITICS", "HIGH", "BLOCK", "COMMUNITY_POST");
        long termB = createSensitiveTermAndGetId(adminToken, "色情词B", "PORNOGRAPHY", "MEDIUM", "REVIEW", "AI_OUTPUT");

        mockMvc.perform(post("/api/v1/admin/content/sensitive-terms/batch-status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "termIds": [%d, %d],
                                  "enabled": false
                                }
                                """.formatted(termA, termB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.affectedCount").value(2));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sensitive-terms.csv",
                "text/csv",
                """
                        term,termType,riskLevel,action,sourceScope,whitelist,enabled
                        政治词A,VIOLENCE,MEDIUM,REVIEW,COMMUNITY_POST,false,true
                        恐怖词C,TERROR,HIGH,BLOCK,AI_INPUT,false,true
                        """.getBytes()
        );

        mockMvc.perform(multipart("/api/v1/admin/content/sensitive-terms/import")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.totalRows").value(2))
                .andExpect(jsonPath("$.data.createdCount").value(1))
                .andExpect(jsonPath("$.data.updatedCount").value(1))
                .andExpect(jsonPath("$.data.skippedCount").value(0));

        mockMvc.perform(get("/api/v1/admin/content/sensitive-terms")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records[0].term").value("恐怖词C"))
                .andExpect(jsonPath("$.data.records[0].termType").value("TERROR"));

        MvcResult exportResult = mockMvc.perform(get("/api/v1/admin/content/sensitive-terms/export")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        String exportContent = exportResult.getResponse().getContentAsString();
        assertThat(exportResult.getResponse().getHeader("Content-Disposition")).contains("sensitive-terms-");
        assertThat(exportContent).contains("term,termType,riskLevel,action,sourceScope,whitelist,enabled");
        assertThat(exportContent).contains("政治词A");
        assertThat(exportContent).contains("VIOLENCE");
        assertThat(exportContent).contains("恐怖词C");
        assertThat(exportContent).contains("TERROR");

        mockMvc.perform(post("/api/v1/admin/content/sensitive-terms/batch-delete")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "termIds": [%d, %d]
                                }
                                """.formatted(termA, termB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.affectedCount").value(2));

        mockMvc.perform(get("/api/v1/admin/content/sensitive-terms")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].term").value("恐怖词C"));
    }

    @Test
    void adminUserDetail_shouldReturnStudentProfileSummary() throws Exception {
        long studentUserId = registerUserAndGetId("STUDENT", "admin-user-detail@example.com", "Passw0rd!", "DetailStudent");
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");
        String studentToken = loginAndGetAccessToken("admin-user-detail@example.com", "Passw0rd!");

        mockMvc.perform(put("/api/v1/profiles/students/me")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "major": "软件工程",
                                  "grade": "大三",
                                  "targetPosition": "前端开发",
                                  "skillTags": ["React", "TypeScript"],
                                  "selfIntro": "用于管理员 USER 举报详情联调"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        createPost(studentToken, "用户详情联调帖", "用于验证学生近 7 天社区贡献分统计。");

        mockMvc.perform(get("/api/v1/admin/users/" + studentUserId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(studentUserId))
                .andExpect(jsonPath("$.data.email").value("admin-user-detail@example.com"))
                .andExpect(jsonPath("$.data.displayName").value("DetailStudent"))
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.studentProfile.major").value("软件工程"))
                .andExpect(jsonPath("$.data.studentProfile.grade").value("大三"))
                .andExpect(jsonPath("$.data.studentProfile.targetPosition").value("前端开发"))
                .andExpect(jsonPath("$.data.studentProfile.skillTags[0]").value("React"))
                .andExpect(jsonPath("$.data.studentProfile.skillTags[1]").value("TypeScript"))
                .andExpect(jsonPath("$.data.studentProfile.selfIntro").value("用于管理员 USER 举报详情联调"))
                .andExpect(jsonPath("$.data.communityScore7d").value(5));
    }

    @Test
    void adminListUsers_shouldSupportKeywordAndFilters() throws Exception {
        registerUser("MENTOR", "list-mentor@example.com", "Passw0rd!", "ListMentor");
        long suspendedStudentUserId = registerUserAndGetId("STUDENT", "list-student@example.com", "Passw0rd!", "ListStudent");
        registerUser("ENTERPRISE", "list-enterprise@example.com", "Passw0rd!", "ListEnterprise");
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");

        mockMvc.perform(post("/api/v1/admin/users/" + suspendedStudentUserId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "SUSPENDED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("page", "1")
                        .param("size", "10")
                        .param("keyword", "ListStudent")
                        .param("role", "STUDENT")
                        .param("status", "SUSPENDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].userId").value(suspendedStudentUserId))
                .andExpect(jsonPath("$.data.records[0].email").value("list-student@example.com"))
                .andExpect(jsonPath("$.data.records[0].displayName").value("ListStudent"))
                .andExpect(jsonPath("$.data.records[0].role").value("STUDENT"))
                .andExpect(jsonPath("$.data.records[0].status").value("SUSPENDED"));
    }

    @Test
    void adminResetPassword_shouldInvalidateOldPassword() throws Exception {
        long userId = registerUserAndGetId("MENTOR", "reset-target@example.com", "Passw0rd!", "ResetTarget");
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");

        mockMvc.perform(post("/api/v1/admin/users/" + userId + "/reset-password")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newPassword": "Reset1234"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "reset-target@example.com",
                                  "password": "Passw0rd!"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-1002"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "reset-target@example.com",
                                  "password": "Reset1234"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    void adminUpdateUserStatus_shouldBlockAndRestoreLogin() throws Exception {
        long userId = registerUserAndGetId("MENTOR", "status-target@example.com", "Passw0rd!", "StatusTarget");
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");

        mockMvc.perform(post("/api/v1/admin/users/" + userId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "SUSPENDED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "status-target@example.com",
                                  "password": "Passw0rd!"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-1006"));

        mockMvc.perform(post("/api/v1/admin/users/" + userId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "status-target@example.com",
                                  "password": "Passw0rd!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    void adminUpdateUserTier_shouldSupportStudentVipToggleOnly() throws Exception {
        long studentUserId = registerUserAndGetId("STUDENT", "tier-target@example.com", "Passw0rd!", "TierTarget");
        long mentorUserId = registerUserAndGetId("MENTOR", "tier-mentor@example.com", "Passw0rd!", "TierMentor");
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");

        mockMvc.perform(post("/api/v1/admin/users/" + studentUserId + "/tier")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tier": "PREMIUM"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        mockMvc.perform(get("/api/v1/admin/users/" + studentUserId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(studentUserId))
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.tier").value("PREMIUM"));

        mockMvc.perform(post("/api/v1/admin/users/" + mentorUserId + "/tier")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tier": "PREMIUM"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BIZ-1001"));
    }

    @Test
    void adminUpdateUserApprovalStatus_shouldSupportMentorAndEnterprise() throws Exception {
        long mentorUserId = registerUserAndGetId("MENTOR", "approval-mentor@example.com", "Passw0rd!", "ApprovalMentor");
        long enterpriseUserId = registerUserAndGetId("ENTERPRISE", "approval-enterprise@example.com", "Passw0rd!", "ApprovalEnterprise");
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");

        mockMvc.perform(post("/api/v1/admin/users/" + mentorUserId + "/approval-status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approvalStatus": "PENDING"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("page", "1")
                        .param("size", "10")
                        .param("role", "MENTOR")
                        .param("approvalStatus", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].userId").value(mentorUserId))
                .andExpect(jsonPath("$.data.records[0].approvalStatus").value("PENDING"));

        mockMvc.perform(post("/api/v1/admin/users/" + enterpriseUserId + "/approval-status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approvalStatus": "REJECTED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        mockMvc.perform(get("/api/v1/admin/users/" + enterpriseUserId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(enterpriseUserId))
                .andExpect(jsonPath("$.data.role").value("ENTERPRISE"))
                .andExpect(jsonPath("$.data.approvalStatus").value("REJECTED"));
    }

    @Test
    void adminEndpoints_shouldHideAndBlockSystemAccounts() throws Exception {
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");
        long systemUserId = insertSystemMentorWithPendingCertification("community-ai-bot@system.local", "AI 助手");

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("keyword", "community-ai-bot@system.local")
                        .param("role", "MENTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/api/v1/admin/users/" + systemUserId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BIZ-1002"));

        mockMvc.perform(post("/api/v1/admin/users/" + systemUserId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "SUSPENDED"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BIZ-1002"));

        mockMvc.perform(post("/api/v1/admin/users/" + systemUserId + "/reset-password")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newPassword": "Reset1234"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BIZ-1002"));

        mockMvc.perform(post("/api/v1/admin/users/" + systemUserId + "/approval-status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approvalStatus": "APPROVED"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BIZ-1002"));

        mockMvc.perform(get("/api/v1/admin/users/certification-reviews")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("keyword", "community-ai-bot@system.local")
                        .param("role", "MENTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/api/v1/admin/users/" + systemUserId + "/certification-review")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BIZ-1002"));

        mockMvc.perform(post("/api/v1/admin/users/" + systemUserId + "/certification-review")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approvalStatus": "APPROVED",
                                  "reviewNote": "系统账号不应进入审核流"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BIZ-1002"));
    }

    @Test
    void reportRateLimit_shouldReturnMod1004() throws Exception {
        registerUser("STUDENT", "limit-reporter@example.com", "Passw0rd!", "LimitReporter");
        String reporterToken = loginAndGetAccessToken("limit-reporter@example.com", "Passw0rd!");
        long[] targetUserIds = new long[6];

        for (int index = 1; index <= 6; index++) {
            targetUserIds[index - 1] = registerUserAndGetId("MENTOR", "limit-target-" + index + "@example.com", "Passw0rd!", "LimitTarget" + index);
        }

        for (int index = 1; index <= 5; index++) {
            mockMvc.perform(post("/api/v1/community/reports")
                            .header("Authorization", "Bearer " + reporterToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "targetType": "USER",
                                      "targetId": "%d",
                                      "reasonCode": "ABUSE",
                                      "detail": "频控测试"
                                    }
                                    """.formatted(targetUserIds[index - 1])))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("OK"));
        }

        mockMvc.perform(post("/api/v1/community/reports")
                        .header("Authorization", "Bearer " + reporterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetType": "USER",
                                  "targetId": "%d",
                                  "reasonCode": "ABUSE",
                                  "detail": "第六次举报应被限流"
                                }
                                """.formatted(targetUserIds[5])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MOD-1004"));
    }

    @Test
    void duplicateReport_shouldReuseCachedResultAndRefreshAfterDecision() throws Exception {
        registerUser("MENTOR", "duplicate-target@example.com", "Passw0rd!", "DuplicateTarget");
        registerUser("STUDENT", "duplicate-reporter@example.com", "Passw0rd!", "DuplicateReporter");
        String adminToken = loginAndGetAccessToken("admin@test.local", "Admin123456!");
        String mentorToken = loginAndGetAccessToken("duplicate-target@example.com", "Passw0rd!");
        String reporterToken = loginAndGetAccessToken("duplicate-reporter@example.com", "Passw0rd!");

        long postId = createPost(mentorToken, "重复举报测试帖", "用于验证重复举报缓存。");
        long reportId = submitReport(reporterToken, "POST", String.valueOf(postId), "ABUSE", "第一次举报");

        mockMvc.perform(post("/api/v1/community/reports")
                        .header("Authorization", "Bearer " + reporterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetType": "POST",
                                  "targetId": "%s",
                                  "reasonCode": "ABUSE",
                                  "detail": "第二次举报应直接返回已存在结果"
                                }
                                """.formatted(postId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportId").value(reportId))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        mockMvc.perform(post("/api/v1/admin/content/reports/" + reportId + "/decision")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "REJECTED",
                                  "action": "NO_ACTION",
                                  "comment": "重复举报缓存失效验证"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        mockMvc.perform(post("/api/v1/community/reports")
                        .header("Authorization", "Bearer " + reporterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetType": "POST",
                                  "targetId": "%s",
                                  "reasonCode": "ABUSE",
                                  "detail": "处置后再次举报应返回最新状态"
                                }
                                """.formatted(postId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportId").value(reportId))
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    private void createSensitiveTerm(String adminToken, String term, String riskLevel, String action, String sourceScope) throws Exception {
        createSensitiveTermAndGetId(adminToken, term, "OTHER", riskLevel, action, sourceScope);
    }

    private long createSensitiveTermAndGetId(String adminToken, String term, String termType, String riskLevel, String action, String sourceScope) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/content/sensitive-terms")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "term": "%s",
                                  "termType": "%s",
                                  "riskLevel": "%s",
                                  "action": "%s",
                                  "sourceScope": "%s",
                                  "whitelist": false,
                                  "enabled": true
                                }
                                """.formatted(term, termType, riskLevel, action, sourceScope)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andReturn();
        return readLong(result, "data", "termId");
    }

    private long createPost(String token, String title, String content) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/community/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "content": "%s",
                                  "tags": ["治理"]
                                }
                                """.formatted(title, content)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andReturn();
        return readLong(result, "data", "postId");
    }

    private long submitReport(String token, String targetType, String targetId, String reasonCode, String detail) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/community/reports")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetType": "%s",
                                  "targetId": "%s",
                                  "reasonCode": "%s",
                                  "detail": "%s"
                                }
                                """.formatted(targetType, targetId, reasonCode, detail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andReturn();
        return readLong(result, "data", "reportId");
    }

    private long readLong(MvcResult result, Object... path) throws Exception {
        JsonNode current = objectMapper.readTree(result.getResponse().getContentAsString());
        for (Object item : path) {
            if (item instanceof Integer index) {
                current = current.path(index);
            } else {
                current = current.path(String.valueOf(item));
            }
        }
        long value = current.asLong();
        assertThat(value).isPositive();
        return value;
    }

    private void registerUser(String role, String email, String password, String displayName) throws Exception {
        registerUserAndGetId(role, email, password, displayName);
    }

    private long registerUserAndGetId(String role, String email, String password, String displayName) throws Exception {
        String registerBody = """
                {
                  "role": "%s",
                  "email": "%s",
                  "password": "%s",
                  "displayName": "%s"
                }
                """.formatted(role, email, password, displayName);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andReturn();
        return readLong(result, "data", "userId");
    }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andReturn();

        String accessToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();
        assertThat(accessToken).isNotBlank();
        return accessToken;
    }

    private long insertSystemMentorWithPendingCertification(String email, String displayName) {
        jdbcTemplate.update(
                """
                INSERT INTO users(
                    email, password_hash, role, tier, status, display_name, real_name,
                    last_login_at, is_deleted, created_at, updated_at
                ) VALUES (?, ?, 'MENTOR', 'FREE', 'ACTIVE', ?, ?, NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                email,
                "!system-account-disabled!",
                displayName,
                displayName
        );
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        assertThat(userId).isNotNull();

        jdbcTemplate.update(
                """
                INSERT INTO mentor_profiles(
                    user_id, company_name, job_title, avatar_url, expertise_tags, service_scenes,
                    bio, price_fen, is_available, approval_status, total_orders, avg_rating, created_at, updated_at
                ) VALUES (?, ?, ?, NULL, ?, ?, ?, ?, 1, 'PENDING', 0, 0.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                userId,
                "系统账号",
                "AI 评论账号",
                "自动首评",
                "社区自动回复",
                "仅用于测试系统账号保护",
                0
        );
        jdbcTemplate.update(
                """
                INSERT INTO certification_submissions(
                    user_id, user_role, real_name, company_name, job_title, status, review_note,
                    reviewed_by, previous_submission_id, is_current, submitted_at, reviewed_at, created_at, updated_at
                ) VALUES (?, 'MENTOR', ?, ?, ?, 'PENDING', NULL, NULL, NULL, 1, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                userId,
                displayName,
                "系统账号",
                "AI 评论账号"
        );
        return userId;
    }
}
