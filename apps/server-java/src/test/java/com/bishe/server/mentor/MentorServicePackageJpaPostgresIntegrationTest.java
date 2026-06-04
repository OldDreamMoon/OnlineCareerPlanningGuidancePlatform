package com.bishe.server.mentor;

import com.bishe.server.mentor.repository.MentorServicePackageRepository;
import com.bishe.server.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 导师服务套餐仓储在 PostgreSQL 下的最小验证。
 */
@DataJpaTest
@Import(MentorServicePackageRepository.class)
class MentorServicePackageJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final long MENTOR_USER_ID = 201L;

    @Autowired
    private MentorServicePackageRepository mentorServicePackageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        insertMentorUser();
        jdbcTemplate.update("DELETE FROM mentor_service_packages WHERE mentor_user_id = ?", MENTOR_USER_ID);
    }

    @Test
    void mentorServicePackageRepositoryShouldSupportReplaceAndReadOnPostgres() {
        assertThat(mentorServicePackageRepository.findPackagesByMentorUserId(MENTOR_USER_ID, false)).isEmpty();

        mentorServicePackageRepository.replacePackagesForMentor(
                MENTOR_USER_ID,
                List.of(
                        new MentorServicePackageRepository.CreatePackageCommand(
                                "标准图文咨询",
                                "RESUME_DIAGNOSIS",
                                "简历诊断",
                                "TEXT_ASYNC",
                                null,
                                8800,
                                "适合先整理问题与材料，再由导师异步回复。",
                                true,
                                1
                        ),
                        new MentorServicePackageRepository.CreatePackageCommand(
                                "45 分钟语音咨询",
                                "PROJECT_STORYTELLING",
                                "项目表达",
                                "APPOINTMENT",
                                45,
                                19900,
                                "适合需要实时追问和项目讲解拆解的同学。",
                                false,
                                2
                        )
                )
        );

        List<MentorServicePackageRepository.MentorServicePackageRow> allPackages =
                mentorServicePackageRepository.findPackagesByMentorUserId(MENTOR_USER_ID, false);
        assertThat(allPackages).hasSize(2);
        assertThat(allPackages.get(0).packageName()).isEqualTo("标准图文咨询");
        assertThat(allPackages.get(0).enabled()).isTrue();
        assertThat(allPackages.get(0).sortNo()).isEqualTo(1);
        assertThat(allPackages.get(0).createdAt()).isNotNull();
        assertThat(allPackages.get(0).updatedAt()).isNotNull();
        assertThat(allPackages.get(1).packageName()).isEqualTo("45 分钟语音咨询");
        assertThat(allPackages.get(1).enabled()).isFalse();
        assertThat(allPackages.get(1).durationMinutes()).isEqualTo(45);
        assertThat(allPackages.get(1).sortNo()).isEqualTo(2);

        long enabledPackageId = allPackages.get(0).id();
        long disabledPackageId = allPackages.get(1).id();

        assertThat(mentorServicePackageRepository.findPackagesByMentorUserId(MENTOR_USER_ID, true))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.id()).isEqualTo(enabledPackageId);
                    assertThat(item.packageName()).isEqualTo("标准图文咨询");
                    assertThat(item.sceneCode()).isEqualTo("RESUME_DIAGNOSIS");
                });

        assertThat(mentorServicePackageRepository.findEnabledPackageByIdForMentor(MENTOR_USER_ID, enabledPackageId))
                .isPresent()
                .get()
                .satisfies(item -> {
                    assertThat(item.packageName()).isEqualTo("标准图文咨询");
                    assertThat(item.enabled()).isTrue();
                });
        assertThat(mentorServicePackageRepository.findEnabledPackageByIdForMentor(MENTOR_USER_ID, disabledPackageId)).isEmpty();

        mentorServicePackageRepository.replacePackagesForMentor(
                MENTOR_USER_ID,
                List.of(
                        new MentorServicePackageRepository.CreatePackageCommand(
                                "30 分钟语音复盘",
                                "MOCK_INTERVIEW_REVIEW",
                                "模拟面试复盘",
                                "APPOINTMENT",
                                30,
                                12900,
                                "适合集中复盘一次模拟面试的问答表现。",
                                true,
                                1
                        ),
                        new MentorServicePackageRepository.CreatePackageCommand(
                                "投递策略加急包",
                                "CAMPUS_RECRUITMENT_STRATEGY",
                                "校招投递策略",
                                "TEXT_ASYNC",
                                null,
                                6600,
                                "适合快速收口投递节奏和优先级。",
                                true,
                                2
                        )
                )
        );

        List<MentorServicePackageRepository.MentorServicePackageRow> replacedPackages =
                mentorServicePackageRepository.findPackagesByMentorUserId(MENTOR_USER_ID, false);
        assertThat(replacedPackages).hasSize(2);
        assertThat(replacedPackages).extracting(MentorServicePackageRepository.MentorServicePackageRow::packageName)
                .containsExactly("30 分钟语音复盘", "投递策略加急包");
        assertThat(replacedPackages).extracting(MentorServicePackageRepository.MentorServicePackageRow::id)
                .doesNotContain(enabledPackageId, disabledPackageId);
        assertThat(mentorServicePackageRepository.findPackagesByMentorUserId(MENTOR_USER_ID, true)).hasSize(2);

        mentorServicePackageRepository.replacePackagesForMentor(MENTOR_USER_ID, List.of());
        assertThat(mentorServicePackageRepository.findPackagesByMentorUserId(MENTOR_USER_ID, false)).isEmpty();
    }

    private void insertMentorUser() {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ?",
                Integer.class,
                MENTOR_USER_ID
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
                VALUES (?, ?, ?, 'MENTOR', 'FREE', 'ACTIVE', ?, ?, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                MENTOR_USER_ID,
                "mentor-package-pg-%s@example.com".formatted(MENTOR_USER_ID),
                "$2a$10$seed",
                "PG Mentor Package",
                "王导师"
        );
    }
}
