package com.bishe.server.notification.repository.jpa;

import com.bishe.server.notification.model.NotificationChannel;
import com.bishe.server.notification.model.NotificationDispatchStatus;
import com.bishe.server.notification.repository.jpa.entity.NotificationDispatchJobEntity;
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
 * 通知派发任务 JPA 仓储。
 */
public interface NotificationDispatchJobJpaRepository extends JpaRepository<NotificationDispatchJobEntity, Long> {

    Optional<NotificationDispatchJobEntity> findByJobId(String jobId);

    @Query("""
            select entity.id
              from NotificationDispatchJobEntity entity
             where entity.status in :statuses
               and entity.nextRunAt <= :now
               and (entity.leaseExpiresAt is null or entity.leaseExpiresAt < :now)
          order by entity.nextRunAt asc, entity.id asc
            """)
    List<Long> findRunnableJobIds(
            @Param("statuses") Collection<NotificationDispatchStatus> statuses,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update NotificationDispatchJobEntity entity
               set entity.status = :runningStatus,
                   entity.leaseOwner = :workerId,
                   entity.leaseExpiresAt = :leaseExpiresAt,
                   entity.attemptCount = entity.attemptCount + 1,
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
            @Param("now") Instant now,
            @Param("updatedAt") Instant updatedAt,
            @Param("runningStatus") NotificationDispatchStatus runningStatus,
            @Param("claimableStatuses") Collection<NotificationDispatchStatus> claimableStatuses
    );

    @Query("""
            select entity.id
              from NotificationDispatchJobEntity entity
             where entity.status in :terminalStatuses
               and entity.createdAt < :beforeTime
          order by entity.createdAt asc, entity.id asc
            """)
    List<Long> findTerminalJobIdsBefore(
            @Param("terminalStatuses") Collection<NotificationDispatchStatus> terminalStatuses,
            @Param("beforeTime") Instant beforeTime,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update NotificationDispatchJobEntity entity
               set entity.status = :sentStatus,
                   entity.sentAt = :sentAt,
                   entity.failedAt = null,
                   entity.errorCode = null,
                   entity.errorMessage = null,
                   entity.leaseOwner = null,
                   entity.leaseExpiresAt = null,
                   entity.updatedAt = :updatedAt
             where entity.id = :jobId
            """)
    int markSent(
            @Param("jobId") long jobId,
            @Param("sentAt") Instant sentAt,
            @Param("updatedAt") Instant updatedAt,
            @Param("sentStatus") NotificationDispatchStatus sentStatus
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update NotificationDispatchJobEntity entity
               set entity.status = :ackedStatus,
                   entity.ackedAt = coalesce(entity.ackedAt, :ackedAt),
                   entity.leaseOwner = null,
                   entity.leaseExpiresAt = null,
                   entity.updatedAt = :updatedAt
             where entity.jobId = :jobId
               and entity.userId = :userId
               and entity.channel = :channel
               and entity.status in :ackableStatuses
            """)
    int markAcked(
            @Param("jobId") String jobId,
            @Param("userId") long userId,
            @Param("ackedAt") Instant ackedAt,
            @Param("updatedAt") Instant updatedAt,
            @Param("channel") NotificationChannel channel,
            @Param("ackedStatus") NotificationDispatchStatus ackedStatus,
            @Param("ackableStatuses") Collection<NotificationDispatchStatus> ackableStatuses
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update NotificationDispatchJobEntity entity
               set entity.status = :retryWaitStatus,
                   entity.nextRunAt = :nextRunAt,
                   entity.failedAt = null,
                   entity.errorCode = :errorCode,
                   entity.errorMessage = :errorMessage,
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
            @Param("retryWaitStatus") NotificationDispatchStatus retryWaitStatus
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update NotificationDispatchJobEntity entity
               set entity.status = :skippedStatus,
                   entity.failedAt = :failedAt,
                   entity.errorCode = :errorCode,
                   entity.errorMessage = :errorMessage,
                   entity.leaseOwner = null,
                   entity.leaseExpiresAt = null,
                   entity.updatedAt = :updatedAt
             where entity.id = :jobId
            """)
    int markSkipped(
            @Param("jobId") long jobId,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("failedAt") Instant failedAt,
            @Param("updatedAt") Instant updatedAt,
            @Param("skippedStatus") NotificationDispatchStatus skippedStatus
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update NotificationDispatchJobEntity entity
               set entity.status = :deadStatus,
                   entity.failedAt = :failedAt,
                   entity.errorCode = :errorCode,
                   entity.errorMessage = :errorMessage,
                   entity.leaseOwner = null,
                   entity.leaseExpiresAt = null,
                   entity.updatedAt = :updatedAt
             where entity.id = :jobId
            """)
    int markDead(
            @Param("jobId") long jobId,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("failedAt") Instant failedAt,
            @Param("updatedAt") Instant updatedAt,
            @Param("deadStatus") NotificationDispatchStatus deadStatus
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update NotificationDispatchJobEntity entity
               set entity.status = :retryWaitStatus,
                   entity.nextRunAt = :nextRunAt,
                   entity.maxAttempts = case
                       when entity.channel = :websocketChannel
                            and :websocketMaxAttempts is not null
                            and entity.maxAttempts < :websocketMaxAttempts
                       then :websocketMaxAttempts
                       else entity.maxAttempts
                   end,
                   entity.failedAt = null,
                   entity.errorCode = null,
                   entity.errorMessage = null,
                   entity.leaseOwner = null,
                   entity.leaseExpiresAt = null,
                   entity.updatedAt = :updatedAt
             where entity.jobId = :jobId
               and entity.status in :retryableStatuses
            """)
    int requeueForAdmin(
            @Param("jobId") String jobId,
            @Param("nextRunAt") Instant nextRunAt,
            @Param("websocketMaxAttempts") Integer websocketMaxAttempts,
            @Param("updatedAt") Instant updatedAt,
            @Param("websocketChannel") NotificationChannel websocketChannel,
            @Param("retryWaitStatus") NotificationDispatchStatus retryWaitStatus,
            @Param("retryableStatuses") Collection<NotificationDispatchStatus> retryableStatuses
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from NotificationDispatchJobEntity entity
             where entity.id in :jobIds
            """)
    int deleteByIdIn(@Param("jobIds") Collection<Long> jobIds);
}
