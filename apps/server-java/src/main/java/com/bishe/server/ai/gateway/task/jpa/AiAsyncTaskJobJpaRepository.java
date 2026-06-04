package com.bishe.server.ai.gateway.task.jpa;

import com.bishe.server.ai.gateway.task.jpa.entity.AiAsyncTaskJobEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * AI 异步任务 JPA 仓储。
 */
public interface AiAsyncTaskJobJpaRepository extends JpaRepository<AiAsyncTaskJobEntity, Long> {

    Optional<AiAsyncTaskJobEntity> findByTaskId(String taskId);

    Optional<AiAsyncTaskJobEntity> findByTaskIdAndUserId(String taskId, long userId);

    long countByUserIdAndTaskTypeAndCreatedAtBetween(long userId, String taskType, Instant startAt, Instant endAt);

    @Query("""
            select entity.id
              from AiAsyncTaskJobEntity entity
             where entity.status in :statuses
               and entity.nextRunAt <= :now
               and (entity.leaseExpiresAt is null or entity.leaseExpiresAt < :now)
          order by entity.nextRunAt asc, entity.id asc
            """)
    List<Long> findRunnableJobIds(
            @Param("statuses") Collection<String> statuses,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Query("""
            select entity.id
              from AiAsyncTaskJobEntity entity
             where entity.status in :terminalStatuses
               and coalesce(entity.finishedAt, entity.updatedAt) < :beforeTime
          order by coalesce(entity.finishedAt, entity.updatedAt) asc, entity.id asc
            """)
    List<Long> findTerminalJobIdsBefore(
            @Param("terminalStatuses") Collection<String> terminalStatuses,
            @Param("beforeTime") Instant beforeTime,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AiAsyncTaskJobEntity entity
               set entity.status = :runningStatus,
                   entity.leaseOwner = :workerId,
                   entity.leaseExpiresAt = :leaseExpiresAt,
                   entity.startedAt = coalesce(entity.startedAt, :startedAt),
                   entity.currentAttempt = entity.currentAttempt + 1,
                   entity.updatedAt = :updatedAt
             where entity.id = :jobId
               and entity.status in :claimableStatuses
               and entity.nextRunAt <= :now
               and (entity.leaseExpiresAt is null or entity.leaseExpiresAt < :now)
            """)
    int claimJob(
            @Param("jobId") long jobId,
            @Param("workerId") String workerId,
            @Param("leaseExpiresAt") Instant leaseExpiresAt,
            @Param("startedAt") Instant startedAt,
            @Param("now") Instant now,
            @Param("updatedAt") Instant updatedAt,
            @Param("runningStatus") String runningStatus,
            @Param("claimableStatuses") Collection<String> claimableStatuses
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AiAsyncTaskJobEntity entity
               set entity.status = :succeededStatus,
                   entity.resultSummary = :resultSummary,
                   entity.resultPayloadJson = :resultPayloadJson,
                   entity.finishedAt = :finishedAt,
                   entity.errorCode = null,
                   entity.errorMessage = null,
                   entity.leaseOwner = null,
                   entity.leaseExpiresAt = null,
                   entity.updatedAt = :updatedAt
             where entity.id = :jobId
            """)
    int markSucceeded(
            @Param("jobId") long jobId,
            @Param("resultSummary") String resultSummary,
            @Param("resultPayloadJson") String resultPayloadJson,
            @Param("finishedAt") Instant finishedAt,
            @Param("updatedAt") Instant updatedAt,
            @Param("succeededStatus") String succeededStatus
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AiAsyncTaskJobEntity entity
               set entity.status = :retryWaitStatus,
                   entity.errorCode = :errorCode,
                   entity.errorMessage = :errorMessage,
                   entity.nextRunAt = :nextRunAt,
                   entity.leaseOwner = null,
                   entity.leaseExpiresAt = null,
                   entity.updatedAt = :updatedAt
             where entity.id = :jobId
            """)
    int markRetryWait(
            @Param("jobId") long jobId,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("nextRunAt") Instant nextRunAt,
            @Param("updatedAt") Instant updatedAt,
            @Param("retryWaitStatus") String retryWaitStatus
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AiAsyncTaskJobEntity entity
               set entity.status = :failedStatus,
                   entity.errorCode = :errorCode,
                   entity.errorMessage = :errorMessage,
                   entity.finishedAt = :finishedAt,
                   entity.leaseOwner = null,
                   entity.leaseExpiresAt = null,
                   entity.updatedAt = :updatedAt
             where entity.id = :jobId
            """)
    int markFailed(
            @Param("jobId") long jobId,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("finishedAt") Instant finishedAt,
            @Param("updatedAt") Instant updatedAt,
            @Param("failedStatus") String failedStatus
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from AiAsyncTaskJobEntity entity
             where entity.id in :jobIds
            """)
    int deleteByIdIn(@Param("jobIds") Collection<Long> jobIds);
}
