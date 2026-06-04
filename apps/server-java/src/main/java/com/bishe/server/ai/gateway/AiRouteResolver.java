package com.bishe.server.ai.gateway;

import com.bishe.server.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 根据任务类型、场景与用户层级解析 AI provider 路由。
 */
@Service
public class AiRouteResolver {

    private final AiGatewayProperties properties;
    private final AiGatewayAdminRepository repository;
    private final AiConfigCryptoService cryptoService;
    private final AiRouteCacheService routeCacheService;
    private final PromptTemplateRenderService promptTemplateRenderService;
    private final AiThinkingPolicyResolver thinkingPolicyResolver;
    private final AiPricingPolicyResolver pricingPolicyResolver;

    public AiRouteResolver(
            AiGatewayProperties properties,
            AiGatewayAdminRepository repository,
            AiConfigCryptoService cryptoService,
            AiRouteCacheService routeCacheService,
            PromptTemplateRenderService promptTemplateRenderService,
            AiThinkingPolicyResolver thinkingPolicyResolver,
            AiPricingPolicyResolver pricingPolicyResolver
    ) {
        this.properties = properties;
        this.repository = repository;
        this.cryptoService = cryptoService;
        this.routeCacheService = routeCacheService;
        this.promptTemplateRenderService = promptTemplateRenderService;
        this.thinkingPolicyResolver = thinkingPolicyResolver;
        this.pricingPolicyResolver = pricingPolicyResolver;
    }

    public AiProviderInvocation resolve(String taskType, String sceneCode, String modelPreference) {
        return resolve(taskType, sceneCode, modelPreference, "ALL");
    }

    public AiProviderInvocation resolve(String taskType, String sceneCode, String modelPreference, String userTier) {
        return resolve(taskType, sceneCode, modelPreference, userTier, Collections.emptyMap(), false);
    }

    public AiProviderInvocation resolve(String taskType, String sceneCode, String modelPreference, Map<String, Object> promptVariables) {
        return resolve(taskType, sceneCode, modelPreference, "ALL", promptVariables, true);
    }

    public AiProviderInvocation resolve(
            String taskType,
            String sceneCode,
            String modelPreference,
            String userTier,
            Map<String, Object> promptVariables
    ) {
        return resolve(taskType, sceneCode, modelPreference, userTier, promptVariables, true);
    }

    public RoutePlan resolvePlan(String taskType, String sceneCode, String modelPreference, String userTier) {
        return resolvePlan(taskType, sceneCode, modelPreference, userTier, Collections.emptyMap(), false);
    }

    public RoutePlan resolvePlan(
            String taskType,
            String sceneCode,
            String modelPreference,
            String userTier,
            Map<String, Object> promptVariables
    ) {
        return resolvePlan(taskType, sceneCode, modelPreference, userTier, promptVariables, true);
    }

    public AiProviderInvocation resolveResumePdf(
            String taskType,
            String sceneCode,
            String modelPreference,
            Map<String, Object> promptVariables
    ) {
        return resolveResumePdf(taskType, sceneCode, modelPreference, "ALL", promptVariables);
    }

    public AiProviderInvocation resolveResumePdf(
            String taskType,
            String sceneCode,
            String modelPreference,
            String userTier,
            Map<String, Object> promptVariables
    ) {
        if (properties.getMode() == AiGatewayMode.MOCK) {
            // MOCK 没有真实 provider route，调用方需要显式走 mock/兜底分支。
            throw new ApiException("AI-2001", "mock mode has no provider route", HttpStatus.BAD_GATEWAY);
        }
        if (properties.getMode() == AiGatewayMode.OPENAI_COMPATIBLE) {
            // legacy 单路由仍可支撑文本场景，但 PDF 真实能力取决于后续 provider 实现。
            return buildLegacyOpenAiInvocation(taskType, sceneCode, modelPreference);
        }
        String normalizedSceneCode = normalizeScene(sceneCode);
        String normalizedUserTier = normalizeUserTier(userTier);
        List<AiGatewayAdminRepository.ResolvedRouteRow> candidates = getEnabledRoutes(taskType, normalizedSceneCode, normalizedUserTier);
        // PDF 简历只选择声明支持 Gemini Native 的路由，避免把文件 bytes 送到纯文本 provider。
        AiGatewayAdminRepository.ResolvedRouteRow routeRow = candidates.stream()
                .filter(this::supportsResumePdf)
                .findFirst()
                .orElseThrow(() -> new ApiException("AI-2001", "resume pdf route config missing", HttpStatus.BAD_GATEWAY));
        return buildInvocation(taskType, sceneCode, modelPreference, normalizedUserTier, promptVariables, true, routeRow);
    }

