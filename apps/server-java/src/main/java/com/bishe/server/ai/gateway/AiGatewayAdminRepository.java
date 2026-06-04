package com.bishe.server.ai.gateway;

import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * AI 网关已解析路由查询仓储。
 */
@Repository
public class AiGatewayAdminRepository {

    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String ALL_TIER = "ALL";
    private static final String FREE_TIER = "FREE";
    private static final String PREMIUM_TIER = "PREMIUM";
    private static final String DEFAULT_STRATEGY = "SINGLE";

    private final AiModelRouteRepository aiModelRouteRepository;
    private final SceneRoutePolicyRepository sceneRoutePolicyRepository;
    private final AiProviderConfigRepository aiProviderConfigRepository;
    private final AiProviderModelRepository aiProviderModelRepository;
    private final PromptTemplateRepository promptTemplateRepository;

    public AiGatewayAdminRepository(
            AiModelRouteRepository aiModelRouteRepository,
            SceneRoutePolicyRepository sceneRoutePolicyRepository,
            AiProviderConfigRepository aiProviderConfigRepository,
            AiProviderModelRepository aiProviderModelRepository,
            PromptTemplateRepository promptTemplateRepository
    ) {
        this.aiModelRouteRepository = aiModelRouteRepository;
        this.sceneRoutePolicyRepository = sceneRoutePolicyRepository;
        this.aiProviderConfigRepository = aiProviderConfigRepository;
        this.aiProviderModelRepository = aiProviderModelRepository;
        this.promptTemplateRepository = promptTemplateRepository;
    }

    public Optional<ResolvedRouteRow> findBestEnabledRoute(String taskType, String sceneCode, String userTier) {
        return findEnabledRoutes(taskType, sceneCode, userTier).stream().findFirst();
    }

    public Optional<ResolvedRouteRow> findBestEnabledRoute(String taskType, String sceneCode) {
        return findBestEnabledRoute(taskType, sceneCode, null);
    }

    public List<ResolvedRouteRow> findEnabledRoutes(String taskType, String sceneCode) {
        return findEnabledRoutes(taskType, sceneCode, null);
    }

    public List<ResolvedRouteRow> findEnabledRoutes(String taskType, String sceneCode, String userTier) {
        String normalizedTaskType = normalizeCode(taskType);
        if (normalizedTaskType == null) {
            return List.of();
        }
        String normalizedSceneCode = normalizeCode(sceneCode);
        String normalizedTier = normalizeTier(userTier);
        ResolvedRouteLookup lookup = buildResolvedRouteLookup(normalizedTaskType);
        List<ResolvedRouteRow> routes = findEnabledRoutesFromPolicies(normalizedTaskType, normalizedSceneCode, normalizedTier, lookup);
        if (!routes.isEmpty()) {
            return routes;
        }
        return findEnabledRoutesLegacy(normalizedTaskType, normalizedSceneCode, lookup);
    }

    private List<ResolvedRouteRow> findEnabledRoutesFromPolicies(
            String taskType,
            String sceneCode,
            String userTier,
            ResolvedRouteLookup lookup
    ) {
        if (sceneCode == null) {
            return List.of();
        }
        Comparator<SceneRoutePolicyRepository.SceneRoutePolicyRow> policyComparator = Comparator
                .comparingInt((SceneRoutePolicyRepository.SceneRoutePolicyRow policy) -> policyTierPriority(policy.userTier(), userTier))
                .thenComparingLong(SceneRoutePolicyRepository.SceneRoutePolicyRow::id);
        Comparator<AiModelRouteRepository.ModelRouteRow> routeComparator = Comparator
                .comparingInt(AiModelRouteRepository.ModelRouteRow::priorityNo)
                .thenComparingLong(AiModelRouteRepository.ModelRouteRow::id);
        return lookup.policies().stream()
                .filter(SceneRoutePolicyRepository.SceneRoutePolicyRow::enabled)
                .filter(policy -> taskType.equals(normalizeCode(policy.taskType())))
                .filter(policy -> sceneCode.equals(normalizeCode(policy.sceneCode())))
                .filter(policy -> matchesPolicyTier(policy.userTier(), userTier))
                .sorted(policyComparator)
                .flatMap(policy -> lookup.routesByPolicyId().getOrDefault(policy.id(), List.of()).stream()
                        .sorted(routeComparator)
                        .map(route -> toResolvedRouteRow(route, policy, lookup))
                        .flatMap(Optional::stream))
                .toList();
    }

