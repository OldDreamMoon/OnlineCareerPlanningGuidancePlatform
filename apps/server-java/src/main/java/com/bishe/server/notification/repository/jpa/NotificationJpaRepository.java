package com.bishe.server.notification.repository.jpa;

import com.bishe.server.notification.model.NotificationCategory;
import com.bishe.server.notification.repository.jpa.entity.NotificationEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 通知收件箱 JPA 仓储。
 */
public interface NotificationJpaRepository extends JpaRepository<NotificationEntity, Long>, JpaSpecificationExecutor<NotificationEntity> {

    List<NotificationEntity> findByUserIdAndArchivedAtIsNullAndIdGreaterThanOrderByIdAsc(long userId, long afterId, Pageable pageable);

    Optional<NotificationEntity> findByIdAndUserIdAndArchivedAtIsNull(long notificationId, long userId);

    Optional<NotificationEntity> findTopByUserIdAndArchivedAtIsNullOrderByIdDesc(long userId);

    long countByUserIdAndArchivedAtIsNullAndReadFalse(long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE NotificationEntity n
               SET n.read = true,
                   n.readAt = COALESCE(n.readAt, :readAt),
                   n.updatedAt = :readAt
             WHERE n.id = :notificationId
               AND n.userId = :userId
               AND n.archivedAt IS NULL
            """)
    int markRead(@Param("notificationId") long notificationId, @Param("userId") long userId, @Param("readAt") Instant readAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE NotificationEntity n
               SET n.read = true,
                   n.readAt = COALESCE(n.readAt, :readAt),
                   n.updatedAt = :readAt
             WHERE n.userId = :userId
               AND n.archivedAt IS NULL
               AND n.read = false
            """)
    int markAllRead(@Param("userId") long userId, @Param("readAt") Instant readAt);

    @Query("""
            SELECT n.category AS category, COUNT(n) AS total
              FROM NotificationEntity n
             WHERE n.userId = :userId
               AND n.archivedAt IS NULL
               AND n.read = false
               AND n.actionCode IS NOT NULL
               AND n.actionCode <> ''
               AND n.actionCode <> 'VIEW_NOTIFICATION_CENTER'
          GROUP BY n.category
            """)
    List<ActionableCountProjection> countUnreadActionableGrouped(@Param("userId") long userId);

    /**
     * 可操作未读数聚合投影。
     */
    interface ActionableCountProjection {

        NotificationCategory getCategory();

        long getTotal();
    }
}
