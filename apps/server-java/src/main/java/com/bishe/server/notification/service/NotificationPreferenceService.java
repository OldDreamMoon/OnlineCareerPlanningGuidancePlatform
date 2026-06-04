package com.bishe.server.notification.service;

import com.bishe.server.auth.model.UserRole;
import com.bishe.server.common.exception.ApiException;
import com.bishe.server.notification.dto.NotificationPreferenceUpdateRequest;
import com.bishe.server.notification.dto.NotificationPreferencesResponse;
import com.bishe.server.notification.model.NotificationCategory;
import com.bishe.server.notification.model.NotificationPriority;
import com.bishe.server.notification.repository.NotificationPreferenceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 用户通知偏好与默认规则解析。
 */
@Service
public class NotificationPreferenceService {

    private static final Map<UserRole, List<NotificationCategory>> CONFIGURABLE_CATEGORIES = buildConfigurableCategories();

    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationPreferenceCacheService notificationPreferenceCacheService;

    public NotificationPreferenceService(
            NotificationPreferenceRepository preferenceRepository,
            NotificationPreferenceCacheService notificationPreferenceCacheService
    ) {
        this.preferenceRepository = preferenceRepository;
        this.notificationPreferenceCacheService = notificationPreferenceCacheService;
    }

    public EffectivePreference resolveEffectivePreference(long userId, NotificationCategory category) {
        NotificationCategory safeCategory = category == null ? NotificationCategory.SYSTEM : category;
        if (safeCategory == NotificationCategory.SYSTEM) {
            return buildDefaultPreference(safeCategory);
        }
        return resolveEffectivePreference(getCustomizedPreferenceRows(userId), safeCategory);
    }

    public NotificationPreferencesResponse listPreferences(long userId, UserRole role) {
        List<NotificationCategory> configurableCategories = resolveConfigurableCategories(role);
        Map<NotificationCategory, NotificationPreferenceRepository.NotificationPreferenceRow> customizedRows = getCustomizedPreferenceRows(userId);
        List<NotificationPreferencesResponse.PreferenceItem> records = configurableCategories.stream()
                .map(category -> {
                    EffectivePreference effective = resolveEffectivePreference(customizedRows, category);
                    return new NotificationPreferencesResponse.PreferenceItem(
                            category.name(),
                            effective.inboxEnabled(),
                            effective.websocketEnabled(),
                            effective.browserPopupEnabled(),
                            effective.emailEnabled(),
                            customizedRows.containsKey(category)
                    );
                })
                .toList();
        return new NotificationPreferencesResponse(records);
    }

    @Transactional
    public NotificationPreferencesResponse.PreferenceItem updatePreference(long userId, UserRole role, NotificationPreferenceUpdateRequest request) {
        NotificationCategory category = NotificationCategory.from(request.category());
        if (!resolveConfigurableCategories(role).contains(category)) {
            throw new ApiException("BIZ-1001", "unsupported notification category for current role", HttpStatus.BAD_REQUEST);
        }
        EffectivePreference base = resolveEffectivePreference(getCustomizedPreferenceRows(userId), category);
        NotificationPreferenceRepository.NotificationPreferenceUpsertCommand command =
                new NotificationPreferenceRepository.NotificationPreferenceUpsertCommand(
                        userId,
                        category,
                        true,
                        request.websocketEnabled() == null ? base.websocketEnabled() : request.websocketEnabled(),
                        request.browserPopupEnabled() == null ? base.browserPopupEnabled() : request.browserPopupEnabled(),
                        request.emailEnabled() == null ? base.emailEnabled() : request.emailEnabled(),
                        base.emailUrgencyThreshold(),
                        base.quietHoursJson()
                );
        preferenceRepository.saveOrUpdate(command);
        notificationPreferenceCacheService.evictUserPreferencesNow(userId);
        notificationPreferenceCacheService.evictUserPreferencesAfterCommit(userId);
        return new NotificationPreferencesResponse.PreferenceItem(
                category.name(),
                command.inboxEnabled(),
                command.websocketEnabled(),
                command.browserPopupEnabled(),
                command.emailEnabled(),
                true
        );
    }

    private Map<NotificationCategory, NotificationPreferenceRepository.NotificationPreferenceRow> getCustomizedPreferenceRows(long userId) {
        if (userId <= 0) {
            return Map.of();
        }
        return notificationPreferenceCacheService.getUserPreferences(userId, () -> preferenceRepository.findByUserId(userId));
    }

    private EffectivePreference resolveEffectivePreference(
            Map<NotificationCategory, NotificationPreferenceRepository.NotificationPreferenceRow> customizedRows,
            NotificationCategory category
    ) {
        EffectivePreference defaults = buildDefaultPreference(category);
        if (category == null || category == NotificationCategory.SYSTEM || customizedRows == null || customizedRows.isEmpty()) {
            return defaults;
        }
        NotificationPreferenceRepository.NotificationPreferenceRow row = customizedRows.get(category);
        return row == null ? defaults : toEffectivePreference(row);
    }

    private EffectivePreference buildDefaultPreference(NotificationCategory category) {
        boolean emailEnabled = category == NotificationCategory.SYSTEM;
        NotificationPriority emailThreshold = category == NotificationCategory.SYSTEM
                ? NotificationPriority.NORMAL
                : NotificationPriority.HIGH;
        return new EffectivePreference(
                category,
                true,
                true,
                true,
                emailEnabled,
                emailThreshold,
                null,
                false
        );
    }

    private EffectivePreference toEffectivePreference(NotificationPreferenceRepository.NotificationPreferenceRow row) {
        return new EffectivePreference(
                row.category(),
                true,
                row.websocketEnabled(),
                row.browserPopupEnabled(),
                row.emailEnabled(),
                row.emailUrgencyThreshold(),
                row.quietHoursJson(),
                true
        );
    }

    private static Map<UserRole, List<NotificationCategory>> buildConfigurableCategories() {
        Map<UserRole, List<NotificationCategory>> mappings = new EnumMap<>(UserRole.class);
        mappings.put(UserRole.STUDENT, List.of(
                NotificationCategory.AI_TASK,
                NotificationCategory.CONSULT,
                NotificationCategory.BOUNTY,
                NotificationCategory.COMMUNITY
        ));
        mappings.put(UserRole.MENTOR, List.of(
                NotificationCategory.CONSULT,
                NotificationCategory.CERTIFICATION,
                NotificationCategory.COMMUNITY
        ));
        mappings.put(UserRole.ENTERPRISE, List.of(
                NotificationCategory.BOUNTY,
                NotificationCategory.CERTIFICATION
        ));
        mappings.put(UserRole.ADMIN, List.of());
        return Map.copyOf(mappings);
    }

    private List<NotificationCategory> resolveConfigurableCategories(UserRole role) {
        if (role == null) {
            return List.of();
        }
        return CONFIGURABLE_CATEGORIES.getOrDefault(role, List.of());
    }

    /**
     * 发布阶段使用的有效偏好。
     */
    public record EffectivePreference(
            NotificationCategory category,
            boolean inboxEnabled,
            boolean websocketEnabled,
            boolean browserPopupEnabled,
            boolean emailEnabled,
            NotificationPriority emailUrgencyThreshold,
            String quietHoursJson,
            boolean customized
    ) {
    }
}