    private List<ResolvedRouteRow> findEnabledRoutesLegacy(
            String taskType,
            String sceneCode,
            ResolvedRouteLookup lookup
    ) {
        Comparator<AiModelRouteRepository.ModelRouteRow> routeComparator = Comparator
                .comparingInt((AiModelRouteRepository.ModelRouteRow route) -> isExactLegacySceneRoute(route.sceneCode(), sceneCode) ? 0 : 1)
                .thenComparingInt(AiModelRouteRepository.ModelRouteRow::priorityNo)
                .thenComparingLong(AiModelRouteRepository.ModelRouteRow::id);
        return lookup.legacyRoutes().stream()
                .filter(route -> taskType.equals(normalizeCode(route.taskType())))
                .filter(route -> isCompatibleLegacySceneRoute(route.sceneCode(), sceneCode))
                .sorted(routeComparator)
                .map(route -> toResolvedRouteRow(route, null, lookup))
                .flatMap(Optional::stream)
                .toList();
    }

    private ResolvedRouteLookup buildResolvedRouteLookup(String taskType) {
        List<AiModelRouteRepository.ModelRouteRow> routes = aiModelRouteRepository.findAllRoutes().stream()
                .filter(AiModelRouteRepository.ModelRouteRow::enabled)
                .filter(route -> taskType.equals(normalizeCode(route.taskType())))
                .toList();

        Set<Long> providerIds = routes.stream()
                .map(AiModelRouteRepository.ModelRouteRow::providerConfigId)
                .collect(java.util.stream.Collectors.toSet());

        Map<Long, AiProviderConfigRepository.ProviderConfigRow> providersById = new HashMap<>();
        for (AiProviderConfigRepository.ProviderConfigRow provider : aiProviderConfigRepository.findAllProviders()) {
            if (providerIds.contains(provider.id())) {
                providersById.put(provider.id(), provider);
            }
        }

        Map<ProviderModelKey, AiProviderModelRepository.ProviderModelRow> providerModelsByKey = new HashMap<>();
        if (!providerIds.isEmpty()) {
            List<Long> orderedProviderIds = new ArrayList<>(providerIds);
            orderedProviderIds.sort(Long::compareTo);
            for (AiProviderModelRepository.ProviderModelRow providerModel : aiProviderModelRepository.findProviderModelsByProviderIds(orderedProviderIds)) {
                if (!providerModel.enabled()) {
                    continue;
                }
                ProviderModelKey key = providerModelKey(providerModel.providerConfigId(), providerModel.modelCode());
                if (key != null) {
                    providerModelsByKey.putIfAbsent(key, providerModel);
                }
            }
        }

        Set<PromptTemplateKey> promptTemplateKeys = routes.stream()
                .map(route -> promptTemplateKey(route.taskType(), route.promptTemplateName()))
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Map<PromptTemplateKey, PromptTemplateRepository.PromptTemplateRow> activePromptTemplates = buildActivePromptTemplateMap(promptTemplateKeys);

        Map<Long, List<AiModelRouteRepository.ModelRouteRow>> routesByPolicyId = new LinkedHashMap<>();
        List<AiModelRouteRepository.ModelRouteRow> legacyRoutes = new ArrayList<>();
        for (AiModelRouteRepository.ModelRouteRow route : routes) {
            if (route.sceneRoutePolicyId() == null) {
                legacyRoutes.add(route);
                continue;
            }
            routesByPolicyId.computeIfAbsent(route.sceneRoutePolicyId(), ignored -> new ArrayList<>()).add(route);
        }

        List<SceneRoutePolicyRepository.SceneRoutePolicyRow> policies = sceneRoutePolicyRepository.findAllRoutePolicies().stream()
                .filter(policy -> taskType.equals(normalizeCode(policy.taskType())))
                .toList();

        return new ResolvedRouteLookup(
                providersById,
                providerModelsByKey,
                activePromptTemplates,
                routesByPolicyId,
                legacyRoutes,
                policies
        );
    }

