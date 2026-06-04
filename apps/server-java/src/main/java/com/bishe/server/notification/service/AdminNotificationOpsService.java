package com.bishe.server.notification.service;

import com.bishe.server.common.TimePayloads;
import com.bishe.server.common.exception.ApiException;
import com.bishe.server.notification.NotificationProperties;
import com.bishe.server.notification.model.NotificationChannel;
import com.bishe.server.notification.model.NotificationDispatchStatus;
import com.bishe.server.notification.repository.AdminNotificationOpsRepository;
import com.bishe.server.notification.repository.NotificationDispatchJobRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员通知运营台服务。
 */
@Service
public class AdminNotificationOpsService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AdminNotificationOpsRepository repository;
    private final NotificationDispatchJobRepository dispatchJobRepository;
    private final NotificationDispatchService dispatchService;
    private final NotificationDispatchCoordinationService coordinationService;
    private final NotificationProperties notificationProperties;
    private final ObjectMapper objectMapper;

    public AdminNotificationOpsService(
            AdminNotificationOpsRepository repository,
            NotificationDispatchJobRepository dispatchJobRepository,
            NotificationDispatchService dispatchService,
            NotificationDispatchCoordinationService coordinationService,
            NotificationProperties notificationProperties,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.dispatchJobRepository = dispatchJobRepository;
        this.dispatchService = dispatchService;
        this.coordinationService = coordinationService;
        this.notificationProperties = notificationProperties;
        this.objectMapper = objectMapper;
    }

    public NotificationOpsOverviewPayload getOverview() {
        AdminNotificationOpsRepository.OverviewStatsRow stats = repository.fetchOverviewStats();
        List<AdminNotificationOpsRepository.ChannelStatusCountRow> counts = repository.findChannelStatusCountsSince(Instant.now().minusSeconds(7 * 24 * 60 * 60L));
        Map<NotificationChannel, ChannelOpsSummaryItem> summaryByChannel = new LinkedHashMap<>();
        for (NotificationChannel channel : NotificationChannel.values()) {
            summaryByChannel.put(channel, new ChannelOpsSummaryItem(channel.name(), 0L, 0L, 0L, 0L, 0L, 0L));
        }
        for (AdminNotificationOpsRepository.ChannelStatusCountRow row : counts) {
            ChannelOpsSummaryItem current = summaryByChannel.get(row.channel());
            if (current == null) {
                continue;
            }
            summaryByChannel.put(row.channel(), current.withStatus(row.status(), row.total()));
        }
        String emailSender = notificationProperties.getEmail().getResendFromEmail();
        return new NotificationOpsOverviewPayload(
                stats.announcementCount(),
                stats.announcementsLast7Days(),
                stats.pendingJobCount(),
                stats.retryJobCount(),
                stats.deadJobCount(),
                notificationProperties.getEmail().isReady(),
                emailSender == null || emailSender.isBlank() ? null : emailSender.trim(),
                notificationProperties.getWebsocket().getTicketTtlSeconds() > 0,
                TimePayloads.toEpochMillis(stats.lastAnnouncementAt()),
                new ArrayList<>(summaryByChannel.values())
        );
    }

    public AnnouncementListPayload listAnnouncements(Integer limit) {
        int safeLimit = Math.min(Math.max(limit == null ? 8 : limit, 1), 20);
        List<AnnouncementItem> records = repository.findRecentAnnouncements(safeLimit).stream()
                .map(this::toAnnouncementItem)
                .toList();
        return new AnnouncementListPayload(records);
    }

    public DispatchJobListPayload listDispatchJobs(Integer page, Integer size, String status, String channel) {
        int safePage = Math.max(page == null ? 1 : page, 1);
        int safeSize = Math.min(Math.max(size == null ? 10 : size, 1), 50);
        NotificationDispatchStatus normalizedStatus = normalizeStatus(status);
        NotificationChannel normalizedChannel = normalizeChannel(channel);
        List<DispatchJobItem> records = repository.findDispatchJobs(normalizedStatus, normalizedChannel, safePage, safeSize).stream()
                .map(this::toDispatchJobItem)
                .toList();
        long total = repository.countDispatchJobs(normalizedStatus, normalizedChannel);
        return new DispatchJobListPayload(records, total, safePage, safeSize);
    }

    public DispatchJobCleanupResponse purgeTerminalDispatchJobs(Integer olderThanHours) {
        int safeOlderThanHours = Math.max(olderThanHours == null ? 0 : olderThanHours, 0);
        Instant deletedBefore = Instant.now().minus(Duration.ofHours(safeOlderThanHours));
        int batchSize = Math.max(notificationProperties.getDispatch().getRetention().getBatchSize(), 1);
        long deletedCount = 0L;
        while (true) {
            NotificationDispatchService.DispatchRetentionPurgeResult result = dispatchService.purgeTerminalJobsBefore(
                    deletedBefore,
                    batchSize
            );
            deletedCount += result.deletedJobCount();
            if (result.deletedJobCount() < batchSize) {
                break;
            }
        }
        if (deletedCount <= 0) {
            return new DispatchJobCleanupResponse(0L, TimePayloads.toEpochMillis(deletedBefore));
        }
        return new DispatchJobCleanupResponse(deletedCount, TimePayloads.toEpochMillis(deletedBefore));
    }

    @Transactional
    public DispatchJobRetryResponse retryDispatchJob(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            throw new ApiException("BIZ-1001", "jobId invalid", HttpStatus.BAD_REQUEST);
        }
        NotificationDispatchJobRepository.DispatchJobRow current = dispatchJobRepository.findByJobId(jobId.trim())
                .orElseThrow(() -> new ApiException("BIZ-1002", "dispatch job not found", HttpStatus.NOT_FOUND));
        if (current.status() != NotificationDispatchStatus.DEAD && current.status() != NotificationDispatchStatus.SKIPPED) {
            throw new ApiException("BIZ-1001", "dispatch job status not retryable", HttpStatus.BAD_REQUEST);
        }
        Instant nextRunAt = Instant.now();
        dispatchJobRepository.requeueForAdmin(
                current.jobId(),
                Timestamp.from(nextRunAt),
                Math.max(notificationProperties.getWebsocket().getDispatchMaxAttempts(), 1)
        );
        coordinationService.scheduleNow(current.id(), nextRunAt);
        NotificationDispatchJobRepository.DispatchJobRow refreshed = dispatchJobRepository.findByJobId(jobId.trim())
                .orElseThrow(() -> new ApiException("BIZ-1002", "dispatch job not found", HttpStatus.NOT_FOUND));
        return new DispatchJobRetryResponse(
                refreshed.jobId(),
                refreshed.status().name(),
                TimePayloads.toEpochMillis(nextRunAt)
        );
    }

    private AnnouncementItem toAnnouncementItem(AdminNotificationOpsRepository.AnnouncementRow row) {
        Map<String, Object> payload = readPayload(row.payloadJson());
        boolean emailRequested = readBoolean(payload.get("emailRequested")).orElse(row.hasEmailDispatch());
        List<String> deliveryChannels = readDeliveryChannels(
                payload.get("deliveryChannels"),
                emailRequested || row.hasEmailDispatch()
        );
        return new AnnouncementItem(
                row.eventId(),
                row.title(),
                row.content(),
                row.priority().name(),
                readStringList(payload.get("targetRoles")),
                deliveryChannels,
                row.notificationCount(),
                row.refType(),
                row.refId(),
                row.actionCode(),
                TimePayloads.toEpochMillis(row.createdAt())
        );
    }

    private DispatchJobItem toDispatchJobItem(AdminNotificationOpsRepository.DispatchJobAdminRow row) {
        return new DispatchJobItem(
                row.jobId(),
                row.notificationId(),
                row.eventId(),
                row.userId(),
                row.channel().name(),
                row.status().name(),
                row.attemptCount(),
                row.maxAttempts(),
                TimePayloads.toEpochMillis(row.nextRunAt()),
                TimePayloads.toEpochMillis(row.sentAt()),
                TimePayloads.toEpochMillis(row.ackedAt()),
                TimePayloads.toEpochMillis(row.failedAt()),
                row.errorCode(),
                row.errorMessage(),
                row.title(),
                row.content(),
                row.type(),
                TimePayloads.toEpochMillis(row.createdAt()),
                TimePayloads.toEpochMillis(row.updatedAt())
        );
    }

    private NotificationDispatchStatus normalizeStatus(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return NotificationDispatchStatus.from(rawValue);
        } catch (IllegalArgumentException exception) {
            throw new ApiException("BIZ-1001", "dispatch status invalid", HttpStatus.BAD_REQUEST);
        }
    }

    private NotificationChannel normalizeChannel(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return NotificationChannel.from(rawValue);
        } catch (IllegalArgumentException exception) {
            throw new ApiException("BIZ-1001", "dispatch channel invalid", HttpStatus.BAD_REQUEST);
        }
    }

    private Map<String, Object> readPayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payloadJson, MAP_TYPE);
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private List<String> readStringList(Object rawValue) {
        if (!(rawValue instanceof List<?> listValue)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object item : listValue) {
            if (item == null) {
                continue;
            }
            String normalized = String.valueOf(item).trim();
            if (!normalized.isBlank()) {
                values.add(normalized);
            }
        }
        return values;
    }

    private java.util.Optional<Boolean> readBoolean(Object rawValue) {
        if (rawValue instanceof Boolean booleanValue) {
            return java.util.Optional.of(booleanValue);
        }
        if (rawValue instanceof String stringValue) {
            String normalized = stringValue.trim();
            if ("true".equalsIgnoreCase(normalized)) {
                return java.util.Optional.of(Boolean.TRUE);
            }
            if ("false".equalsIgnoreCase(normalized)) {
                return java.util.Optional.of(Boolean.FALSE);
            }
        }
        return java.util.Optional.empty();
    }

    private List<String> readDeliveryChannels(Object rawValue, boolean emailRequested) {
        List<String> channels = new ArrayList<>();
        channels.add("IN_APP");
        if (rawValue instanceof List<?> listValue) {
            for (Object item : listValue) {
                if (item == null) {
                    continue;
                }
                String normalized = String.valueOf(item).trim().toUpperCase();
                if ("IN_APP".equals(normalized)) {
                    continue;
                }
                if ("EMAIL".equals(normalized) && !channels.contains("EMAIL")) {
                    channels.add("EMAIL");
                }
            }
            return List.copyOf(channels);
        }
        if (emailRequested) {
            channels.add("EMAIL");
        }
        return List.copyOf(channels);
    }

    public record NotificationOpsOverviewPayload(
            long announcementCount,
            long announcementsLast7Days,
            long pendingJobCount,
            long retryJobCount,
            long deadJobCount,
            boolean emailReady,
            String emailSender,
            boolean websocketReady,
            Long lastAnnouncementAt,
            List<ChannelOpsSummaryItem> channelStats
    ) {
    }

    public record ChannelOpsSummaryItem(
            String channel,
            long total,
            long sent,
            long acked,
            long retryWait,
            long dead,
            long skipped
    ) {
        ChannelOpsSummaryItem withStatus(NotificationDispatchStatus status, long count) {
            long safeCount = Math.max(count, 0L);
            return switch (status) {
                case SENT -> new ChannelOpsSummaryItem(channel, total + safeCount, sent + safeCount, acked, retryWait, dead, skipped);
                case ACKED -> new ChannelOpsSummaryItem(channel, total + safeCount, sent, acked + safeCount, retryWait, dead, skipped);
                case RETRY_WAIT -> new ChannelOpsSummaryItem(channel, total + safeCount, sent, acked, retryWait + safeCount, dead, skipped);
                case DEAD -> new ChannelOpsSummaryItem(channel, total + safeCount, sent, acked, retryWait, dead + safeCount, skipped);
                case SKIPPED -> new ChannelOpsSummaryItem(channel, total + safeCount, sent, acked, retryWait, dead, skipped + safeCount);
                default -> new ChannelOpsSummaryItem(channel, total + safeCount, sent, acked, retryWait, dead, skipped);
            };
        }
    }

    public record AnnouncementListPayload(List<AnnouncementItem> records) {
    }

    public record AnnouncementItem(
            String eventId,
            String title,
            String content,
            String priority,
            List<String> targetRoles,
            List<String> deliveryChannels,
            long notificationCount,
            String refType,
            String refId,
            String actionCode,
            Long createdAt
    ) {
    }

    public record DispatchJobListPayload(
            List<DispatchJobItem> records,
            long total,
            int page,
            int size
    ) {
    }

    public record DispatchJobItem(
            String jobId,
            long notificationId,
            String eventId,
            long userId,
            String channel,
            String status,
            int attemptCount,
            int maxAttempts,
            Long nextRunAt,
            Long sentAt,
            Long ackedAt,
            Long failedAt,
            String errorCode,
            String errorMessage,
            String title,
            String content,
            String type,
            Long createdAt,
            Long updatedAt
    ) {
    }

    public record DispatchJobRetryResponse(
            String jobId,
            String status,
            Long nextRunAt
    ) {
    }

    public record DispatchJobCleanupResponse(
            long deletedCount,
            Long deletedBeforeAt
    ) {
    }
}