    private AiProviderInvocation resolve(
            String taskType,
            String sceneCode,
            String modelPreference,
            String userTier,
            Map<String, Object> promptVariables,
            boolean strictPromptRender
    ) {
        return resolvePlan(taskType, sceneCode, modelPreference, userTier, promptVariables, strictPromptRender).primaryCandidate();
    }

    private RoutePlan resolvePlan(
            String taskType,
            String sceneCode,
            String modelPreference,
            String userTier,
            Map<String, Object> promptVariables,
            boolean strictPromptRender
    ) {
        if (properties.getMode() == AiGatewayMode.MOCK) {
            // 正式解析阶段不返回 mock invocation，mock 由网关服务层统一处理。
            throw new ApiException("AI-2001", "mock mode has no provider route", HttpStatus.BAD_GATEWAY);
        }
        if (properties.getMode() == AiGatewayMode.OPENAI_COMPATIBLE) {
            // 兼容模式保留旧版单 provider 配置，不走数据库路由策略。
            AiProviderInvocation invocation = buildLegacyOpenAiInvocation(taskType, sceneCode, modelPreference);
            return new RoutePlan(
                    invocation.taskType(),
                    invocation.sceneCode(),
                    normalizeUserTier(userTier),
                    "ALL",
                    null,
                    AiRouteStrategyType.SINGLE.name(),
                    List.of(invocation)
            );
        }

        String normalizedSceneCode = normalizeScene(sceneCode);
        String normalizedUserTier = normalizeUserTier(userTier);
        List<AiGatewayAdminRepository.ResolvedRouteRow> candidates = getEnabledRoutes(taskType, normalizedSceneCode, normalizedUserTier);
        if (candidates.isEmpty()) {
            // 数据库路由缺失时是否回退 legacy 由配置开关决定，避免静默吃掉运营台配置错误。
            AiGatewayAdminRepository.ResolvedRouteRow fallbackRoute = buildLegacyFallbackRoute(taskType, sceneCode, modelPreference);
            AiProviderInvocation invocation = buildInvocation(
                    taskType,
                    sceneCode,
                    modelPreference,
                    normalizedUserTier,
                    promptVariables,
                    strictPromptRender,
                    fallbackRoute
            );
            return new RoutePlan(
                    invocation.taskType(),
                    invocation.sceneCode(),
                    normalizedUserTier,
                    "ALL",
                    null,
                    AiRouteStrategyType.SINGLE.name(),
                    List.of(invocation)
            );
        }

        SelectedPolicy selectedPolicy = selectPolicy(candidates, normalizedUserTier);
        // 同一策略下可能有多条候选，FAILOVER/WEIGHTED 在网关执行阶段再决定顺序和失败切换。
        List<AiProviderInvocation> invocations = selectedPolicy.rows().stream()
                .map(routeRow -> buildInvocation(
                        taskType,
                        sceneCode,
                        modelPreference,
                        selectedPolicy.resolvedUserTier(),
                        promptVariables,
                        strictPromptRender,
                        routeRow
                ))
                .toList();
        return new RoutePlan(
                taskType,
                normalizeScene(sceneCode),
                normalizedUserTier,
                selectedPolicy.resolvedUserTier(),
                selectedPolicy.policyCode(),
                selectedPolicy.strategyType(),
                invocations
        );
    }

    private SelectedPolicy selectPolicy(List<AiGatewayAdminRepository.ResolvedRouteRow> routes, String requestedUserTier) {
        // repository 已按 userTier 精确度和排序返回，首条记录代表本次命中的策略。
        AiGatewayAdminRepository.ResolvedRouteRow first = routes.stream()
                .findFirst()
                .orElseThrow(() -> new ApiException("AI-2001", "route config missing", HttpStatus.BAD_GATEWAY));
        String policyCode = safe(first.routePolicyCode());
        Long policyId = first.sceneRoutePolicyId();
        String resolvedTier = normalizeUserTier(first.routePolicyUserTier());
        String strategyType = normalizeStrategy(first.strategyType());
        List<AiGatewayAdminRepository.ResolvedRouteRow> selectedRows = routes.stream()
                .filter(row -> samePolicy(policyId, policyCode, row))
                .toList();
        if (selectedRows.isEmpty()) {
            selectedRows = List.of(first);
        }
        return new SelectedPolicy(
                policyId,
                policyCode.isBlank() ? null : policyCode,
                resolvedTier,
                strategyType,
                selectedRows
        );
    }

