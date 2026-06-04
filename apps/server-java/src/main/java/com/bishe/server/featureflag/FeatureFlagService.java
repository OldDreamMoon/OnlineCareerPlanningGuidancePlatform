package com.bishe.server.featureflag;

import com.bishe.server.adminconsole.AdminConsoleSnapshotCacheService;
import com.bishe.server.common.exception.ApiException;
import com.bishe.server.consult.PaymentMode;
import com.bishe.server.consult.PaymentProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 功能开关服务：统一提供管理员配置与业务运行时读取能力。
 */
@Service
public class FeatureFlagService {

    public static final String KEY_PAYMENT_ENABLED = "payment.enabled";
    public static final String KEY_PAYMENT_MODE = "payment.mode";
    public static final String KEY_VOICE_ENABLED = "voice.enabled";
    public static final String KEY_VOICE_INTERVIEW_ENABLED = "voice.interview.enabled";
    public static final String KEY_VOICE_STT_ENABLED = "voice.stt.enabled";
    public static final String KEY_VOICE_TTS_ENABLED = "voice.tts.enabled";
    public static final String KEY_COMMUNITY_AI_DRAFT_ENABLED = "community.ai-draft.enabled";
    public static final String KEY_COMMUNITY_AI_FIRST_REPLY_ENABLED = "community.ai-first-reply.enabled";
    public static final String KEY_COMMUNITY_AI_PRE_ANSWER_ENABLED = "community.ai-pre-answer.enabled";
    public static final String KEY_STUDENT_PORTRAIT_ASYNC_REFRESH_ENABLED = "student.portrait.async-refresh.enabled";
    public static final String KEY_STUDENT_PORTRAIT_SUMMARY_MODE = "student.portrait.summary.mode";

    private static final String TYPE_ENUM = "ENUM";
    private static final String TYPE_BOOLEAN = "BOOLEAN";

    private final FeatureFlagRepository repository;
    private final PaymentProperties paymentProperties;
    private final FeatureFlagCacheService featureFlagCacheService;
    private final AdminConsoleSnapshotCacheService adminConsoleSnapshotCacheService;

    public FeatureFlagService(
            FeatureFlagRepository repository,
            PaymentProperties paymentProperties,
            FeatureFlagCacheService featureFlagCacheService,
            AdminConsoleSnapshotCacheService adminConsoleSnapshotCacheService
    ) {
        this.repository = repository;
        this.paymentProperties = paymentProperties;
        this.featureFlagCacheService = featureFlagCacheService;
        this.adminConsoleSnapshotCacheService = adminConsoleSnapshotCacheService;
    }

    @Transactional(readOnly = true)
    public FeatureFlagListPayload listFlags() {
        Map<String, FeatureFlagRepository.FeatureFlagRow> rowMap = loadRowMap();
        List<FeatureFlagItem> records = definitions().values().stream()
                .map(definition -> toItem(definition, rowMap))
                .toList();
        return new FeatureFlagListPayload(records);
    }

    @Transactional
    public FeatureFlagItem updateFlag(long operatorUserId, FeatureFlagUpdateCommand command) {
        FlagDefinition definition = findRequiredDefinition(command.key());
        String normalizedValue = normalizeValue(definition, command.value());
        repository.upsertFlag(definition.key(), normalizedValue, definition.description(), operatorUserId);
        FeatureFlagRepository.FeatureFlagRow row = repository.findByKey(definition.key())
                .orElseThrow(() -> new ApiException("BIZ-1001", "feature flag save failed", HttpStatus.INTERNAL_SERVER_ERROR));
        evictFeatureFlagSnapshot();
        evictAdminConsoleSnapshot();
        return toItem(definition, Map.of(definition.key(), row));
    }

    private void evictFeatureFlagSnapshot() {
        featureFlagCacheService.evictNow();
        featureFlagCacheService.evictAfterCommit();
    }

    private void evictAdminConsoleSnapshot() {
        adminConsoleSnapshotCacheService.evictNow();
        adminConsoleSnapshotCacheService.evictAfterCommit();
    }

    @Transactional(readOnly = true)
    public boolean isPaymentEnabled() {
        return Boolean.parseBoolean(readBooleanFlag(KEY_PAYMENT_ENABLED, true));
    }

