package com.bishe.server.notification.repository;

import com.bishe.server.notification.model.NotificationCategory;
import com.bishe.server.notification.model.NotificationPriority;
import com.bishe.server.notification.repository.jpa.NotificationEventJpaRepository;
import com.bishe.server.notification.repository.jpa.entity.NotificationEventEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * 标准化通知事件仓储。
 */
@Repository
public class NotificationEventRepository {

    private final NotificationEventJpaRepository notificationEventJpaRepository;

    public NotificationEventRepository(NotificationEventJpaRepository notificationEventJpaRepository) {
        this.notificationEventJpaRepository = notificationEventJpaRepository;
    }

    public Optional<NotificationEventRow> findByDedupeKey(String dedupeKey) {
        if (dedupeKey == null || dedupeKey.isBlank()) {
            return Optional.empty();
        }
        return notificationEventJpaRepository.findByDedupeKey(dedupeKey.trim()).map(this::toRow);
    }

    public long createEvent(NotificationEventInsertCommand command) {
        NotificationEventEntity entity = NotificationEventEntity.create(
                command.eventId(),
                command.type(),
                command.category(),
                command.sourceType(),
                command.sourceId(),
                command.actorUserId(),
                command.priority(),
                command.dedupeKey(),
                command.payloadJson(),
                command.occurredAt()
        );
        notificationEventJpaRepository.saveAndFlush(entity);
        return entity.getId() == null ? 0L : entity.getId();
    }

    private NotificationEventRow toRow(NotificationEventEntity entity) {
        return new NotificationEventRow(
                entity.getId(),
                entity.getEventId(),
                entity.getType(),
                entity.getCategory(),
                entity.getSourceType(),
                entity.getSourceId(),
                entity.getActorUserId(),
                entity.getPriority(),
                entity.getDedupeKey(),
                entity.getPayloadJson(),
                entity.getOccurredAt(),
                entity.getCreatedAt()
        );
    }

    /**
     * 事件写入命令。
     */
    public record NotificationEventInsertCommand(
            String eventId,
            String type,
            NotificationCategory category,
            String sourceType,
            String sourceId,
            Long actorUserId,
            NotificationPriority priority,
            String dedupeKey,
            String payloadJson,
            Instant occurredAt
    ) {
    }

    /**
     * 标准化事件记录。
     */
    public record NotificationEventRow(
            long id,
            String eventId,
            String type,
            NotificationCategory category,
            String sourceType,
            String sourceId,
            Long actorUserId,
            NotificationPriority priority,
            String dedupeKey,
            String payloadJson,
            Instant occurredAt,
            Instant createdAt
    ) {
    }
}
