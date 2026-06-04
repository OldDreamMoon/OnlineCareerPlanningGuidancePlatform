package com.bishe.server.certification;

import com.bishe.server.certification.repository.CertificationRepository;
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

/**
 * 认证仓储在 PostgreSQL 下的最小验证。
 */
@DataJpaTest
@Import(CertificationRepository.class)
class CertificationJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final long MENTOR_USER_ID = 401L;
    private static final long ENTERPRISE_USER_ID = 402L;

    @Autowired
    private CertificationRepository certificationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        insertUser(MENTOR_USER_ID, "MENTOR", "pg-cert-mentor@example.com", "PG Mentor");
        insertUser(ENTERPRISE_USER_ID, "ENTERPRISE", "pg-cert-enterprise@example.com", "PG Enterprise");
        ensureProfileRows();
        jdbcTemplate.update("DELETE FROM certification_submission_assets");
        jdbcTemplate.update("DELETE FROM certification_submissions");
    }

    @Test
    void certificationRepositoryShouldSupportSubmissionLifecycleAndReviewQueriesOnPostgres() {
        Instant firstSubmittedAt = Instant.parse("2099-04-01T08:00:00Z");
        Instant secondSubmittedAt = Instant.parse("2099-04-02T08:00:00Z");
        Instant reviewedAt = Instant.parse("2099-04-03T09:00:00Z");

        long mentorSubmissionId = certificationRepository.createSubmission(
                MENTOR_USER_ID,
                "MENTOR",
                "王导师",
                "字节跳动",
                "后端导师",
                "PENDING",
                null,
                firstSubmittedAt
        );
        long mentorAssetId = certificationRepository.createAsset(
                mentorSubmissionId,
                "cert-bucket",
                "mentor/asset-1.png",
                "mentor-license.png",
                "image/png",
                1024L
        );

        assertThat(certificationRepository.findCurrentSubmission(MENTOR_USER_ID))
                .isPresent()
                .get()
                .satisfies(row -> {
                    assertThat(row.submissionId()).isEqualTo(mentorSubmissionId);
                    assertThat(row.current()).isTrue();
                    assertThat(row.status()).isEqualTo("PENDING");
                    assertThat(row.submittedAt()).isEqualTo(firstSubmittedAt);
                });

        assertThat(certificationRepository.findAssetsBySubmissionIds(List.of(mentorSubmissionId)))
                .singleElement()
                .satisfies(asset -> {
                    assertThat(asset.assetId()).isEqualTo(mentorAssetId);
                    assertThat(asset.lifecycleStatus()).isEqualTo("ACTIVE");
                    assertThat(asset.originalFilename()).isEqualTo("mentor-license.png");
                });
        assertThat(certificationRepository.findActiveAssetsBySubmissionId(mentorSubmissionId)).hasSize(1);

        assertThat(certificationRepository.findReviewSubject(MENTOR_USER_ID))
                .isNotNull()
                .satisfies(subject -> {
                    assertThat(subject.role()).isEqualTo("MENTOR");
                    assertThat(subject.approvalStatus()).isEqualTo("PENDING");
                });

        certificationRepository.markCurrentSubmissionReplaced(MENTOR_USER_ID);
        certificationRepository.markAssetsLifecycle(List.of(mentorAssetId), "REPLACED", "REPLACED_BY_NEW_SUBMISSION", secondSubmittedAt);

        long currentEnterpriseSubmissionId = certificationRepository.createSubmission(
                ENTERPRISE_USER_ID,
                "ENTERPRISE",
                "李经理",
                "深度求索",
                "招聘经理",
                "PENDING",
                null,
                firstSubmittedAt
        );
        certificationRepository.createAsset(
                currentEnterpriseSubmissionId,
                "cert-bucket",
                "enterprise/asset-1.pdf",
                "enterprise-license.pdf",
                "application/pdf",
                2048L
        );

        long replacementSubmissionId = certificationRepository.createSubmission(
                MENTOR_USER_ID,
                "MENTOR",
                "王导师",
                "字节跳动",
                "资深后端导师",
                "PENDING",
                mentorSubmissionId,
                secondSubmittedAt
        );
        long replacementAssetId = certificationRepository.createAsset(
                replacementSubmissionId,
                "cert-bucket",
                "mentor/asset-2.png",
                "mentor-license-v2.png",
                "image/png",
                2049L
        );

        certificationRepository.updateSubmissionReview(
                replacementSubmissionId,
                "APPROVED",
                "材料完整",
                ENTERPRISE_USER_ID,
                reviewedAt
        );

        assertThat(certificationRepository.findSubmissionById(replacementSubmissionId))
                .isPresent()
                .get()
                .satisfies(row -> {
                    assertThat(row.status()).isEqualTo("APPROVED");
                    assertThat(row.reviewNote()).isEqualTo("材料完整");
                    assertThat(row.reviewedBy()).isEqualTo(ENTERPRISE_USER_ID);
                    assertThat(row.reviewedAt()).isEqualTo(reviewedAt);
                    assertThat(row.previousSubmissionId()).isEqualTo(mentorSubmissionId);
                });

        assertThat(certificationRepository.findSubmissionsByUserId(MENTOR_USER_ID))
                .extracting(CertificationRepository.SubmissionRow::submissionId)
                .containsExactly(replacementSubmissionId, mentorSubmissionId);
        assertThat(certificationRepository.findActiveAssetsBySubmissionId(mentorSubmissionId)).isEmpty();
        assertThat(certificationRepository.findAssetAccessRow(replacementAssetId))
                .isPresent()
                .get()
                .satisfies(asset -> {
                    assertThat(asset.submissionId()).isEqualTo(replacementSubmissionId);
                    assertThat(asset.userId()).isEqualTo(MENTOR_USER_ID);
                    assertThat(asset.userRole()).isEqualTo("MENTOR");
                    assertThat(asset.lifecycleStatus()).isEqualTo("ACTIVE");
                });

        assertThat(certificationRepository.countCurrentReviewItems(null, null, null)).isEqualTo(2);
        assertThat(certificationRepository.findCurrentReviewItems(null, null, null, 1, 10))
                .hasSize(2)
                .first()
                .satisfies(item -> {
                    assertThat(item.userId()).isEqualTo(MENTOR_USER_ID);
                    assertThat(item.submissionId()).isEqualTo(replacementSubmissionId);
                    assertThat(item.submissionStatus()).isEqualTo("APPROVED");
                    assertThat(item.activeAssetCount()).isEqualTo(1);
                    assertThat(item.primaryAssetName()).isEqualTo("mentor-license-v2.png");
                });
        assertThat(certificationRepository.findCurrentReviewItems("深度求索", "ENTERPRISE", "PENDING", 1, 10))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.userId()).isEqualTo(ENTERPRISE_USER_ID);
                    assertThat(item.approvalStatus()).isEqualTo("PENDING");
                    assertThat(item.activeAssetCount()).isEqualTo(1);
                });
    }

    private void insertUser(long userId, String role, String email, String displayName) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ?",
                Integer.class,
                userId
        );
        if (existing != null && existing > 0) {
            return;
        }

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
                )
                VALUES (?, ?, ?, ?, 'FREE', 'ACTIVE', ?, ?, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                userId,
                email,
                "$2a$10$seed",
                role,
                displayName,
                displayName
        );
    }

    private void ensureProfileRows() {
        jdbcTemplate.update(
                """
                INSERT INTO mentor_profiles(user_id, approval_status, created_at, updated_at)
                VALUES (?, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (user_id) DO UPDATE SET approval_status = EXCLUDED.approval_status, updated_at = CURRENT_TIMESTAMP
                """,
                MENTOR_USER_ID
        );
        jdbcTemplate.update(
                """
                INSERT INTO enterprise_profiles(user_id, approval_status, created_at, updated_at)
                VALUES (?, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (user_id) DO UPDATE SET approval_status = EXCLUDED.approval_status, updated_at = CURRENT_TIMESTAMP
                """,
                ENTERPRISE_USER_ID
        );
    }
}
