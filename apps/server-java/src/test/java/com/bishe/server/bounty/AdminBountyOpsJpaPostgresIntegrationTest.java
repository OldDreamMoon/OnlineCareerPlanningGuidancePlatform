package com.bishe.server.bounty;

import com.bishe.server.bounty.repository.AdminBountyOpsRepository;
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

@DataJpaTest
@Import(AdminBountyOpsRepository.class)
class AdminBountyOpsJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final long ENTERPRISE_USER_ID = 3101L;
    private static final long STUDENT_A = 3102L;
    private static final long STUDENT_B = 3103L;

    @Autowired
    private AdminBountyOpsRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM bounty_submissions WHERE student_user_id IN (?, ?) OR reviewer_user_id = ?", STUDENT_A, STUDENT_B, ENTERPRISE_USER_ID);
        jdbcTemplate.update("DELETE FROM bounty_tasks WHERE enterprise_user_id = ?", ENTERPRISE_USER_ID);
        jdbcTemplate.update("DELETE FROM enterprise_profiles WHERE user_id = ?", ENTERPRISE_USER_ID);
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?, ?)", ENTERPRISE_USER_ID, STUDENT_A, STUDENT_B);
    }

    @Test
    void repositoryShouldAggregateTaskOpsOnPostgres() {
        Instant now = Instant.parse("2030-03-18T08:00:00Z");

        insertUser(ENTERPRISE_USER_ID, "ENTERPRISE", "bounty-pg-enterprise@example.com", "Pg Enterprise");
        insertUser(STUDENT_A, "STUDENT", "bounty-pg-student-a@example.com", "Pg Student A");
        insertUser(STUDENT_B, "STUDENT", "bounty-pg-student-b@example.com", "Pg Student B");
        insertEnterpriseProfile();

        long taskId = insertTask(
                "PG 管理任务治理",
                "用于验证管理侧 bounty 任务聚合查询已经切到 JPA。",
                "奖金 500 元",
                "OPEN",
                now.minusSeconds(3600),
                now.plusSeconds(86400)
        );
        insertSubmission(taskId, STUDENT_A, "SUBMITTED", now.minusSeconds(1800));
        insertSubmission(taskId, STUDENT_B, "REJECTED", now.minusSeconds(900));

        assertThat(repository.findTaskOps(null, null))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.taskId()).isEqualTo(taskId);
                    assertThat(row.enterpriseUserId()).isEqualTo(ENTERPRISE_USER_ID);
                    assertThat(row.enterpriseName()).isEqualTo("PG Enterprise Ltd.");
                    assertThat(row.enterpriseApprovalStatus()).isEqualTo("APPROVED");
                    assertThat(row.submissionCount()).isEqualTo(2);
                    assertThat(row.pendingSubmissionCount()).isEqualTo(1);
                    assertThat(row.acceptedSubmissionCount()).isEqualTo(0);
                    assertThat(row.rejectedSubmissionCount()).isEqualTo(1);
                    assertThat(row.latestSubmissionAt()).isEqualTo(now.minusSeconds(900));
                });

        assertThat(repository.findTaskOps("enterprise ltd", null))
                .singleElement()
                .extracting(AdminBountyOpsRepository.AdminTaskOpsRow::taskId)
                .isEqualTo(taskId);

        assertThat(repository.findTaskOps("治理", "OPEN"))
                .singleElement()
                .extracting(AdminBountyOpsRepository.AdminTaskOpsRow::status)
                .isEqualTo("OPEN");

        assertThat(repository.findTaskOps("不存在", null)).isEmpty();

        assertThat(repository.findTaskOpsById(taskId))
                .isPresent()
                .get()
                .extracting(AdminBountyOpsRepository.AdminTaskOpsRow::taskId)
                .isEqualTo(taskId);
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
                displayName
        );
    }

    private void insertEnterpriseProfile() {
        jdbcTemplate.update(
                """
                INSERT INTO enterprise_profiles(
                    user_id,
                    company_name,
                    approval_status,
                    logo_object_key,
                    logo_updated_at,
                    created_at,
                    updated_at
                ) VALUES (?, 'PG Enterprise Ltd.', 'APPROVED', 'logo/pg-enterprise.png', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                ENTERPRISE_USER_ID
        );
    }

    private long insertTask(
            String title,
            String description,
            String rewardDescription,
            String status,
            Instant createdAt,
            Instant deadlineAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO bounty_tasks(
                    enterprise_user_id,
                    title,
                    description,
                    reward_description,
                    status,
                    accepted_submission_id,
                    deadline_at,
                    closed_at,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, NULL, ?, NULL, ?, ?)
                """,
                ENTERPRISE_USER_ID,
                title,
                description,
                rewardDescription,
                status,
                Timestamp.from(deadlineAt),
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        );
        Long taskId = jdbcTemplate.queryForObject(
                "SELECT id FROM bounty_tasks WHERE enterprise_user_id = ? AND title = ?",
                Long.class,
                ENTERPRISE_USER_ID,
                title
        );
        assertThat(taskId).isNotNull();
        return taskId;
    }

    private void insertSubmission(long taskId, long studentUserId, String status, Instant createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO bounty_submissions(
                    task_id,
                    student_user_id,
                    content_text,
                    attachment_links,
                    status,
                    review_comment,
                    reviewed_at,
                    reviewer_user_id,
                    created_at,
                    updated_at
                ) VALUES (?, ?, 'content', NULL, ?, NULL, NULL, NULL, ?, ?)
                """,
                taskId,
                studentUserId,
                status,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        );
    }
}
