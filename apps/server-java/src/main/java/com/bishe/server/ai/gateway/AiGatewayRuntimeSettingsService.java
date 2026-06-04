package com.bishe.server.ai.gateway;

import com.bishe.server.adminconsole.AdminConsoleSnapshotCacheService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI 网关运行时开关：统一管理调试模式与 AI 请求日志开关。
 */
@Service
public class AiGatewayRuntimeSettingsService {

    public static final String KEY_DEBUG_MODE_ENABLED = "DEBUG_MODE_ENABLED";
    public static final String KEY_AI_REQUEST_LOG_ENABLED = "AI_REQUEST_LOG_ENABLED";
    public static final String KEY_DEFAULT_REASONING_EFFORT = "DEFAULT_REASONING_EFFORT";
    public static final String KEY_DEFAULT_THINKING_BUDGET = "DEFAULT_THINKING_BUDGET";
    public static final String KEY_DEFAULT_THINKING_LEVEL = "DEFAULT_THINKING_LEVEL";

    private static final String DEBUG_MODE_DESCRIPTION = "控制 AI 网关后端诊断日志输出。";
    private static final String AI_REQUEST_LOG_DESCRIPTION = "控制 AI 请求开始/重试/错误请求体与响应体摘要日志。";
    private static final String DEFAULT_REASONING_EFFORT_DESCRIPTION = "控制全局默认思考强度；留空时沿用代码内置推荐。";
    private static final String DEFAULT_THINKING_BUDGET_DESCRIPTION = "控制全局默认思考预算；留空时沿用代码内置推荐。";
    private static final String DEFAULT_THINKING_LEVEL_DESCRIPTION = "控制全局默认思考层级；留空时沿用代码内置推荐。";

    private final AiGatewayRuntimeSettingRepository runtimeSettingRepository;
    private final AiGatewayProperties properties;
    private final AiGatewayRuntimeSettingsCacheService runtimeSettingsCacheService;
    private final AdminConsoleSnapshotCacheService adminConsoleSnapshotCacheService;

    public AiGatewayRuntimeSettingsService(
            AiGatewayRuntimeSettingRepository runtimeSettingRepository,
            AiGatewayProperties properties,
            AiGatewayRuntimeSettingsCacheService runtimeSettingsCacheService,
            AdminConsoleSnapshotCacheService adminConsoleSnapshotCacheService
    ) {
        this.runtimeSettingRepository = runtimeSettingRepository;
        this.properties = properties;
        this.runtimeSettingsCacheService = runtimeSettingsCacheService;
        this.adminConsoleSnapshotCacheService = adminConsoleSnapshotCacheService;
    }

    @Transactional(readOnly = true)
    public RuntimeSettingsSnapshot getRuntimeSettings() {
        return runtimeSettingsCacheService.getRuntimeSettings(this::loadRuntimeSettingsFromRepository);
    }

    @Transactional
    public RuntimeSettingsSnapshot updateRuntimeSettings(long operatorUserId, UpdateRuntimeSettingsCommand command) {
        RuntimeSettingsSnapshot current = getRuntimeSettings();
        boolean debugModeEnabled = command.debugModeEnabled() == null ? current.debugModeEnabled() : command.debugModeEnabled();
        boolean aiRequestLogEnabled = command.aiRequestLogEnabled() == null ? current.aiRequestLogEnabled() : command.aiRequestLogEnabled();
        String defaultReasoningEffort = command.defaultReasoningEffort() == null ? "" : command.defaultReasoningEffort();
        String defaultThinkingLevel = command.defaultThinkingLevel() == null ? "" : command.defaultThinkingLevel();
        String defaultThinkingBudget = command.defaultThinkingBudget() == null ? "" : Integer.toString(command.defaultThinkingBudget());
        runtimeSettingRepository.upsert(KEY_DEBUG_MODE_ENABLED, Boolean.toString(debugModeEnabled), DEBUG_MODE_DESCRIPTION, operatorUserId);
        runtimeSettingRepository.upsert(KEY_AI_REQUEST_LOG_ENABLED, Boolean.toString(aiRequestLogEnabled), AI_REQUEST_LOG_DESCRIPTION, operatorUserId);
        runtimeSettingRepository.upsert(KEY_DEFAULT_REASONING_EFFORT, defaultReasoningEffort, DEFAULT_REASONING_EFFORT_DESCRIPTION, operatorUserId);
        runtimeSettingRepository.upsert(KEY_DEFAULT_THINKING_BUDGET, defaultThinkingBudget, DEFAULT_THINKING_BUDGET_DESCRIPTION, operatorUserId);
        runtimeSettingRepository.upsert(KEY_DEFAULT_THINKING_LEVEL, defaultThinkingLevel, DEFAULT_THINKING_LEVEL_DESCRIPTION, operatorUserId);
        runtimeSettingsCacheService.evictNow();
        runtimeSettingsCacheService.evictAfterCommit();
        adminConsoleSnapshotCacheService.evictNow();
        adminConsoleSnapshotCacheService.evictAfterCommit();
        return loadRuntimeSettingsFromRepository();
    }