    public void requirePaymentEnabled() {
        if (!isPaymentEnabled()) {
            throw new ApiException("BIZ-1003", "支付入口暂未开放，请稍后再试。", HttpStatus.FORBIDDEN);
        }
    }

    @Transactional(readOnly = true)
    public PaymentMode resolvePaymentMode() {
        String rawValue = loadFlag(KEY_PAYMENT_MODE)
                .map(FeatureFlagRepository.FeatureFlagRow::flagValue)
                .orElse(paymentProperties.currentMode().name());
        return PaymentMode.parse(normalizeValue(definitions().get(KEY_PAYMENT_MODE), rawValue));
    }

    @Transactional(readOnly = true)
    public boolean isVoiceEnabled() {
        return isVoiceInterviewEnabled() && isVoiceSttEnabled() && isVoiceTtsEnabled();
    }

    @Transactional(readOnly = true)
    public boolean isVoiceInterviewEnabled() {
        return Boolean.parseBoolean(readBooleanFlag(KEY_VOICE_INTERVIEW_ENABLED, legacyVoiceEnabledByDefault()));
    }

    @Transactional(readOnly = true)
    public boolean isVoiceSttEnabled() {
        return Boolean.parseBoolean(readBooleanFlag(KEY_VOICE_STT_ENABLED, legacyVoiceEnabledByDefault()));
    }

    @Transactional(readOnly = true)
    public boolean isVoiceTtsEnabled() {
        return Boolean.parseBoolean(readBooleanFlag(KEY_VOICE_TTS_ENABLED, legacyVoiceEnabledByDefault()));
    }

    @Transactional(readOnly = true)
    public boolean isCommunityAiPreAnswerEnabled() {
        return isCommunityAiDraftEnabled();
    }

    @Transactional(readOnly = true)
    public boolean isCommunityAiDraftEnabled() {
        return Boolean.parseBoolean(readBooleanFlag(KEY_COMMUNITY_AI_DRAFT_ENABLED, legacyCommunityAiPreAnswerEnabledByDefault()));
    }

    @Transactional(readOnly = true)
    public boolean isCommunityAiFirstReplyEnabled() {
        return Boolean.parseBoolean(readBooleanFlag(KEY_COMMUNITY_AI_FIRST_REPLY_ENABLED, legacyCommunityAiPreAnswerEnabledByDefault()));
    }

    @Transactional(readOnly = true)
    public boolean isStudentPortraitAsyncRefreshEnabled() {
        return Boolean.parseBoolean(readBooleanFlag(KEY_STUDENT_PORTRAIT_ASYNC_REFRESH_ENABLED, true));
    }

    @Transactional(readOnly = true)
    public StudentPortraitSummaryMode getStudentPortraitSummaryMode() {
        String rawValue = loadFlag(KEY_STUDENT_PORTRAIT_SUMMARY_MODE)
                .map(FeatureFlagRepository.FeatureFlagRow::flagValue)
                .orElse(StudentPortraitSummaryMode.TEMPLATE.name());
        return StudentPortraitSummaryMode.valueOf(normalizeValue(findRequiredDefinition(KEY_STUDENT_PORTRAIT_SUMMARY_MODE), rawValue));
    }

    public void requireVoiceEnabled() {
        if (!isVoiceEnabled()) {
            throw new ApiException("BIZ-1003", "当前语音能力暂未开启，请稍后再试。", HttpStatus.FORBIDDEN);
        }
    }

    public void requireVoiceInterviewEnabled() {
        if (!isVoiceInterviewEnabled()) {
            throw new ApiException("BIZ-1003", "语音面试入口暂未开放，请稍后再试。", HttpStatus.FORBIDDEN);
        }
    }

    public void requireVoiceSttEnabled() {
        if (!isVoiceSttEnabled()) {
            throw new ApiException("BIZ-1003", "语音转写能力暂未开放，请稍后再试。", HttpStatus.FORBIDDEN);
        }
    }

    public void requireVoiceTtsEnabled() {
        if (!isVoiceTtsEnabled()) {
            throw new ApiException("BIZ-1003", "语音播报能力暂未开放，请稍后再试。", HttpStatus.FORBIDDEN);
        }
    }

    public void requireCommunityAiPreAnswerEnabled() {
        requireCommunityAiDraftEnabled();
    }