    private boolean samePolicy(
            Long policyId,
            String policyCode,
            AiGatewayAdminRepository.ResolvedRouteRow candidate
    ) {
        if (candidate == null) {
            return false;
        }
        if (policyId != null) {
            return policyId.equals(candidate.sceneRoutePolicyId());
        }
        String candidateCode = safe(candidate.routePolicyCode());
        if (!policyCode.isBlank()) {
            return policyCode.equals(candidateCode);
        }
        return candidate.sceneRoutePolicyId() == null;
    }

    private List<AiGatewayAdminRepository.ResolvedRouteRow> getEnabledRoutes(String taskType, String sceneCode, String userTier) {
        return routeCacheService.getEnabledRoutes(
                taskType,
                sceneCode,
                userTier,
                () -> repository.findEnabledRoutes(taskType, sceneCode, userTier)
        );
    }

    private AiProviderInvocation buildInvocation(
            String taskType,
            String sceneCode,
            String modelPreference,
            String userTier,
            Map<String, Object> promptVariables,
            boolean strictPromptRender,
            AiGatewayAdminRepository.ResolvedRouteRow routeRow
    ) {
        String routeModel = hasRealModelPreference(modelPreference) ? modelPreference.trim() : safe(routeRow.modelName());
        if (routeModel.isBlank()) {
            throw new ApiException("AI-2001", "route model missing", HttpStatus.BAD_GATEWAY);
        }
        ResolvedPrompt resolvedPrompt = resolvePrompt(routeRow, promptVariables, strictPromptRender);
        AiExecutionMode executionMode = AiExecutionMode.from(routeRow.executionMode());
        // thinking 与 pricing 在 invocation 内一并固化，调用日志和成本统计共用这份解析结果。
        AiThinkingConfig thinkingConfig = thinkingPolicyResolver.resolve(
                taskType,
                sceneCode,
                executionMode,
                routeModel,
                promptVariables,
                routeRow.providerExtraConfigJson(),
                routeRow.policyExtraConfigJson(),
                routeRow.routeExtraConfigJson()
        );
        AiPricingPolicyResolver.PricingDecision pricingDecision = pricingPolicyResolver.resolve(routeRow);
        return new AiProviderInvocation(
                taskType,
                normalizeScene(sceneCode),
                routeRow.routeCode(),
                executionMode,
                routeRow.providerCode(),
                routeRow.displayName(),
                AiProviderType.from(routeRow.providerType()),
                routeRow.baseUrl(),
                cryptoService.decrypt(routeRow.apiKeyCiphertext()),
                routeModel,
                Duration.ofMillis(Math.max(routeRow.timeoutMs(), 1000)),
                Math.max(routeRow.maxRetries(), 0),
                pricingDecision.inputPer1k(),
                pricingDecision.outputPer1k(),
                routeRow.temperature() == null ? 0.2d : routeRow.temperature().doubleValue(),
                resolvedPrompt.systemPrompt(),
                routeRow.promptTemplateName(),
                routeRow.promptTemplateVersionNo(),
                resolvedPrompt.promptTemplateFormat(),
                resolvedPrompt.renderedBundleJson(),
                resolvedPrompt.promptSeedMessages(),
                thinkingConfig,
                routeRow.providerExtraConfigJson(),
                routeRow.routeExtraConfigJson(),
                safe(routeRow.routePolicyCode()),
                normalizeUserTier(routeRow.routePolicyUserTier()),
                normalizeStrategy(routeRow.strategyType()),
                routeRow.candidateWeight()
        );
    }

    private boolean supportsResumePdf(AiGatewayAdminRepository.ResolvedRouteRow routeRow) {
        if (routeRow == null || routeRow.providerType() == null) {
            return false;
        }
        return AiProviderType.GEMINI_NATIVE.name().equalsIgnoreCase(routeRow.providerType());
    }

