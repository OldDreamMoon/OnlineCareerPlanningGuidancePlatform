package com.bishe.server.notification.repository.jpa;

import com.bishe.server.notification.model.NotificationCategory;
import com.bishe.server.notification.repository.jpa.entity.NotificationPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 用户通知偏好 JPA 仓储。
 */
public interface NotificationPreferenceJpaRepository extends JpaRepository<NotificationPreferenceEntity, Long> {

    Optional<NotificationPreferenceEntity> findByUserIdAndCategory(long userId, NotificationCategory category);

    List<NotificationPreferenceEntity> findByUserIdOrderByCategoryAsc(long userId);
}
