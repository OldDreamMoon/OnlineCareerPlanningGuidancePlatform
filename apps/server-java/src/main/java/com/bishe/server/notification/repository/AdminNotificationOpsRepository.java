package com.bishe.server.notification.repository;

import com.bishe.server.notification.model.NotificationChannel;
import com.bishe.server.notification.model.NotificationDispatchStatus;
import com.bishe.server.notification.model.NotificationPriority;
import com.bishe.server.notification.repository.jpa.NotificationAdminOpsJpaRepository;
import com.bishe.server.notification.repository.jpa.NotificationDispatchJobAdminJpaRepository;
import com.bishe.server.notification.repository.jpa.entity.NotificationEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理员通知运营台聚合查询。
 */
@Repository
public class AdminNotificationOpsRepository {

    private final NotificationAdminOpsJpaRepository notificationAdminOpsJpaRepository;
    private final NotificationDispatchJobAdminJpaRepository notificationDispatchJobAdminJpaRepository;

    public AdminNotificationOpsRepository(
            NotificationAdminOpsJpaRepository notificationAdminOpsJpaRepository,
            NotificationDispatchJobAdminJpaRepository notificationDispatchJobAdminJpaRepository
    ) {
        this.notificationAdminOpsJpaRepository = notificationAdminOpsJpaRepository;
        this.notificationDispatchJobAdminJpaRepository = notificationDispatchJobAdminJpaRepository;
    }

    public OverviewStatsRow fetchOverviewStats() {
        Map<NotificationDispatchStatus, Long> countsByStatus = notificationDispatchJobAdminJpaRepository.countGroupedByStatus()
                .stream()
                .collect(Collectors.toMap(
                        NotificationDispatchJobAdminJpaRepository.StatusCountView::getStatus,
                        NotificationDispatchJobAdminJpaRepository.StatusCountView::getTotal,
                        AdminNotificationOpsRepository::sumLongs
                ));
        return new OverviewStatsRow(
                notificationAdminOpsJpaRepository.countDistinctAnnouncementEvents(),
                notificationAdminOpsJpaRepository.countDistinctAnnouncementEventsSince(defaultRecentSince()),
                countsByStatus.getOrDefault(NotificationDispatchStatus.PENDING, 0L),
                countsByStatus.getOrDefault(NotificationDispatchStatus.RETRY_WAIT, 0L),
                countsByStatus.getOrDefault(NotificationDispatchStatus.DEAD, 0L),
                notificationAdminOpsJpaRepository.findLatestAnnouncementCreatedAt()
        );
    }

    public List<AnnouncementRow> findRecentAnnouncements(int limit) {
        List<NotificationAdminOpsJpaRepository.AnnouncementEventRef> eventRefs = notificationAdminOpsJpaRepository.findRecentAnnouncementEventRefs(
                PageRequest.of(0, Math.max(limit, 1))
        );
        if (eventRefs.isEmpty()) {
            return List.of();
        }
        List<String> eventIds = eventRefs.stream()
                .map(NotificationAdminOpsJpaRepository.AnnouncementEventRef::getEventId)
                .filter(Objects::nonNull)
                .toList();
        Map<String, NotificationEntity> latestAnnouncementByEventId = notificationAdminOpsJpaRepository.findLatestAnnouncementsByEventIds(eventIds)
                .stream()
                .collect(Collectors.toMap(NotificationEntity::getEventId, Function.identity(), (left, right) -> left));
        Map<String, Long> notificationCountByEventId = notificationAdminOpsJpaRepository.countAnnouncementNotificationsByEventIds(eventIds)
                .stream()
                .collect(Collectors.toMap(
                        NotificationAdminOpsJpaRepository.AnnouncementEventCount::getEventId,
                        NotificationAdminOpsJpaRepository.AnnouncementEventCount::getTotal,
                        AdminNotificationOpsRepository::sumLongs
                ));
        Set<String> emailDispatchEventIds = Set.copyOf(
                notificationDispatchJobAdminJpaRepository.findDistinctEventIdsByChannelAndEventIdIn(NotificationChannel.EMAIL, eventIds)
        );
        return eventRefs.stream()
                .map(ref -> toAnnouncementRow(
                        ref,
                        latestAnnouncementByEventId.get(ref.getEventId()),
                        notificationCountByEventId.getOrDefault(ref.getEventId(), 0L),
                        emailDispatchEventIds.contains(ref.getEventId())
                ))
                .filter(Objects::nonNull)
                .toList();
    }

