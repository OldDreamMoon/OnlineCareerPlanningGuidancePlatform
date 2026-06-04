package com.bishe.server.notification.repository.jpa;

import com.bishe.server.notification.repository.jpa.entity.NotificationEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * 管理员通知运营台的公告聚合读模型仓储。
 */
public interface NotificationAdminOpsJpaRepository extends JpaRepository<NotificationEntity, Long> {

    @Query("""
            select count(distinct n.eventId)
              from NotificationEntity n
             where n.archivedAt is null
               and n.type = 'SYSTEM_ANNOUNCEMENT'
               and n.eventId is not null
            """)
    long countDistinctAnnouncementEvents();

    @Query("""
            select count(distinct n.eventId)
              from NotificationEntity n
             where n.archivedAt is null
               and n.type = 'SYSTEM_ANNOUNCEMENT'
               and n.eventId is not null
               and n.createdAt >= :since
            """)
    long countDistinctAnnouncementEventsSince(@Param("since") Instant since);

    @Query("""
            select max(n.createdAt)
              from NotificationEntity n
             where n.archivedAt is null
               and n.type = 'SYSTEM_ANNOUNCEMENT'
               and n.eventId is not null
            """)
    Instant findLatestAnnouncementCreatedAt();

    @Query("""
            select n.eventId as eventId,
                   max(n.createdAt) as createdAt
              from NotificationEntity n
             where n.archivedAt is null
               and n.type = 'SYSTEM_ANNOUNCEMENT'
               and n.eventId is not null
          group by n.eventId
          order by max(n.createdAt) desc, n.eventId desc
            """)
    List<AnnouncementEventRef> findRecentAnnouncementEventRefs(Pageable pageable);

    @Query("""
            select n
              from NotificationEntity n
             where n.archivedAt is null
               and n.type = 'SYSTEM_ANNOUNCEMENT'
               and n.eventId in :eventIds
               and n.id in (
                   select max(innerNotification.id)
                     from NotificationEntity innerNotification
                    where innerNotification.archivedAt is null
                      and innerNotification.type = 'SYSTEM_ANNOUNCEMENT'
                      and innerNotification.eventId in :eventIds
                 group by innerNotification.eventId
               )
          order by n.createdAt desc, n.id desc
            """)
    List<NotificationEntity> findLatestAnnouncementsByEventIds(@Param("eventIds") Collection<String> eventIds);

    @Query("""
            select n.eventId as eventId,
                   count(n) as total
              from NotificationEntity n
             where n.archivedAt is null
               and n.type = 'SYSTEM_ANNOUNCEMENT'
               and n.eventId in :eventIds
          group by n.eventId
            """)
    List<AnnouncementEventCount> countAnnouncementNotificationsByEventIds(@Param("eventIds") Collection<String> eventIds);

    interface AnnouncementEventRef {

        String getEventId();

        Instant getCreatedAt();
    }

    interface AnnouncementEventCount {

        String getEventId();

        long getTotal();
    }
}
