package com.bishe.server.mentor;

import com.bishe.server.mentor.repository.MentorRepository;
import com.bishe.server.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 导师主仓储在 PostgreSQL 下的最小验证。
 */
@DataJpaTest
@Import(MentorRepository.class)
class MentorRepositoryJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final long PRIMARY_MENTOR_USER_ID = 6801L;
    private static final long SECONDARY_MENTOR_USER_ID = 6802L;
    private static final long SYSTEM_MENTOR_USER_ID = 6803L;
    private static final long STUDENT_USER_ID = 6804L;

    @Autowired
    private MentorRepository mentorRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM consult_orders WHERE mentor_user_id IN (?, ?, ?) OR student_user_id = ?",
                PRIMARY_MENTOR_USER_ID,
                SECONDARY_MENTOR_USER_ID,
                SYSTEM_MENTOR_USER_ID,
                STUDENT_USER_ID
        );
        jdbcTemplate.update(
                "DELETE FROM mentor_profiles WHERE user_id IN (?, ?, ?)",
                PRIMARY_MENTOR_USER_ID,
                SECONDARY_MENTOR_USER_ID,
                SYSTEM_MENTOR_USER_ID
        );
        jdbcTemplate.update(
                "DELETE FROM users WHERE id IN (?, ?, ?, ?)",
                PRIMARY_MENTOR_USER_ID,
                SECONDARY_MENTOR_USER_ID,
                SYSTEM_MENTOR_USER_ID,
                STUDENT_USER_ID
        );
    }

    @Test
    void repositoryShouldSupportMentorProfileQueriesAndMutationsOnPostgres() {
        insertUser(PRIMARY_MENTOR_USER_ID, "MENTOR", "mentor-primary-pg@example.com", "PG 导师一号");
        insertUser(SECONDARY_MENTOR_USER_ID, "MENTOR", "mentor-secondary-pg@example.com", "PG 导师二号");
        insertUser(SYSTEM_MENTOR_USER_ID, "MENTOR", "mentor-bot@system.local", "系统导师");
        insertUser(STUDENT_USER_ID, "STUDENT", "mentor-review-student@example.com", "评价学生");

        mentorRepository.createDefaultProfileIfAbsent(
                PRIMARY_MENTOR_USER_ID,
                "PG 导师一号",
                "主导师科技",
                "资深后端导师",
                "APPROVED"
        );
        mentorRepository.createDefaultProfileIfAbsent(
                SECONDARY_MENTOR_USER_ID,
                "PG 导师二号",
                null,
                null,
                "PENDING"
        );
        mentorRepository.createDefaultProfileIfAbsent(
                SYSTEM_MENTOR_USER_ID,
                "系统导师",
                "系统公司",
                "系统岗位",
                "APPROVED"
        );

        mentorRepository.updateOwnProfile(
                PRIMARY_MENTOR_USER_ID,
                "主导师科技",
                "首席架构导师",
                true,
                "https://example.com/mentor-primary.png",
                "系统设计,后端架构,面试辅导",
                "模拟面试复盘,项目表达",
                "8 年大厂后端经验，擅长系统设计与求职辅导。",
                "适合需要冲刺大厂后端岗位的同学。",
                "不适合零基础语法入门。",
                "请提前准备简历、项目背景和目标岗位 JD。",
                "工作日晚间回复更稳定。",
                19900,
                true
        );
        mentorRepository.saveOrUpdateAvatar(
                PRIMARY_MENTOR_USER_ID,
                "mentor-avatars",
                "primary/avatar.jpg",
                "image/jpeg",
                Instant.parse("2030-06-01T10:15:30Z")
        );
        mentorRepository.incrementTotalOrders(PRIMARY_MENTOR_USER_ID);
        mentorRepository.incrementTotalOrders(PRIMARY_MENTOR_USER_ID);
        mentorRepository.decrementTotalOrders(PRIMARY_MENTOR_USER_ID);

        mentorRepository.updateCertificationIdentity(SECONDARY_MENTOR_USER_ID, "认证公司", "认证岗位");
        mentorRepository.updateApprovalStatus(SECONDARY_MENTOR_USER_ID, "APPROVED");

        insertReview("PG-MENTOR-REVIEW-001", STUDENT_USER_ID, PRIMARY_MENTOR_USER_ID, 5, "讲解非常清楚", Instant.parse("2030-06-02T08:00:00Z"));
        insertReview("PG-MENTOR-REVIEW-002", STUDENT_USER_ID, PRIMARY_MENTOR_USER_ID, 4, "建议很实用", Instant.parse("2030-06-03T08:00:00Z"));
        mentorRepository.refreshAverageRating(PRIMARY_MENTOR_USER_ID);

        assertThat(mentorRepository.findOwnProfile(PRIMARY_MENTOR_USER_ID))
                .isPresent()
                .get()
                .satisfies(profile -> {
                    assertThat(profile.companyName()).isEqualTo("主导师科技");
                    assertThat(profile.jobTitle()).isEqualTo("首席架构导师");
                    assertThat(profile.showRealName()).isTrue();
                    assertThat(profile.avatarObjectKey()).isEqualTo("primary/avatar.jpg");
                    assertThat(profile.avatarContentType()).isEqualTo("image/jpeg");
                    assertThat(profile.avatarUpdatedAt()).isEqualTo(Instant.parse("2030-06-01T10:15:30Z"));
                    assertThat(profile.expertiseTags()).containsExactly("系统设计", "后端架构", "面试辅导");
                    assertThat(profile.serviceScenes()).containsExactly("模拟面试复盘", "项目表达");
                    assertThat(profile.priceFen()).isEqualTo(19900);
                    assertThat(profile.totalOrders()).isEqualTo(1);
                    assertThat(profile.avgRating()).isEqualByComparingTo("4.50");
                });

        assertThat(mentorRepository.findAvatarAssetByUserId(PRIMARY_MENTOR_USER_ID))
                .isPresent()
                .get()
                .satisfies(asset -> {
                    assertThat(asset.bucket()).isEqualTo("mentor-avatars");
                    assertThat(asset.objectKey()).isEqualTo("primary/avatar.jpg");
                    assertThat(asset.contentType()).isEqualTo("image/jpeg");
                });

        assertThat(mentorRepository.findPublicAvatarSourceByUserId(PRIMARY_MENTOR_USER_ID))
                .isPresent()
                .get()
                .satisfies(source -> {
                    assertThat(source.avatarUrl()).isEqualTo("https://example.com/mentor-primary.png");
                    assertThat(source.bucket()).isEqualTo("mentor-avatars");
                    assertThat(source.objectKey()).isEqualTo("primary/avatar.jpg");
                    assertThat(source.contentType()).isEqualTo("image/jpeg");
                });

        assertThat(mentorRepository.findMentors(
                "架构",
                "后端",
                "模拟面试",
                10000,
                30000,
                true,
                null,
                1,
                10
        ))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.userId()).isEqualTo(PRIMARY_MENTOR_USER_ID);
                    assertThat(row.realName()).isEqualTo("PG 导师一号 实名");
                    assertThat(row.companyName()).isEqualTo("主导师科技");
                    assertThat(row.totalOrders()).isEqualTo(1);
                    assertThat(row.avgRating()).isEqualByComparingTo("4.50");
                });

        assertThat(mentorRepository.countMentors(null, null, null, null, null, null, null)).isEqualTo(2L);
        assertThat(mentorRepository.findMentorCandidates(null, null, null, null, null, null, java.util.List.of(PRIMARY_MENTOR_USER_ID), 5))
                .singleElement()
                .extracting(MentorRepository.MentorListRow::userId)
                .isEqualTo(PRIMARY_MENTOR_USER_ID);

        assertThat(mentorRepository.findMentorDetail(PRIMARY_MENTOR_USER_ID))
                .isPresent()
                .get()
                .satisfies(detail -> {
                    assertThat(detail.suitableFor()).contains("冲刺大厂");
                    assertThat(detail.prepMaterials()).contains("目标岗位 JD");
                    assertThat(detail.replyRhythm()).contains("工作日晚间");
                });
        assertThat(mentorRepository.findMentorDetail(SYSTEM_MENTOR_USER_ID)).isEmpty();

        assertThat(mentorRepository.findRecentReviews(PRIMARY_MENTOR_USER_ID, 3))
                .extracting(
                        MentorRepository.MentorRecentReviewRow::orderNo,
                        MentorRepository.MentorRecentReviewRow::studentDisplayName,
                        MentorRepository.MentorRecentReviewRow::rating
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("PG-MENTOR-REVIEW-002", "评价学生", 4),
                        org.assertj.core.groups.Tuple.tuple("PG-MENTOR-REVIEW-001", "评价学生", 5)
                );

        assertThat(mentorRepository.findOwnProfile(SECONDARY_MENTOR_USER_ID))
                .isPresent()
                .get()
                .satisfies(profile -> {
                    assertThat(profile.companyName()).isEqualTo("认证公司");
                    assertThat(profile.jobTitle()).isEqualTo("认证岗位");
                    assertThat(profile.approvalStatus()).isEqualTo("APPROVED");
                });
    }

    private void insertUser(long userId, String role, String email, String displayName) {
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
                displayName + " 实名"
        );
    }

    private void insertReview(
            String orderNo,
            long studentUserId,
            long mentorUserId,
            int rating,
            String comment,
            Instant createdAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO consult_orders(
                    order_no,
                    student_user_id,
                    mentor_user_id,
                    amount_fen,
                    status,
                    question_text,
                    review_rating,
                    review_comment,
                    closed_at,
                    created_at
                ) VALUES (?, ?, ?, 9900, 'CLOSED', ?, ?, ?, ?, ?)
                """,
                orderNo,
                studentUserId,
                mentorUserId,
                "导师评价测试问题",
                rating,
                comment,
                Timestamp.from(createdAt.minusSeconds(1800)),
                Timestamp.from(createdAt)
        );
    }
}
