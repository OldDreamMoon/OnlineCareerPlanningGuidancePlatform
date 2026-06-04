package com.bishe.server.bounty;

import com.bishe.server.bounty.repository.BountyRepository;
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
@Import(BountyRepository.class)
class BountyJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final long ENTERPRISE_USER_ID = 3301L;
    private static final long STUDENT_A_USER_ID = 3302L;
    private static final long STUDENT_B_USER_ID = 3303L;

    @Autowired
    private BountyRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                """
                DELETE FROM bounty_submission_events
                 WHERE task_id IN (SELECT id FROM bounty_tasks WHERE enterprise_user_id = ?)
                    OR submission_id IN (SELECT id FROM bounty_submissions WHERE student_user_id IN (?, ?))
                """,
                ENTERPRISE_USER_ID,
                STUDENT_A_USER_ID,
                STUDENT_B_USER_ID
        );
        jdbcTemplate.update(
                """
                DELETE FROM bounty_submissions
                 WHERE task_id IN (SELECT id FROM bounty_tasks WHERE enterprise_user_id = ?)
                    OR student_user_id IN (?, ?)
                """,
                ENTERPRISE_USER_ID,
                STUDENT_A_USER_ID,
                STUDENT_B_USER_ID
        );
        jdbcTemplate.update("DELETE FROM bounty_tasks WHERE enterprise_user_id = ?", ENTERPRISE_USER_ID);
        jdbcTemplate.update("DELETE FROM enterprise_profiles WHERE user_id = ?", ENTERPRISE_USER_ID);
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?, ?)", ENTERPRISE_USER_ID, STUDENT_A_USER_ID, STUDENT_B_USER_ID);
    }

    @Test
    void repositoryShouldSupportTaskQueriesOnPostgres() {
        insertUser(ENTERPRISE_USER_ID, "ENTERPRISE", "bounty-pg-enterprise@example.com", "Pg Enterprise");
        insertUser(STUDENT_A_USER_ID, "STUDENT", "bounty-pg-student-a@example.com", "Pg Student A");
        insertUser(STUDENT_B_USER_ID, "STUDENT", "bounty-pg-student-b@example.com", "Pg Student B");
        insertEnterpriseProfile();

        long taskId = repository.createTask(
                ENTERPRISE_USER_ID,
                "PG 活动页优化",
                "提交活动页改版方案和实现说明。",
                "奖金 800 元",
                Instant.parse("2030-05-01T08:00:00Z")
        );
        long secondTaskId = repository.createTask(
                ENTERPRISE_USER_ID,
                "PG 海报设计",
                "补充一版线下活动海报。",
                "优先面试机会",
                null
        );
        repository.createSubmission(taskId, STUDENT_A_USER_ID, "我提交了 Figma 方案。", "https://demo.example.com/a");
        repository.createSubmission(taskId, STUDENT_B_USER_ID, "我补充了 React 实现。", "https://demo.example.com/b");

        assertThat(repository.countTasks("活动页", "open", null)).isEqualTo(1L);
        assertThat(repository.countTasks(null, "OPEN", ENTERPRISE_USER_ID)).isEqualTo(2L);

        assertThat(repository.findTasks(STUDENT_A_USER_ID, "pg", "OPEN", null, 1, 10))
                .extracting(BountyRepository.TaskRow::taskId)
                .containsExactly(secondTaskId, taskId);

        assertThat(repository.findTaskById(taskId, STUDENT_A_USER_ID))
                .isPresent()
                .get()
                .satisfies(row -> {
                    assertThat(row.enterpriseUserId()).isEqualTo(ENTERPRISE_USER_ID);
                    assertThat(row.enterpriseName()).isEqualTo("PG Enterprise Ltd.");
                    assertThat(row.enterpriseLogoObjectKey()).isEqualTo("logo/bounty-pg.png");
                    assertThat(row.title()).isEqualTo("PG 活动页优化");
                    assertThat(row.status()).isEqualTo("OPEN");
                    assertThat(row.submissionCount()).isEqualTo(2);
                    assertThat(row.submittedByMe()).isTrue();
                });

        repository.updateTask(secondTaskId, "PG 海报设计-更新", "补充线下活动海报与导出源文件。", "奖金 300 元", Instant.parse("2030-05-03T08:00:00Z"));
        repository.updateTaskStatus(secondTaskId, "CLOSED");
        assertThat(repository.findTaskById(secondTaskId, STUDENT_A_USER_ID))
                .isPresent()
                .get()
                .satisfies(row -> {
                    assertThat(row.title()).isEqualTo("PG 海报设计-更新");
                    assertThat(row.status()).isEqualTo("CLOSED");
                    assertThat(row.closedAt()).isNotNull();
                });

        repository.updateTaskStatus(secondTaskId, "OPEN");
        assertThat(repository.findTaskById(secondTaskId, STUDENT_A_USER_ID))
                .isPresent()
                .get()
                .satisfies(row -> {
                    assertThat(row.status()).isEqualTo("OPEN");
                    assertThat(row.closedAt()).isNull();
                });

        assertThat(repository.findTasksByEnterpriseUserId(ENTERPRISE_USER_ID))
                .extracting(BountyRepository.TaskRow::taskId)
                .containsExactly(secondTaskId, taskId);
        assertThat(repository.findTaskIdsByEnterpriseUserId(ENTERPRISE_USER_ID))
                .containsExactly(taskId, secondTaskId);
    }

    @Test
    void repositoryShouldSupportSubmissionReviewAndEventsOnPostgres() {
        Instant baseTime = Instant.parse("2030-05-02T09:00:00Z");

        insertUser(ENTERPRISE_USER_ID, "ENTERPRISE", "bounty-pg-review-enterprise@example.com", "Review Enterprise");
        insertUser(STUDENT_A_USER_ID, "STUDENT", "bounty-pg-review-student-a@example.com", "Review Student A");
        insertUser(STUDENT_B_USER_ID, "STUDENT", "bounty-pg-review-student-b@example.com", "Review Student B");
        insertEnterpriseProfile();

        long taskId = repository.createTask(
                ENTERPRISE_USER_ID,
                "PG 审核任务",
                "验证 bounty 投稿审核与事件留痕。",
                "优先推荐",
                Instant.parse("2030-05-10T08:00:00Z")
        );
        long submissionA = repository.createSubmission(taskId, STUDENT_A_USER_ID, "我提交了审核方案 A。", "https://demo.example.com/review-a");
        long submissionB = repository.createSubmission(taskId, STUDENT_B_USER_ID, "我提交了审核方案 B。", "https://demo.example.com/review-b");
        repository.createSubmissionEvent(submissionA, taskId, STUDENT_A_USER_ID, "SUBMITTED", null, null, null, "方案 A 已提交。", baseTime);
        repository.createSubmissionEvent(submissionB, taskId, STUDENT_B_USER_ID, "SUBMITTED", null, null, null, "方案 B 已提交。", baseTime.plusSeconds(30));

        assertThat(repository.findSubmissionByTaskAndStudent(taskId, STUDENT_A_USER_ID))
                .isPresent()
                .get()
                .satisfies(row -> {
                    assertThat(row.studentName()).isEqualTo("Review Student A");
                    assertThat(row.status()).isEqualTo("SUBMITTED");
                });

        assertThat(repository.findSubmittedTaskIds(STUDENT_A_USER_ID, List.of(taskId))).containsExactly(taskId);
        assertThat(repository.findSubmissionsByTaskId(taskId, null))
                .extracting(BountyRepository.SubmissionRow::submissionId)
                .containsExactly(submissionB, submissionA);
        assertThat(repository.findSubmissionsByTaskIds(List.of(taskId)))
                .extracting(BountyRepository.SubmissionRow::studentName)
                .containsExactly("Review Student B", "Review Student A");

        repository.reviewSubmission(
                submissionA,
                "ACCEPTED",
                "方案完整，进入优先沟通名单。",
                "WECHAT",
                null,
                "请在本周内补充演示视频。",
                ENTERPRISE_USER_ID
        );

        assertThat(repository.findOtherPendingSubmissions(taskId, submissionA))
                .singleElement()
                .extracting(BountyRepository.SubmissionRow::submissionId)
                .isEqualTo(submissionB);

        repository.rejectOtherPendingSubmissions(
                taskId,
                submissionA,
                "任务已结束，已有中选方案。",
                "任务已结束，已有中选方案。",
                ENTERPRISE_USER_ID
        );
        repository.updateTaskAcceptedSubmission(taskId, submissionA);
        repository.createSubmissionEvent(
                submissionA,
                taskId,
                ENTERPRISE_USER_ID,
                "CONTACT_SENT",
                "方案完整，进入优先沟通名单。",
                "WECHAT",
                null,
                "请在本周内补充演示视频。",
                baseTime.plusSeconds(60)
        );
        repository.createSubmissionEvent(
                submissionB,
                taskId,
                ENTERPRISE_USER_ID,
                "AUTO_REJECTED_TASK_CLOSED",
                "任务已结束，已有中选方案。",
                null,
                "任务已结束，已有中选方案。",
                "任务已结束，企业已选定其他方案。",
                baseTime.plusSeconds(90)
        );
        repository.createSubmissionEvent(
                submissionA,
                taskId,
                ENTERPRISE_USER_ID,
                "TASK_CLOSED_AFTER_ACCEPT",
                null,
                null,
                null,
                "任务已结束，当前结果留痕已固定在工作区中。",
                baseTime.plusSeconds(120)
        );

        assertThat(repository.findSubmissionById(submissionA))
                .isPresent()
                .get()
                .satisfies(row -> {
                    assertThat(row.status()).isEqualTo("ACCEPTED");
                    assertThat(row.reviewComment()).isEqualTo("方案完整，进入优先沟通名单。");
                    assertThat(row.contactIntent()).isEqualTo("WECHAT");
                    assertThat(row.reviewNote()).isEqualTo("请在本周内补充演示视频。");
                    assertThat(row.reviewerUserId()).isEqualTo(ENTERPRISE_USER_ID);
                    assertThat(row.reviewedAt()).isNotNull();
                });

        assertThat(repository.findSubmissionById(submissionB))
                .isPresent()
                .get()
                .satisfies(row -> {
                    assertThat(row.status()).isEqualTo("REJECTED");
                    assertThat(row.reviewComment()).isEqualTo("任务已结束，已有中选方案。");
                    assertThat(row.rejectTemplate()).isEqualTo("任务已结束，已有中选方案。");
                    assertThat(row.reviewerUserId()).isEqualTo(ENTERPRISE_USER_ID);
                });

        assertThat(repository.findSubmissionsByTaskId(taskId, "REJECTED"))
                .singleElement()
                .extracting(BountyRepository.SubmissionRow::submissionId)
                .isEqualTo(submissionB);

        assertThat(repository.findTaskById(taskId, STUDENT_A_USER_ID))
                .isPresent()
                .get()
                .satisfies(row -> {
                    assertThat(row.status()).isEqualTo("CLOSED");
                    assertThat(row.acceptedSubmissionId()).isEqualTo(submissionA);
                    assertThat(row.submissionCount()).isEqualTo(2);
                });

        assertThat(repository.findSubmissionEventsBySubmissionIds(List.of(submissionA, submissionB)))
                .extracting(BountyRepository.SubmissionEventRow::eventType)
                .containsExactly(
                        "SUBMITTED",
                        "CONTACT_SENT",
                        "TASK_CLOSED_AFTER_ACCEPT",
                        "SUBMITTED",
                        "AUTO_REJECTED_TASK_CLOSED"
                );
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
                ) VALUES (?, 'PG Enterprise Ltd.', 'APPROVED', 'logo/bounty-pg.png', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                ENTERPRISE_USER_ID
        );
    }
}
