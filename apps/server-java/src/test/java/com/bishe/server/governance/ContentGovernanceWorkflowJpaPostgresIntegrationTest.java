package com.bishe.server.governance;

import com.bishe.server.auth.model.UserAccountStatus;
import com.bishe.server.auth.model.UserRole;
import com.bishe.server.auth.repository.jpa.UserAccountJpaRepository;
import com.bishe.server.auth.repository.jpa.entity.UserAccountEntity;
import com.bishe.server.community.repository.jpa.CommunityCommentJpaRepository;
import com.bishe.server.community.repository.jpa.CommunityPostJpaRepository;
import com.bishe.server.profile.repository.jpa.entity.CommentEntity;
import com.bishe.server.profile.repository.jpa.entity.PostEntity;
import com.bishe.server.support.AbstractPostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * 治理状态机仓储在 PostgreSQL 下的最小验证。
 */
@DataJpaTest
@Import(ContentGovernanceRepository.class)
class ContentGovernanceWorkflowJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private ContentGovernanceRepository contentGovernanceRepository;

    @Autowired
    private UserAccountJpaRepository userAccountJpaRepository;

    @Autowired
    private CommunityPostJpaRepository communityPostJpaRepository;

    @Autowired
    private CommunityCommentJpaRepository communityCommentJpaRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void reportAndAuditQueriesShouldWorkWithWorkflowTablesOnPostgres() {
        UserAccountEntity reporter = saveUser("pg-gov-workflow-reporter@example.com", UserRole.STUDENT, "PG 举报人");
        UserAccountEntity operator = saveUser("pg-gov-workflow-admin@example.com", UserRole.ADMIN, "PG 管理员");
        UserAccountEntity author = saveUser("pg-gov-workflow-author@example.com", UserRole.MENTOR, "PG 作者");

        PostEntity post = communityPostJpaRepository.saveAndFlush(PostEntity.create(
                author.getId(),
                "PG 治理举报帖子",
                "GENERAL_HELP",
                "这是一条用于治理仓储 PostgreSQL 验证的帖子正文。",
                "治理,PG",
                "OPEN",
                "PASS",
                "LOW",
                0L
        ));
        CommentEntity comment = communityCommentJpaRepository.saveAndFlush(CommentEntity.create(
                post.getId(),
                author.getId(),
                "这条评论需要进入举报与审计链路验证。",
                false,
                "PASS",
                "LOW",
                0L
        ));

        assertThat(contentGovernanceRepository.existsTarget("POST", String.valueOf(post.getId()))).isTrue();
        assertThat(contentGovernanceRepository.existsTarget("COMMENT", String.valueOf(comment.getId()))).isTrue();

        long reportId = contentGovernanceRepository.insertReport(
                reporter.getId(),
                "COMMENT",
                String.valueOf(comment.getId()),
                "ABUSE",
                "PG 举报链路验证"
        );

        assertThat(contentGovernanceRepository.findExistingReport(
                reporter.getId(),
                "COMMENT",
                String.valueOf(comment.getId()),
                "ABUSE"
        )).hasValueSatisfying(existing -> {
            assertThat(existing.reportId()).isEqualTo(reportId);
            assertThat(existing.status()).isEqualTo("PENDING");
        });
        assertThat(contentGovernanceRepository.countOpenReportsByTarget("COMMENT", String.valueOf(comment.getId()))).isEqualTo(1L);

        assertThat(contentGovernanceRepository.findMyReports(reporter.getId(), null, 1, 10))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.reportId()).isEqualTo(reportId);
                    assertThat(row.targetType()).isEqualTo("COMMENT");
                    assertThat(row.targetId()).isEqualTo(String.valueOf(comment.getId()));
                    assertThat(row.contentPostId()).isEqualTo(String.valueOf(post.getId()));
                    assertThat(row.contentTitle()).isEqualTo("PG 治理举报帖子");
                    assertThat(row.contentBody()).isEqualTo("这条评论需要进入举报与审计链路验证。");
                    assertThat(row.reasonCode()).isEqualTo("ABUSE");
                    assertThat(row.reportDetail()).isEqualTo("PG 举报链路验证");
                    assertThat(row.status()).isEqualTo("PENDING");
                    assertThat(row.latestAction()).isEqualTo("NONE");
                });
        assertThat(contentGovernanceRepository.countMyReports(reporter.getId(), "PENDING")).isEqualTo(1L);

        assertThat(contentGovernanceRepository.findAdminReports(null, "COMMENT", 1, 10))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.reportId()).isEqualTo(reportId);
                    assertThat(row.contentPostId()).isEqualTo(String.valueOf(post.getId()));
                    assertThat(row.contentTitle()).isEqualTo("PG 治理举报帖子");
                    assertThat(row.contentBody()).isEqualTo("这条评论需要进入举报与审计链路验证。");
                    assertThat(row.reasonCode()).isEqualTo("ABUSE");
                    assertThat(row.status()).isEqualTo("PENDING");
                    assertThat(row.latestAction()).isEqualTo("NONE");
                    assertThat(row.reportCount()).isEqualTo(1L);
                });
        assertThat(contentGovernanceRepository.countAdminReports("PENDING", "COMMENT")).isEqualTo(1L);

        contentGovernanceRepository.updateReportStatus(reportId, "ACCEPTED", "TAKE_DOWN", true);
        contentGovernanceRepository.insertReportAction(
                reportId,
                operator.getId(),
                "ACCEPTED",
                "TAKE_DOWN",
                "PG 管理员确认下架"
        );
        contentGovernanceRepository.insertAuditLog(
                "PG-GOV-TRACE-REPORT-001",
                operator.getId(),
                "REPORT_DECISION",
                "COMMENT",
                String.valueOf(comment.getId()),
                "{\"reportId\":%d}".formatted(reportId)
        );

        assertThat(contentGovernanceRepository.countOpenReportsByTarget("COMMENT", String.valueOf(comment.getId()))).isEqualTo(1L);
        assertThat(contentGovernanceRepository.countMyReports(reporter.getId(), "ACCEPTED")).isEqualTo(1L);

        assertThat(contentGovernanceRepository.findAdminReportDetail(reportId))
                .hasValueSatisfying(detail -> {
                    assertThat(detail.reportId()).isEqualTo(reportId);
                    assertThat(detail.reporterUserId()).isEqualTo(reporter.getId());
                    assertThat(detail.reporterDisplayName()).isEqualTo("PG 举报人");
                    assertThat(detail.contentPostId()).isEqualTo(String.valueOf(post.getId()));
                    assertThat(detail.contentTitle()).isEqualTo("PG 治理举报帖子");
                    assertThat(detail.contentBody()).isEqualTo("这条评论需要进入举报与审计链路验证。");
                    assertThat(detail.status()).isEqualTo("ACCEPTED");
                    assertThat(detail.latestAction()).isEqualTo("TAKE_DOWN");
                    assertThat(detail.reportCount()).isEqualTo(1L);
                });
        assertThat(contentGovernanceRepository.findReportForDecision(reportId))
                .hasValueSatisfying(report -> {
                    assertThat(report.reportId()).isEqualTo(reportId);
                    assertThat(report.targetType()).isEqualTo("COMMENT");
                    assertThat(report.targetId()).isEqualTo(String.valueOf(comment.getId()));
                    assertThat(report.status()).isEqualTo("ACCEPTED");
                });
        assertThat(contentGovernanceRepository.findCommunityNotificationTarget("COMMENT", String.valueOf(comment.getId())))
                .hasValueSatisfying(target -> {
                    assertThat(target.ownerUserId()).isEqualTo(author.getId());
                    assertThat(target.postId()).isEqualTo(String.valueOf(post.getId()));
                    assertThat(target.postTitle()).isEqualTo("PG 治理举报帖子");
                });
        assertThat(contentGovernanceRepository.listReportActions(reportId))
                .singleElement()
                .satisfies(action -> {
                    assertThat(action.operatorUserId()).isEqualTo(operator.getId());
                    assertThat(action.operatorDisplayName()).isEqualTo("PG 管理员");
                    assertThat(action.decision()).isEqualTo("ACCEPTED");
                    assertThat(action.action()).isEqualTo("TAKE_DOWN");
                    assertThat(action.comment()).isEqualTo("PG 管理员确认下架");
                });

        assertThat(contentGovernanceRepository.findAuditLogs(
                "COMMENT",
                String.valueOf(comment.getId()),
                "REPORT_DECISION",
                "PG-GOV-TRACE-REPORT-001",
                1,
                10
        )).singleElement()
                .satisfies(log -> {
                    assertThat(log.traceId()).isEqualTo("PG-GOV-TRACE-REPORT-001");
                    assertThat(log.operatorUserId()).isEqualTo(operator.getId());
                    assertThat(log.operatorDisplayName()).isEqualTo("PG 管理员");
                    assertThat(log.actionType()).isEqualTo("REPORT_DECISION");
                    assertThat(log.targetType()).isEqualTo("COMMENT");
                    assertThat(log.targetId()).isEqualTo(String.valueOf(comment.getId()));
                    assertThat(log.detailJson()).isEqualTo("{\"reportId\":%d}".formatted(reportId));
                });
        assertThat(contentGovernanceRepository.countAuditLogs(
                "COMMENT",
                String.valueOf(comment.getId()),
                "REPORT_DECISION",
                "PG-GOV-TRACE-REPORT-001"
        )).isEqualTo(1L);
    }

    @Test
    void reviewQueueQueriesShouldWorkForCommunityCommentAndAiOutputOnPostgres() {
        UserAccountEntity operator = saveUser("pg-gov-review-admin@example.com", UserRole.ADMIN, "PG 审核管理员");
        UserAccountEntity author = saveUser("pg-gov-review-author@example.com", UserRole.STUDENT, "PG 待审作者");

        PostEntity post = communityPostJpaRepository.saveAndFlush(PostEntity.create(
                author.getId(),
                "PG 待审帖子",
                "GENERAL_HELP",
                "这是一条评论待审场景下的父帖正文。",
                "治理,待审",
                "OPEN",
                "PASS",
                "LOW",
                0L
        ));
        CommentEntity comment = communityCommentJpaRepository.saveAndFlush(CommentEntity.create(
                post.getId(),
                author.getId(),
                "评论正文包含需要人工复核的内容。",
                false,
                "PASS",
                "LOW",
                0L
        ));

        long commentEventId = contentGovernanceRepository.insertModerationEvent(
                "PG-GOV-TRACE-REVIEW-COMMENT",
                "COMMUNITY_COMMENT",
                "COMMENT",
                String.valueOf(comment.getId()),
                "MEDIUM",
                "REVIEW",
                "TERM_MATCH_COMMENT",
                null,
                operator.getId()
        );
        contentGovernanceRepository.updateTargetModeration(
                "COMMENT",
                String.valueOf(comment.getId()),
                "REVIEW",
                "MEDIUM",
                commentEventId,
                null
        );
        entityManager.clear();

        long aiEventId = contentGovernanceRepository.insertModerationEvent(
                "PG-GOV-TRACE-REVIEW-AI",
                "AI_OUTPUT",
                "AI_REPLY",
                null,
                "MEDIUM",
                "REVIEW",
                "TERM_MATCH_AI",
                "AI 输出需要进入人工复核。",
                operator.getId()
        );

        assertThat(contentGovernanceRepository.findTargetState("COMMENT", String.valueOf(comment.getId())))
                .hasValueSatisfying(state -> {
                    assertThat(state.targetType()).isEqualTo("COMMENT");
                    assertThat(state.status()).isEqualTo("REVIEW");
                    assertThat(state.riskLevel()).isEqualTo("MEDIUM");
                });

        assertThat(contentGovernanceRepository.findReviewQueue(null, 1, 10))
                .extracting(
                        ContentGovernanceRepository.ReviewQueueItemRow::eventId,
                        ContentGovernanceRepository.ReviewQueueItemRow::sourceType
                )
                .contains(tuple(commentEventId, "COMMUNITY_COMMENT"), tuple(aiEventId, "AI_OUTPUT"));
        assertThat(contentGovernanceRepository.countReviewQueue(null)).isEqualTo(2L);
        assertThat(contentGovernanceRepository.countReviewQueue("COMMUNITY_COMMENT")).isEqualTo(1L);
        assertThat(contentGovernanceRepository.countReviewQueue("AI_OUTPUT")).isEqualTo(1L);

        assertThat(contentGovernanceRepository.findReviewQueueItem(commentEventId))
                .hasValueSatisfying(item -> {
                    assertThat(item.sourceType()).isEqualTo("COMMUNITY_COMMENT");
                    assertThat(item.targetType()).isEqualTo("COMMENT");
                    assertThat(item.targetId()).isEqualTo(String.valueOf(comment.getId()));
                    assertThat(item.riskLevel()).isEqualTo("MEDIUM");
                    assertThat(item.reasonCode()).isEqualTo("TERM_MATCH_COMMENT");
                    assertThat(item.preview()).contains("评论正文包含需要人工复核的内容");
                });
        assertThat(contentGovernanceRepository.findReviewQueueDetail(commentEventId))
                .hasValueSatisfying(detail -> {
                    assertThat(detail.sourceType()).isEqualTo("COMMUNITY_COMMENT");
                    assertThat(detail.targetType()).isEqualTo("COMMENT");
                    assertThat(detail.targetId()).isEqualTo(String.valueOf(comment.getId()));
                    assertThat(detail.contentTitle()).isEqualTo("PG 待审帖子");
                    assertThat(detail.contentBody()).isEqualTo("评论正文包含需要人工复核的内容。");
                    assertThat(detail.postId()).isEqualTo(String.valueOf(post.getId()));
                    assertThat(detail.postTitle()).isEqualTo("PG 待审帖子");
                    assertThat(detail.postBody()).isEqualTo("这是一条评论待审场景下的父帖正文。");
                    assertThat(detail.authorUserId()).isEqualTo(author.getId());
                    assertThat(detail.authorDisplayName()).isEqualTo("PG 待审作者");
                    assertThat(detail.authorRole()).isEqualTo("STUDENT");
                });

        assertThat(contentGovernanceRepository.findReviewQueueDetail(aiEventId))
                .hasValueSatisfying(detail -> {
                    assertThat(detail.sourceType()).isEqualTo("AI_OUTPUT");
                    assertThat(detail.targetType()).isEqualTo("AI_REPLY");
                    assertThat(detail.targetId()).isEqualTo("TRACE:PG-GOV-TRACE-REVIEW-AI");
                    assertThat(detail.preview()).contains("AI 输出需要进入人工复核");
                    assertThat(detail.contentBody()).isEqualTo("AI 输出需要进入人工复核。");
                    assertThat(detail.authorUserId()).isNull();
                });

        contentGovernanceRepository.updateModerationEventTarget(aiEventId, "AI-REVIEW-TARGET-001");
        assertThat(contentGovernanceRepository.findReviewQueueItem(aiEventId))
                .hasValueSatisfying(item -> assertThat(item.targetId()).isEqualTo("AI-REVIEW-TARGET-001"));

        contentGovernanceRepository.updateModerationEventDecision(commentEventId, "BLOCK", "HIGH", "MANUAL_REJECT");
        assertThat(contentGovernanceRepository.findReviewQueueItem(commentEventId)).isEmpty();
        assertThat(contentGovernanceRepository.countReviewQueue("COMMUNITY_COMMENT")).isZero();
    }

    private UserAccountEntity saveUser(String email, UserRole role, String displayName) {
        return userAccountJpaRepository.saveAndFlush(UserAccountEntity.create(
                email,
                "$2a$10$pg-governance-seed",
                role,
                "FREE",
                UserAccountStatus.ACTIVE,
                displayName,
                displayName + " 实名"
        ));
    }
}