    private Map<PromptTemplateKey, PromptTemplateRepository.PromptTemplateRow> buildActivePromptTemplateMap(
            Set<PromptTemplateKey> promptTemplateKeys
    ) {
        if (promptTemplateKeys.isEmpty()) {
            return Map.of();
        }
        Map<PromptTemplateKey, PromptTemplateRepository.PromptTemplateRow> activePromptTemplates = new HashMap<>();
        for (PromptTemplateRepository.PromptTemplateRow promptTemplate : promptTemplateRepository.findAllPromptTemplates()) {
            if (!ACTIVE_STATUS.equalsIgnoreCase(safe(promptTemplate.status()).trim())) {
                continue;
            }
            PromptTemplateKey key = promptTemplateKey(promptTemplate.taskType(), promptTemplate.templateName());
            if (key == null || !promptTemplateKeys.contains(key)) {
                continue;
            }
            activePromptTemplates.putIfAbsent(key, promptTemplate);
        }
        return activePromptTemplates;
    }

    private Optional<ResolvedRouteRow> toResolvedRouteRow(
            AiModelRouteRepository.ModelRouteRow route,
            SceneRoutePolicyRepository.SceneRoutePolicyRow policy,
            ResolvedRouteLookup lookup
    ) {
        AiProviderConfigRepository.ProviderConfigRow provider = lookup.providersById().get(route.providerConfigId());
        if (provider == null || !provider.enabled()) {
            return Optional.empty();
        }
        PromptTemplateKey promptTemplateKey = promptTemplateKey(route.taskType(), route.promptTemplateName());
        PromptTemplateRepository.PromptTemplateRow promptTemplate = promptTemplateKey == null
                ? null
                : lookup.activePromptTemplates().get(promptTemplateKey);
        ProviderModelKey providerModelKey = providerModelKey(route.providerConfigId(), route.modelName());
        AiProviderModelRepository.ProviderModelRow providerModel = providerModelKey == null
                ? null
                : lookup.providerModelsByKey().get(providerModelKey);
        return Optional.of(new ResolvedRouteRow(
                policy == null ? null : policy.id(),
                policy == null ? null : policy.policyCode(),
                policy == null ? null : policy.userTier(),
                policy == null ? DEFAULT_STRATEGY : normalizeStrategyType(policy.strategyType()),
                route.routeCode(),
                route.taskType(),
                route.sceneCode(),
                route.modelName(),
                route.priorityNo(),
                route.candidateWeight(),
                route.executionMode(),
                route.temperature(),
                route.systemPrompt(),
                route.promptTemplateName(),
                promptTemplate == null ? null : promptTemplate.versionNo(),
                promptTemplate == null ? null : promptTemplate.templateFormat(),
                promptTemplate == null ? null : promptTemplate.content(),
                promptTemplate == null ? null : promptTemplate.variablesJson(),
                promptTemplate == null ? null : promptTemplate.bundleJson(),
                policy == null ? null : policy.extraConfigJson(),
                route.extraConfigJson(),
                provider.providerCode(),
                provider.providerType(),
                provider.displayName(),
                provider.baseUrl(),
                provider.apiKeyCiphertext(),
                provider.timeoutMs(),
                provider.maxRetries(),
                provider.costPer1kInput(),
                provider.costPer1kOutput(),
                providerModel == null ? null : providerModel.inputCostPer1k(),
                providerModel == null ? null : providerModel.outputCostPer1k(),
                provider.extraConfigJson()
        ));
    }

    private boolean matchesPolicyTier(String policyUserTier, String requestedUserTier) {
        String normalizedPolicyTier = normalizeTier(policyUserTier);
        if (requestedUserTier == null) {
            return ALL_TIER.equals(normalizedPolicyTier)
                    || FREE_TIER.equals(normalizedPolicyTier)
                    || PREMIUM_TIER.equals(normalizedPolicyTier);
        }
        return requestedUserTier.equals(normalizedPolicyTier) || ALL_TIER.equals(normalizedPolicyTier);
    }

