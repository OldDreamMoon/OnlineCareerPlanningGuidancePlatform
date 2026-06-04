package com.bishe.server.profile;

import com.bishe.server.community.service.CommunityLeaderboardCacheService;
import com.bishe.server.community.service.CommunityPostListCacheService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户画像与社区基础最小闭环测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProfileCommunityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CommunityLeaderboardCacheService communityLeaderboardCacheService;

    @Autowired
    private CommunityPostListCacheService communityPostListCacheService;

    @BeforeEach
    void setUp() {
        communityLeaderboardCacheService.evictAllNow();
        communityPostListCacheService.evictAllNow();
    }

    @Test
    void studentProfile_shouldSupportGetAndUpdate() throws Exception {
        registerUser("STUDENT", "profile@example.com", "Passw0rd!", "Alice");
        String studentToken = loginAndGetAccessToken("profile@example.com", "Passw0rd!");

        mockMvc.perform(get("/api/v1/profiles/students/me")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.displayName").value("Alice"))
                .andExpect(jsonPath("$.data.communityScore7d").value(0))
                .andExpect(jsonPath("$.data.skillTags").isArray())
                .andExpect(jsonPath("$.data.portrait.tags").isArray());

        String updateBody = """
                {
                  "major": "软件工程",
                  "grade": "大二",
                  "targetPosition": "后端开发",
                  "skillTags": ["Java", "MySQL", "Spring Boot"],
                  "selfIntro": "希望通过真实项目训练提升工程能力。"
                }
                """;

        mockMvc.perform(put("/api/v1/profiles/students/me")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("profile updated"))
                .andExpect(jsonPath("$.data.updated").value(true))
                .andExpect(jsonPath("$.data.portraitRefreshTriggered").value(true));

        mockMvc.perform(get("/api/v1/profiles/students/me")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.major").value("软件工程"))
                .andExpect(jsonPath("$.data.grade").value("大二"))
                .andExpect(jsonPath("$.data.targetPosition").value("后端开发"))
                .andExpect(jsonPath("$.data.skillTags[0]").value("Java"))
                .andExpect(jsonPath("$.data.skillTags[1]").value("MySQL"))
                .andExpect(jsonPath("$.data.skillTags[2]").value("Spring Boot"))
                .andExpect(jsonPath("$.data.portrait.updatedAt").isNumber());
    }

    @Test
    void mentorAccessStudentProfile_shouldReturn403() throws Exception {
        registerUser("MENTOR", "mentor-profile@example.com", "Passw0rd!", "MentorProfile");
        String mentorToken = loginAndGetAccessToken("mentor-profile@example.com", "Passw0rd!");

        mockMvc.perform(get("/api/v1/profiles/students/me")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-1004"));
    }

    @Test
    void portraitRefreshAndLeaderboard_shouldAggregateSkillInterviewAndCommunitySignals() throws Exception {
        registerUser("STUDENT", "portrait-signal-a@example.com", "Passw0rd!", "AliceSignal");
        registerUser("STUDENT", "portrait-signal-b@example.com", "Passw0rd!", "BobSignal");
        String studentAToken = loginAndGetAccessToken("portrait-signal-a@example.com", "Passw0rd!");
        String studentBToken = loginAndGetAccessToken("portrait-signal-b@example.com", "Passw0rd!");
        long studentAUserId = findUserIdByEmail("portrait-signal-a@example.com");

        MvcResult createPostResult = mockMvc.perform(post("/api/v1/community/posts")
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "后端实习准备记录",
                                  "content": "我在整理项目表达、八股与面试题，希望得到建议。",
                                  "tags": ["成长记录", "后端"]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long postId = objectMapper.readTree(createPostResult.getResponse().getContentAsString()).path("data").path("postId").asLong();
        assertThat(postId).isPositive();

        mockMvc.perform(post("/api/v1/community/posts/{postId}/comments", postId)
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "我计划先把项目结果量化出来。"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/community/posts/{postId}/like", postId)
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value(1));

        mockMvc.perform(post("/api/v1/skills/progress")
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nodeId": "programming_language_foundations",
                                  "targetStatus": "MASTERED"
                                }
                                """))
                .andExpect(status().isOk());

        MvcResult createSessionResult = mockMvc.perform(post("/api/v1/ai/interview/sessions")
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetRole": "Backend Engineer",
                                  "mode": "INTERVIEW_TEXT"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String sessionId = objectMapper.readTree(createSessionResult.getResponse().getContentAsString()).path("data").path("sessionId").asText();
        assertThat(sessionId).startsWith("is_");

        mockMvc.perform(post("/api/v1/ai/interview/sessions/{sessionId}/reply", sessionId)
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answerText": "我主导了订单中心缓存改造，把接口延迟降低了 30%，并补充了监控告警。"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/profiles/students/me")
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.communityScore7d").value(8))
                .andExpect(jsonPath("$.data.portrait.evidence.masteredSkills").value(1))
                .andExpect(jsonPath("$.data.portrait.evidence.interviewMessages7d").value(1))
                .andExpect(jsonPath("$.data.portrait.evidence.posts7d").value(1))
                .andExpect(jsonPath("$.data.portrait.evidence.comments7d").value(1))
                .andExpect(jsonPath("$.data.portrait.evidence.likesReceived7d").value(1))
                .andExpect(jsonPath("$.data.portrait.tags[?(@.code=='SKILL_PROGRESS_ACTIVE')]").exists())
                .andExpect(jsonPath("$.data.portrait.tags[?(@.code=='COMMUNITY_ACTIVE')]").exists());

        mockMvc.perform(get("/api/v1/community/leaderboard")
                        .header("Authorization", "Bearer " + studentBToken)
                        .param("window", "7d")
                        .param("page", "1")
                        .param("size", "10")
                        .param("keyword", "Java 后端面试技巧"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.window").value("7d"))
                .andExpect(jsonPath("$.data.records[0].studentUserId").value(studentAUserId))
                .andExpect(jsonPath("$.data.records[0].score").value(8))
                .andExpect(jsonPath("$.data.records[0].postCount").value(1))
                .andExpect(jsonPath("$.data.records[0].commentCount").value(1))
                .andExpect(jsonPath("$.data.records[0].likeReceivedCount").value(1));
    }

    @Test
    void communityFlow_shouldSupportPostCommentAndLike() throws Exception {
        registerUser("MENTOR", "mentor-community@example.com", "Passw0rd!", "MentorBob");
        registerUser("STUDENT", "student-community@example.com", "Passw0rd!", "Alice");
        String mentorToken = loginAndGetAccessToken("mentor-community@example.com", "Passw0rd!");
        String studentToken = loginAndGetAccessToken("student-community@example.com", "Passw0rd!");

        MvcResult createPostResult = mockMvc.perform(post("/api/v1/community/posts")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Java 后端面试技巧",
                                  "content": "总结几条我最近面试中常被问到的问题。",
                                  "tags": ["面经分享", "技术讨论"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.moderation.action").value("PASS"))
                .andExpect(jsonPath("$.data.aiFirstCommentCreated").value(true))
                .andExpect(jsonPath("$.data.aiFirstCommentId").isNumber())
                .andReturn();

        long postId = objectMapper.readTree(createPostResult.getResponse().getContentAsString())
                .path("data")
                .path("postId")
                .asLong();
        assertThat(postId).isPositive();

        mockMvc.perform(get("/api/v1/community/posts")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("page", "1")
                        .param("size", "10")
                        .param("keyword", "Java 后端面试技巧"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].postId").value(postId))
                .andExpect(jsonPath("$.data.records[0].title").value("Java 后端面试技巧"))
                .andExpect(jsonPath("$.data.records[0].authorRole").value("MENTOR"))
                .andExpect(jsonPath("$.data.records[0].tags[0]").value("面经分享"));

        mockMvc.perform(post("/api/v1/community/posts/{postId}/comments", postId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"content\":\"这条经验很有帮助，谢谢分享。\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.commentId").isNumber())
                .andExpect(jsonPath("$.data.moderation.action").value("PASS"));

        mockMvc.perform(post("/api/v1/community/posts/{postId}/like", postId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(true))
                .andExpect(jsonPath("$.data.likeCount").value(1));

        mockMvc.perform(get("/api/v1/community/posts/{postId}", postId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postId").value(postId))
                .andExpect(jsonPath("$.data.commentCount").value(2))
                .andExpect(jsonPath("$.data.likeCount").value(1))
                .andExpect(jsonPath("$.data.likedByMe").value(true))
                .andExpect(jsonPath("$.data.comments[0].displayName").value("AI 助手"))
                .andExpect(jsonPath("$.data.comments[0].ai").value(true))
                .andExpect(jsonPath("$.data.comments[0].content").isNotEmpty())
                .andExpect(jsonPath("$.data.comments[1].displayName").value("Alice"))
                .andExpect(jsonPath("$.data.comments[1].ai").value(false))
                .andExpect(jsonPath("$.data.comments[1].content").value("这条经验很有帮助，谢谢分享。"));

        mockMvc.perform(delete("/api/v1/community/posts/{postId}/like", postId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(false))
                .andExpect(jsonPath("$.data.likeCount").value(0));
    }

    @Test
    void leaderboard_shouldUseRedisCacheAndRefreshAfterLike() throws Exception {
        registerUser("STUDENT", "leaderboard-cache-a@example.com", "Passw0rd!", "CacheAlice");
        registerUser("STUDENT", "leaderboard-cache-b@example.com", "Passw0rd!", "CacheBob");
        String studentAToken = loginAndGetAccessToken("leaderboard-cache-a@example.com", "Passw0rd!");
        String studentBToken = loginAndGetAccessToken("leaderboard-cache-b@example.com", "Passw0rd!");

        MvcResult createPostResult = mockMvc.perform(post("/api/v1/community/posts")
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "缓存榜单测试帖",
                                  "content": "用于验证社区榜单缓存刷新。",
                                  "tags": ["缓存", "榜单"]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long postId = objectMapper.readTree(createPostResult.getResponse().getContentAsString())
                .path("data")
                .path("postId")
                .asLong();
        assertThat(postId).isPositive();

        mockMvc.perform(get("/api/v1/community/leaderboard")
                        .header("Authorization", "Bearer " + studentBToken)
                        .param("window", "7d")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].studentUserId").value(findUserIdByEmail("leaderboard-cache-a@example.com")))
                .andExpect(jsonPath("$.data.records[0].score").value(5))
                .andExpect(jsonPath("$.data.records[0].likeReceivedCount").value(0));

        mockMvc.perform(post("/api/v1/community/posts/{postId}/like", postId)
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(true))
                .andExpect(jsonPath("$.data.likeCount").value(1));

        mockMvc.perform(get("/api/v1/community/leaderboard")
                        .header("Authorization", "Bearer " + studentBToken)
                        .param("window", "7d")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].studentUserId").value(findUserIdByEmail("leaderboard-cache-a@example.com")))
                .andExpect(jsonPath("$.data.records[0].score").value(6))
                .andExpect(jsonPath("$.data.records[0].likeReceivedCount").value(1));
    }

    @Test
    void postList_shouldUsePublicCacheAndMergeViewerStateAfterLike() throws Exception {
        registerUser("STUDENT", "post-list-cache-a@example.com", "Passw0rd!", "PostAlice");
        registerUser("STUDENT", "post-list-cache-b@example.com", "Passw0rd!", "PostBob");
        String studentAToken = loginAndGetAccessToken("post-list-cache-a@example.com", "Passw0rd!");
        String studentBToken = loginAndGetAccessToken("post-list-cache-b@example.com", "Passw0rd!");

        MvcResult createPostResult = mockMvc.perform(post("/api/v1/community/posts")
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "帖子列表缓存测试帖",
                                  "content": "用于验证社区帖子列表缓存与 viewer 态合并。",
                                  "tags": ["缓存", "列表"]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long postId = objectMapper.readTree(createPostResult.getResponse().getContentAsString())
                .path("data")
                .path("postId")
                .asLong();
        assertThat(postId).isPositive();

        mockMvc.perform(get("/api/v1/community/posts")
                        .header("Authorization", "Bearer " + studentBToken)
                        .param("page", "1")
                        .param("size", "10")
                        .param("keyword", "帖子列表缓存测试帖"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].postId").value(postId))
                .andExpect(jsonPath("$.data.records[0].likeCount").value(0))
                .andExpect(jsonPath("$.data.records[0].likedByMe").value(false))
                .andExpect(jsonPath("$.data.records[0].authoredByMe").value(false))
                .andExpect(jsonPath("$.data.records[0].participatedByMe").value(false));

        mockMvc.perform(post("/api/v1/community/posts/{postId}/like", postId)
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(true))
                .andExpect(jsonPath("$.data.likeCount").value(1));

        mockMvc.perform(get("/api/v1/community/posts")
                        .header("Authorization", "Bearer " + studentBToken)
                        .param("page", "1")
                        .param("size", "10")
                        .param("keyword", "帖子列表缓存测试帖"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].postId").value(postId))
                .andExpect(jsonPath("$.data.records[0].likeCount").value(1))
                .andExpect(jsonPath("$.data.records[0].likedByMe").value(true))
                .andExpect(jsonPath("$.data.records[0].authoredByMe").value(false))
                .andExpect(jsonPath("$.data.records[0].participatedByMe").value(false));
    }

    @Test
    void postList_shouldBypassPublicCacheWhenViewerHasOwnNonPublicPost() throws Exception {
        registerUser("STUDENT", "post-list-private@example.com", "Passw0rd!", "PrivateAlice");
        String studentToken = loginAndGetAccessToken("post-list-private@example.com", "Passw0rd!");
        long studentUserId = findUserIdByEmail("post-list-private@example.com");

        jdbcTemplate.update(
                """
                INSERT INTO posts(
                    user_id, title, content, tags, scenario_code, resolved_status, moderation_status,
                    risk_level, last_moderation_event_id, is_deleted, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'GENERAL_HELP', 'OPEN', 'REVIEW', 'MEDIUM', NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                studentUserId,
                "仅自己可见的待审帖",
                "这条帖子用于验证列表缓存旁路逻辑。",
                "缓存,待审"
        );

        mockMvc.perform(get("/api/v1/community/posts")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("page", "1")
                        .param("size", "10")
                        .param("keyword", "仅自己可见的待审帖"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].title").value("仅自己可见的待审帖"))
                .andExpect(jsonPath("$.data.records[0].moderationStatus").value("REVIEW"))
                .andExpect(jsonPath("$.data.records[0].authoredByMe").value(true))
                .andExpect(jsonPath("$.data.records[0].participatedByMe").value(true));
    }

    @Test
    void postDetail_shouldUsePublicCacheAndRefreshAfterLikeAndComment() throws Exception {
        registerUser("STUDENT", "post-detail-cache-a@example.com", "Passw0rd!", "DetailAlice");
        registerUser("STUDENT", "post-detail-cache-b@example.com", "Passw0rd!", "DetailBob");
        String studentAToken = loginAndGetAccessToken("post-detail-cache-a@example.com", "Passw0rd!");
        String studentBToken = loginAndGetAccessToken("post-detail-cache-b@example.com", "Passw0rd!");

        MvcResult createPostResult = mockMvc.perform(post("/api/v1/community/posts")
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "帖子详情缓存测试帖",
                                  "content": "用于验证社区帖子详情缓存与 viewer 态合并。",
                                  "tags": ["缓存", "详情"]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long postId = objectMapper.readTree(createPostResult.getResponse().getContentAsString())
                .path("data")
                .path("postId")
                .asLong();
        assertThat(postId).isPositive();

        MvcResult firstDetailResult = mockMvc.perform(get("/api/v1/community/posts/{postId}", postId)
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postId").value(postId))
                .andExpect(jsonPath("$.data.likedByMe").value(false))
                .andReturn();
        JsonNode firstDetail = objectMapper.readTree(firstDetailResult.getResponse().getContentAsString()).path("data");
        int initialCommentCount = firstDetail.path("commentCount").asInt();
        int initialLikeCount = firstDetail.path("likeCount").asInt();
        int initialCommentsSize = firstDetail.path("comments").size();

        mockMvc.perform(post("/api/v1/community/posts/{postId}/comments", postId)
                        .header("Authorization", "Bearer " + studentBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "详情缓存命中后仍应看到最新评论。"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        mockMvc.perform(post("/api/v1/community/posts/{postId}/like", postId)
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(true));

        mockMvc.perform(get("/api/v1/community/posts/{postId}", postId)
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postId").value(postId))
                .andExpect(jsonPath("$.data.commentCount").value(initialCommentCount + 1))
                .andExpect(jsonPath("$.data.likeCount").value(initialLikeCount + 1))
                .andExpect(jsonPath("$.data.likedByMe").value(true))
                .andExpect(jsonPath("$.data.comments.length()").value(initialCommentsSize + 1))
                .andExpect(jsonPath("$.data.comments[*].content").value(org.hamcrest.Matchers.hasItem("详情缓存命中后仍应看到最新评论。")));
    }

    @Test
    void postDetail_shouldReuseCachedPublicCommentListBeforeInvalidation() throws Exception {
        registerUser("STUDENT", "post-comment-cache-a@example.com", "Passw0rd!", "CommentAlice");
        registerUser("STUDENT", "post-comment-cache-b@example.com", "Passw0rd!", "CommentBob");
        String studentAToken = loginAndGetAccessToken("post-comment-cache-a@example.com", "Passw0rd!");
        String studentBToken = loginAndGetAccessToken("post-comment-cache-b@example.com", "Passw0rd!");
        long studentBUserId = findUserIdByEmail("post-comment-cache-b@example.com");

        MvcResult createPostResult = mockMvc.perform(post("/api/v1/community/posts")
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "帖子评论缓存命中测试帖",
                                  "content": "用于验证评论列表缓存命中。",
                                  "tags": ["缓存", "评论"]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long postId = objectMapper.readTree(createPostResult.getResponse().getContentAsString())
                .path("data")
                .path("postId")
                .asLong();
        assertThat(postId).isPositive();

        MvcResult firstDetailResult = mockMvc.perform(get("/api/v1/community/posts/{postId}", postId)
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode firstDetail = objectMapper.readTree(firstDetailResult.getResponse().getContentAsString()).path("data");
        int initialCommentCount = firstDetail.path("commentCount").asInt();
        int initialCommentsSize = firstDetail.path("comments").size();

        jdbcTemplate.update(
                """
                INSERT INTO comments(
                    post_id, user_id, content, is_ai, moderation_status, risk_level,
                    last_moderation_event_id, is_deleted, created_at, updated_at
                ) VALUES (?, ?, ?, 0, 'PASS', 'LOW', NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                postId,
                studentBUserId,
                "这条评论通过直写数据库制造，不会触发缓存失效。"
        );

        mockMvc.perform(get("/api/v1/community/posts/{postId}", postId)
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commentCount").value(initialCommentCount))
                .andExpect(jsonPath("$.data.comments.length()").value(initialCommentsSize))
                .andExpect(jsonPath("$.data.comments[*].content").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("这条评论通过直写数据库制造，不会触发缓存失效。"))));
    }

    @Test
    void postDetail_shouldBypassPublicCacheWhenAuthorViewsOwnNonPublicPost() throws Exception {
        registerUser("STUDENT", "post-detail-private@example.com", "Passw0rd!", "PrivateDetailAlice");
        registerUser("STUDENT", "post-detail-other@example.com", "Passw0rd!", "PrivateDetailBob");
        String authorToken = loginAndGetAccessToken("post-detail-private@example.com", "Passw0rd!");
        String otherToken = loginAndGetAccessToken("post-detail-other@example.com", "Passw0rd!");
        long authorUserId = findUserIdByEmail("post-detail-private@example.com");

        jdbcTemplate.update(
                """
                INSERT INTO posts(
                    user_id, title, content, tags, scenario_code, resolved_status, moderation_status,
                    risk_level, last_moderation_event_id, is_deleted, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'GENERAL_HELP', 'OPEN', 'REVIEW', 'MEDIUM', NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                authorUserId,
                "仅作者可见的详情帖",
                "这条帖子用于验证详情缓存旁路逻辑。",
                "缓存,详情,待审"
        );
        Long postId = jdbcTemplate.queryForObject(
                "SELECT id FROM posts WHERE user_id = ? AND title = ? ORDER BY id DESC LIMIT 1",
                Long.class,
                authorUserId,
                "仅作者可见的详情帖"
        );
        assertThat(postId).isNotNull();

        mockMvc.perform(get("/api/v1/community/posts/{postId}", postId)
                        .header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postId").value(postId))
                .andExpect(jsonPath("$.data.title").value("仅作者可见的详情帖"))
                .andExpect(jsonPath("$.data.moderationStatus").value("REVIEW"));

        mockMvc.perform(get("/api/v1/community/posts/{postId}", postId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BIZ-1002"));
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

    private long findUserIdByEmail(String email) {
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
        String accessToken = jsonNode.path("data").path("accessToken").asText();
        assertThat(accessToken).isNotBlank();
        return accessToken;
    }
}
