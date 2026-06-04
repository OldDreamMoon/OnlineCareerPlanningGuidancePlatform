package com.bishe.server.bounty.repository.jpa;

import com.bishe.server.bounty.repository.jpa.entity.BountyTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BountyTaskAdminOpsJpaRepository extends JpaRepository<BountyTaskEntity, Long> {

    interface TaskOpsView {
        Long getTaskId();

        Long getEnterpriseUserId();

        String getTitle();

        String getDescription();

        String getRewardDescription();

        String getStatus();

        Long getAcceptedSubmissionId();

        Instant getDeadlineAt();

        Instant getClosedAt();

        Instant getCreatedAt();

        Instant getUpdatedAt();

        long getSubmissionCount();

        long getPendingSubmissionCount();

        long getAcceptedSubmissionCount();

        long getRejectedSubmissionCount();

        Instant getLatestSubmissionAt();
    }

    @Query("""
            select task.id as taskId,
                   task.enterpriseUserId as enterpriseUserId,
                   task.title as title,
                   task.description as description,
                   task.rewardDescription as rewardDescription,
                   task.status as status,
                   task.acceptedSubmissionId as acceptedSubmissionId,
                   task.deadlineAt as deadlineAt,
                   task.closedAt as closedAt,
                   task.createdAt as createdAt,
                   task.updatedAt as updatedAt,
                   count(submission) as submissionCount,
                   coalesce(sum(case when submission.status in ('SUBMITTED', 'REVIEWING') then 1 else 0 end), 0) as pendingSubmissionCount,
                   coalesce(sum(case when submission.status = 'ACCEPTED' then 1 else 0 end), 0) as acceptedSubmissionCount,
                   coalesce(sum(case when submission.status = 'REJECTED' then 1 else 0 end), 0) as rejectedSubmissionCount,
                   max(submission.createdAt) as latestSubmissionAt
              from BountyTaskEntity task
              left join task.submissions submission
             where (:status is null or task.status = :status)
          group by task.id,
                   task.enterpriseUserId,
                   task.title,
                   task.description,
                   task.rewardDescription,
                   task.status,
                   task.acceptedSubmissionId,
                   task.deadlineAt,
                   task.closedAt,
                   task.createdAt,
                   task.updatedAt
          order by task.createdAt desc, task.id desc
            """)
    List<TaskOpsView> findTaskOps(@Param("status") String status);

    @Query("""
            select task.id as taskId,
                   task.enterpriseUserId as enterpriseUserId,
                   task.title as title,
                   task.description as description,
                   task.rewardDescription as rewardDescription,
                   task.status as status,
                   task.acceptedSubmissionId as acceptedSubmissionId,
                   task.deadlineAt as deadlineAt,
                   task.closedAt as closedAt,
                   task.createdAt as createdAt,
                   task.updatedAt as updatedAt,
                   count(submission) as submissionCount,
                   coalesce(sum(case when submission.status in ('SUBMITTED', 'REVIEWING') then 1 else 0 end), 0) as pendingSubmissionCount,
                   coalesce(sum(case when submission.status = 'ACCEPTED' then 1 else 0 end), 0) as acceptedSubmissionCount,
                   coalesce(sum(case when submission.status = 'REJECTED' then 1 else 0 end), 0) as rejectedSubmissionCount,
                   max(submission.createdAt) as latestSubmissionAt
              from BountyTaskEntity task
              left join task.submissions submission
             where task.id = :taskId
          group by task.id,
                   task.enterpriseUserId,
                   task.title,
                   task.description,
                   task.rewardDescription,
                   task.status,
                   task.acceptedSubmissionId,
                   task.deadlineAt,
                   task.closedAt,
                   task.createdAt,
                   task.updatedAt
            """)
    Optional<TaskOpsView> findTaskOpsById(@Param("taskId") long taskId);
}
