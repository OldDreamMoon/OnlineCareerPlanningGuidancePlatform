package com.bishe.server.profile;

import com.bishe.server.profile.repository.EnterpriseProfileRepository;
import com.bishe.server.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 企业资料仓储在 PostgreSQL 下的最小验证。
 */
@DataJpaTest
@Import(EnterpriseProfileRepository.class)
class EnterpriseProfileJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final long ACTIVE_ENTERPRISE_USER_ID = 101L;
    private static final long SUSPENDED_ENTERPRISE_USER_ID = 102L;

    @Autowired
    private EnterpriseProfileRepository enterpriseProfileRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedEnterpriseUsers() {
        insertEnterpriseUser(ACTIVE_ENTERPRISE_USER_ID, "ACTIVE", "PG Enterprise", "王招聘");
        insertEnterpriseUser(SUSPENDED_ENTERPRISE_USER_ID, "SUSPENDED", "PG Suspended Enterprise", "李暂停");
    }

    @Test
    void enterpriseProfileRepositoryShouldSupportProfileAndLogoFlowsOnPostgres() {
        assertThat(enterpriseProfileRepository.findOwnProfile(ACTIVE_ENTERPRISE_USER_ID)).isEmpty();

        enterpriseProfileRepository.createDefaultProfileIfAbsent(
                ACTIVE_ENTERPRISE_USER_ID,
                "星河智能",
                "校招负责人",
                "PENDING"
        );

        assertThat(enterpriseProfileRepository.findOwnProfile(ACTIVE_ENTERPRISE_USER_ID))
                .isPresent()
                .get()
                .satisfies(profile -> {
                    assertThat(profile.userId()).isEqualTo(ACTIVE_ENTERPRISE_USER_ID);
                    assertThat(profile.displayName()).isEqualTo("PG Enterprise");
                    assertThat(profile.realName()).isEqualTo("王招聘");
                    assertThat(profile.companyName()).isEqualTo("星河智能");
                    assertThat(profile.contactTitle()).isEqualTo("校招负责人");
                    assertThat(profile.industry()).isEqualTo("待补充");
                    assertThat(profile.companySize()).isEqualTo("待补充");
                    assertThat(profile.hiringTags()).isEqualTo("校招,实习");
                    assertThat(profile.approvalStatus()).isEqualTo("PENDING");
                });

        enterpriseProfileRepository.updateOwnProfile(
                ACTIVE_ENTERPRISE_USER_ID,
                "星河智能升级版",
                "招聘总监",
                "人工智能 / 企业服务",
                "200-500 人",
                "算法工程,产品运营",
                "我们提供真实校招任务与企业项目协作。",
                "官网：https://nebula.example.com",
                "欢迎附带作品集和代码仓库。"
        );
        enterpriseProfileRepository.updateApprovalStatus(ACTIVE_ENTERPRISE_USER_ID, "APPROVED");

        Instant logoUpdatedAt = Instant.parse("2026-04-16T09:15:00Z");
        enterpriseProfileRepository.saveOrUpdateLogo(
                ACTIVE_ENTERPRISE_USER_ID,
                "enterprise-logo-bucket",
                "nebula/logo.png",
                "image/png",
                logoUpdatedAt
        );

        enterpriseProfileRepository.createDefaultProfileIfAbsent(
                ACTIVE_ENTERPRISE_USER_ID,
                "不会覆盖旧值",
                "不会覆盖旧值",
                "REJECTED"
        );

        assertThat(enterpriseProfileRepository.findOwnProfile(ACTIVE_ENTERPRISE_USER_ID))
                .isPresent()
                .get()
                .satisfies(profile -> {
                    assertThat(profile.companyName()).isEqualTo("星河智能升级版");
                    assertThat(profile.contactTitle()).isEqualTo("招聘总监");
                    assertThat(profile.industry()).isEqualTo("人工智能 / 企业服务");
                    assertThat(profile.companySize()).isEqualTo("200-500 人");
                    assertThat(profile.hiringTags()).isEqualTo("算法工程,产品运营");
                    assertThat(profile.bio()).isEqualTo("我们提供真实校招任务与企业项目协作。");
                    assertThat(profile.externalLinks()).isEqualTo("官网：https://nebula.example.com");
                    assertThat(profile.preferences()).isEqualTo("欢迎附带作品集和代码仓库。");
                    assertThat(profile.logoObjectKey()).isEqualTo("nebula/logo.png");
                    assertThat(profile.logoContentType()).isEqualTo("image/png");
                    assertThat(profile.logoUpdatedAt()).isEqualTo(logoUpdatedAt);
                    assertThat(profile.approvalStatus()).isEqualTo("APPROVED");
                });

        assertThat(enterpriseProfileRepository.findLogoAssetByUserId(ACTIVE_ENTERPRISE_USER_ID))
                .isPresent()
                .get()
                .satisfies(logo -> {
                    assertThat(logo.bucket()).isEqualTo("enterprise-logo-bucket");
                    assertThat(logo.objectKey()).isEqualTo("nebula/logo.png");
                    assertThat(logo.contentType()).isEqualTo("image/png");
                    assertThat(logo.updatedAt()).isEqualTo(logoUpdatedAt);
                });

        assertThat(enterpriseProfileRepository.findPublicLogoAssetByUserId(ACTIVE_ENTERPRISE_USER_ID))
                .isPresent()
                .get()
                .satisfies(logo -> {
                    assertThat(logo.bucket()).isEqualTo("enterprise-logo-bucket");
                    assertThat(logo.objectKey()).isEqualTo("nebula/logo.png");
                });
    }

    @Test
    void enterpriseProfileRepositoryShouldHideSuspendedEnterpriseLogoFromPublicViewOnPostgres() {
        Instant logoUpdatedAt = Instant.parse("2026-04-16T10:00:00Z");

        enterpriseProfileRepository.saveOrUpdateLogo(
                SUSPENDED_ENTERPRISE_USER_ID,
                "enterprise-logo-bucket",
                "suspended/logo.png",
                "image/png",
                logoUpdatedAt
        );

        assertThat(enterpriseProfileRepository.findLogoAssetByUserId(SUSPENDED_ENTERPRISE_USER_ID))
                .isPresent()
                .get()
                .satisfies(logo -> assertThat(logo.objectKey()).isEqualTo("suspended/logo.png"));

        assertThat(enterpriseProfileRepository.findPublicLogoAssetByUserId(SUSPENDED_ENTERPRISE_USER_ID)).isEmpty();
        assertThat(enterpriseProfileRepository.findOwnProfile(SUSPENDED_ENTERPRISE_USER_ID)).isEmpty();
    }

    private void insertEnterpriseUser(long userId, String status, String displayName, String realName) {
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
                VALUES (?, ?, ?, 'ENTERPRISE', 'FREE', ?, ?, ?, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                userId,
                "enterprise-pg-%s@example.com".formatted(userId),
                "$2a$10$seed",
                status,
                displayName,
                realName
        );
    }
}
