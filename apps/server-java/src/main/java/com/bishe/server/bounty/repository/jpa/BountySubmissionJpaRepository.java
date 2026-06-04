package com.bishe.server.bounty.repository.jpa;

import com.bishe.server.bounty.repository.jpa.entity.BountySubmissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BountySubmissionJpaRepository extends JpaRepository<BountySubmissionEntity, Long> {

    Optional<BountySubmissionEntity> findByTaskIdAndStudentUserId(long taskId, long studentUserId);

    List<BountySubmissionEntity> findByTaskIdOrderByCreatedAtDescIdDesc(long taskId);

    List<BountySubmissionEntity> findByTaskIdAndStatusOrderByCreatedAtDescIdDesc(long taskId, String status);

    List<BountySubmissionEntity> findByTaskIdInOrderByTaskIdAscCreatedAtDescIdDesc(Collection<Long> taskIds);

    List<BountySubmissionEntity> findByTaskIdAndIdNotAndStatusInOrderByCreatedAtDescIdDesc(
            long taskId,
            long excludedSubmissionId,
            Collection<String> statuses
    );

    @Query("""
            select distinct submission.taskId
              from BountySubmissionEntity submission
             where submission.studentUserId = :studentUserId
               and submission.taskId in :taskIds
          order by submission.taskId asc
            """)
    List<Long> findSubmittedTaskIds(
            @Param("studentUserId") long studentUserId,
            @Param("taskIds") Collection<Long> taskIds
    );

    @Query("""
            select submission.taskId as taskId,
                   count(submission.id) as submissionCount
              from BountySubmissionEntity submission
             where submission.taskId in :taskIds
          group by submission.taskId
            """)
    List<TaskSubmissionCountView> countSubmissionByTaskIds(@Param("taskIds") Collection<Long> taskIds);

    interface TaskSubmissionCountView {

        Long getTaskId();

        long getSubmissionCount();
    }
}
