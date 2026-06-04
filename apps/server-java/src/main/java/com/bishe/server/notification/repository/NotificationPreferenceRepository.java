package com.bishe.server.notification.repository;

import com.bishe.server.notification.model.NotificationCategory;
import com.bishe.server.notification.model.NotificationPriority;
import com.bishe.server.notification.repository.jpa.NotificationPreferenceJpaRepository;
import com.bishe.server.notification.repository.jpa.entity.NotificationPreferenceEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 用户通知偏好仓储。
 */
@Repository
public class NotificationPreferenceRepository {

    private final NotificationPreferenceJpaRepository notificationPreferenceJpaRepository;

    public NotificationPreferenceRepository(NotificationPreferenceJpaRepository notificationPreferenceJpaRepository) {
        this.notificationPreferenceJpaRepository = notificationPreferenceJpaRepository;
    }

    public Optional<NotificationPreferenceRow> findByUserIdAndCategory(long userId, NotificationCategory category) {
        return notificationPreferenceJpaRepository.findByUserIdAndCategory(userId, category).map(this::toRow);
    }

    public List<NotificationPreferenceRow> findByUserId(long userId) {
        return notificationPreferenceJpaRepository.findByUserIdOrderByCategoryAsc(userId).stream()
                .map(this::toRow)
                .toList();
    }

    public void saveOrUpdate(NotificationPreferenceUpsertCommand command) {
        NotificationPreferenceEntity entity = notificationPreferenceJpaRepository
                .findByUserIdAndCategory(command.userId(), command.category())
                .orElseGet(() -> NotificationPreferenceEntity.create(command.userId(), command.category()));
        entity.setInboxEnabled(command.inboxEnabled());
        entity.setWebsocketEnabled(command.websocketEnabled());
        entity.setBrowserPopupEnabled(command.browserPopupEnabled());
        entity.setEmailEnabled(command.emailEnabled());
        entity.setEmailUrgencyThreshold(command.emailUrgencyThreshold());
        entity.setQuietHoursJson(command.quietHoursJson());
        notificationPreferenceJpaRepository.saveAndFlush(entity);
    }

    private NotificationPreferenceRow toRow(NotificationPreferenceEntity entity) {
        return new NotificationPreferenceRow(
                entity.getId(),
                entity.getUserId(),
                entity.getCategory(),
                entity.isInboxEnabled(),
                entity.isWebsocketEnabled(),
                entity.isBrowserPopupEnabled(),
                entity.isEmailEnabled(),
                entity.getEmailUrgencyThreshold(),
                entity.getQuietHoursJson(),
                entity.getUpdatedAt()
        );
    }

    /**
     * 偏好 upsert 命令。
     */
    public record NotificationPreferenceUpsertCommand(
            long userId,
            NotificationCategory category,
            boolean inboxEnabled,
            boolean websocketEnabled,
            boolean browserPopupEnabled,
            boolean emailEnabled,
            NotificationPriority emailUrgencyThreshold,
            String quietHoursJson
    ) {
    }

    /**
     * 偏好数据行。
     */
    public record NotificationPreferenceRow(
            long id,
            long userId,
            NotificationCategory category,
            boolean inboxEnabled,
            boolean websocketEnabled,
            boolean browserPopupEnabled,
            boolean emailEnabled,
            NotificationPriority emailUrgencyThreshold,
            String quietHoursJson,
            Instant updatedAt
    ) {
    }
}
