package com.bishe.server.notification.repository;

import com.bishe.server.notification.model.NotificationCategory;
import com.bishe.server.notification.model.NotificationPriority;
import com.bishe.server.notification.repository.jpa.NotificationJpaRepository;
import com.bishe.server.notification.repository.jpa.entity.NotificationEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 通知收件箱仓储。
 */
@Repository
public class NotificationRepository {

    private final NotificationJpaRepository notificationJpaRepository;

    public NotificationRepository(NotificationJpaRepository notificationJpaRepository) {
        this.notificationJpaRepository = notificationJpaRepository;
    }

    public long createNotification(NotificationInsertCommand command) {
        NotificationEntity entity = NotificationEntity.create(
                command.userId(),
                command.type(),
                command.category(),
                command.title(),
                command.content(),
                command.refType(),
                command.refId(),
                command.actionCode(),
                command.priority(),
                command.eventId(),
                command.payloadJson()
        );
        notificationJpaRepository.saveAndFlush(entity);
        return entity.getId() == null ? 0L : entity.getId();
    }

    public List<NotificationRow> findNotifications(long userId, boolean unreadOnly, NotificationCategory category, int page, int size) {
        Specification<NotificationEntity> specification = buildInboxSpecification(userId, unreadOnly, category);
        PageRequest pageRequest = PageRequest.of(
                Math.max(page - 1, 0),
                Math.max(size, 1),
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );
        return notificationJpaRepository.findAll(specification, pageRequest).getContent().stream()
                .map(this::toRow)
                .toList();
    }

    public List<NotificationRow> findNotificationsAfterId(long userId, long afterId, int limit) {
        return notificationJpaRepository.findByUserIdAndArchivedAtIsNullAndIdGreaterThanOrderByIdAsc(
                        userId,
                        Math.max(afterId, 0L),
                        PageRequest.of(0, Math.max(limit, 1))
                ).stream()
                .map(this::toRow)
                .toList();
    }

    public long countNotifications(long userId, boolean unreadOnly, NotificationCategory category) {
        return notificationJpaRepository.count(buildInboxSpecification(userId, unreadOnly, category));
    }

    public long countUnreadActionable(long userId, NotificationCategory category) {
        return notificationJpaRepository.count(buildActionableUnreadSpecification(userId, category));
    }

    public Map<NotificationCategory, Long> countUnreadActionableGrouped(long userId) {
        Map<NotificationCategory, Long> counts = new EnumMap<>(NotificationCategory.class);
        for (NotificationJpaRepository.ActionableCountProjection row : notificationJpaRepository.countUnreadActionableGrouped(userId)) {
            counts.put(row.getCategory(), Math.max(row.getTotal(), 0L));
        }
        return counts;
    }

    public long countUnread(long userId) {
        return notificationJpaRepository.countByUserIdAndArchivedAtIsNullAndReadFalse(userId);
    }

    public Optional<NotificationRow> findById(long notificationId, long userId) {
        return notificationJpaRepository.findByIdAndUserIdAndArchivedAtIsNull(notificationId, userId)
                .map(this::toRow);
    }

    public Long findLatestNotificationId(long userId) {
        return notificationJpaRepository.findTopByUserIdAndArchivedAtIsNullOrderByIdDesc(userId)
                .map(NotificationEntity::getId)
                .orElse(null);
    }

    public void markRead(long notificationId, long userId) {
        notificationJpaRepository.markRead(notificationId, userId, Instant.now());
    }

    public long markAllRead(long userId) {
        return notificationJpaRepository.markAllRead(userId, Instant.now());
    }

    private NotificationRow toRow(NotificationEntity entity) {
        return new NotificationRow(
                entity.getId(),
                entity.getUserId(),
                entity.getType(),
                entity.getCategory(),
                entity.getTitle(),
                entity.getContent(),
                entity.getRefType(),
                entity.getRefId(),
                entity.getActionCode(),
                entity.getPriority(),
                entity.getEventId(),
                entity.getPayloadJson(),
                entity.isRead(),
                entity.getCreatedAt(),
                entity.getReadAt(),
                entity.getArchivedAt()
        );
    }

    private Specification<NotificationEntity> buildInboxSpecification(long userId, boolean unreadOnly, NotificationCategory category) {
        return Specification.where(hasUserId(userId))
                .and(hasArchivedAtNull())
                .and(hasUnreadWhenRequired(unreadOnly))
                .and(hasCategory(category));
    }

    private Specification<NotificationEntity> buildActionableUnreadSpecification(long userId, NotificationCategory category) {
        return Specification.where(hasUserId(userId))
                .and(hasArchivedAtNull())
                .and(hasUnreadWhenRequired(true))
                .and(hasActionableCode())
                .and(hasCategory(category));
    }

    private Specification<NotificationEntity> hasUserId(long userId) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("userId"), userId);
    }

    private Specification<NotificationEntity> hasArchivedAtNull() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.isNull(root.get("archivedAt"));
    }

    private Specification<NotificationEntity> hasUnreadWhenRequired(boolean unreadOnly) {
        if (!unreadOnly) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.isFalse(root.get("read"));
    }

    private Specification<NotificationEntity> hasCategory(NotificationCategory category) {
        if (category == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("category"), category);
    }

    private Specification<NotificationEntity> hasActionableCode() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.and(
                criteriaBuilder.isNotNull(root.get("actionCode")),
                criteriaBuilder.notEqual(root.get("actionCode"), ""),
                criteriaBuilder.notEqual(root.get("actionCode"), "VIEW_NOTIFICATION_CENTER")
        );
    }

    /**
     * 收件箱写入命令。
     */
    public record NotificationInsertCommand(
            long userId,
            String type,
            NotificationCategory category,
            String title,
            String content,
            String refType,
            String refId,
            String actionCode,
            NotificationPriority priority,
            String eventId,
            String payloadJson
    ) {
    }

    /**
     * 通知行。
     */
    public record NotificationRow(
            long id,
            long userId,
            String type,
            NotificationCategory category,
            String title,
            String content,
            String refType,
            String refId,
            String actionCode,
            NotificationPriority priority,
            String eventId,
            String payloadJson,
            boolean read,
            Instant createdAt,
            Instant readAt,
            Instant archivedAt
    ) {
    }

    /**
     * 可操作未读数分组。
     */
    public record ActionableCountRow(NotificationCategory category, long total) {
    }
}
