package com.bishe.server.notification.service;

import com.bishe.server.common.TimePayloads;
import com.bishe.server.notification.NotificationProperties;
import com.bishe.server.notification.model.NotificationCategory;
import com.bishe.server.notification.model.NotificationChannel;
import com.bishe.server.notification.model.NotificationPriority;
import com.bishe.server.notification.repository.NotificationDispatchJobRepository;
import com.bishe.server.notification.repository.NotificationEventRepository;
import com.bishe.server.notification.repository.NotificationRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 平台通知统一发布入口：负责标准化事件、入箱与渠道任务入队。
 */
@Service
public class PlatformNotificationPublishService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final NotificationEventRepository notificationEventRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationDispatchJobRepository dispatchJobRepository;
    private final NotificationCountCacheService notificationCountCacheService;
    private final NotificationPreferenceService preferenceService;
    private final NotificationDescriptorRegistry descriptorRegistry;
    private final NotificationDispatchCoordinationService notificationDispatchCoordinationService;
    private final NotificationProperties notificationProperties;
    private final ObjectMapper objectMapper;

    public PlatformNotificationPublishService(
            NotificationEventRepository notificationEventRepository,
            NotificationRepository notificationRepository,
            NotificationDispatchJobRepository dispatchJobRepository,
            NotificationCountCacheService notificationCountCacheService,
            NotificationPreferenceService preferenceService,
            NotificationDescriptorRegistry descriptorRegistry,
            NotificationDispatchCoordinationService notificationDispatchCoordinationService,
            NotificationProperties notificationProperties,
            ObjectMapper objectMapper
    ) {
        this.notificationEventRepository = notificationEventRepository;
        this.notificationRepository = notificationRepository;
        this.dispatchJobRepository = dispatchJobRepository;
        this.notificationCountCacheService = notificationCountCacheService;
        this.preferenceService = preferenceService;
        this.descriptorRegistry = descriptorRegistry;
        this.notificationDispatchCoordinationService = notificationDispatchCoordinationService;
        this.notificationProperties = notificationProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PublishResult publish(NotificationPublishCommand command) {
        // descriptor 是默认语义，业务调用方传入的标题、分类和动作码拥有更高优先级。
        NotificationDescriptorRegistry.NotificationDescriptor descriptor = descriptorRegistry.resolve(command.eventType());
        NotificationCategory category = command.category() == null ? descriptor.category() : command.category();
        NotificationPriority priority = command.priority() == null ? descriptor.priority() : command.priority();
        Instant occurredAt = command.occurredAt() == null ? Instant.now() : command.occurredAt();
        // 收件人先去重并过滤非法 id，后面 inbox 和渠道任务都基于这份名单。
        List<Long> recipients = normalizeRecipients(command.recipientUserIds());
        if (recipients.isEmpty()) {
            return new PublishResult(null, List.of());
        }
        if (command.dedupeKey() != null && !command.dedupeKey().isBlank()
                && notificationEventRepository.findByDedupeKey(command.dedupeKey().trim()).isPresent()) {
            // dedupeKey 命中说明同一业务事件已发布过，直接返回空结果保持幂等。
            return new PublishResult(null, List.of());
        }

        // 先固化事件，再为每个收件人生成 inbox 记录和渠道派发任务。
        LinkedHashMap<String, Object> basePayload = new LinkedHashMap<>();
        if (command.payload() != null && !command.payload().isEmpty()) {
            basePayload.putAll(command.payload());
        }
        basePayload.putIfAbsent("eventType", command.eventType());
        basePayload.putIfAbsent("sourceType", command.sourceType());
        basePayload.putIfAbsent("sourceId", command.sourceId());

        String eventId = "ntfevt_" + UUID.randomUUID().toString().replace("-", "");
        notificationEventRepository.createEvent(new NotificationEventRepository.NotificationEventInsertCommand(
                eventId,
                command.eventType(),
                category,
                trimToNull(command.sourceType()),
                trimToNull(command.sourceId()),
                command.actorUserId(),
                priority,
                trimToNull(command.dedupeKey()),
                writeJson(basePayload),
                occurredAt
        ));

        List<Long> notificationIds = new ArrayList<>();
        for (Long recipientUserId : recipients) {
            NotificationPreferenceService.EffectivePreference preference = preferenceService.resolveEffectivePreference(recipientUserId, category);
            LinkedHashMap<String, Object> notificationPayload = new LinkedHashMap<>(basePayload);

            // 单条通知的文案和跳转字段按 command -> descriptor -> fallback 的顺序兜底。
            String title = firstNonBlank(command.title(), descriptor.title(), "平台通知");
            String content = firstNonBlank(command.content(), readString(basePayload.get("content")), title);
            String refType = firstNonBlank(command.refType(), descriptor.refType(), "NOTIFICATION_CENTER");
            String refId = firstNonBlank(command.refId(), command.sourceId(), readString(basePayload.get("refId")));
            String actionCode = firstNonBlank(command.actionCode(), descriptor.actionCode(), "VIEW_NOTIFICATION_CENTER");

            long notificationId = notificationRepository.createNotification(new NotificationRepository.NotificationInsertCommand(
                    recipientUserId,
                    command.eventType(),
                    category,
                    title,
                    content,
                    refType,
                    refId,
                    actionCode,
                    priority,
                    eventId,
                    writeJson(notificationPayload)
            ));
            // 未读数在事务提交后失效/刷新，避免读取到回滚后的虚假计数。
            notificationCountCacheService.recordCreatedAfterCommit(recipientUserId, category, actionCode);
            notificationIds.add(notificationId);

            NotificationSnapshot snapshot = new NotificationSnapshot(
                    notificationId,
                    command.eventType(),
                    category.name(),
                    title,
                    content,
                    false,
                    refType,
                    refId,
                    actionCode,
                    isActionable(actionCode),
                    notificationPayload,
                    TimePayloads.toEpochMillis(occurredAt),
                    null
            );

            if (preference.websocketEnabled()) {
                // WebSocket 不直接同步发送，先入派发任务表，再由协调器提交后调度。
                String jobId = "ntfjob_" + UUID.randomUUID().toString().replace("-", "");
                int websocketMaxAttempts = Math.max(notificationProperties.getWebsocket().getDispatchMaxAttempts(), 1);
                long dispatchJobId = dispatchJobRepository.createJob(new NotificationDispatchJobRepository.DispatchJobInsertCommand(
                        jobId,
                        notificationId,
                        eventId,
                        recipientUserId,
                        NotificationChannel.WEBSOCKET,
                        websocketMaxAttempts,
                        writeJson(Map.of(
                                "type", "NOTIFICATION_CREATED",
                                "jobId", jobId,
                                "browserPopupAllowed", preference.browserPopupEnabled(),
                                "notification", snapshot
                        ))
                ));
                notificationDispatchCoordinationService.schedulePendingAfterCommit(dispatchJobId, Instant.now());
            }

            Boolean emailRequested = command.emailRequested();
            boolean shouldEnqueueEmail;
            if (Boolean.FALSE.equals(emailRequested)) {
                shouldEnqueueEmail = false;
            } else if (Boolean.TRUE.equals(emailRequested)) {
                // 显式请求邮件提醒时，仍然尊重收件人的邮件总开关，但不再受优先级阈值约束。
                shouldEnqueueEmail = preference.emailEnabled()
                        && notificationProperties.getEmail().isReady();
            } else {
                // 默认邮件只发给达到用户紧急阈值的通知，普通站内提醒不会打扰邮箱。
                shouldEnqueueEmail = preference.emailEnabled()
                        && priority.meetsThreshold(preference.emailUrgencyThreshold())
                        && notificationProperties.getEmail().isReady();
            }
            if (shouldEnqueueEmail) {
                String jobId = "ntfjob_" + UUID.randomUUID().toString().replace("-", "");
                LinkedHashMap<String, Object> emailPayload = new LinkedHashMap<>();
                emailPayload.put("subject", title);
                emailPayload.put("title", title);
                emailPayload.put("content", content);
                emailPayload.put("category", category.name());
                emailPayload.put("type", command.eventType());
                emailPayload.put("priority", priority.name());
                if (refType != null && !refType.isBlank()) {
                    emailPayload.put("refType", refType);
                }
                if (refId != null && !refId.isBlank()) {
                    emailPayload.put("refId", refId);
                }
                if (actionCode != null && !actionCode.isBlank()) {
                    emailPayload.put("actionCode", actionCode);
                }
                if (notificationProperties.getAppBaseUrl() != null && !notificationProperties.getAppBaseUrl().isBlank()) {
                    emailPayload.put("appBaseUrl", notificationProperties.getAppBaseUrl().trim());
                }
                long dispatchJobId = dispatchJobRepository.createJob(new NotificationDispatchJobRepository.DispatchJobInsertCommand(
                        jobId,
                        notificationId,
                        eventId,
                        recipientUserId,
                        NotificationChannel.EMAIL,
                        3,
                        writeJson(emailPayload)
                ));
                notificationDispatchCoordinationService.schedulePendingAfterCommit(dispatchJobId, Instant.now());
            }
        }

        return new PublishResult(eventId, notificationIds);
    }

    private List<Long> normalizeRecipients(List<Long> recipientUserIds) {
        if (recipientUserIds == null || recipientUserIds.isEmpty()) {
            return List.of();
        }
        // LinkedHashSet 保留业务方给定顺序，同时去掉重复收件人。
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        for (Long userId : recipientUserIds) {
            if (userId != null && userId > 0) {
                normalized.add(userId);
            }
        }
        return List.copyOf(normalized);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to serialize notification payload", ex);
        }
    }

    private String readString(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof String stringValue) {
            return stringValue;
        }
        try {
            // payload 里如果传入结构化 content，就转成 JSON 字符串兜底展示。
            Map<String, Object> mapValue = objectMapper.convertValue(rawValue, MAP_TYPE);
            return writeJson(mapValue);
        } catch (IllegalArgumentException ex) {
            return String.valueOf(rawValue);
        }
    }

    private String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return null;
    }

    private String trimToNull(String rawValue) {
        return rawValue == null || rawValue.isBlank() ? null : rawValue.trim();
    }

    private boolean isActionable(String actionCode) {
        // 通知中心本身不算可操作通知，其余 actionCode 会参与前端跳转判断。
        return actionCode != null
                && !actionCode.isBlank()
                && !"VIEW_NOTIFICATION_CENTER".equalsIgnoreCase(actionCode);
    }

    /**
     * 发布命令。
     */
    public record NotificationPublishCommand(
            String eventType,
            NotificationCategory category,
            String sourceType,
            String sourceId,
            Long actorUserId,
            List<Long> recipientUserIds,
            NotificationPriority priority,
            String title,
            String content,
            String refType,
            String refId,
            String actionCode,
            Map<String, Object> payload,
            Boolean emailRequested,
            String dedupeKey,
            Instant occurredAt
    ) {
        public NotificationPublishCommand(
                String eventType,
                NotificationCategory category,
                String sourceType,
                String sourceId,
                Long actorUserId,
                List<Long> recipientUserIds,
                NotificationPriority priority,
                String title,
                String content,
                String refType,
                String refId,
                String actionCode,
                Map<String, Object> payload,
                String dedupeKey,
                Instant occurredAt
        ) {
            this(
                    eventType,
                    category,
                    sourceType,
                    sourceId,
                    actorUserId,
                    recipientUserIds,
                    priority,
                    title,
                    content,
                    refType,
                    refId,
                    actionCode,
                    payload,
                    null,
                    dedupeKey,
                    occurredAt
            );
        }
    }

    /**
     * 发布结果。
     */
    public record PublishResult(
            String eventId,
            List<Long> notificationIds
    ) {
    }

    /**
     * 推送给客户端的通知快照。
     */
    public record NotificationSnapshot(
            long id,
            String type,
            String category,
            String title,
            String content,
            boolean read,
            String refType,
            String refId,
            String actionCode,
            boolean actionable,
            Map<String, Object> payload,
            Long createdAt,
            Long readAt
    ) {
    }
}