    public List<ChannelStatusCountRow> findChannelStatusCountsSince(Instant since) {
        return notificationDispatchJobAdminJpaRepository.countGroupedByChannelAndStatusSince(
                        since == null ? defaultRecentSince() : since
                ).stream()
                .map(row -> new ChannelStatusCountRow(row.getChannel(), row.getStatus(), row.getTotal()))
                .toList();
    }

    private static Long sumLongs(Long left, Long right) {
        return Long.valueOf((left == null ? 0L : left) + (right == null ? 0L : right));
    }

    public List<DispatchJobAdminRow> findDispatchJobs(
            NotificationDispatchStatus status,
            NotificationChannel channel,
            int page,
            int size
    ) {
        return notificationDispatchJobAdminJpaRepository.findAdminRows(
                        status,
                        channel,
                        PageRequest.of(Math.max(page - 1, 0), Math.max(size, 1))
                ).stream()
                .map(this::toDispatchJobAdminRow)
                .toList();
    }

    public long countDispatchJobs(NotificationDispatchStatus status, NotificationChannel channel) {
        return notificationDispatchJobAdminJpaRepository.countAdminRows(status, channel);
    }

    private AnnouncementRow toAnnouncementRow(
            NotificationAdminOpsJpaRepository.AnnouncementEventRef eventRef,
            NotificationEntity entity,
            long notificationCount,
            boolean hasEmailDispatch
    ) {
        if (entity == null) {
            return null;
        }
        return new AnnouncementRow(
                entity.getEventId(),
                entity.getTitle(),
                entity.getContent(),
                entity.getPriority() == null ? NotificationPriority.NORMAL : entity.getPriority(),
                entity.getRefType(),
                entity.getRefId(),
                entity.getActionCode(),
                entity.getPayloadJson(),
                hasEmailDispatch,
                notificationCount,
                eventRef.getCreatedAt()
        );
    }

    private DispatchJobAdminRow toDispatchJobAdminRow(NotificationDispatchJobAdminJpaRepository.DispatchJobAdminView row) {
        return new DispatchJobAdminRow(
                row.getId() == null ? 0L : row.getId(),
                row.getJobId(),
                row.getNotificationId() == null ? 0L : row.getNotificationId(),
                row.getEventId(),
                row.getUserId() == null ? 0L : row.getUserId(),
                row.getChannel(),
                row.getStatus(),
                row.getAttemptCount() == null ? 0 : row.getAttemptCount(),
                row.getMaxAttempts() == null ? 0 : row.getMaxAttempts(),
                row.getNextRunAt(),
                row.getSentAt(),
                row.getAckedAt(),
                row.getFailedAt(),
                row.getErrorCode(),
                row.getErrorMessage(),
                row.getTitle(),
                row.getContent(),
                row.getType(),
                row.getCreatedAt(),
                row.getUpdatedAt()
        );
    }

    private Instant defaultRecentSince() {
        return Instant.now().minusSeconds(7L * 24 * 60 * 60);
    }

    public record OverviewStatsRow(
            long announcementCount,
            long announcementsLast7Days,
            long pendingJobCount,
            long retryJobCount,
            long deadJobCount,
            Instant lastAnnouncementAt
    ) {
    }

    public record AnnouncementRow(
            String eventId,
            String title,
            String content,
            NotificationPriority priority,
            String refType,
            String refId,
            String actionCode,
            String payloadJson,
            boolean hasEmailDispatch,
            long notificationCount,
            Instant createdAt
    ) {
    }

    public record ChannelStatusCountRow(
            NotificationChannel channel,
            NotificationDispatchStatus status,
            long total
    ) {
    }

    public record DispatchJobAdminRow(
            long id,
            String jobId,
            long notificationId,
            String eventId,
            long userId,
            NotificationChannel channel,
            NotificationDispatchStatus status,
            int attemptCount,
            int maxAttempts,
            Instant nextRunAt,
            Instant sentAt,
            Instant ackedAt,
            Instant failedAt,
            String errorCode,
            String errorMessage,
            String title,
            String content,
            String type,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
