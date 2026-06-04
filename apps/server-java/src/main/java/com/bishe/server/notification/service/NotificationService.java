package com.bishe.server.notification.service;

import com.bishe.server.auth.model.UserRole;
import com.bishe.server.common.TimePayloads;
import com.bishe.server.common.exception.ApiException;
import com.bishe.server.notification.dto.NotificationListResponse;
import com.bishe.server.notification.dto.NotificationPreferenceUpdateRequest;
import com.bishe.server.notification.dto.NotificationPreferencesResponse;
import com.bishe.server.notification.dto.NotificationReadAllResponse;
import com.bishe.server.notification.dto.NotificationReadResponse;
import com.bishe.server.notification.dto.NotificationSyncResponse;
import com.bishe.server.notification.dto.NotificationUnreadCountResponse;
import com.bishe.server.notification.dto.NotificationWebSocketTicketResponse;
import com.bishe.server.notification.model.NotificationCategory;
import com.bishe.server.notification.repository.NotificationRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通知统一门面：兼容旧调用，同时对外暴露收件箱、偏好与 WS 能力。
 */
@Service
public class NotificationService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final NotificationRepository notificationRepository;
    private final NotificationCountCacheService notificationCountCacheService;
    private final PlatformNotificationPublishService platformNotificationPublishService;
    private final NotificationPreferenceService notificationPreferenceService;
    private final NotificationWebSocketTicketService notificationWebSocketTicketService;
    private final NotificationDispatchService notificationDispatchService;
    private final ObjectMapper objectMapper;

    public NotificationService(
            NotificationRepository notificationRepository,
            NotificationCountCacheService notificationCountCacheService,
            PlatformNotificationPublishService platformNotificationPublishService,
            NotificationPreferenceService notificationPreferenceService,
            NotificationWebSocketTicketService notificationWebSocketTicketService,
            NotificationDispatchService notificationDispatchService,
            ObjectMapper objectMapper
    ) {
        this.notificationRepository = notificationRepository;
        this.notificationCountCacheService = notificationCountCacheService;
        this.platformNotificationPublishService = platformNotificationPublishService;
        this.notificationPreferenceService = notificationPreferenceService;
        this.notificationWebSocketTicketService = notificationWebSocketTicketService;
        this.notificationDispatchService = notificationDispatchService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void createNotification(long userId, String type, String content, String refId) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("legacyCompat", true);
        platformNotificationPublishService.publish(new PlatformNotificationPublishService.NotificationPublishCommand(
                type,
                null,
                deriveSourceType(type),
                refId,
                null,
                List.of(userId),
                null,
                null,
                content,
                null,
                refId,
                null,
                payload,
                null,
                Instant.now()
        ));
    }

    @Transactional
    public PlatformNotificationPublishService.PublishResult publish(PlatformNotificationPublishService.NotificationPublishCommand command) {
        return platformNotificationPublishService.publish(command);
    }

    public NotificationListResponse listNotifications(long userId, int page, int size, boolean unreadOnly, String categoryCode) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        NotificationCategory category = resolveCategoryFilter(categoryCode);
        NotificationCountCacheService.NotificationCountSnapshot counts = loadNotificationCounts(userId);
        long unreadCount = counts.unreadCount();
        long actionableCount = counts.actionableCount(category);
        long total = notificationRepository.countNotifications(userId, unreadOnly, category);
        return new NotificationListResponse(
                unreadCount,
                actionableCount,
                notificationRepository.findNotifications(userId, unreadOnly, category, safePage, safeSize)
                        .stream()
                        .map(this::toNotificationItem)
                        .toList(),
                total,
                safePage,
                safeSize
        );
    }

    public NotificationUnreadCountResponse getUnreadCount(long userId) {
        return new NotificationUnreadCountResponse(loadNotificationCounts(userId).unreadCount());
    }

    public NotificationSyncResponse syncNotifications(long userId, Long afterId, Integer limit) {
        int safeLimit = Math.min(Math.max(limit == null ? 20 : limit, 1), 50);
        long normalizedAfterId = afterId == null ? 0L : Math.max(afterId, 0L);
        NotificationCountCacheService.NotificationCountSnapshot counts = loadNotificationCounts(userId);
        Long latestNotificationId = notificationRepository.findLatestNotificationId(userId);

        List<NotificationRepository.NotificationRow> rows;
        boolean hasMore = false;
        if (normalizedAfterId > 0) {
            List<NotificationRepository.NotificationRow> batch = notificationRepository.findNotificationsAfterId(userId, normalizedAfterId, safeLimit + 1);
            hasMore = batch.size() > safeLimit;
            rows = hasMore ? batch.subList(0, safeLimit) : batch;
        } else {
            List<NotificationRepository.NotificationRow> batch = notificationRepository.findNotifications(userId, false, null, 1, safeLimit + 1);
            rows = batch.size() > safeLimit ? batch.subList(0, safeLimit) : batch;
        }

        return new NotificationSyncResponse(
                counts.unreadCount(),
                counts.actionableCount(null),
                rows.stream().map(this::toNotificationItem).toList(),
                hasMore,
                latestNotificationId == null || latestNotificationId <= 0 ? null : latestNotificationId,
                safeLimit
        );
    }

    @Transactional
    public NotificationReadResponse markRead(long userId, long notificationId) {
        NotificationRepository.NotificationRow notification = notificationRepository.findById(notificationId, userId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "notification not found", HttpStatus.NOT_FOUND));
        if (!notification.read()) {
            notificationRepository.markRead(notificationId, userId);
            notificationCountCacheService.evictAfterCommit(userId);
            notification = notificationRepository.findById(notificationId, userId)
                    .orElseThrow(() -> new ApiException("BIZ-1002", "notification not found", HttpStatus.NOT_FOUND));
        }
        return new NotificationReadResponse(
                notification.id(),
                true,
                TimePayloads.toEpochMillis(notification.readAt() == null ? Instant.now() : notification.readAt())
        );
    }

    @Transactional
    public NotificationReadAllResponse markAllRead(long userId) {
        long updatedCount = notificationRepository.markAllRead(userId);
        notificationCountCacheService.evictAfterCommit(userId);
        return new NotificationReadAllResponse(updatedCount, 0L);
    }

    public NotificationPreferencesResponse listPreferences(long userId, UserRole role) {
        return notificationPreferenceService.listPreferences(userId, role);
    }

    @Transactional
    public NotificationPreferencesResponse.PreferenceItem updatePreference(long userId, UserRole role, NotificationPreferenceUpdateRequest request) {
        return notificationPreferenceService.updatePreference(userId, role, request);
    }

    public NotificationWebSocketTicketResponse issueWebSocketTicket(long userId, UserRole role) {
        if (role == null || role == UserRole.ADMIN) {
            throw new ApiException("BIZ-1001", "notification center is unavailable for current role", HttpStatus.FORBIDDEN);
        }
        NotificationWebSocketTicketService.TicketIssueResult ticket = notificationWebSocketTicketService.issueTicket(userId);
        return new NotificationWebSocketTicketResponse(ticket.ticket(), "/ws/notifications", TimePayloads.toEpochMillis(ticket.expiresAt()));
    }

    @Transactional
    public void acknowledgeWebSocketDelivery(long userId, String jobId) {
        notificationDispatchService.acknowledgeWebSocketJob(jobId, userId);
    }

    private NotificationListResponse.NotificationItem toNotificationItem(NotificationRepository.NotificationRow item) {
        return new NotificationListResponse.NotificationItem(
                item.id(),
                item.type(),
                item.category().name(),
                item.title(),
                item.content(),
                item.read(),
                item.refType(),
                item.refId(),
                item.actionCode(),
                isActionable(item.actionCode()),
                sanitizePayloadForInbox(item.category().name(), readPayload(item.payloadJson())),
                TimePayloads.toEpochMillis(item.createdAt()),
                TimePayloads.toEpochMillis(item.readAt())
        );
    }

    private Map<String, Object> readPayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payloadJson, MAP_TYPE);
        } catch (Exception ex) {
            return Map.of("raw", payloadJson);
        }
    }

    private Map<String, Object> sanitizePayloadForInbox(String category, Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return Map.of();
        }
        if (!"AI_TASK".equalsIgnoreCase(category)) {
            return payload;
        }

        LinkedHashMap<String, Object> sanitized = new LinkedHashMap<>();
        putIfPresent(sanitized, "sessionId", payload.get("sessionId"));
        putIfPresent(sanitized, "taskId", payload.get("taskId"));
        putIfPresent(sanitized, "taskType", payload.get("taskType"));
        putIfPresent(sanitized, "status", payload.get("status"));
        putIfPresent(sanitized, "sceneCode", payload.get("sceneCode"));
        putIfPresent(sanitized, "linkedRecordId", payload.get("linkedRecordId"));
        putIfPresent(sanitized, "resultSummary", payload.get("resultSummary"));
        putIfPresent(sanitized, "errorCode", payload.get("errorCode"));
        putIfPresent(sanitized, "errorMessage", payload.get("errorMessage"));
        return sanitized.isEmpty() ? Map.of() : sanitized;
    }

    private void putIfPresent(LinkedHashMap<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        target.put(key, value);
    }

    private NotificationCategory resolveCategoryFilter(String rawValue) {
        if (rawValue == null || rawValue.isBlank() || "ALL".equalsIgnoreCase(rawValue.trim())) {
            return null;
        }
        try {
            return NotificationCategory.from(rawValue);
        } catch (IllegalArgumentException exception) {
            throw new ApiException("BIZ-1001", "unsupported notification category", HttpStatus.BAD_REQUEST);
        }
    }

    private boolean isActionable(String actionCode) {
        return actionCode != null
                && !actionCode.isBlank()
                && !"VIEW_NOTIFICATION_CENTER".equalsIgnoreCase(actionCode);
    }

    private String deriveSourceType(String type) {
        if (type == null || type.isBlank()) {
            return "NOTIFICATION_CENTER";
        }
        String normalized = type.trim();
        if (normalized.startsWith("AI_")) {
            return "AI_ASYNC_TASK";
        }
        if (normalized.startsWith("CONSULT_")) {
            return "CONSULT_ORDER";
        }
        if (normalized.startsWith("BOUNTY_")) {
            return "BOUNTY_TASK";
        }
        if (normalized.startsWith("CERTIFICATION_")) {
            return "CERTIFICATION";
        }
        return "NOTIFICATION_CENTER";
    }

    private NotificationCountCacheService.NotificationCountSnapshot loadNotificationCounts(long userId) {
        return notificationCountCacheService.getCounts(userId, () -> {
            Map<NotificationCategory, Long> actionableByCategory = notificationRepository.countUnreadActionableGrouped(userId);
            long totalActionable = actionableByCategory.values().stream()
                    .mapToLong(Long::longValue)
                    .sum();
            return new NotificationCountCacheService.NotificationCountSnapshot(
                    notificationRepository.countUnread(userId),
                    totalActionable,
                    actionableByCategory
            );
        });
    }

}