    public boolean isDebugModeEnabled() {
        return getRuntimeSettings().debugModeEnabled();
    }

    public boolean isAiRequestLogEnabled() {
        return getRuntimeSettings().aiRequestLogEnabled();
    }

    private RuntimeSettingsSnapshot loadRuntimeSettingsFromRepository() {
        Map<String, AiGatewayRuntimeSettingRepository.RuntimeSettingRow> rowMap = runtimeSettingRepository.findAll().stream()
                .collect(Collectors.toMap(AiGatewayRuntimeSettingRepository.RuntimeSettingRow::settingKey, Function.identity()));
        boolean defaultDebugModeEnabled = properties.getLogging().isDebugEnabled();
        boolean defaultAiRequestLogEnabled = properties.getLogging().isAiRequestLogEnabled();
        AiGatewayRuntimeSettingRepository.RuntimeSettingRow debugRow = rowMap.get(KEY_DEBUG_MODE_ENABLED);
        AiGatewayRuntimeSettingRepository.RuntimeSettingRow aiRequestLogRow = rowMap.get(KEY_AI_REQUEST_LOG_ENABLED);
        AiGatewayRuntimeSettingRepository.RuntimeSettingRow reasoningEffortRow = rowMap.get(KEY_DEFAULT_REASONING_EFFORT);
        AiGatewayRuntimeSettingRepository.RuntimeSettingRow thinkingBudgetRow = rowMap.get(KEY_DEFAULT_THINKING_BUDGET);
        AiGatewayRuntimeSettingRepository.RuntimeSettingRow thinkingLevelRow = rowMap.get(KEY_DEFAULT_THINKING_LEVEL);
        boolean debugModeEnabled = parseBoolean(debugRow == null ? null : debugRow.settingValue(), defaultDebugModeEnabled);
        boolean aiRequestLogEnabled = parseBoolean(aiRequestLogRow == null ? null : aiRequestLogRow.settingValue(), defaultAiRequestLogEnabled);
        String defaultReasoningEffort = parseOptionalText(reasoningEffortRow == null ? null : reasoningEffortRow.settingValue());
        Integer defaultThinkingBudget = parseOptionalInteger(thinkingBudgetRow == null ? null : thinkingBudgetRow.settingValue());
        String defaultThinkingLevel = parseOptionalText(thinkingLevelRow == null ? null : thinkingLevelRow.settingValue());
        Instant updatedAt = latest(
                latest(debugRow == null ? null : debugRow.updatedAt(), aiRequestLogRow == null ? null : aiRequestLogRow.updatedAt()),
                latest(
                        reasoningEffortRow == null ? null : reasoningEffortRow.updatedAt(),
                        latest(
                                thinkingBudgetRow == null ? null : thinkingBudgetRow.updatedAt(),
                                thinkingLevelRow == null ? null : thinkingLevelRow.updatedAt()
                        )
                )
        );
        return new RuntimeSettingsSnapshot(
                debugModeEnabled,
                aiRequestLogEnabled,
                defaultDebugModeEnabled,
                defaultAiRequestLogEnabled,
                defaultReasoningEffort,
                defaultThinkingBudget,
                defaultThinkingLevel,
                updatedAt
        );
    }

    private boolean parseBoolean(String rawValue, boolean fallback) {
        if (rawValue == null || rawValue.isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(rawValue.trim());
    }

    private String parseOptionalText(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return rawValue.trim();
    }

    private Integer parseOptionalInteger(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(rawValue.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Instant latest(Instant left, Instant right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    public record UpdateRuntimeSettingsCommand(
            Boolean debugModeEnabled,
            Boolean aiRequestLogEnabled,
            String defaultReasoningEffort,
            Integer defaultThinkingBudget,
            String defaultThinkingLevel
    ) {
    }

    public record RuntimeSettingsSnapshot(
            boolean debugModeEnabled,
            boolean aiRequestLogEnabled,
            boolean defaultDebugModeEnabled,
            boolean defaultAiRequestLogEnabled,
            String defaultReasoningEffort,
            Integer defaultThinkingBudget,
            String defaultThinkingLevel,
            Instant updatedAt
    ) {
        public static RuntimeSettingsSnapshot empty() {
            return new RuntimeSettingsSnapshot(false, false, false, false, null, null, null, null);
        }
    }
}