    private ResolvedPrompt resolvePrompt(
            AiGatewayAdminRepository.ResolvedRouteRow routeRow,
            Map<String, Object> promptVariables,
            boolean strictPromptRender
    ) {
        if (routeRow.promptTemplateName() == null || routeRow.promptTemplateName().isBlank()) {
            // 没绑定模板时直接使用路由级 system prompt，适合简单场景或历史兼容配置。
            return new ResolvedPrompt(routeRow.routeSystemPrompt(), AiPromptTemplateFormat.TEXT.name(), null, List.of());
        }
        AiPromptTemplateFormat templateFormat = AiPromptTemplateFormat.from(routeRow.promptTemplateFormat());
        if (templateFormat == AiPromptTemplateFormat.MESSAGE_BUNDLE) {
            if (routeRow.promptTemplateBundleJson() == null || routeRow.promptTemplateBundleJson().isBlank()) {
                throw new ApiException("AI-2003", "active prompt template bundle missing", HttpStatus.BAD_GATEWAY);
            }
            // runtime 渲染严格要求变量齐全；preview 渲染用于后台巡检，允许保留占位信息。
            PromptTemplateRenderService.BundleRenderResult renderResult = strictPromptRender
                    ? promptTemplateRenderService.renderBundleForRuntime(
                    routeRow.promptTemplateContent(),
                    routeRow.promptTemplateVariablesJson(),
                    routeRow.promptTemplateBundleJson(),
                    promptVariables
            )
                    : promptTemplateRenderService.renderBundleForPreview(
                    routeRow.promptTemplateContent(),
                    routeRow.promptTemplateVariablesJson(),
                    routeRow.promptTemplateBundleJson(),
                    promptVariables
            );
            return new ResolvedPrompt(
                    mergePromptSections(routeRow.routeSystemPrompt(), renderResult.systemPrompt()),
                    templateFormat.name(),
                    renderResult.renderedBundleJson(),
                    renderResult.promptSeedMessages()
            );
        }
        if (routeRow.promptTemplateContent() == null || routeRow.promptTemplateContent().isBlank()) {
            throw new ApiException("AI-2003", "active prompt template missing", HttpStatus.BAD_GATEWAY);
        }
        PromptTemplateRenderService.RenderResult renderResult = strictPromptRender
                ? promptTemplateRenderService.renderForRuntime(routeRow.promptTemplateContent(), routeRow.promptTemplateVariablesJson(), promptVariables)
                : promptTemplateRenderService.renderForPreview(routeRow.promptTemplateContent(), routeRow.promptTemplateVariablesJson(), promptVariables);
        return new ResolvedPrompt(
                mergePromptSections(routeRow.routeSystemPrompt(), renderResult.renderedContent()),
                templateFormat.name(),
                null,
                List.of()
        );
    }

    private AiProviderInvocation buildLegacyOpenAiInvocation(String taskType, String sceneCode, String modelPreference) {
        AiGatewayProperties.OpenAiCompatible legacy = properties.getOpenaiCompatible();
        String model = hasRealModelPreference(modelPreference)
                ? modelPreference.trim()
                : chooseLegacyModel(taskType, legacy);
        return new AiProviderInvocation(
                taskType,
                normalizeScene(sceneCode),
                "LEGACY_OPENAI_ROUTE",
                AiExecutionMode.SYNC_BLOCKING,
                safe(legacy.getProviderKey()).isBlank() ? "openai-compatible" : legacy.getProviderKey().trim(),
                "Legacy OpenAI Compatible",
                AiProviderType.OPENAI_COMPATIBLE,
                legacy.getBaseUrl(),
                legacy.getApiKey(),
                model,
                Duration.ofMillis(Math.max(legacy.getTimeoutMs(), 1000)),
                Math.max(legacy.getMaxRetries(), 0),
                legacy.getCostPer1kInput(),
                legacy.getCostPer1kOutput(),
                legacy.getTemperature(),
                null,
                null,
                null,
                AiPromptTemplateFormat.TEXT.name(),
                null,
                List.of(),
                thinkingPolicyResolver.resolve(
                        taskType,
                        sceneCode,
                        AiExecutionMode.SYNC_BLOCKING,
                        model,
                        Collections.emptyMap(),
                        null,
                        null,
                        null
                ),
                null,
                null,
                null,
                "ALL",
                AiRouteStrategyType.SINGLE.name(),
                100
        );
    }