    public void requireCommunityAiDraftEnabled() {
        if (!isCommunityAiDraftEnabled()) {
            throw new ApiException("BIZ-1003", "社区智能辅助暂未开放，请稍后再试。", HttpStatus.FORBIDDEN);
        }
    }

    private String readBooleanFlag(String key, boolean defaultValue) {
        FlagDefinition definition = findRequiredDefinition(key);
        return loadFlag(key)
                .map(FeatureFlagRepository.FeatureFlagRow::flagValue)
                .map(value -> normalizeValue(definition, value))
                .orElse(Boolean.toString(defaultValue));
    }

    private Optional<FeatureFlagRepository.FeatureFlagRow> loadFlag(String key) {
        return Optional.ofNullable(loadRowMap().get(key));
    }

    private boolean legacyVoiceEnabledByDefault() {
        return readLegacyBooleanFlag(KEY_VOICE_ENABLED, true);
    }

    private boolean legacyCommunityAiPreAnswerEnabledByDefault() {
        return readLegacyBooleanFlag(KEY_COMMUNITY_AI_PRE_ANSWER_ENABLED, true);
    }

    private boolean readLegacyBooleanFlag(String key, boolean defaultValue) {
        return loadFlag(key)
                .map(FeatureFlagRepository.FeatureFlagRow::flagValue)
                .map(this::normalizeBooleanValue)
                .map(Boolean::parseBoolean)
                .orElse(defaultValue);
    }

