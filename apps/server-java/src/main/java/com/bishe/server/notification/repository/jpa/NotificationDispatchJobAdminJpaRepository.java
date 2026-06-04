package com.bishe.server.notification.repository.jpa;

import com.bishe.server.notification.model.NotificationChannel;
import com.bishe.server.notification.model.NotificationDispatchStatus;
import com.bishe.server.notification.repository.jpa.entity.NotificationDispatchJobEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * 管理员通知运营台的派发任务读模型仓储。
 */
public interface NotificationDispatchJobAdminJpaRepository extends JpaRepository<NotificationDispatchJobEntity, Long> {

    @Query("""
            select j.status as status,
                   count(j) as total
              from NotificationDispatchJobEntity j
          group by j.status
            """)
    List<StatusCountView> countGroupedByStatus();

    @Query("""
            select j.channel as channel,
                   j.status as status,
                   count(j) as total
              from NotificationDispatchJobEntity j
             where j.createdAt >= :since
          group by j.channel, j.status
          order by j.channel asc, j.status asc
            """)
    List<ChannelStatusCountView> countGroupedByChannelAndStatusSince(@Param("since") Instant since);

    @Query("""
            select distinct j.eventId
              from NotificationDispatchJobEntity j
             where j.channel = :channel
               and j.eventId in :eventIds
            """)
    List<String> findDistinctEventIdsByChannelAndEventIdIn(
            @Param("channel") NotificationChannel channel,
            @Param("eventIds") Collection<String> eventIds
    );

    @Query("""
            select j.id as id,
                   j.jobId as jobId,
                   j.notificationId as notificationId,
                   j.eventId as eventId,
                   j.userId as userId,
                   j.channel as channel,
                   j.status as status,
                   j.attemptCount as attemptCount,
                   j.maxAttempts as maxAttempts,
                   j.nextRunAt as nextRunAt,
                   j.sentAt as sentAt,
                   j.ackedAt as ackedAt,
                   j.failedAt as failedAt,
                   j.errorCode as errorCode,
                   j.errorMessage as errorMessage,
                   n.title as title,
                   n.content as content,
                   n.type as type,
                   j.createdAt as createdAt,
                   j.updatedAt as updatedAt
              from NotificationDispatchJobEntity j
         left join j.notification n
             where (:status is null or j.status = :status)
               and (:channel is null or j.channel = :channel)
          order by case
                       when j.status in (
                           com.bishe.server.notification.model.NotificationDispatchStatus.DEAD,
                           com.bishe.server.notification.model.NotificationDispatchStatus.RETRY_WAIT,
                           com.bishe.server.notification.model.NotificationDispatchStatus.SKIPPED
                       ) then 0
                       else 1
                   end asc,
                   coalesce(j.failedAt, j.updatedAt, j.createdAt) desc,
                   j.id desc
            """)
    List<DispatchJobAdminView> findAdminRows(
            @Param("status") NotificationDispatchStatus status,
            @Param("channel") NotificationChannel channel,
            Pageable pageable
    );

    @Query("""
            select count(j)
              from NotificationDispatchJobEntity j
             where (:status is null or j.status = :status)
               and (:channel is null or j.channel = :channel)
            """)
    long countAdminRows(
            @Param("status") NotificationDispatchStatus status,
            @Param("channel") NotificationChannel channel
    );

    interface StatusCountView {

        NotificationDispatchStatus getStatus();

        long getTotal();
    }

    interface ChannelStatusCountView {

        NotificationChannel getChannel();

        NotificationDispatchStatus getStatus();

        long getTotal();
    }

    interface DispatchJobAdminView {

        Long getId();

        String getJobId();

        Long getNotificationId();

        String getEventId();

        Long getUserId();

        NotificationChannel getChannel();

        NotificationDispatchStatus getStatus();

        Integer getAttemptCount();

        Integer getMaxAttempts();

        Instant getNextRunAt();

        Instant getSentAt();

        Instant getAckedAt();

        Instant getFailedAt();

        String getErrorCode();

        String getErrorMessage();

        String getTitle();

        String getContent();

        String getType();

        Instant getCreatedAt();

        Instant getUpdatedAt();
    }
}
