package com.bishe.server.notification.service;

import com.bishe.server.auth.model.UserRole;
import com.bishe.server.auth.repository.UserRepository;
import com.bishe.server.common.TimePayloads;
import com.bishe.server.common.exception.ApiException;
import com.bishe.server.notification.dto.AdminNotificationAnnouncementRequest;
import com.bishe.server.notification.dto.AdminNotificationAnnouncementResponse;
import com.bishe.server.notification.model.NotificationCategory;
import com.bishe.server.notification.model.NotificationPriority;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 系统公告发布服务。
 */
@Service
public class NotificationAnnouncementService {

    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public NotificationAnnouncementService(UserRepository userRepository, NotificationService notificationService) {
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public AdminNotificationAnnouncementResponse publishAnnouncement(long operatorUserId, AdminNotificationAnnouncementRequest request) {
        List<UserRole> targetRoles = normalizeRoles(request.targetRoles());
        boolean emailRequested = Boolean.TRUE.equals(request.emailRequested());
        LinkedHashSet<Long> targetUserIds = new LinkedHashSet<>();
        if (request.targetUserIds() != null) {
            for (Long targetUserId : request.targetUserIds()) {
                if (targetUserId != null && targetUserId > 0) {
                    targetUserIds.add(targetUserId);
                }
            }
        }
        if (targetUserIds.isEmpty()) {
            targetUserIds.addAll(userRepository.findActiveUserIdsByRoles(targetRoles));
        }
        if (targetUserIds.isEmpty()) {
            throw new ApiException("BIZ-1001", "announcement recipients resolved to empty", HttpStatus.BAD_REQUEST);
        }

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("announcement", true);
        payload.put("targetRoles", targetRoles.stream().map(UserRole::name).toList());
        payload.put("emailRequested", emailRequested);
        payload.put("deliveryChannels", emailRequested ? List.of("IN_APP", "EMAIL") : List.of("IN_APP"));
        payload.put("operatorUserId", operatorUserId);

        PlatformNotificationPublishService.PublishResult result = notificationService.publish(
                new PlatformNotificationPublishService.NotificationPublishCommand(
                        "SYSTEM_ANNOUNCEMENT",
                        NotificationCategory.SYSTEM,
                        "NOTIFICATION_CENTER",
                        request.refId(),
                        operatorUserId,
                        List.copyOf(targetUserIds),
                        request.priority() == null || request.priority().isBlank()
                                ? NotificationPriority.NORMAL
                                : NotificationPriority.from(request.priority()),
                        request.title(),
                        request.content(),
                        request.refType(),
                        request.refId(),
                        request.actionCode(),
                        payload,
                        emailRequested,
                        null,
                        Instant.now()
                )
        );
        return new AdminNotificationAnnouncementResponse(
                result.eventId(),
                result.notificationIds().size(),
                targetRoles.stream().map(UserRole::name).toList(),
                TimePayloads.toEpochMillis(Instant.now())
        );
    }

    private List<UserRole> normalizeRoles(List<String> rawRoles) {
        if (rawRoles == null || rawRoles.isEmpty()) {
            return List.of(UserRole.STUDENT, UserRole.MENTOR, UserRole.ENTERPRISE);
        }
        LinkedHashSet<UserRole> roles = new LinkedHashSet<>();
        for (String rawRole : rawRoles) {
            if (rawRole == null || rawRole.isBlank()) {
                continue;
            }
            roles.add(UserRole.parse(rawRole));
        }
        return roles.isEmpty() ? List.of(UserRole.STUDENT, UserRole.MENTOR, UserRole.ENTERPRISE) : List.copyOf(roles);
    }
}