    private AiGatewayAdminRepository.ResolvedRouteRow buildLegacyFallbackRoute(String taskType, String sceneCode, String modelPreference) {
        AiGatewayProperties.OpenAiCompatible legacy = properties.getOpenaiCompatible();
        // fallback 是运行时保底，不是正常策略命中；关闭开关后直接暴露路由缺失问题。
        if (!properties.isAllowLegacyFallback()) {
            throw new ApiException("AI-2001", "route config missing", HttpStatus.BAD_GATEWAY);
        }
        if (legacy.getBaseUrl() == null || legacy.getBaseUrl().isBlank()) {
            throw new ApiException("AI-2001", "route config missing", HttpStatus.BAD_GATEWAY);
        }
        return new AiGatewayAdminRepository.ResolvedRouteRow(
                null,
                null,
                "ALL",
                AiRouteStrategyType.SINGLE.name(),
                "LEGACY_FALLBACK_ROUTE",
                taskType,
                normalizeScene(sceneCode),
                hasRealModelPreference(modelPreference) ? modelPreference.trim() : chooseLegacyModel(taskType, legacy),
                9999,
                100,
                AiExecutionMode.SYNC_BLOCKING.name(),
                BigDecimal.valueOf(legacy.getTemperature()),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                safe(legacy.getProviderKey()).isBlank() ? "openai-compatible" : legacy.getProviderKey().trim(),
                AiProviderType.OPENAI_COMPATIBLE.name(),
                "Legacy OpenAI Compatible",
                legacy.getBaseUrl(),
                cryptoService.encrypt(legacy.getApiKey()),
                legacy.getTimeoutMs(),
                legacy.getMaxRetries(),
                legacy.getCostPer1kInput(),
                legacy.getCostPer1kOutput(),
                null,
                null,
                null
        );
    }

    private String chooseLegacyModel(String taskType, AiGatewayProperties.OpenAiCompatible legacy) {
        if ("STT".equalsIgnoreCase(taskType) && legacy.getSpeechToTextModel() != null && !legacy.getSpeechToTextModel().isBlank()) {
            return legacy.getSpeechToTextModel().trim();
        }
        if ("TTS".equalsIgnoreCase(taskType) && legacy.getTextToSpeechModel() != null && !legacy.getTextToSpeechModel().isBlank()) {
            return legacy.getTextToSpeechModel().trim();
        }
        return safe(legacy.getDefaultModel()).trim();
    }

    private String normalizeScene(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return rawValue.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeUserTier(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return "ALL";
        }
        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ALL", "FREE", "PREMIUM" -> normalized;
            default -> "ALL";
        };
    }

    private String normalizeStrategy(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return AiRouteStrategyType.SINGLE.name();
        }
        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "SINGLE", "FAILOVER", "WEIGHTED" -> normalized;
            default -> AiRouteStrategyType.SINGLE.name();
        };
    }

    private boolean hasRealModelPreference(String modelPreference) {
        if (modelPreference == null || modelPreference.isBlank()) {
            return false;
        }
        String normalized = modelPreference.trim().toLowerCase(Locale.ROOT);
        return !normalized.startsWith("mock-");
    }

    private String mergePromptSections(String routeSystemPrompt, String templateSystemPrompt) {
        String normalizedRoutePrompt = safe(routeSystemPrompt).trim();
        String normalizedTemplatePrompt = safe(templateSystemPrompt).trim();
        if (normalizedRoutePrompt.isBlank()) {
            return normalizedTemplatePrompt.isBlank() ? null : normalizedTemplatePrompt;
        }
        if (normalizedTemplatePrompt.isBlank()) {
            return normalizedRoutePrompt;
        }
        return normalizedRoutePrompt + "\n\n" + normalizedTemplatePrompt;
    }

    private String safe(String rawValue) {
        return rawValue == null ? "" : rawValue;
    }

    public enum AiRouteStrategyType {
        SINGLE,
        FAILOVER,
        WEIGHTED
    }

    public record RoutePlan(
            String taskType,
            String sceneCode,
            String requestedUserTier,
            String resolvedUserTier,
            String routePolicyCode,
            String strategyType,
            List<AiProviderInvocation> candidates
    ) {
        public AiProviderInvocation primaryCandidate() {
            return candidates == null || candidates.isEmpty()
                    ? null
                    : candidates.getFirst();
        }
    }

    private record ResolvedPrompt(
            String systemPrompt,
            String promptTemplateFormat,
            String renderedBundleJson,
            List<AiChatMessage> promptSeedMessages
    ) {
    }

    private record SelectedPolicy(
            Long policyId,
            String policyCode,
            String resolvedUserTier,
            String strategyType,
            List<AiGatewayAdminRepository.ResolvedRouteRow> rows
    ) {
    }
}
