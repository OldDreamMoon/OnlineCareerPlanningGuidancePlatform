package com.bishe.server.community;

import com.bishe.server.community.repository.CommunityRepository;
import com.bishe.server.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(CommunityRepository.class)
class CommunityJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final long MENTOR_USER_ID = 4401L;
    private static final long STUDENT_VIEWER_USER_ID = 4402L;
    private static final long STUDENT_OWNER_USER_ID = 4403L;
    private static final long AI_BOT_USER_ID = 4404L;

    @Autowired
    private CommunityRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                """
                DELETE FROM post_likes
                 WHERE user_id IN (?, ?, ?, ?)
                    OR post_id IN (
                        SELECT id
                          FROM posts
                         WHERE user_id IN (?, ?, ?, ?)
                    )
                """,
                MENTOR_USER_ID,
                STUDENT_VIEWER_USER_ID,
                STUDENT_OWNER_USER_ID,
                AI_BOT_USER_ID,
                MENTOR_USER_ID,
                STUDENT_VIEWER_USER_ID,
                STUDENT_OWNER_USER_ID,
                AI_BOT_USER_ID
        );
        jdbcTemplate.update(
                """
                DELETE FROM comments
                 WHERE user_id IN (?, ?, ?, ?)
                    OR post_id IN (
                        SELECT id
                          FROM posts
                         WHERE user_id IN (?, ?, ?, ?)
                    )
                """,
                MENTOR_USER_ID,
                STUDENT_VIEWER_USER_ID,
                STUDENT_OWNER_USER_ID,
                AI_BOT_USER_ID,
                MENTOR_USER_ID,
                STUDENT_VIEWER_USER_ID,
                STUDENT_OWNER_USER_ID,
                AI_BOT_USER_ID
        );
        jdbcTemplate.update(
                "DELETE FROM posts WHERE user_id IN (?, ?, ?, ?)",
                MENTOR_USER_ID,
                STUDENT_VIEWER_USER_ID,
                STUDENT_OWNER_USER_ID,
                AI_BOT_USER_ID
        );
        jdbcTemplate.update(
                "DELETE FROM mentor_profiles WHERE user_id IN (?, ?)",
                MENTOR_USER_ID,
                AI_BOT_USER_ID
        );
        jdbcTemplate.update(
                "DELETE FROM users WHERE id IN (?, ?, ?, ?)",
                MENTOR_USER_ID,
                STUDENT_VIEWER_USER_ID,
                STUDENT_OWNER_USER_ID,
                AI_BOT_USER_ID
        );
    }

    @Test
    void repositoryShouldSupportVisiblePostsCommentsAndLikesOnPostgres() {
        insertUser(MENTOR_USER_ID, "MENTOR", "community-pg-mentor@example.com", "PG Mentor", "PG Mentor Real");
        insertUser(STUDENT_VIEWER_USER_ID, "STUDENT", "community-pg-viewer@example.com", "Viewer Student", "Viewer Student");
        insertUser(STUDENT_OWNER_USER_ID, "STUDENT", "community-pg-owner@example.com", "Owner Student", "Owner Student");
        insertUser(AI_BOT_USER_ID, "MENTOR", "community-pg-ai@example.com", "AI 助手", "AI 助手");
        insertMentorProfile(MENTOR_USER_ID, true, "https://cdn.example.com/mentor.png", "mentor/avatar.png");

        long visiblePostId = repository.createPost(
                MENTOR_USER_ID,
                "PG 社区帖子",
                "GENERAL_HELP",
                "这是一条 PostgreSQL 社区帖子。",
                "面经分享,技术讨论",
                "OPEN",
                "PASS",
                "LOW",
                7001L
        );
        long hiddenPostId = repository.createPost(
                STUDENT_OWNER_USER_ID,
                "待审帖子",
                "GENERAL_HELP",
                "这条帖子暂时不公开。",
                "治理测试",
                "OPEN",
                "REVIEW",
                "MEDIUM",
                7002L
        );

        repository.createAiComment(visiblePostId, AI_BOT_USER_ID, "AI 助手先补一句。", "PASS", "LOW", 7101L);
        repository.createComment(visiblePostId, STUDENT_VIEWER_USER_ID, "学生补充一个问题。", "PASS", "LOW", 7102L);
        repository.createComment(visiblePostId, MENTOR_USER_ID, "导师追加一点说明。", "PASS", "LOW", 7103L);
        repository.addLike(visiblePostId, STUDENT_VIEWER_USER_ID);

        assertThat(repository.countVisiblePosts(STUDENT_VIEWER_USER_ID, "社区帖子", "面经分享", "general_help")).isEqualTo(1L);
        assertThat(repository.countOwnNonPublicPosts(STUDENT_OWNER_USER_ID, "待审", null, null)).isEqualTo(1L);

        assertThat(repository.findVisiblePosts(STUDENT_VIEWER_USER_ID, "PG", null, null, 1, 10))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.postId()).isEqualTo(visiblePostId);
                    assertThat(row.authorUserId()).isEqualTo(MENTOR_USER_ID);
                    assertThat(row.authorDisplayName()).isEqualTo("PG Mentor");
                    assertThat(row.authorRealName()).isEqualTo("PG Mentor Real");
                    assertThat(row.authorShowRealName()).isTrue();
                    assertThat(row.authorRole()).isEqualTo("MENTOR");
                    assertThat(row.authorAvatarObjectKey()).isEqualTo("mentor/avatar.png");
                    assertThat(row.hasMentorProfile()).isTrue();
                    assertThat(row.commentCount()).isEqualTo(3L);
                    assertThat(row.likeCount()).isEqualTo(1L);
                    assertThat(row.likedByMe()).isTrue();
                    assertThat(row.hasMentorReply()).isTrue();
                    assertThat(row.authoredByMe()).isFalse();
                    assertThat(row.participatedByMe()).isTrue();
                });

        assertThat(repository.findVisiblePostDetail(STUDENT_VIEWER_USER_ID, visiblePostId))
                .isPresent()
                .get()
                .satisfies(row -> {
                    assertThat(row.title()).isEqualTo("PG 社区帖子");
                    assertThat(row.resolvedStatus()).isEqualTo("OPEN");
                    assertThat(row.commentCount()).isEqualTo(3L);
                    assertThat(row.likeCount()).isEqualTo(1L);
                });
        assertThat(repository.findVisiblePostDetail(STUDENT_VIEWER_USER_ID, hiddenPostId)).isEmpty();

        assertThat(repository.findAdminReadablePostDetail(hiddenPostId))
                .isPresent()
                .get()
                .satisfies(row -> {
                    assertThat(row.authorUserId()).isEqualTo(STUDENT_OWNER_USER_ID);
                    assertThat(row.moderationStatus()).isEqualTo("REVIEW");
                    assertThat(row.riskLevel()).isEqualTo("MEDIUM");
                });

        assertThat(repository.findPostContext(visiblePostId))
                .isPresent()
                .get()
                .satisfies(row -> {
                    assertThat(row.authorDisplayName()).isEqualTo("PG Mentor");
                    assertThat(row.title()).isEqualTo("PG 社区帖子");
                    assertThat(row.moderationStatus()).isEqualTo("PASS");
                });

        assertThat(repository.findVisibleComments(visiblePostId))
                .extracting(CommunityRepository.CommentRow::displayName, CommunityRepository.CommentRow::ai)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("AI 助手", true),
                        org.assertj.core.groups.Tuple.tuple("Viewer Student", false),
                        org.assertj.core.groups.Tuple.tuple("PG Mentor", false)
                );
        assertThat(repository.findAdminReadableComments(visiblePostId)).hasSize(3);

        assertThat(repository.findLikedPostIds(STUDENT_VIEWER_USER_ID, List.of(visiblePostId, hiddenPostId)))
                .containsExactly(visiblePostId);
        assertThat(repository.findParticipatedPostIds(STUDENT_VIEWER_USER_ID, List.of(visiblePostId, hiddenPostId)))
                .containsExactly(visiblePostId);

        assertThat(repository.hasLike(visiblePostId, STUDENT_VIEWER_USER_ID)).isTrue();
        assertThat(repository.countLikes(visiblePostId)).isEqualTo(1L);
        repository.removeLike(visiblePostId, STUDENT_VIEWER_USER_ID);
        assertThat(repository.hasLike(visiblePostId, STUDENT_VIEWER_USER_ID)).isFalse();
        assertThat(repository.countLikes(visiblePostId)).isEqualTo(0L);

        repository.updateResolvedStatus(visiblePostId, "RESOLVED");
        assertThat(repository.findVisiblePostDetail(STUDENT_VIEWER_USER_ID, visiblePostId))
                .isPresent()
                .get()
                .extracting(CommunityRepository.PostSummaryRow::resolvedStatus)
                .isEqualTo("RESOLVED");
    }

    @Test
    void repositoryShouldSupportLeaderboardQueriesOnPostgres() {
        Instant windowStart = Instant.now().minusSeconds(24 * 3600L);

        insertUser(MENTOR_USER_ID, "MENTOR", "community-pg-leader-mentor@example.com", "Mentor Lead", "Mentor Lead");
        insertUser(STUDENT_VIEWER_USER_ID, "STUDENT", "community-pg-leader-viewer@example.com", "Viewer Student", "Viewer Student");
        insertUser(STUDENT_OWNER_USER_ID, "STUDENT", "community-pg-leader-owner@example.com", "Owner Student", "Owner Student");

        long mentorPostId = repository.createPost(
                MENTOR_USER_ID,
                "导师公开帖",
                "GENERAL_HELP",
                "导师先发一条公开帖。",
                "导师经验",
                "OPEN",
                "PASS",
                "LOW",
                7201L
        );
        long studentPostId = repository.createPost(
                STUDENT_OWNER_USER_ID,
                "学生公开帖",
                "GENERAL_HELP",
                "学生发一条公开帖。",
                "成长记录",
                "OPEN",
                "PASS",
                "LOW",
                7202L
        );

        repository.createComment(mentorPostId, STUDENT_OWNER_USER_ID, "学生给导师帖子留言。", "PASS", "LOW", 7203L);
        repository.addLike(studentPostId, STUDENT_VIEWER_USER_ID);

        assertThat(repository.countLeaderboardRows(windowStart)).isEqualTo(1L);
        assertThat(repository.findLeaderboardRows(windowStart, 1, 10))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.studentUserId()).isEqualTo(STUDENT_OWNER_USER_ID);
                    assertThat(row.displayName()).isEqualTo("Owner Student");
                    assertThat(row.score()).isEqualTo(8L);
                    assertThat(row.postCount()).isEqualTo(1);
                    assertThat(row.commentCount()).isEqualTo(1);
                    assertThat(row.likeReceivedCount()).isEqualTo(1);
                    assertThat(row.latestActivityAt()).isNotNull();
                });

        assertThat(repository.findVisiblePostIdsByAuthorUserId(STUDENT_OWNER_USER_ID)).containsExactly(studentPostId);
        assertThat(repository.findVisibleCommentedPostIdsByUserId(STUDENT_OWNER_USER_ID)).containsExactly(mentorPostId);
    }

    private void insertUser(long userId, String role, String email, String displayName, String realName) {
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
                    is_deleted,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, 'FREE', 'ACTIVE', ?, ?, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                userId,
                email,
                "$2a$10$seed",
                role,
                displayName,
                realName
        );
    }

    private void insertMentorProfile(long userId, boolean showRealName, String avatarUrl, String avatarObjectKey) {
        jdbcTemplate.update(
                """
                INSERT INTO mentor_profiles(
                    user_id,
                    approval_status,
                    show_real_name,
                    avatar_url,
                    avatar_object_key,
                    avatar_updated_at,
                    created_at,
                    updated_at
                ) VALUES (?, 'APPROVED', ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                userId,
                showRealName,
                avatarUrl,
                avatarObjectKey
        );
    }
}