    private int policyTierPriority(String policyUserTier, String requestedUserTier) {
        String normalizedPolicyTier = normalizeTier(policyUserTier);
        if (requestedUserTier == null) {
            if (ALL_TIER.equals(normalizedPolicyTier)) {
                return 0;
            }
            if (FREE_TIER.equals(normalizedPolicyTier)) {
                return 1;
            }
            if (PREMIUM_TIER.equals(normalizedPolicyTier)) {
                return 2;
            }
            return 3;
        }
        return requestedUserTier.equals(normalizedPolicyTier) ? 0 : 1;
    }

    private boolean isCompatibleLegacySceneRoute(String routeSceneCode, String requestedSceneCode) {
        String normalizedRouteSceneCode = normalizeCode(routeSceneCode);
        if (requestedSceneCode == null) {
            return normalizedRouteSceneCode == null;
        }
        return requestedSceneCode.equals(normalizedRouteSceneCode) || normalizedRouteSceneCode == null;
    }

    private boolean isExactLegacySceneRoute(String routeSceneCode, String requestedSceneCode) {
        String normalizedRouteSceneCode = normalizeCode(routeSceneCode);
        return requestedSceneCode != null && requestedSceneCode.equals(normalizedRouteSceneCode);
    }

    private String normalizeStrategyType(String rawValue) {
        String normalized = normalizeCode(rawValue);
        return normalized == null ? DEFAULT_STRATEGY : normalized;
    }

    private PromptTemplateKey promptTemplateKey(String taskType, String promptTemplateName) {
        String normalizedTaskType = normalizeCode(taskType);
        String normalizedTemplateName = normalizeCode(promptTemplateName);
        if (normalizedTaskType == null || normalizedTemplateName == null) {
            return null;
        }
        return new PromptTemplateKey(normalizedTaskType, normalizedTemplateName);
    }

    private ProviderModelKey providerModelKey(long providerConfigId, String modelCode) {
        String normalizedModelCode = normalizeCode(modelCode);
        if (normalizedModelCode == null) {
            return null;
        }
        return new ProviderModelKey(providerConfigId, normalizedModelCode);
    }

    private static String safe(String rawValue) {
        return rawValue == null ? "" : rawValue;
    }

    private static String normalizeCode(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return rawValue.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeTier(String rawValue) {
        String normalized = normalizeCode(rawValue);
        return normalized == null ? null : normalized;
    }

    public record ResolvedRouteRow(
            Long sceneRoutePolicyId,
            String routePolicyCode,
            String routePolicyUserTier,
            String strategyType,
            String routeCode,
            String taskType,
            String sceneCode,
            String modelName,
            int priorityNo,
            int candidateWeight,
            String executionMode,
            BigDecimal temperature,
            String routeSystemPrompt,
            String promptTemplateName,
            Integer promptTemplateVersionNo,
            String promptTemplateFormat,
            String promptTemplateContent,
            String promptTemplateVariablesJson,
            String promptTemplateBundleJson,
            String policyExtraConfigJson,
            String routeExtraConfigJson,
            String providerCode,
            String providerType,
            String displayName,
            String baseUrl,
            String apiKeyCiphertext,
            int timeoutMs,
            int maxRetries,
            BigDecimal costPer1kInput,
            BigDecimal costPer1kOutput,
            BigDecimal modelInputCostPer1k,
            BigDecimal modelOutputCostPer1k,
            String providerExtraConfigJson
    ) {
    }

    private record PromptTemplateKey(
            String taskType,
            String templateName
    ) {
    }

    private record ProviderModelKey(
            long providerConfigId,
            String modelCode
    ) {
    }

    private record ResolvedRouteLookup(
            Map<Long, AiProviderConfigRepository.ProviderConfigRow> providersById,
            Map<ProviderModelKey, AiProviderModelRepository.ProviderModelRow> providerModelsByKey,
            Map<PromptTemplateKey, PromptTemplateRepository.PromptTemplateRow> activePromptTemplates,
            Map<Long, List<AiModelRouteRepository.ModelRouteRow>> routesByPolicyId,
            List<AiModelRouteRepository.ModelRouteRow> legacyRoutes,
            List<SceneRoutePolicyRepository.SceneRoutePolicyRow> policies
    ) {
    }
}
