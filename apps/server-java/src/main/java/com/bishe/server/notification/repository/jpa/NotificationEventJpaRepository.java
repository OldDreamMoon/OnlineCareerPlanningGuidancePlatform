package com.bishe.server.notification.repository.jpa;

import com.bishe.server.notification.repository.jpa.entity.NotificationEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 通知事件 JPA 仓储。
 */
public interface NotificationEventJpaRepository extends JpaRepository<NotificationEventEntity, Long> {

    Optional<NotificationEventEntity> findByDedupeKey(String dedupeKey);
}