    private Map<String, FeatureFlagRepository.FeatureFlagRow> loadRowMap() {
        return featureFlagCacheService.getSnapshot(repository::findAll).stream()
                .collect(Collectors.toMap(FeatureFlagRepository.FeatureFlagRow::flagKey, Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    private FeatureFlagItem toItem(FlagDefinition definition, Map<String, FeatureFlagRepository.FeatureFlagRow> rowMap) {
        FeatureFlagRepository.FeatureFlagRow row = rowMap.get(definition.key());
        if (row == null && definition.fallbackKey() != null) {
            row = rowMap.get(definition.fallbackKey());
        }
        String defaultValue = definition.defaultValue();
        String currentValue = row == null ? defaultValue : normalizeValue(definition, row.flagValue());
        return new FeatureFlagItem(
                definition.key(),
                definition.displayName(),
                definition.description(),
                definition.valueType(),
                definition.allowedValues(),
                currentValue,
                defaultValue,
                row != null,
                row == null ? null : row.updatedBy(),
                row == null ? null : row.updatedAt()
        );
    }

    private FlagDefinition findRequiredDefinition(String key) {
        String normalizedKey = key == null ? "" : key.trim();
        FlagDefinition definition = definitions().get(normalizedKey);
        if (definition == null) {
            throw new ApiException("BIZ-1001", "feature flag key invalid", HttpStatus.BAD_REQUEST);
        }
        return definition;
    }

    private String normalizeValue(FlagDefinition definition, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new ApiException("BIZ-1001", "feature flag value invalid", HttpStatus.BAD_REQUEST);
        }
        if (TYPE_BOOLEAN.equals(definition.valueType())) {
            String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
            if (!"true".equals(normalized) && !"false".equals(normalized)) {
                throw new ApiException("BIZ-1001", "feature flag value invalid", HttpStatus.BAD_REQUEST);
            }
            return normalized;
        }
        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
        if (!definition.allowedValues().contains(normalized)) {
            throw new ApiException("BIZ-1001", "feature flag value invalid", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeBooleanValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new ApiException("BIZ-1001", "feature flag value invalid", HttpStatus.BAD_REQUEST);
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        if (!"true".equals(normalized) && !"false".equals(normalized)) {
            throw new ApiException("BIZ-1001", "feature flag value invalid", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private Map<String, FlagDefinition> definitions() {
        LinkedHashMap<String, FlagDefinition> definitions = new LinkedHashMap<>();
        definitions.put(KEY_PAYMENT_ENABLED, new FlagDefinition(
                KEY_PAYMENT_ENABLED,
                "支付入口",
                "控制用户是否还能继续提交咨询订单并完成支付。",
                TYPE_BOOLEAN,
                List.of("true", "false"),
                "true",
                null
        ));
        definitions.put(KEY_PAYMENT_MODE, new FlagDefinition(
                KEY_PAYMENT_MODE,
                "支付模式",
                "控制新咨询订单默认使用哪种支付环境。",
                TYPE_ENUM,
                List.of(PaymentMode.MOCK.name(), PaymentMode.SANDBOX.name()),
                paymentProperties.currentMode().name(),
                null
        ));
        definitions.put(KEY_VOICE_INTERVIEW_ENABLED, new FlagDefinition(
                KEY_VOICE_INTERVIEW_ENABLED,
                "语音面试入口",
                "控制用户是否还能选择语音作答模式开始面试。",
                TYPE_BOOLEAN,
                List.of("true", "false"),
                "true",
                KEY_VOICE_ENABLED
        ));
        definitions.put(KEY_VOICE_STT_ENABLED, new FlagDefinition(
                KEY_VOICE_STT_ENABLED,
                "语音转写",
                "控制语音回答能否自动整理成文字，适合在识别波动时临时关闭。",
                TYPE_BOOLEAN,
                List.of("true", "false"),
                "true",
                KEY_VOICE_ENABLED
        ));
        definitions.put(KEY_VOICE_TTS_ENABLED, new FlagDefinition(
                KEY_VOICE_TTS_ENABLED,
                "语音播报",
                "控制题目播报和朗读提醒是否继续提供。",
                TYPE_BOOLEAN,
                List.of("true", "false"),
                "true",
                KEY_VOICE_ENABLED
        ));
        definitions.put(KEY_COMMUNITY_AI_DRAFT_ENABLED, new FlagDefinition(
                KEY_COMMUNITY_AI_DRAFT_ENABLED,
                "社区智能辅助",
                "控制帖子详情中的智能回复参考是否继续提供。",
                TYPE_BOOLEAN,
                List.of("true", "false"),
                "true",
                KEY_COMMUNITY_AI_PRE_ANSWER_ENABLED
        ));
        definitions.put(KEY_COMMUNITY_AI_FIRST_REPLY_ENABLED, new FlagDefinition(
                KEY_COMMUNITY_AI_FIRST_REPLY_ENABLED,
                "社区自动首评",
                "控制新帖发布后是否自动补充一条参考回复，帮助带动首轮互动。",
                TYPE_BOOLEAN,
                List.of("true", "false"),
                "true",
                KEY_COMMUNITY_AI_PRE_ANSWER_ENABLED
        ));
        definitions.put(KEY_STUDENT_PORTRAIT_ASYNC_REFRESH_ENABLED, new FlagDefinition(
                KEY_STUDENT_PORTRAIT_ASYNC_REFRESH_ENABLED,
                "学生画像定时批刷新",
                "仅控制学生画像的凌晨定时批量刷新；不影响资料编辑、技能进度和 AI 完成后的按需重算。",
                TYPE_BOOLEAN,
                List.of("true", "false"),
                "true",
                null
        ));
        definitions.put(KEY_STUDENT_PORTRAIT_SUMMARY_MODE, new FlagDefinition(
                KEY_STUDENT_PORTRAIT_SUMMARY_MODE,
                "学生画像总结模式",
                "控制成长画像的表达层策略：关闭、模板总结，或仅在高价值触发时低频走 AI 网关润色。",
                TYPE_ENUM,
                List.of(
                        StudentPortraitSummaryMode.OFF.name(),
                        StudentPortraitSummaryMode.TEMPLATE.name(),
                        StudentPortraitSummaryMode.LLM.name()
                ),
                StudentPortraitSummaryMode.TEMPLATE.name(),
                null
        ));
        return definitions;
    }

    public enum StudentPortraitSummaryMode {
        OFF,
        TEMPLATE,
        LLM
    }

    private record FlagDefinition(
            String key,
            String displayName,
            String description,
            String valueType,
            List<String> allowedValues,
            String defaultValue,
            String fallbackKey
    ) {
    }

    public record FeatureFlagUpdateCommand(String key, String value) {
    }

    public record FeatureFlagListPayload(List<FeatureFlagItem> records) {
    }

    public record FeatureFlagItem(
            String key,
            String displayName,
            String description,
            String valueType,
            List<String> allowedValues,
            String currentValue,
            String defaultValue,
            boolean overridden,
            Long updatedBy,
            Instant updatedAt
    ) {
    }
}
