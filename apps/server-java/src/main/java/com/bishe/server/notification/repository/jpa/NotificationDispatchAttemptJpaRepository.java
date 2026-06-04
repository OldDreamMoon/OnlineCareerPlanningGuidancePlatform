package com.bishe.server.notification.repository.jpa;

import com.bishe.server.notification.repository.jpa.entity.NotificationDispatchAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

/**
 * 通知派发尝试 JPA 仓储。
 */
public interface NotificationDispatchAttemptJpaRepository extends JpaRepository<NotificationDispatchAttemptEntity, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from NotificationDispatchAttemptEntity entity
             where entity.jobId in :jobIds
            """)
    int deleteByJobIdIn(@Param("jobIds") Collection<Long> jobIds);
}
