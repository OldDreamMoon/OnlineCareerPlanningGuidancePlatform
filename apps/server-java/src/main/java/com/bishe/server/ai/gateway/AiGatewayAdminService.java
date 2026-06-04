package com.bishe.server.ai.gateway;

import com.bishe.server.common.exception.ApiException;
import com.bishe.server.dashboard.AdminWorkbenchCacheService;
import com.bishe.server.governance.ContentGovernanceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.time.temporal.ChronoUnit;

/**
 * AI 网关后台管理服务。
 */
@Service
public class AiGatewayAdminService {

    private static final String ROUTE_POLICY_THINKING_SOURCE = "ROUTE_POLICY_CONFIG";

    private final AiGatewayAdminRepository repository;
    private final AiGatewayAdminAnalyticsRepository analyticsRepository;
    private final AiRouteResolver routeResolver;
    private final AiRouteCacheService aiRouteCacheService;
    private final AiGatewayAdminListCacheService aiGatewayAdminListCacheService;
    private final AdminWorkbenchCacheService adminWorkbenchCacheService;
    private final PromptTemplateRepository promptTemplateRepository;
    private final SceneRoutePolicyRepository sceneRoutePolicyRepository;
    private final AiProviderConfigRepository aiProviderConfigRepository;
    private final AiProviderModelRepository aiProviderModelRepository;
    private final AiModelRouteRepository aiModelRouteRepository;
    private final AiGatewayRuntimeSettingsService runtimeSettingsService;
    private final AiConfigCryptoService cryptoService;
    private final GeminiTransportResolver geminiTransportResolver;
    private final PromptTemplateRenderService promptTemplateRenderService;
    private final AiThinkingPolicyResolver thinkingPolicyResolver;
    private final AiPricingPolicyResolver pricingPolicyResolver;
    private final ContentGovernanceRepository contentGovernanceRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AiGatewayAdminService(
            AiGatewayAdminRepository repository,
            AiGatewayAdminAnalyticsRepository analyticsRepository,
            AiRouteResolver routeResolver,
            AiRouteCacheService aiRouteCacheService,
            AiGatewayAdminListCacheService aiGatewayAdminListCacheService,
            AdminWorkbenchCacheService adminWorkbenchCacheService,
            PromptTemplateRepository promptTemplateRepository,
            SceneRoutePolicyRepository sceneRoutePolicyRepository,
            AiProviderConfigRepository aiProviderConfigRepository,
            AiProviderModelRepository aiProviderModelRepository,
            AiModelRouteRepository aiModelRouteRepository,
            AiGatewayRuntimeSettingsService runtimeSettingsService,
            AiConfigCryptoService cryptoService,
            GeminiTransportResolver geminiTransportResolver,
            PromptTemplateRenderService promptTemplateRenderService,
            AiThinkingPolicyResolver thinkingPolicyResolver,
            AiPricingPolicyResolver pricingPolicyResolver,
            ContentGovernanceRepository contentGovernanceRepository,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.analyticsRepository = analyticsRepository;
        this.routeResolver = routeResolver;
        this.aiRouteCacheService = aiRouteCacheService;
        this.aiGatewayAdminListCacheService = aiGatewayAdminListCacheService;
        this.adminWorkbenchCacheService = adminWorkbenchCacheService;
        this.promptTemplateRepository = promptTemplateRepository;
        this.sceneRoutePolicyRepository = sceneRoutePolicyRepository;
        this.aiProviderConfigRepository = aiProviderConfigRepository;
        this.aiProviderModelRepository = aiProviderModelRepository;
        this.aiModelRouteRepository = aiModelRouteRepository;
        this.runtimeSettingsService = runtimeSettingsService;
        this.cryptoService = cryptoService;
        this.geminiTransportResolver = geminiTransportResolver;
        this.promptTemplateRenderService = promptTemplateRenderService;
        this.thinkingPolicyResolver = thinkingPolicyResolver;
        this.pricingPolicyResolver = pricingPolicyResolver;
        this.contentGovernanceRepository = contentGovernanceRepository;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public AdminMetaPayload getMeta() {
        List<ProviderOption> providerTypes = List.of(
                new ProviderOption(AiProviderType.OPENAI_COMPATIBLE.name(), "OpenAI 兼容接口"),
                new ProviderOption(AiProviderType.GEMINI_NATIVE.name(), "Gemini 原生接口")
        );
        List<TaskTypeOption> taskTypes = AiGatewayTaskType.codes().stream()
                .map(code -> new TaskTypeOption(code, taskTypeLabel(code)))
                .toList();
        List<ExecutionModeOption> executionModes = AiExecutionMode.codes().stream()
                .map(code -> new ExecutionModeOption(code, executionModeLabel(code)))
                .toList();
        List<TierOption> tierOptions = List.of(
                new TierOption("ALL", "通用"),
                new TierOption("FREE", "普通"),
                new TierOption("PREMIUM", "VIP")
        );
        List<RouteStrategyOption> routeStrategyTypes = List.of(
                new RouteStrategyOption("SINGLE", "单通道"),
                new RouteStrategyOption("FAILOVER", "故障转移"),
                new RouteStrategyOption("WEIGHTED", "加权分流")
        );
        return new AdminMetaPayload(providerTypes, taskTypes, executionModes, tierOptions, routeStrategyTypes);
    }

    public ProviderListPayload listProviders() {
        return aiGatewayAdminListCacheService.getProviders(() -> {
            List<AiProviderConfigRepository.ProviderConfigRow> providers = aiProviderConfigRepository.findAllProviders().stream()
                    .sorted(Comparator.comparing(AiProviderConfigRepository.ProviderConfigRow::enabled).reversed()
                            .thenComparing(AiProviderConfigRepository.ProviderConfigRow::providerCode))
                    .toList();
            Map<Long, List<ProviderModelItem>> modelsByProviderId = buildProviderModelsByProviderId(
                    providers.stream().map(AiProviderConfigRepository.ProviderConfigRow::id).toList()
            );
            List<ProviderItem> records = providers.stream()
                    .map(provider -> toProviderItem(provider, modelsByProviderId.getOrDefault(provider.id(), List.of())))
                    .toList();
            return new ProviderListPayload(records);
        });
    }

    public RoutePolicyListPayload listRoutePolicies() {
        List<SceneRoutePolicyRepository.SceneRoutePolicyRow> policies = sceneRoutePolicyRepository.findAllRoutePolicies();
        Map<Long, List<RouteCandidateItem>> candidatesByPolicyId = new LinkedHashMap<>();
        aiModelRouteRepository.findAllRoutes().forEach(route -> {
            if (route.sceneRoutePolicyId() == null) {
                return;
            }
            candidatesByPolicyId.computeIfAbsent(route.sceneRoutePolicyId(), ignored -> new ArrayList<>())
                    .add(toRouteCandidateItem(route));
        });
        candidatesByPolicyId.values().forEach(items -> items.sort(Comparator
                .comparing(RouteCandidateItem::priorityNo)
                .thenComparing(RouteCandidateItem::routeCode)));
        List<RoutePolicyItem> records = policies.stream()
                .map(policy -> toRoutePolicyItem(policy, candidatesByPolicyId.getOrDefault(policy.id(), List.of())))
                .toList();
        return new RoutePolicyListPayload(records);
    }

    public RouteListPayload listRoutes() {
        return aiGatewayAdminListCacheService.getRoutes(() -> {
            List<RouteItem> records = aiModelRouteRepository.findAllRoutes().stream()
                    .map(this::toRouteItem)
                    .toList();
            return new RouteListPayload(records);
        });
    }

    public PromptTemplateListPayload listPromptTemplates() {
        return aiGatewayAdminListCacheService.getPromptTemplates(() -> {
            List<PromptTemplateItem> records = promptTemplateRepository.findAllPromptTemplates().stream()
                    .map(this::toPromptTemplateItem)
                    .toList();
            return new PromptTemplateListPayload(records);
        });
    }

    @Transactional
    public ProviderItem createProvider(UpsertProviderCommand command) {
        NormalizedProvider normalized = normalizeProvider(command, null);
        long providerId = aiProviderConfigRepository.insertProvider(
                normalized.providerCode(),
                normalized.providerType().name(),
                normalized.displayName(),
                normalized.baseUrl(),
                normalized.apiKeyCiphertext(),
                normalized.apiKeyMasked(),
                normalized.enabled(),
                normalized.timeoutMs(),
                normalized.maxRetries(),
                normalized.costPer1kInput(),
                normalized.costPer1kOutput(),
                normalized.extraConfigJson()
        );
        aiProviderModelRepository.replaceProviderModels(providerId, normalized.models());
        evictAiGatewayConfigCachesNowAndAfterCommit();
        evictAdminWorkbenchCacheNowAndAfterCommit();
        return getProviderItem(providerId);
    }

    @Transactional
    public ProviderItem updateProvider(long providerId, UpsertProviderCommand command) {
        AiProviderConfigRepository.ProviderConfigRow current = aiProviderConfigRepository.findProviderById(providerId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "provider not found", HttpStatus.NOT_FOUND));
        NormalizedProvider normalized = normalizeProvider(command, current);
        aiProviderConfigRepository.updateProvider(
                providerId,
                normalized.providerCode(),
                normalized.providerType().name(),
                normalized.displayName(),
                normalized.baseUrl(),
                normalized.apiKeyCiphertext(),
                normalized.apiKeyMasked(),
                normalized.enabled(),
                normalized.timeoutMs(),
                normalized.maxRetries(),
                normalized.costPer1kInput(),
                normalized.costPer1kOutput(),
                normalized.extraConfigJson()
        );
        if (normalized.replaceModels()) {
            aiProviderModelRepository.replaceProviderModels(providerId, normalized.models());
        }
        evictAiGatewayConfigCachesNowAndAfterCommit();
        evictAdminWorkbenchCacheNowAndAfterCommit();
        return getProviderItem(providerId);
    }

    public ProviderConnectivityPayload testProviderConnectivity(long providerId) {
        AiProviderConfigRepository.ProviderConfigRow provider = aiProviderConfigRepository.findProviderById(providerId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "provider not found", HttpStatus.NOT_FOUND));
        Instant checkedAt = Instant.now();
        String baseUrl = provider.baseUrl() == null ? "" : provider.baseUrl().trim();
        if (baseUrl.isBlank()) {
            return buildProviderConnectivityPayload(provider, null, false, false, false, null, 0L, "INVALID_CONFIG", "Base URL 未配置", checkedAt);
        }
        if (provider.apiKeyCiphertext() == null || provider.apiKeyCiphertext().isBlank()) {
            return buildProviderConnectivityPayload(provider, null, false, false, false, null, 0L, "INVALID_CONFIG", "API Key 未配置", checkedAt);
        }

        String apiKey;
        try {
            apiKey = cryptoService.decrypt(provider.apiKeyCiphertext());
        } catch (Exception ex) {
            return buildProviderConnectivityPayload(provider, null, false, false, false, null, 0L, "INVALID_CONFIG", "API Key 解密失败", checkedAt);
        }

        AiProviderType providerType;
        try {
            providerType = parseProviderType(provider.providerType(), provider.providerType());
        } catch (ApiException ex) {
            return buildProviderConnectivityPayload(provider, null, false, false, false, null, 0L, "UNSUPPORTED", "Provider 类型不受支持", checkedAt);
        }

        ProviderProbeRequest probeRequest = buildProviderProbeRequest(providerType, baseUrl, apiKey, provider.extraConfigJson());
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(probeRequest.probeUrl()))
                .GET()
                .timeout(Duration.ofMillis(Math.max(1000, provider.timeoutMs())))
                .header("Accept", "application/json");
        probeRequest.headers().forEach(requestBuilder::header);

        long startedAt = System.nanoTime();
        try {
            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long latencyMs = Math.max(Duration.ofNanos(System.nanoTime() - startedAt).toMillis(), 1L);
            int httpStatus = response.statusCode();
            boolean available = httpStatus >= 200 && httpStatus < 300;
            boolean authenticated = available;
            String status = available ? "SUCCESS" : (httpStatus == 401 || httpStatus == 403 ? "AUTH_ERROR" : "HTTP_ERROR");
            String message = available
                    ? "连通成功，可正常访问上游模型列表"
                    : "上游返回 HTTP " + httpStatus + "，请检查 Base URL 与 API Key";
            return buildProviderConnectivityPayload(provider, probeRequest.probeUrl(), true, authenticated, available, httpStatus, latencyMs, status, message, checkedAt);
        } catch (HttpTimeoutException ex) {
            long latencyMs = Math.max(Duration.ofNanos(System.nanoTime() - startedAt).toMillis(), 1L);
            return buildProviderConnectivityPayload(provider, probeRequest.probeUrl(), false, false, false, null, latencyMs, "TIMEOUT", "连通性测试超时，请检查网络或上游响应速度", checkedAt);
        } catch (IllegalArgumentException ex) {
            return buildProviderConnectivityPayload(provider, probeRequest.probeUrl(), false, false, false, null, 0L, "INVALID_CONFIG", "Provider Base URL 非法", checkedAt);
        } catch (Exception ex) {
            long latencyMs = Math.max(Duration.ofNanos(System.nanoTime() - startedAt).toMillis(), 1L);
            return buildProviderConnectivityPayload(provider, probeRequest.probeUrl(), false, false, false, null, latencyMs, "NETWORK_ERROR", summarizeConnectivityException(ex), checkedAt);
        }
    }

    @Transactional
    public RoutePolicyItem createRoutePolicy(UpsertRoutePolicyCommand command) {
        NormalizedRoutePolicy normalized = normalizeRoutePolicy(command, null);
        long policyId = sceneRoutePolicyRepository.insertRoutePolicy(
                normalized.policyCode(),
                normalized.taskType(),
                normalized.sceneCode(),
                normalized.userTier(),
                normalized.strategyType(),
                normalized.enabled(),
                normalized.notes(),
                normalized.extraConfigJson()
        );
        evictAiGatewayConfigCachesNowAndAfterCommit();
        return getRoutePolicyItem(policyId);
    }

    @Transactional
    public RoutePolicyItem updateRoutePolicy(long policyId, UpsertRoutePolicyCommand command) {
        SceneRoutePolicyRepository.SceneRoutePolicyRow current = sceneRoutePolicyRepository.findRoutePolicyById(policyId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "route policy not found", HttpStatus.NOT_FOUND));
        NormalizedRoutePolicy normalized = normalizeRoutePolicy(command, current);
        sceneRoutePolicyRepository.updateRoutePolicy(
                policyId,
                normalized.policyCode(),
                normalized.taskType(),
                normalized.sceneCode(),
                normalized.userTier(),
                normalized.strategyType(),
                normalized.enabled(),
                normalized.notes(),
                normalized.extraConfigJson()
        );
        evictAiGatewayConfigCachesNowAndAfterCommit();
        return getRoutePolicyItem(policyId);
    }

    @Transactional
    public RouteItem createRoute(UpsertRouteCommand command) {
        NormalizedRoute normalized = normalizeRoute(command, null);
        long routeId = aiModelRouteRepository.insertRoute(
                normalized.routeCode(),
                normalized.taskType(),
                normalized.sceneCode(),
                normalized.sceneRoutePolicyId(),
                normalized.providerConfigId(),
                normalized.modelName(),
                normalized.priorityNo(),
                normalized.candidateWeight(),
                normalized.executionMode(),
                normalized.enabled(),
                normalized.temperature(),
                normalized.systemPrompt(),
                normalized.promptTemplateName(),
                normalized.extraConfigJson()
        );
        evictAiGatewayConfigCachesNowAndAfterCommit();
        return getRouteItem(routeId);
    }

    @Transactional
    public RouteItem updateRoute(long routeId, UpsertRouteCommand command) {
        AiModelRouteRepository.ModelRouteRow current = aiModelRouteRepository.findRouteById(routeId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "route not found", HttpStatus.NOT_FOUND));
        NormalizedRoute normalized = normalizeRoute(command, current);
        aiModelRouteRepository.updateRoute(
                routeId,
                normalized.routeCode(),
                normalized.taskType(),
                normalized.sceneCode(),
                normalized.sceneRoutePolicyId(),
                normalized.providerConfigId(),
                normalized.modelName(),
                normalized.priorityNo(),
                normalized.candidateWeight(),
                normalized.executionMode(),
                normalized.enabled(),
                normalized.temperature(),
                normalized.systemPrompt(),
                normalized.promptTemplateName(),
                normalized.extraConfigJson()
        );
        evictAiGatewayConfigCachesNowAndAfterCommit();
        return getRouteItem(routeId);
    }

    @Transactional
    public PromptTemplateItem createPromptTemplate(UpsertPromptTemplateCommand command) {
        NormalizedPromptTemplate normalized = normalizePromptTemplate(command, null);
        long templateId = promptTemplateRepository.insertPromptTemplate(
                normalized.taskType(),
                normalized.templateName(),
                normalized.versionNo(),
                normalized.status(),
                normalized.templateFormat(),
                normalized.content(),
                normalized.description(),
                normalized.variablesJson(),
                normalized.bundleJson()
        );
        if ("ACTIVE".equals(normalized.status())) {
            promptTemplateRepository.deactivateOtherPromptTemplates(normalized.taskType(), normalized.templateName(), templateId);
        }
        evictAiGatewayConfigCachesNowAndAfterCommit();
        return promptTemplateRepository.findPromptTemplateById(templateId)
                .map(this::toPromptTemplateItem)
                .orElseThrow(() -> new ApiException("BIZ-1002", "prompt template not found", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public PromptTemplateItem updatePromptTemplate(long templateId, UpsertPromptTemplateCommand command) {
        PromptTemplateRepository.PromptTemplateRow current = promptTemplateRepository.findPromptTemplateById(templateId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "prompt template not found", HttpStatus.NOT_FOUND));
        NormalizedPromptTemplate normalized = normalizePromptTemplate(command, current);
        promptTemplateRepository.updatePromptTemplate(
                templateId,
                normalized.taskType(),
                normalized.templateName(),
                normalized.versionNo(),
                normalized.status(),
                normalized.templateFormat(),
                normalized.content(),
                normalized.description(),
                normalized.variablesJson(),
                normalized.bundleJson()
        );
        if ("ACTIVE".equals(normalized.status())) {
            promptTemplateRepository.deactivateOtherPromptTemplates(normalized.taskType(), normalized.templateName(), templateId);
        }
        evictAiGatewayConfigCachesNowAndAfterCommit();
        return promptTemplateRepository.findPromptTemplateById(templateId)
                .map(this::toPromptTemplateItem)
                .orElseThrow(() -> new ApiException("BIZ-1002", "prompt template not found", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public PromptTemplateItem publishPromptTemplate(long templateId) {
        PromptTemplateRepository.PromptTemplateRow target = promptTemplateRepository.findPromptTemplateById(templateId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "prompt template not found", HttpStatus.NOT_FOUND));
        return activatePromptTemplate(target);
    }

    @Transactional
    public PromptTemplateItem rollbackPromptTemplate(long templateId, RollbackPromptTemplateCommand command) {
        PromptTemplateRepository.PromptTemplateRow source = promptTemplateRepository.findPromptTemplateById(templateId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "prompt template not found", HttpStatus.NOT_FOUND));
        if (command.targetTemplateId() == null || command.targetTemplateId() <= 0) {
            throw new ApiException("BIZ-1001", "targetTemplateId invalid", HttpStatus.BAD_REQUEST);
        }
        PromptTemplateRepository.PromptTemplateRow target = promptTemplateRepository.findPromptTemplateById(command.targetTemplateId())
                .orElseThrow(() -> new ApiException("BIZ-1002", "prompt template not found", HttpStatus.NOT_FOUND));
        ensureSamePromptTemplateFamily(source, target);
        return activatePromptTemplate(target);
    }

    public String decryptApiKey(String providerCode) {
        AiProviderConfigRepository.ProviderConfigRow provider = aiProviderConfigRepository.findProviderByCode(providerCode)
                .orElseThrow(() -> new ApiException("BIZ-1002", "provider not found", HttpStatus.NOT_FOUND));
        return cryptoService.decrypt(provider.apiKeyCiphertext());
    }

    public RuntimeSettingsPayload getRuntimeSettings() {
        return toRuntimeSettingsPayload(runtimeSettingsService.getRuntimeSettings());
    }

    @Transactional
    public RuntimeSettingsPayload updateRuntimeSettings(long operatorUserId, UpdateRuntimeSettingsCommand command) {
        String defaultReasoningEffort = normalizeOptionalReasoningEffort(command.defaultReasoningEffort());
        Integer defaultThinkingBudget = normalizeOptionalThinkingBudget(command.defaultThinkingBudget());
        String defaultThinkingLevel = normalizeOptionalText(command.defaultThinkingLevel(), null, 40);
        return toRuntimeSettingsPayload(runtimeSettingsService.updateRuntimeSettings(
                operatorUserId,
                new AiGatewayRuntimeSettingsService.UpdateRuntimeSettingsCommand(
                        command.debugModeEnabled(),
                        command.aiRequestLogEnabled(),
                        defaultReasoningEffort,
                        defaultThinkingBudget,
                        defaultThinkingLevel
                )
        ));
    }

    public CostDashboardPayload getCostDashboard(String rawPeriod) {
        String period = normalizePeriod(rawPeriod);
        TimeWindow window = resolveTimeWindow(period);
        AiGatewayAdminAnalyticsRepository.OverviewRow overview = analyticsRepository.summarizeOverview(window.startAt(), window.endAt());
        return new CostDashboardPayload(
                period,
                overview.totalCalls(),
                decimalOrDefault(overview.totalCost(), BigDecimal.ZERO).toPlainString(),
                analyticsRepository.summarizeByModel(window.startAt(), window.endAt()).stream().map(this::toMetricItem).toList(),
                analyticsRepository.summarizeByProvider(window.startAt(), window.endAt()).stream().map(this::toMetricItem).toList(),
                analyticsRepository.summarizeByTaskType(window.startAt(), window.endAt()).stream().map(this::toMetricItem).toList(),
                analyticsRepository.summarizeByTier(window.startAt(), window.endAt()).stream().map(this::toMetricItem).toList(),
                analyticsRepository.summarizeTopUsers(window.startAt(), window.endAt(), 10).stream().map(this::toTopUserItem).toList()
        );
    }

    public ApplicationSceneMetricsPayload getApplicationSceneMetrics(Integer rawDays) {
        int days = normalizePositiveInt(rawDays, 7, 1, 30, "days");
        Instant endAt = Instant.now();
        Instant startAt = endAt.minus(days, ChronoUnit.DAYS);
        List<ApplicationSceneMetricItem> records = analyticsRepository.summarizeByScene(
                        Timestamp.from(startAt),
                        Timestamp.from(endAt),
                        100
                ).stream()
                .map(this::toApplicationSceneMetricItem)
                .toList();
        long totalCalls = records.stream()
                .mapToLong(ApplicationSceneMetricItem::calls)
                .sum();
        BigDecimal totalCost = records.stream()
                .map(record -> new BigDecimal(record.totalCost()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ApplicationSceneMetricsPayload(
                days,
                totalCalls,
                decimalOrDefault(totalCost, BigDecimal.ZERO).toPlainString(),
                records
        );
    }

    public ApplicationOpsPayload getApplicationOps(Integer rawDays) {
        int days = normalizePositiveInt(rawDays, 7, 1, 30, "days");

        Map<String, ApplicationSceneMetricItem> metricsByScene = new LinkedHashMap<>();
        getApplicationSceneMetrics(days).records().forEach(item ->
                metricsByScene.put(buildSceneKey(item.taskType(), item.sceneCode()), item)
        );

        Map<String, List<RouteItem>> routesByScene = new LinkedHashMap<>();
        listRoutes().records().forEach(route -> routesByScene
                .computeIfAbsent(buildSceneKey(route.taskType(), route.sceneCode()), ignored -> new java.util.ArrayList<>())
                .add(route));
        routesByScene.values().forEach(items -> items.sort(this::compareRoutes));

        Map<String, List<PromptTemplateItem>> templatesByFamily = new LinkedHashMap<>();
        listPromptTemplates().records().forEach(template -> templatesByFamily
                .computeIfAbsent(buildTemplateKey(template.taskType(), template.templateName()), ignored -> new java.util.ArrayList<>())
                .add(template));
        templatesByFamily.values().forEach(items -> items.sort(Comparator.comparing(PromptTemplateItem::versionNo).reversed()));

        List<ApplicationOpsSceneItem> records = AiApplicationSceneRegistry.list().stream()
                .map(definition -> toApplicationOpsSceneItem(definition, metricsByScene, routesByScene, templatesByFamily))
                .sorted(this::compareApplicationOpsRecords)
                .toList();

        ApplicationOpsSummary summary = buildApplicationOpsSummary(records);
        return new ApplicationOpsPayload(
                days,
                summary,
                records.stream()
                        .filter(item -> item.active() && item.calls() > 0)
                        .sorted(Comparator.comparing(ApplicationOpsSceneItem::calls).reversed()
                                .thenComparing(ApplicationOpsSceneItem::totalCost, Comparator.comparing(this::parseDecimal).reversed())
                                .thenComparing(ApplicationOpsSceneItem::displayName))
                        .limit(4)
                        .toList(),
                records.stream()
                        .filter(item -> parseDecimal(item.totalCost()).compareTo(BigDecimal.ZERO) > 0)
                        .sorted(Comparator.comparing(ApplicationOpsSceneItem::totalCost, Comparator.comparing(this::parseDecimal).reversed())
                                .thenComparing(ApplicationOpsSceneItem::calls, Comparator.reverseOrder())
                                .thenComparing(ApplicationOpsSceneItem::displayName))
                        .limit(4)
                        .toList(),
                records.stream()
                        .filter(ApplicationOpsSceneItem::followUp)
                        .sorted(this::compareApplicationOpsRecords)
                        .limit(4)
                        .toList(),
                records.stream()
                        .filter(ApplicationOpsSceneItem::dormant)
                        .sorted(Comparator.comparing(ApplicationOpsSceneItem::displayName))
                        .limit(4)
                        .toList(),
                records
        );
    }


    public TrafficHeatmapPayload getTrafficHeatmap(Integer rawDays, String rawTimezone) {
        int days = normalizePositiveInt(rawDays, 7, 1, 30, "days");
        java.time.ZoneId zoneId = resolveZoneId(rawTimezone);

        java.time.LocalDate endDate = java.time.LocalDate.now(zoneId);
        java.time.LocalDate startDate = endDate.minusDays(days - 1L);
        java.time.ZonedDateTime startDateTime = startDate.atStartOfDay(zoneId);
        java.time.ZonedDateTime endDateTime = endDate.plusDays(1).atStartOfDay(zoneId).minusNanos(1);

        Map<java.time.LocalDate, long[]> callsByDay = new LinkedHashMap<>();
        Map<java.time.LocalDate, long[]> successByDay = new LinkedHashMap<>();
        for (int index = 0; index < days; index++) {
            java.time.LocalDate date = startDate.plusDays(index);
            callsByDay.put(date, new long[24]);
            successByDay.put(date, new long[24]);
        }

        long totalCalls = 0L;
        long successCalls = 0L;
        long maxCallsPerHour = 0L;
        for (AiGatewayAdminAnalyticsRepository.HourlyTrafficLogRow row : analyticsRepository.findTrafficLogs(
                Timestamp.from(startDateTime.toInstant()),
                Timestamp.from(endDateTime.toInstant())
        )) {
            if (row.createdAt() == null) {
                continue;
            }
            java.time.ZonedDateTime createdAt = row.createdAt().atZone(zoneId);
            java.time.LocalDate date = createdAt.toLocalDate();
            long[] callBuckets = callsByDay.get(date);
            long[] successBuckets = successByDay.get(date);
            if (callBuckets == null || successBuckets == null) {
                continue;
            }
            int hour = createdAt.getHour();
            callBuckets[hour]++;
            totalCalls++;
            if ("SUCCESS".equalsIgnoreCase(row.status())) {
                successBuckets[hour]++;
                successCalls++;
            }
            maxCallsPerHour = Math.max(maxCallsPerHour, callBuckets[hour]);
        }

        long heatmapMaxCallsPerHour = maxCallsPerHour;
        List<HeatmapDayPayload> rows = callsByDay.entrySet().stream()
                .map(entry -> {
                    java.time.LocalDate date = entry.getKey();
                    long[] callBuckets = entry.getValue();
                    long[] successBuckets = successByDay.get(date);
                    java.util.List<HeatmapHourPayload> hours = new java.util.ArrayList<>();
                    long dayTotalCalls = 0L;
                    for (int hour = 0; hour < 24; hour++) {
                        long calls = callBuckets[hour];
                        long hourSuccessCalls = successBuckets[hour];
                        int intensity = calls <= 0 ? 0 : Math.max(1, Math.min(4, (int) Math.ceil((double) calls * 4 / Math.max(1L, heatmapMaxCallsPerHour))));
                        hours.add(new HeatmapHourPayload(hour, calls, hourSuccessCalls, intensity));
                        dayTotalCalls += calls;
                    }
                    return new HeatmapDayPayload(
                            date.toString(),
                            resolveDayLabel(date.getDayOfWeek()),
                            dayTotalCalls,
                            hours
                    );
                })
                .toList();

        TimeWindow monthWindow = resolveTimeWindow("month");
        AiGatewayAdminAnalyticsRepository.OverviewRow monthOverview = analyticsRepository.summarizeOverview(monthWindow.startAt(), monthWindow.endAt());
        String successRate = totalCalls <= 0 ? "0.0%" : String.format(Locale.ROOT, "%.1f%%", (successCalls * 100.0) / totalCalls);
        return new TrafficHeatmapPayload(
                zoneId.getId(),
                days,
                totalCalls,
                successCalls,
                successRate,
                monthOverview.totalCalls(),
                maxCallsPerHour,
                rows
        );
    }

    public ProviderRuntimeStatsPayload getProviderRuntimeStats(Integer rawHours, String rawTimezone) {
        int hours = normalizePositiveInt(rawHours, 24, 6, 72, "hours");
        java.time.ZoneId zoneId = resolveZoneId(rawTimezone);
        java.time.ZonedDateTime currentHour = java.time.ZonedDateTime.now(zoneId).truncatedTo(ChronoUnit.HOURS);
        java.time.ZonedDateTime startHour = currentHour.minusHours(hours - 1L);
        java.time.ZonedDateTime endHourExclusive = currentHour.plusHours(1L);
        DateTimeFormatter bucketFormatter = DateTimeFormatter.ofPattern("MM-dd HH:00");

        Map<String, ProviderRuntimeAccumulator> accumulatorMap = new LinkedHashMap<>();
        aiProviderConfigRepository.findAllProviders().stream()
                .sorted(Comparator.comparing(AiProviderConfigRepository.ProviderConfigRow::enabled).reversed()
                        .thenComparing(AiProviderConfigRepository.ProviderConfigRow::providerCode))
                .forEach(provider -> accumulatorMap.put(provider.providerCode(), new ProviderRuntimeAccumulator(provider, hours)));

        for (AiGatewayAdminAnalyticsRepository.ProviderRuntimeLogRow row : analyticsRepository.findProviderRuntimeLogs(
                Timestamp.from(startHour.toInstant()),
                Timestamp.from(endHourExclusive.toInstant())
        )) {
            if (row.createdAt() == null || row.provider() == null || row.provider().isBlank()) {
                continue;
            }
            ProviderRuntimeAccumulator accumulator = accumulatorMap.get(row.provider().trim());
            if (accumulator == null) {
                continue;
            }
            java.time.ZonedDateTime bucketTime = row.createdAt().atZone(zoneId).truncatedTo(ChronoUnit.HOURS);
            long bucketIndex = ChronoUnit.HOURS.between(startHour, bucketTime);
            if (bucketIndex < 0 || bucketIndex >= hours) {
                continue;
            }
            int index = (int) bucketIndex;
            accumulator.calls[index]++;
            accumulator.totalCalls++;
            accumulator.latencyTotals[index] += Math.max(row.latencyMs(), 0L);
            accumulator.totalLatency += Math.max(row.latencyMs(), 0L);
            if ("SUCCESS".equalsIgnoreCase(row.status())) {
                accumulator.successCalls[index]++;
                accumulator.totalSuccessCalls++;
            } else {
                accumulator.failureCalls[index]++;
            }
            if (accumulator.lastEventAt == null || row.createdAt().isAfter(accumulator.lastEventAt)) {
                accumulator.lastEventAt = row.createdAt();
            }
        }

        List<ProviderRuntimeItem> providers = accumulatorMap.values().stream()
                .map(accumulator -> {
                    List<ProviderRuntimeBlock> blocks = new java.util.ArrayList<>();
                    for (int index = 0; index < hours; index++) {
                        java.time.ZonedDateTime bucketTime = startHour.plusHours(index);
                        long calls = accumulator.calls[index];
                        long successCalls = accumulator.successCalls[index];
                        long avgLatencyMs = calls <= 0 ? 0L : Math.round((double) accumulator.latencyTotals[index] / calls);
                        blocks.add(new ProviderRuntimeBlock(
                                bucketTime.format(bucketFormatter),
                                calls,
                                successCalls,
                                avgLatencyMs,
                                resolveRuntimeStatus(accumulator.provider.enabled(), calls, successCalls)
                        ));
                    }
                    long avgLatencyMs = accumulator.totalCalls <= 0 ? 0L : Math.round((double) accumulator.totalLatency / accumulator.totalCalls);
                    return new ProviderRuntimeItem(
                            accumulator.provider.id(),
                            accumulator.provider.providerCode(),
                            accumulator.provider.displayName(),
                            accumulator.provider.providerType(),
                            accumulator.provider.enabled(),
                            resolveRuntimeStatus(accumulator.provider.enabled(), accumulator.totalCalls, accumulator.totalSuccessCalls),
                            formatRate(accumulator.totalSuccessCalls, accumulator.totalCalls),
                            accumulator.totalCalls,
                            avgLatencyMs,
                            com.bishe.server.common.TimePayloads.toEpochMillis(accumulator.lastEventAt),
                            blocks
                    );
                })
                .sorted(Comparator.comparing(ProviderRuntimeItem::enabled).reversed()
                        .thenComparing(ProviderRuntimeItem::totalCalls, Comparator.reverseOrder())
                        .thenComparing(ProviderRuntimeItem::providerCode))
                .toList();

        long healthyProviders = providers.stream().filter(item -> "HEALTHY".equals(item.runtimeStatus())).count();
        long degradedProviders = providers.stream().filter(item -> "DEGRADED".equals(item.runtimeStatus())).count();
        long downProviders = providers.stream().filter(item -> "DOWN".equals(item.runtimeStatus())).count();
        long idleProviders = providers.stream().filter(item -> "IDLE".equals(item.runtimeStatus())).count();
        long disabledProviders = providers.stream().filter(item -> "DISABLED".equals(item.runtimeStatus())).count();
        return new ProviderRuntimeStatsPayload(
                zoneId.getId(),
                hours,
                providers.size(),
                healthyProviders,
                degradedProviders,
                downProviders,
                idleProviders,
                disabledProviders,
                providers
        );
    }

    public CostDashboardExportPayload exportCostDashboard(String rawPeriod) {
        CostDashboardPayload dashboard = getCostDashboard(rawPeriod);
        StringBuilder builder = new StringBuilder();
        builder.append("section,name,calls,cost_cny,extra\n");
        builder.append(csvLine("OVERVIEW", dashboard.period(), dashboard.totalCalls(), dashboard.totalCost(), "topUsers=" + dashboard.topUsers().size()));
        appendMetricLines(builder, "MODEL", dashboard.byModel());
        appendMetricLines(builder, "PROVIDER", dashboard.byProvider());
        appendMetricLines(builder, "TASK_TYPE", dashboard.byTaskType());
        appendMetricLines(builder, "USER_TIER", dashboard.byTier());
        for (TopUserItem item : dashboard.topUsers()) {
            builder.append(csvLine(
                    "TOP_USER",
                    item.email().isBlank() ? String.valueOf(item.userId()) : item.email(),
                    item.calls(),
                    item.cost(),
                    "userId=" + item.userId() + ";displayName=" + safe(item.displayName())
            ));
        }
        String filename = "ai-cost-dashboard-"
                + dashboard.period()
                + "-"
                + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + ".csv";
        return new CostDashboardExportPayload(filename, builder.toString().getBytes(StandardCharsets.UTF_8));
    }

    public PromptTemplateRenderPreviewPayload previewPromptTemplateRender(PromptTemplateRenderPreviewCommand command) {
        String taskType = normalizeTaskType(command.taskType(), command.taskType());
        String templateFormat = normalizePromptTemplateFormat(command.templateFormat(), AiPromptTemplateFormat.TEXT.name());
        String content = normalizeTemplateContent(command.content(), null, templateFormat);
        String variablesJson = normalizeJson(command.variablesJson(), null, false);
        String bundleJson = normalizeTemplateBundleJson(command.bundleJson(), null, templateFormat);
        Map<String, Object> renderVariables = promptTemplateRenderService.parseRuntimeVariablesJson(command.renderVariablesJson());
        if (AiPromptTemplateFormat.MESSAGE_BUNDLE.name().equals(templateFormat)) {
            PromptTemplateRenderService.BundleRenderResult renderResult = promptTemplateRenderService.renderBundleForPreview(
                    content,
                    variablesJson,
                    bundleJson,
                    renderVariables
            );
            return new PromptTemplateRenderPreviewPayload(
                    taskType,
                    templateFormat,
                    renderResult.renderedContent(),
                    renderResult.renderedBundleJson(),
                    renderResult.placeholderVariables(),
                    renderResult.missingVariables(),
                    writeJson(renderResult.resolvedVariables())
            );
        }
        PromptTemplateRenderService.RenderResult renderResult = promptTemplateRenderService.renderForPreview(content, variablesJson, renderVariables);
        return new PromptTemplateRenderPreviewPayload(
                taskType,
                templateFormat,
                renderResult.renderedContent(),
                null,
                renderResult.placeholderVariables(),
                renderResult.missingVariables(),
                writeJson(renderResult.resolvedVariables())
        );
    }

    public AiLogListPayload listLogs(AiLogQueryCommand command) {
        int page = normalizePositiveInt(command.page(), 1, 1, 100000, "page");
        int size = normalizePositiveInt(command.size(), 20, 1, 100, "size");
        String taskType = normalizeOptionalTaskType(command.taskType());
        String sceneCode = normalizeOptionalCode(command.sceneCode(), null);
        String provider = normalizeOptionalCode(command.provider(), null);
        String status = normalizeOptionalCode(command.status(), null);
        Long userId = normalizeOptionalId(command.userId());
        String traceId = normalizeOptionalText(command.traceId(), null, 120);
        int offset = (page - 1) * size;
        List<AiLogItem> records = analyticsRepository.findAiLogs(taskType, sceneCode, provider, status, userId, traceId, size, offset).stream()
                .map(this::toAiLogItem)
                .toList();
        long total = analyticsRepository.countAiLogs(taskType, sceneCode, provider, status, userId, traceId);
        return new AiLogListPayload(records, total, page, size);
    }


    public AiLogDetailPayload getLogDetail(long logId) {
        return analyticsRepository.findAiLogDetail(logId)
                .map(this::toAiLogDetailPayload)
                .orElseThrow(() -> new ApiException("BIZ-1002", "ai log not found", HttpStatus.NOT_FOUND));
    }

    public RoutePreviewPayload previewResolvedRoute(RouteResolvePreviewCommand command) {
        String taskType = normalizeTaskType(command.taskType(), null);
        String sceneCode = normalizeOptionalCode(command.sceneCode(), null);
        String modelPreference = normalizeOptionalText(command.modelPreference(), null, 120);
        String userTier = normalizeRoutePolicyTier(command.userTier(), "ALL");
        AiProviderInvocation invocation = routeResolver.resolve(taskType, sceneCode, modelPreference, userTier);
        return new RoutePreviewPayload(
                invocation.taskType(),
                invocation.sceneCode(),
                safe(invocation.routePolicyCode()),
                normalizeRoutePolicyTier(invocation.routePolicyUserTier(), userTier),
                normalizeRouteStrategyType(invocation.routeStrategyType(), "SINGLE"),
                invocation.routeCode(),
                invocation.providerCode(),
                invocation.providerDisplayName(),
                invocation.providerType().name(),
                invocation.baseUrl(),
                invocation.model(),
                invocation.timeout().toMillis(),
                invocation.maxRetries(),
                invocation.executionMode().name(),
                BigDecimal.valueOf(invocation.temperature()).stripTrailingZeros().toPlainString(),
                safe(invocation.systemPrompt()),
                safe(invocation.promptTemplateName()),
                invocation.promptTemplateVersionNo(),
                invocation.thinkingConfig() == null ? null : invocation.thinkingConfig().reasoningEffortCode(),
                invocation.thinkingConfig() == null ? null : invocation.thinkingConfig().thinkingBudget(),
                invocation.thinkingConfig() == null ? null : invocation.thinkingConfig().thinkingLevel(),
                invocation.thinkingConfig() == null ? null : invocation.thinkingConfig().source(),
                decimalOrDefault(invocation.costPer1kInput(), BigDecimal.ZERO).toPlainString(),
                decimalOrDefault(invocation.costPer1kOutput(), BigDecimal.ZERO).toPlainString(),
                AiPricingPolicyResolver.CURRENCY_CNY,
                safe(invocation.providerExtraConfigJson()),
                safe(invocation.routeExtraConfigJson())
        );
    }

    private AiLogItem toAiLogItem(AiGatewayAdminAnalyticsRepository.AiLogRow row) {
        return new AiLogItem(
                row.id(),
                row.traceId(),
                row.userId(),
                safe(row.userEmail()),
                safe(row.userDisplayName()),
                row.taskType(),
                safe(row.sceneCode()),
                safe(row.routeCode()),
                safe(row.routePolicyCode()),
                row.provider(),
                row.model(),
                row.status(),
                safe(row.errorCode()),
                row.latencyMs(),
                row.requestTokens(),
                row.responseTokens(),
                row.totalTokens(),
                row.thoughtsTokens(),
                safe(row.reasoningEffort()),
                row.thinkingBudget(),
                safe(row.thinkingLevel()),
                decimalOrDefault(row.estimatedCost(), BigDecimal.ZERO).toPlainString(),
                row.chargedPoints(),
                row.quotaWeight(),
                safe(row.resultSummary()),
                safe(row.userTier()),
                com.bishe.server.common.TimePayloads.toEpochMillis(row.createdAt())
        );
    }


    private AiLogDetailPayload toAiLogDetailPayload(AiGatewayAdminAnalyticsRepository.AiLogDetailRow row) {
        GovernanceTraceSummaryPayload governanceTraceSummary = buildGovernanceTraceSummary(row.traceId());
        CurrentRouteSnapshotPayload currentRouteSnapshot = buildCurrentRouteSnapshot(
                row.taskType(),
                row.sceneCode(),
                row.model(),
                row.userTier(),
                row.routeCode(),
                row.routePolicyCode(),
                row.provider()
        );
        return new AiLogDetailPayload(
                row.id(),
                row.traceId(),
                row.userId(),
                safe(row.userEmail()),
                safe(row.userDisplayName()),
                row.taskType(),
                safe(row.sceneCode()),
                safe(row.routeCode()),
                safe(row.routePolicyCode()),
                row.provider(),
                row.model(),
                row.status(),
                safe(row.errorCode()),
                row.latencyMs(),
                row.requestTokens(),
                row.responseTokens(),
                row.totalTokens(),
                row.thoughtsTokens(),
                safe(row.reasoningEffort()),
                row.thinkingBudget(),
                safe(row.thinkingLevel()),
                decimalOrDefault(row.estimatedCost(), BigDecimal.ZERO).toPlainString(),
                row.chargedPoints(),
                row.quotaWeight(),
                safe(row.resultSummary()),
                safe(row.resultPayloadJson()),
                safe(row.userTier()),
                com.bishe.server.common.TimePayloads.toEpochMillis(row.createdAt()),
                governanceTraceSummary,
                currentRouteSnapshot
        );
    }

    private CurrentRouteSnapshotPayload buildCurrentRouteSnapshot(
            String taskType,
            String sceneCode,
            String modelPreference,
            String userTier,
            String routeCode,
            String routePolicyCode,
            String providerCode
    ) {
        try {
            String normalizedTaskType = normalizeOptionalTaskType(taskType);
            String normalizedSceneCode = normalizeOptionalCode(sceneCode, null);
            String normalizedUserTier = normalizeRoutePolicyTier(userTier, "ALL");
            if (normalizedTaskType == null) {
                return null;
            }
            List<AiGatewayAdminRepository.ResolvedRouteRow> enabledRoutes = repository.findEnabledRoutes(
                    normalizedTaskType,
                    normalizedSceneCode,
                    normalizedUserTier
            );
            return findMatchingCurrentRoute(
                    enabledRoutes,
                    normalizedSceneCode,
                    normalizedUserTier,
                    routeCode,
                    routePolicyCode,
                    providerCode,
                    modelPreference
            )
                    .or(() -> findMatchingLegacyRoute(
                            normalizedTaskType,
                            normalizedSceneCode,
                            routeCode,
                            providerCode,
                            modelPreference
                    ))
                    .or(() -> enabledRoutes.stream().sorted(currentRouteComparator(normalizedSceneCode, normalizedUserTier)).findFirst())
                    .map(routeRow -> toCurrentRouteSnapshotPayload(routeRow, normalizedSceneCode, modelPreference))
                    .orElseGet(() -> {
                        AiProviderInvocation invocation = routeResolver.resolve(normalizedTaskType, normalizedSceneCode, modelPreference, normalizedUserTier);
                        return toCurrentRouteSnapshotPayload(invocation);
                    });
        } catch (Exception ex) {
            return null;
        }
    }

    private Optional<AiGatewayAdminRepository.ResolvedRouteRow> findMatchingCurrentRoute(
            List<AiGatewayAdminRepository.ResolvedRouteRow> routes,
            String sceneCode,
            String userTier,
            String routeCode,
            String routePolicyCode,
            String providerCode,
            String modelPreference
    ) {
        if (routes == null || routes.isEmpty()) {
            return Optional.empty();
        }
        Comparator<AiGatewayAdminRepository.ResolvedRouteRow> comparator = currentRouteComparator(sceneCode, userTier);
        String normalizedRouteCode = normalizeOptionalCode(routeCode, null);
        if (normalizedRouteCode != null) {
            Optional<AiGatewayAdminRepository.ResolvedRouteRow> matchedByRouteCode = routes.stream()
                    .filter(route -> normalizedRouteCode.equals(normalizeOptionalCode(route.routeCode(), null)))
                    .sorted(comparator)
                    .findFirst();
            if (matchedByRouteCode.isPresent()) {
                return matchedByRouteCode;
            }
        }

        String normalizedRoutePolicyCode = normalizeOptionalCode(routePolicyCode, null);
        if (normalizedRoutePolicyCode != null) {
            Optional<AiGatewayAdminRepository.ResolvedRouteRow> matchedByPolicyCode = routes.stream()
                    .filter(route -> normalizedRoutePolicyCode.equals(normalizeOptionalCode(route.routePolicyCode(), null)))
                    .sorted(comparator)
                    .findFirst();
            if (matchedByPolicyCode.isPresent()) {
                return matchedByPolicyCode;
            }
        }

        String normalizedProviderCode = normalizeOptionalCode(providerCode, null);
        String normalizedModelPreference = normalizeOptionalModel(modelPreference);
        if (normalizedProviderCode == null && normalizedModelPreference == null) {
            return Optional.empty();
        }
        return routes.stream()
                .filter(route -> matchesRouteProviderAndModel(route, normalizedProviderCode, normalizedModelPreference))
                .sorted(comparator)
                .findFirst();
    }

    private Optional<AiGatewayAdminRepository.ResolvedRouteRow> findMatchingLegacyRoute(
            String taskType,
            String sceneCode,
            String routeCode,
            String providerCode,
            String modelPreference
    ) {
        Comparator<AiModelRouteRepository.ModelRouteRow> comparator = Comparator
                .comparing((AiModelRouteRepository.ModelRouteRow row) -> isExactLegacySceneRoute(row, sceneCode) ? 0 : 1)
                .thenComparingInt(AiModelRouteRepository.ModelRouteRow::priorityNo)
                .thenComparing(row -> safe(row.routeCode()));
        String normalizedRouteCode = normalizeOptionalCode(routeCode, null);
        String normalizedProviderCode = normalizeOptionalCode(providerCode, null);
        String normalizedModelPreference = normalizeOptionalModel(modelPreference);
        return aiModelRouteRepository.findAllRoutes().stream()
                .filter(AiModelRouteRepository.ModelRouteRow::enabled)
                .filter(route -> route.sceneRoutePolicyId() == null)
                .filter(route -> Objects.equals(taskType, route.taskType()))
                .filter(route -> isCompatibleLegacySceneRoute(route, sceneCode))
                .filter(route -> normalizedRouteCode == null
                        ? matchesRouteProviderAndModel(route, normalizedProviderCode, normalizedModelPreference)
                        : normalizedRouteCode.equals(normalizeOptionalCode(route.routeCode(), null)))
                .sorted(comparator)
                .map(this::toResolvedRouteRow)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private Optional<AiGatewayAdminRepository.ResolvedRouteRow> toResolvedRouteRow(AiModelRouteRepository.ModelRouteRow row) {
        AiProviderConfigRepository.ProviderConfigRow provider = aiProviderConfigRepository.findProviderById(row.providerConfigId()).orElse(null);
        if (provider == null || !provider.enabled()) {
            return Optional.empty();
        }
        SceneRoutePolicyRepository.SceneRoutePolicyRow routePolicy = row.sceneRoutePolicyId() == null
                ? null
                : sceneRoutePolicyRepository.findRoutePolicyById(row.sceneRoutePolicyId()).orElse(null);
        Integer activePromptTemplateVersionNo = resolveActivePromptTemplateVersion(row.taskType(), row.promptTemplateName());
        AiProviderModelRepository.ProviderModelRow providerModel = aiProviderModelRepository.findProviderModelsByProviderId(row.providerConfigId()).stream()
                .filter(AiProviderModelRepository.ProviderModelRow::enabled)
                .filter(model -> safe(model.modelCode()).trim().equalsIgnoreCase(safe(row.modelName()).trim()))
                .findFirst()
                .orElse(null);
        return Optional.of(new AiGatewayAdminRepository.ResolvedRouteRow(
                row.sceneRoutePolicyId(),
                routePolicy == null ? null : routePolicy.policyCode(),
                routePolicy == null ? null : routePolicy.userTier(),
                routePolicy == null ? "SINGLE" : normalizeRouteStrategyType(routePolicy.strategyType(), "SINGLE"),
                row.routeCode(),
                row.taskType(),
                row.sceneCode(),
                row.modelName(),
                row.priorityNo(),
                row.candidateWeight(),
                row.executionMode(),
                row.temperature(),
                row.systemPrompt(),
                row.promptTemplateName(),
                activePromptTemplateVersionNo,
                null,
                null,
                null,
                null,
                null,
                row.extraConfigJson(),
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

    private CurrentRouteSnapshotPayload toCurrentRouteSnapshotPayload(
            AiGatewayAdminRepository.ResolvedRouteRow routeRow,
            String sceneCode,
            String modelPreference
    ) {
        String resolvedSceneCode = normalizeOptionalCode(sceneCode, normalizeOptionalCode(routeRow.sceneCode(), null));
        String resolvedModel = safe(modelPreference).isBlank() ? safe(routeRow.modelName()) : modelPreference.trim();
        AiExecutionMode executionMode = AiExecutionMode.from(routeRow.executionMode());
        AiThinkingConfig thinkingConfig = thinkingPolicyResolver.resolve(
                routeRow.taskType(),
                resolvedSceneCode,
                executionMode,
                resolvedModel,
                Map.of(),
                routeRow.providerExtraConfigJson(),
                routeRow.policyExtraConfigJson(),
                routeRow.routeExtraConfigJson()
        );
        AiPricingPolicyResolver.PricingDecision pricingDecision = pricingPolicyResolver.resolve(routeRow);
        return new CurrentRouteSnapshotPayload(
                safe(routeRow.routePolicyCode()),
                normalizeRoutePolicyTier(routeRow.routePolicyUserTier(), "ALL"),
                normalizeRouteStrategyType(routeRow.strategyType(), "SINGLE"),
                normalizeOptionalCode(routeRow.routeCode(), routeRow.routeCode()),
                resolvedSceneCode,
                normalizeOptionalCode(routeRow.providerCode(), routeRow.providerCode()),
                routeRow.displayName(),
                routeRow.providerType(),
                resolvedModel,
                executionMode.name(),
                safe(routeRow.promptTemplateName()),
                routeRow.promptTemplateVersionNo(),
                thinkingConfig == null ? null : thinkingConfig.reasoningEffortCode(),
                thinkingConfig == null ? null : thinkingConfig.thinkingBudget(),
                thinkingConfig == null ? null : thinkingConfig.thinkingLevel(),
                thinkingConfig == null ? null : thinkingConfig.source(),
                decimalOrDefault(pricingDecision.inputPer1k(), BigDecimal.ZERO).toPlainString(),
                decimalOrDefault(pricingDecision.outputPer1k(), BigDecimal.ZERO).toPlainString(),
                pricingDecision.currency()
        );
    }

    private CurrentRouteSnapshotPayload toCurrentRouteSnapshotPayload(AiProviderInvocation invocation) {
        return new CurrentRouteSnapshotPayload(
                safe(invocation.routePolicyCode()),
                normalizeRoutePolicyTier(invocation.routePolicyUserTier(), "ALL"),
                normalizeRouteStrategyType(invocation.routeStrategyType(), "SINGLE"),
                invocation.routeCode(),
                invocation.sceneCode(),
                invocation.providerCode(),
                invocation.providerDisplayName(),
                invocation.providerType().name(),
                invocation.model(),
                invocation.executionMode().name(),
                safe(invocation.promptTemplateName()),
                invocation.promptTemplateVersionNo(),
                invocation.thinkingConfig() == null ? null : invocation.thinkingConfig().reasoningEffortCode(),
                invocation.thinkingConfig() == null ? null : invocation.thinkingConfig().thinkingBudget(),
                invocation.thinkingConfig() == null ? null : invocation.thinkingConfig().thinkingLevel(),
                invocation.thinkingConfig() == null ? null : invocation.thinkingConfig().source(),
                decimalOrDefault(invocation.costPer1kInput(), BigDecimal.ZERO).toPlainString(),
                decimalOrDefault(invocation.costPer1kOutput(), BigDecimal.ZERO).toPlainString(),
                AiPricingPolicyResolver.CURRENCY_CNY
        );
    }

    private Comparator<AiGatewayAdminRepository.ResolvedRouteRow> currentRouteComparator(String sceneCode, String userTier) {
        return Comparator
                .comparing((AiGatewayAdminRepository.ResolvedRouteRow row) -> isSystemManagedRoute(row))
                .thenComparing(row -> isExactTierRoute(row, userTier) ? 0 : 1)
                .thenComparing(row -> isExactSceneRoute(row, sceneCode) ? 0 : 1)
                .thenComparingInt(AiGatewayAdminRepository.ResolvedRouteRow::priorityNo)
                .thenComparing(row -> safe(row.routeCode()));
    }

    private boolean isSystemManagedRoute(AiGatewayAdminRepository.ResolvedRouteRow row) {
        String routeCode = safe(row.routeCode()).trim().toUpperCase(Locale.ROOT);
        String providerCode = safe(row.providerCode()).trim().toUpperCase(Locale.ROOT);
        return routeCode.startsWith("SYSTEM_") || providerCode.startsWith("SYSTEM_");
    }

    private boolean isExactSceneRoute(AiGatewayAdminRepository.ResolvedRouteRow row, String sceneCode) {
        String normalizedSceneCode = normalizeOptionalCode(sceneCode, null);
        String rowSceneCode = normalizeOptionalCode(row.sceneCode(), null);
        return normalizedSceneCode != null && normalizedSceneCode.equals(rowSceneCode);
    }

    private boolean isCompatibleLegacySceneRoute(AiModelRouteRepository.ModelRouteRow row, String sceneCode) {
        String normalizedSceneCode = normalizeOptionalCode(sceneCode, null);
        String rowSceneCode = normalizeOptionalCode(row.sceneCode(), null);
        if (normalizedSceneCode == null) {
            return rowSceneCode == null;
        }
        return normalizedSceneCode.equals(rowSceneCode) || rowSceneCode == null;
    }

    private boolean isExactLegacySceneRoute(AiModelRouteRepository.ModelRouteRow row, String sceneCode) {
        String normalizedSceneCode = normalizeOptionalCode(sceneCode, null);
        String rowSceneCode = normalizeOptionalCode(row.sceneCode(), null);
        return normalizedSceneCode != null && normalizedSceneCode.equals(rowSceneCode);
    }

    private boolean isExactTierRoute(AiGatewayAdminRepository.ResolvedRouteRow row, String userTier) {
        String normalizedTier = normalizeRoutePolicyTier(userTier, "ALL");
        String rowTier = normalizeRoutePolicyTier(row.routePolicyUserTier(), "ALL");
        return Objects.equals(normalizedTier, rowTier);
    }

    private boolean matchesRouteProviderAndModel(
            AiGatewayAdminRepository.ResolvedRouteRow row,
            String providerCode,
            String modelPreference
    ) {
        if (providerCode != null && !providerCode.equals(normalizeOptionalCode(row.providerCode(), null))) {
            return false;
        }
        if (modelPreference != null && !modelPreference.equalsIgnoreCase(safe(row.modelName()).trim())) {
            return false;
        }
        return providerCode != null || modelPreference != null;
    }

    private boolean matchesRouteProviderAndModel(
            AiModelRouteRepository.ModelRouteRow row,
            String providerCode,
            String modelPreference
    ) {
        String resolvedProviderCode = aiProviderConfigRepository.findProviderById(row.providerConfigId())
                .map(AiProviderConfigRepository.ProviderConfigRow::providerCode)
                .map(code -> normalizeOptionalCode(code, null))
                .orElse(null);
        if (providerCode != null && !providerCode.equals(resolvedProviderCode)) {
            return false;
        }
        if (modelPreference != null && !modelPreference.equalsIgnoreCase(safe(row.modelName()).trim())) {
            return false;
        }
        return providerCode != null || modelPreference != null;
    }

    private GovernanceTraceSummaryPayload buildGovernanceTraceSummary(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return new GovernanceTraceSummaryPayload(0L, List.of(), null);
        }
        List<ContentGovernanceRepository.AuditLogRow> recentAudits = contentGovernanceRepository.findAuditLogs(null, null, null, traceId, 1, 5);
        long auditCount = contentGovernanceRepository.countAuditLogs(null, null, null, traceId);
        List<String> recentActionTypes = recentAudits.stream()
                .map(ContentGovernanceRepository.AuditLogRow::actionType)
                .filter(actionType -> actionType != null && !actionType.isBlank())
                .distinct()
                .limit(5)
                .toList();
        Long latestAuditAt = recentAudits.isEmpty() || recentAudits.getFirst().createdAt() == null
                ? null
                : com.bishe.server.common.TimePayloads.toEpochMillis(recentAudits.getFirst().createdAt());
        return new GovernanceTraceSummaryPayload(auditCount, recentActionTypes, latestAuditAt);
    }

    private MetricItem toMetricItem(AiGatewayAdminAnalyticsRepository.GroupedMetricRow row) {
        return new MetricItem(
                safe(row.name()),
                row.calls(),
                decimalOrDefault(row.cost(), BigDecimal.ZERO).toPlainString()
        );
    }

    private TopUserItem toTopUserItem(AiGatewayAdminAnalyticsRepository.UserCostRow row) {
        return new TopUserItem(
                row.userId(),
                safe(row.userEmail()),
                safe(row.userDisplayName()),
                row.calls(),
                decimalOrDefault(row.cost(), BigDecimal.ZERO).toPlainString()
        );
    }

    private ApplicationSceneMetricItem toApplicationSceneMetricItem(AiGatewayAdminAnalyticsRepository.SceneMetricRow row) {
        return new ApplicationSceneMetricItem(
                safe(row.taskType()),
                safe(row.sceneCode()),
                row.calls(),
                row.successCalls(),
                formatRate(row.successCalls(), row.calls()),
                row.avgLatencyMs(),
                decimalOrDefault(row.totalCost(), BigDecimal.ZERO).toPlainString(),
                com.bishe.server.common.TimePayloads.toEpochMillis(row.lastCallAt())
        );
    }

    private ApplicationOpsSceneItem toApplicationOpsSceneItem(
            AiApplicationSceneRegistry.ApplicationSceneDefinition definition,
            Map<String, ApplicationSceneMetricItem> metricsByScene,
            Map<String, List<RouteItem>> routesByScene,
            Map<String, List<PromptTemplateItem>> templatesByFamily
    ) {
        String sceneKey = buildSceneKey(definition.taskType(), definition.sceneCode());
        ApplicationSceneMetricItem metric = metricsByScene.get(sceneKey);
        List<RouteItem> matchedRoutes = routesByScene.getOrDefault(sceneKey, List.of());
        RouteItem primaryRoute = matchedRoutes.isEmpty() ? null : matchedRoutes.getFirst();

        String promptTemplateName = primaryRoute == null ? "" : safe(primaryRoute.promptTemplateName());
        boolean usesBuiltinPrompt = primaryRoute != null && promptTemplateName.isBlank();

        List<PromptTemplateItem> templateFamily = List.of();
        if (primaryRoute != null && !promptTemplateName.isBlank()) {
            templateFamily = templatesByFamily.getOrDefault(buildTemplateKey(primaryRoute.taskType(), promptTemplateName), List.of());
        }
        PromptTemplateItem activeTemplate = templateFamily.stream()
                .filter(item -> "ACTIVE".equalsIgnoreCase(item.status()))
                .findFirst()
                .orElse(null);

        long calls = metric == null ? 0L : metric.calls();
        long successCalls = metric == null ? 0L : metric.successCalls();
        long avgLatencyMs = metric == null ? 0L : metric.avgLatencyMs();
        String successRate = metric == null ? "0.0%" : safe(metric.successRate());
        String totalCost = metric == null ? "0" : safe(metric.totalCost());
        Long lastCallAt = metric == null ? null : metric.lastCallAt();

        boolean hasReadyRoute = primaryRoute != null && primaryRoute.enabled();
        boolean hasActiveTemplate = activeTemplate != null;
        boolean missingTemplate = hasReadyRoute && !usesBuiltinPrompt && !hasActiveTemplate;
        Double successRateValue = parsePercentValue(successRate);
        boolean lowSuccess = calls > 0 && successRateValue != null && successRateValue < 85.0d;
        boolean highLatency = calls > 0 && avgLatencyMs >= 8000L;
        boolean dormant = hasReadyRoute && !missingTemplate && calls <= 0;
        boolean active = calls > 0;
        boolean ready = hasReadyRoute && !missingTemplate;

        String status;
        String statusLabel;
        String riskType;
        String riskLabel;
        boolean followUp;
        boolean planned;
        String nextActionLabel;
        String nextActionTo;

        if (primaryRoute == null) {
            status = "PLANNED";
            statusLabel = "待接入";
            riskType = "NO_ROUTE";
            riskLabel = "未配置主路由";
            followUp = false;
            planned = true;
            nextActionLabel = "去配路由";
            nextActionTo = "/admin/ai/gateway?tab=routes";
        } else if (!primaryRoute.enabled()) {
            status = "BLOCKED";
            statusLabel = "待恢复";
            riskType = "ROUTE_DISABLED";
            riskLabel = "主路由已停用";
            followUp = true;
            planned = false;
            nextActionLabel = "恢复路由";
            nextActionTo = "/admin/ai/gateway?tab=routes";
        } else if (missingTemplate) {
            status = "BLOCKED";
            statusLabel = "待补模板";
            riskType = "MISSING_TEMPLATE";
            riskLabel = templateFamily.isEmpty() ? "尚未建立模板" : "模板未发布";
            followUp = true;
            planned = false;
            nextActionLabel = templateFamily.isEmpty() ? "去建模板" : "去发布模板";
            nextActionTo = "/admin/ai/gateway?tab=templates";
        } else if (lowSuccess) {
            status = "WATCH";
            statusLabel = "需关注";
            riskType = "LOW_SUCCESS";
            riskLabel = "成功率偏低";
            followUp = true;
            planned = false;
            nextActionLabel = "查看日志";
            nextActionTo = "/admin/ai/gateway?tab=logs";
        } else if (highLatency) {
            status = "WATCH";
            statusLabel = "需关注";
            riskType = "HIGH_LATENCY";
            riskLabel = "平均时延偏高";
            followUp = true;
            planned = false;
            nextActionLabel = "查看日志";
            nextActionTo = "/admin/ai/gateway?tab=logs";
        } else if (dormant) {
            status = "DORMANT";
            statusLabel = "待激活";
            riskType = "DORMANT";
            riskLabel = "近窗口静默";
            followUp = false;
            planned = false;
            nextActionLabel = "查看运行配置";
            nextActionTo = "/admin/runtime?tab=ai-channels";
        } else if (usesBuiltinPrompt) {
            status = "GOVERN";
            statusLabel = "待治理";
            riskType = "BUILTIN_PROMPT";
            riskLabel = "仍用内置提示词";
            followUp = true;
            planned = false;
            nextActionLabel = "补模板治理";
            nextActionTo = "/admin/ai/gateway?tab=templates";
        } else {
            status = active ? "ACTIVE" : "READY";
            statusLabel = active ? "经营中" : "已就绪";
            riskType = "HEALTHY";
            riskLabel = active ? "运行稳定" : "链路已备好";
            followUp = false;
            planned = false;
            nextActionLabel = active ? "查看链路" : "查看运行配置";
            nextActionTo = active ? "/admin/ai/gateway?tab=routes" : "/admin/runtime?tab=ai-channels";
        }

        String chainSummary;
        if (primaryRoute == null) {
            chainSummary = "未配置主路由";
        } else if (!primaryRoute.enabled()) {
            chainSummary = safe(primaryRoute.providerDisplayName()) + " · 已停用";
        } else if (usesBuiltinPrompt) {
            chainSummary = safe(primaryRoute.providerDisplayName()) + " · " + safe(primaryRoute.modelName()) + " · 内置提示词";
        } else if (activeTemplate != null) {
            chainSummary = safe(primaryRoute.providerDisplayName())
                    + " · "
                    + safe(primaryRoute.modelName())
                    + " · "
                    + safe(activeTemplate.templateName())
                    + " v"
                    + activeTemplate.versionNo();
        } else if (!templateFamily.isEmpty()) {
            chainSummary = safe(primaryRoute.providerDisplayName()) + " · " + safe(primaryRoute.modelName()) + " · 模板待发布";
        } else {
            chainSummary = safe(primaryRoute.providerDisplayName()) + " · " + safe(primaryRoute.modelName()) + " · 待建模板";
        }

        return new ApplicationOpsSceneItem(
                definition.channelCode(),
                definition.displayName(),
                definition.ownerDomain(),
                definition.frontEntry(),
                definition.summary(),
                definition.taskType(),
                definition.sceneCode(),
                calls,
                successCalls,
                successRate,
                avgLatencyMs,
                totalCost,
                lastCallAt,
                matchedRoutes.size(),
                primaryRoute == null ? "" : safe(primaryRoute.routeCode()),
                primaryRoute == null ? "" : safe(primaryRoute.providerDisplayName()),
                primaryRoute == null ? "" : safe(primaryRoute.modelName()),
                usesBuiltinPrompt,
                hasActiveTemplate,
                activeTemplate == null ? "" : safe(activeTemplate.templateName()),
                activeTemplate == null ? null : activeTemplate.versionNo(),
                status,
                statusLabel,
                riskType,
                riskLabel,
                followUp,
                active,
                ready,
                dormant,
                planned,
                chainSummary,
                nextActionLabel,
                nextActionTo
        );
    }

    private ApplicationOpsSummary buildApplicationOpsSummary(List<ApplicationOpsSceneItem> records) {
        long totalChannels = records.size();
        long readyChannels = records.stream().filter(ApplicationOpsSceneItem::ready).count();
        long activeChannels = records.stream().filter(ApplicationOpsSceneItem::active).count();
        long followUpChannels = records.stream().filter(ApplicationOpsSceneItem::followUp).count();
        long dormantChannels = records.stream().filter(ApplicationOpsSceneItem::dormant).count();
        long plannedChannels = records.stream().filter(ApplicationOpsSceneItem::planned).count();
        long totalCalls = records.stream().mapToLong(ApplicationOpsSceneItem::calls).sum();
        long totalSuccessCalls = records.stream().mapToLong(ApplicationOpsSceneItem::successCalls).sum();
        BigDecimal totalCost = records.stream()
                .map(ApplicationOpsSceneItem::totalCost)
                .map(this::parseDecimal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long weightedLatencyTotal = records.stream()
                .mapToLong(item -> item.avgLatencyMs() * Math.max(0L, item.calls()))
                .sum();
        long avgLatencyMs = totalCalls <= 0 ? 0L : Math.round((double) weightedLatencyTotal / (double) totalCalls);
        return new ApplicationOpsSummary(
                totalChannels,
                readyChannels,
                activeChannels,
                followUpChannels,
                dormantChannels,
                plannedChannels,
                totalCalls,
                decimalOrDefault(totalCost, BigDecimal.ZERO).toPlainString(),
                formatRate(totalSuccessCalls, totalCalls),
                avgLatencyMs
        );
    }

    private int compareApplicationOpsRecords(ApplicationOpsSceneItem left, ApplicationOpsSceneItem right) {
        int rankCompare = Integer.compare(resolveApplicationOpsSortRank(right), resolveApplicationOpsSortRank(left));
        if (rankCompare != 0) {
            return rankCompare;
        }
        int callsCompare = Long.compare(right.calls(), left.calls());
        if (callsCompare != 0) {
            return callsCompare;
        }
        int costCompare = parseDecimal(right.totalCost()).compareTo(parseDecimal(left.totalCost()));
        if (costCompare != 0) {
            return costCompare;
        }
        return left.displayName().compareTo(right.displayName());
    }

    private int resolveApplicationOpsSortRank(ApplicationOpsSceneItem item) {
        if (item.followUp()) {
            return switch (item.status()) {
                case "BLOCKED" -> 5;
                case "WATCH" -> 4;
                case "GOVERN" -> 3;
                default -> 2;
            };
        }
        if (item.active()) {
            return 2;
        }
        if (item.dormant()) {
            return 1;
        }
        if (item.ready()) {
            return 0;
        }
        return -1;
    }

    private int compareRoutes(RouteItem left, RouteItem right) {
        if (left.enabled() != right.enabled()) {
            return left.enabled() ? -1 : 1;
        }
        return Integer.compare(left.priorityNo(), right.priorityNo());
    }

    private String buildSceneKey(String taskType, String sceneCode) {
        return safe(taskType).trim().toUpperCase(Locale.ROOT) + "::" + safe(sceneCode).trim().toUpperCase(Locale.ROOT);
    }

    private AiApplicationSceneRegistry.ApplicationSceneDefinition findSceneDefinition(String taskType, String sceneCode) {
        return AiApplicationSceneRegistry.list().stream()
                .filter(definition -> Objects.equals(definition.taskType(), taskType))
                .filter(definition -> Objects.equals(definition.sceneCode(), sceneCode))
                .findFirst()
                .orElse(null);
    }

    private String taskTypeLabel(String code) {
        return switch (safe(code).trim().toUpperCase(Locale.ROOT)) {
            case "RESUME" -> "简历优化";
            case "INTERVIEW_TEXT" -> "文字面试";
            case "INTERVIEW_SUMMARY" -> "面试总结";
            case "PORTRAIT_SUMMARY" -> "画像总结";
            case "COMMUNITY_REPLY" -> "社区回复";
            case "ICEBREAK" -> "破冰话术";
            case "STT" -> "语音转文字";
            case "TTS" -> "文字转语音";
            default -> safe(code);
        };
    }

    private String executionModeLabel(String code) {
        return switch (safe(code).trim().toUpperCase(Locale.ROOT)) {
            case "SYNC_BLOCKING" -> "同步阻塞";
            case "STREAM_SSE" -> "流式输出";
            case "ASYNC_JOB" -> "异步任务";
            case "REALTIME_SESSION" -> "实时会话";
            default -> safe(code);
        };
    }

    private String buildTemplateKey(String taskType, String templateName) {
        return safe(taskType).trim().toUpperCase(Locale.ROOT) + "::" + safe(templateName).trim().toUpperCase(Locale.ROOT);
    }

    private Double parsePercentValue(String rawValue) {
        String normalized = safe(rawValue).replace("%", "").trim();
        if (normalized.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal parseDecimal(String rawValue) {
        String normalized = safe(rawValue).trim();
        if (normalized.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private RuntimeSettingsPayload toRuntimeSettingsPayload(AiGatewayRuntimeSettingsService.RuntimeSettingsSnapshot snapshot) {
        return new RuntimeSettingsPayload(
                snapshot.debugModeEnabled(),
                snapshot.aiRequestLogEnabled(),
                snapshot.defaultDebugModeEnabled(),
                snapshot.defaultAiRequestLogEnabled(),
                snapshot.defaultReasoningEffort(),
                snapshot.defaultThinkingBudget(),
                snapshot.defaultThinkingLevel(),
                com.bishe.server.common.TimePayloads.toEpochMillis(snapshot.updatedAt())
        );
    }

    private ProviderItem getProviderItem(long providerId) {
        AiProviderConfigRepository.ProviderConfigRow provider = aiProviderConfigRepository.findProviderById(providerId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "provider not found", HttpStatus.NOT_FOUND));
        List<ProviderModelItem> models = aiProviderModelRepository.findProviderModelsByProviderId(providerId).stream()
                .map(this::toProviderModelItem)
                .toList();
        return toProviderItem(provider, models);
    }

    private RouteItem getRouteItem(long routeId) {
        return aiModelRouteRepository.findRouteById(routeId)
                .map(this::toRouteItem)
                .orElseThrow(() -> new ApiException("BIZ-1002", "route not found", HttpStatus.NOT_FOUND));
    }

    private Map<Long, List<ProviderModelItem>> buildProviderModelsByProviderId(List<Long> providerIds) {
        Map<Long, List<ProviderModelItem>> modelsByProviderId = new LinkedHashMap<>();
        aiProviderModelRepository.findProviderModelsByProviderIds(providerIds).forEach(row ->
                modelsByProviderId.computeIfAbsent(row.providerConfigId(), ignored -> new ArrayList<>())
                        .add(toProviderModelItem(row))
        );
        modelsByProviderId.values().forEach(items -> items.sort(Comparator
                .comparing(ProviderModelItem::enabled).reversed()
                .thenComparing(ProviderModelItem::modelCode)));
        return modelsByProviderId;
    }

    private ProviderItem toProviderItem(AiProviderConfigRepository.ProviderConfigRow row, List<ProviderModelItem> models) {
        return new ProviderItem(
                row.id(),
                row.providerCode(),
                row.providerType(),
                row.displayName(),
                row.baseUrl(),
                row.enabled(),
                row.timeoutMs(),
                row.maxRetries(),
                decimalOrDefault(row.costPer1kInput(), BigDecimal.ZERO).toPlainString(),
                decimalOrDefault(row.costPer1kOutput(), BigDecimal.ZERO).toPlainString(),
                row.apiKeyMasked() == null ? "" : row.apiKeyMasked(),
                row.apiKeyCiphertext() != null && !row.apiKeyCiphertext().isBlank(),
                safe(row.extraConfigJson()),
                models == null ? List.of() : models,
                com.bishe.server.common.TimePayloads.toEpochMillis(row.createdAt()),
                com.bishe.server.common.TimePayloads.toEpochMillis(row.updatedAt())
        );
    }

    private ProviderModelItem toProviderModelItem(AiProviderModelRepository.ProviderModelRow row) {
        return new ProviderModelItem(
                row.id(),
                row.providerConfigId(),
                row.modelCode(),
                row.displayName(),
                row.enabled(),
                decimalOrDefault(row.inputCostPer1k(), BigDecimal.ZERO).toPlainString(),
                decimalOrDefault(row.outputCostPer1k(), BigDecimal.ZERO).toPlainString(),
                row.contextWindow(),
                row.maxOutputTokens(),
                safe(row.supportedTaskTypesJson()),
                safe(row.notes()),
                com.bishe.server.common.TimePayloads.toEpochMillis(row.createdAt()),
                com.bishe.server.common.TimePayloads.toEpochMillis(row.updatedAt())
        );
    }

    private java.time.ZoneId resolveZoneId(String rawTimezone) {
        try {
            return java.time.ZoneId.of(rawTimezone == null || rawTimezone.isBlank() ? "Asia/Shanghai" : rawTimezone.trim());
        } catch (Exception ex) {
            throw new ApiException("BIZ-1001", "timezone invalid", HttpStatus.BAD_REQUEST);
        }
    }

    private String resolveRuntimeStatus(boolean enabled, long calls, long successCalls) {
        if (!enabled) {
            return "DISABLED";
        }
        if (calls <= 0) {
            return "IDLE";
        }
        double successRate = (double) successCalls / (double) calls;
        if (successRate >= 0.98d) {
            return "HEALTHY";
        }
        if (successRate >= 0.80d) {
            return "DEGRADED";
        }
        return "DOWN";
    }

    private String formatRate(long success, long total) {
        if (total <= 0) {
            return "0.0%";
        }
        return String.format(Locale.ROOT, "%.1f%%", (success * 100.0d) / total);
    }

    private ProviderProbeRequest buildProviderProbeRequest(
            AiProviderType providerType,
            String rawBaseUrl,
            String apiKey,
            String providerExtraConfigJson
    ) {
        return switch (providerType) {
            case OPENAI_COMPATIBLE -> new ProviderProbeRequest(
                    normalizeOpenAiBaseUrl(rawBaseUrl) + "/models",
                    Map.of("Authorization", bearerToken(apiKey))
            );
            case GEMINI_NATIVE -> {
                GeminiTransportResolver.RequestSpec requestSpec = geminiTransportResolver.buildModelsProbeRequest(
                        rawBaseUrl,
                        apiKey,
                        providerExtraConfigJson
                );
                yield new ProviderProbeRequest(requestSpec.endpoint(), requestSpec.headers());
            }
        };
    }

    private ProviderConnectivityPayload buildProviderConnectivityPayload(
            AiProviderConfigRepository.ProviderConfigRow provider,
            String probeUrl,
            boolean reachable,
            boolean authenticated,
            boolean available,
            Integer httpStatus,
            long latencyMs,
            String status,
            String message,
            Instant checkedAt
    ) {
        return new ProviderConnectivityPayload(
                provider.id(),
                provider.providerCode(),
                provider.displayName(),
                provider.providerType(),
                provider.baseUrl(),
                probeUrl,
                reachable,
                authenticated,
                available,
                httpStatus,
                latencyMs,
                status,
                message,
                com.bishe.server.common.TimePayloads.toEpochMillis(checkedAt)
        );
    }

    private String normalizeOpenAiBaseUrl(String baseUrl) {
        String normalized = normalizeRequiredText(baseUrl, null, "baseUrl", 255);
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private String bearerToken(String apiKey) {
        String normalized = normalizeRequiredText(apiKey, null, "apiKey", 1024);
        return normalized.toLowerCase(Locale.ROOT).startsWith("bearer ") ? normalized : "Bearer " + normalized;
    }

    private String summarizeConnectivityException(Exception ex) {
        String message = ex == null || ex.getMessage() == null || ex.getMessage().isBlank()
                ? "连通性测试失败，请检查网络或上游服务状态"
                : ex.getMessage().replaceAll("\s+", " ").trim();
        return message.length() <= 180 ? message : message.substring(0, 180) + "...";
    }

    private RouteItem toRouteItem(AiModelRouteRepository.ModelRouteRow row) {
        RouteDisplayContext context = loadRouteDisplayContext(row);
        return new RouteItem(
                row.id(),
                row.sceneRoutePolicyId(),
                context.routePolicy() == null ? "" : safe(context.routePolicy().policyCode()),
                normalizeRoutePolicyTier(context.routePolicy() == null ? null : context.routePolicy().userTier()),
                normalizeRouteStrategyType(context.routePolicy() == null ? null : context.routePolicy().strategyType()),
                row.routeCode(),
                row.taskType(),
                row.sceneCode(),
                row.providerConfigId(),
                context.provider().providerCode(),
                context.provider().providerType(),
                context.provider().displayName(),
                row.modelName(),
                row.priorityNo(),
                row.candidateWeight(),
                safe(row.executionMode()),
                row.enabled(),
                decimalOrDefault(row.temperature(), BigDecimal.valueOf(0.2)).toPlainString(),
                safe(row.systemPrompt()),
                safe(row.promptTemplateName()),
                context.activePromptTemplateVersionNo(),
                safe(row.extraConfigJson()),
                com.bishe.server.common.TimePayloads.toEpochMillis(row.createdAt()),
                com.bishe.server.common.TimePayloads.toEpochMillis(row.updatedAt())
        );
    }

    private RouteCandidateItem toRouteCandidateItem(AiModelRouteRepository.ModelRouteRow row) {
        RouteDisplayContext context = loadRouteDisplayContext(row);
        return new RouteCandidateItem(
                row.id(),
                row.sceneRoutePolicyId(),
                row.routeCode(),
                row.providerConfigId(),
                context.provider().providerCode(),
                context.provider().displayName(),
                context.provider().providerType(),
                row.modelName(),
                row.priorityNo(),
                row.candidateWeight(),
                safe(row.executionMode()),
                row.enabled(),
                safe(row.promptTemplateName()),
                context.activePromptTemplateVersionNo(),
                safe(row.systemPrompt()),
                safe(row.extraConfigJson()),
                com.bishe.server.common.TimePayloads.toEpochMillis(row.createdAt()),
                com.bishe.server.common.TimePayloads.toEpochMillis(row.updatedAt())
        );
    }

    private RoutePolicyItem getRoutePolicyItem(long policyId) {
        SceneRoutePolicyRepository.SceneRoutePolicyRow policy = sceneRoutePolicyRepository.findRoutePolicyById(policyId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "route policy not found", HttpStatus.NOT_FOUND));
        List<RouteCandidateItem> candidates = aiModelRouteRepository.findAllRoutes().stream()
                .filter(route -> Objects.equals(route.sceneRoutePolicyId(), policyId))
                .map(this::toRouteCandidateItem)
                .sorted(Comparator.comparing(RouteCandidateItem::priorityNo).thenComparing(RouteCandidateItem::routeCode))
                .toList();
        return toRoutePolicyItem(policy, candidates);
    }

    private RoutePolicyItem toRoutePolicyItem(
            SceneRoutePolicyRepository.SceneRoutePolicyRow row,
            List<RouteCandidateItem> candidates
    ) {
        AiApplicationSceneRegistry.ApplicationSceneDefinition sceneDefinition = findSceneDefinition(row.taskType(), row.sceneCode());
        AiThinkingConfig configuredThinking = readRoutePolicyThinkingConfig(row.extraConfigJson());
        return new RoutePolicyItem(
                row.id(),
                row.policyCode(),
                sceneDefinition == null ? "" : sceneDefinition.channelCode(),
                sceneDefinition == null ? row.sceneCode() : sceneDefinition.displayName(),
                sceneDefinition == null ? "" : sceneDefinition.ownerDomain(),
                sceneDefinition == null ? "" : sceneDefinition.frontEntry(),
                sceneDefinition == null ? "" : sceneDefinition.summary(),
                row.taskType(),
                row.sceneCode(),
                normalizeRoutePolicyTier(row.userTier()),
                normalizeRouteStrategyType(row.strategyType()),
                row.enabled(),
                configuredThinking == null ? null : configuredThinking.reasoningEffortCode(),
                configuredThinking == null ? null : configuredThinking.thinkingBudget(),
                configuredThinking == null ? null : configuredThinking.thinkingLevel(),
                safe(row.notes()),
                candidates == null ? List.of() : candidates,
                com.bishe.server.common.TimePayloads.toEpochMillis(row.createdAt()),
                com.bishe.server.common.TimePayloads.toEpochMillis(row.updatedAt())
        );
    }

    private PromptTemplateItem toPromptTemplateItem(PromptTemplateRepository.PromptTemplateRow row) {
        return new PromptTemplateItem(
                row.id(),
                row.taskType(),
                row.templateName(),
                row.versionNo(),
                row.status(),
                safe(row.templateFormat()),
                safe(row.content()),
                safe(row.description()),
                safe(row.variablesJson()),
                safe(row.bundleJson()),
                com.bishe.server.common.TimePayloads.toEpochMillis(row.createdAt()),
                com.bishe.server.common.TimePayloads.toEpochMillis(row.updatedAt())
        );
    }

    private NormalizedProvider normalizeProvider(UpsertProviderCommand command, AiProviderConfigRepository.ProviderConfigRow current) {
        String providerCode = normalizeCode(command.providerCode(), current == null ? null : current.providerCode(), "providerCode");
        AiProviderType providerType = parseProviderType(command.providerType(), current == null ? null : current.providerType());
        String displayName = normalizeRequiredText(command.displayName(), current == null ? null : current.displayName(), "displayName", 60);
        String baseUrl = normalizeRequiredText(command.baseUrl(), current == null ? null : current.baseUrl(), "baseUrl", 255);
        boolean enabled = command.enabled() != null ? command.enabled() : current == null || current.enabled();
        int timeoutMs = normalizePositiveInt(command.timeoutMs(), current == null ? 15000 : current.timeoutMs(), 1000, 120000, "timeoutMs");
        int maxRetries = normalizePositiveInt(command.maxRetries(), current == null ? 1 : current.maxRetries(), 0, 5, "maxRetries");
        BigDecimal costPer1kInput = normalizeDecimal(command.costPer1kInput(), current == null ? BigDecimal.ZERO : current.costPer1kInput(), "costPer1kInput");
        BigDecimal costPer1kOutput = normalizeDecimal(command.costPer1kOutput(), current == null ? BigDecimal.ZERO : current.costPer1kOutput(), "costPer1kOutput");
        String extraConfigJson = normalizeJson(command.extraConfigJson(), current == null ? null : current.extraConfigJson(), false);

        String apiKeyCiphertext = current == null ? null : current.apiKeyCiphertext();
        String apiKeyMasked = current == null ? "" : safe(current.apiKeyMasked());
        if (command.apiKey() != null && !command.apiKey().isBlank()) {
            String apiKey = command.apiKey().trim();
            apiKeyCiphertext = cryptoService.encrypt(apiKey);
            apiKeyMasked = cryptoService.mask(apiKey);
        }

        boolean replaceModels = command.models() != null || current == null;
        List<AiProviderModelRepository.ProviderModelMutation> models = replaceModels
                ? normalizeProviderModels(command.models())
                : aiProviderModelRepository.findProviderModelsByProviderId(Objects.requireNonNull(current).id()).stream()
                .map(row -> new AiProviderModelRepository.ProviderModelMutation(
                        row.modelCode(),
                        row.displayName(),
                        row.enabled(),
                        decimalOrDefault(row.inputCostPer1k(), BigDecimal.ZERO),
                        decimalOrDefault(row.outputCostPer1k(), BigDecimal.ZERO),
                        row.contextWindow(),
                        row.maxOutputTokens(),
                        blankToNull(row.supportedTaskTypesJson()),
                        blankToNull(row.notes())
                ))
                .toList();

        return new NormalizedProvider(
                providerCode,
                providerType,
                displayName,
                baseUrl,
                apiKeyCiphertext,
                apiKeyMasked,
                enabled,
                timeoutMs,
                maxRetries,
                costPer1kInput,
                costPer1kOutput,
                extraConfigJson,
                replaceModels,
                models
        );
    }

    private List<AiProviderModelRepository.ProviderModelMutation> normalizeProviderModels(
            List<UpsertProviderModelCommand> commands
    ) {
        if (commands == null || commands.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, AiProviderModelRepository.ProviderModelMutation> deduped = new LinkedHashMap<>();
        for (UpsertProviderModelCommand command : commands) {
            if (command == null) {
                continue;
            }
            String modelCode = normalizeRequiredModelCode(command.modelCode(), null);
            String displayName = normalizeRequiredText(command.displayName(), modelCode, "modelDisplayName", 120);
            boolean enabled = command.enabled() == null || command.enabled();
            BigDecimal inputCostPer1k = normalizeDecimal(command.inputCostPer1k(), BigDecimal.ZERO, "modelInputCostPer1k");
            BigDecimal outputCostPer1k = normalizeDecimal(command.outputCostPer1k(), BigDecimal.ZERO, "modelOutputCostPer1k");
            Integer contextWindow = normalizeOptionalInt(command.contextWindow(), 1, 10_000_000, "contextWindow");
            Integer maxOutputTokens = normalizeOptionalInt(command.maxOutputTokens(), 1, 10_000_000, "maxOutputTokens");
            String supportedTaskTypesJson = normalizeSupportedTaskTypesJson(command.supportedTaskTypes());
            String notes = normalizeOptionalText(command.notes(), null, 255);
            deduped.put(modelCode.toLowerCase(Locale.ROOT), new AiProviderModelRepository.ProviderModelMutation(
                    modelCode,
                    displayName,
                    enabled,
                    inputCostPer1k,
                    outputCostPer1k,
                    contextWindow,
                    maxOutputTokens,
                    supportedTaskTypesJson,
                    notes
            ));
        }
        return List.copyOf(deduped.values());
    }

    private NormalizedRoutePolicy normalizeRoutePolicy(
            UpsertRoutePolicyCommand command,
            SceneRoutePolicyRepository.SceneRoutePolicyRow current
    ) {
        String taskType = normalizeTaskType(command.taskType(), current == null ? null : current.taskType());
        String sceneCode = normalizeRequiredCode(command.sceneCode(), current == null ? null : current.sceneCode(), "sceneCode");
        String userTier = normalizeRoutePolicyTier(command.userTier(), current == null ? "ALL" : current.userTier());
        String policyCode = normalizeCode(command.policyCode(), defaultRoutePolicyCode(taskType, sceneCode, userTier), "policyCode");
        String strategyType = normalizeRouteStrategyType(command.strategyType(), current == null ? "SINGLE" : current.strategyType());
        boolean enabled = command.enabled() != null ? command.enabled() : current == null || current.enabled();
        String notes = normalizeOptionalText(command.notes(), current == null ? null : current.notes(), 255);
        String reasoningEffort = normalizeOptionalReasoningEffort(command.reasoningEffort());
        Integer thinkingBudget = normalizeOptionalThinkingBudget(command.thinkingBudget());
        String thinkingLevel = normalizeOptionalText(command.thinkingLevel(), null, 40);
        String extraConfigJson = mergeThinkingConfigIntoExtraConfig(
                current == null ? null : current.extraConfigJson(),
                reasoningEffort,
                thinkingBudget,
                thinkingLevel
        );
        return new NormalizedRoutePolicy(policyCode, taskType, sceneCode, userTier, strategyType, enabled, notes, extraConfigJson);
    }

    private NormalizedRoute normalizeRoute(UpsertRouteCommand command, AiModelRouteRepository.ModelRouteRow current) {
        Long sceneRoutePolicyId = normalizeOptionalRoutePolicyId(command.sceneRoutePolicyId(), current == null ? null : current.sceneRoutePolicyId());
        SceneRoutePolicyRepository.SceneRoutePolicyRow routePolicy = sceneRoutePolicyId == null
                ? null
                : sceneRoutePolicyRepository.findRoutePolicyById(sceneRoutePolicyId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "route policy not found", HttpStatus.NOT_FOUND));
        String routeCode = normalizeCode(command.routeCode(), current == null ? null : current.routeCode(), "routeCode");
        String taskType = normalizeTaskType(
                command.taskType(),
                routePolicy == null ? (current == null ? null : current.taskType()) : routePolicy.taskType()
        );
        String sceneCode = routePolicy == null
                ? normalizeOptionalCode(command.sceneCode(), current == null ? null : current.sceneCode())
                : normalizeRequiredCode(command.sceneCode(), routePolicy.sceneCode(), "sceneCode");
        if (routePolicy == null && taskType != null && sceneCode != null) {
            routePolicy = sceneRoutePolicyRepository.findRoutePolicyBySceneAndTier(taskType, sceneCode, "ALL").orElse(null);
            sceneRoutePolicyId = routePolicy == null ? null : routePolicy.id();
        }
        long providerConfigId = normalizeProviderConfigId(command.providerConfigId(), current == null ? 0L : current.providerConfigId());
        String modelName = normalizeRequiredText(command.modelName(), current == null ? null : current.modelName(), "modelName", 120);
        ensureProviderModelConfigured(providerConfigId, modelName);
        int priorityNo = normalizePositiveInt(command.priorityNo(), current == null ? 100 : current.priorityNo(), 0, 10000, "priorityNo");
        int candidateWeight = normalizePositiveInt(command.candidateWeight(), current == null ? 100 : current.candidateWeight(), 1, 10000, "candidateWeight");
        String executionMode = normalizeExecutionMode(command.executionMode(), current == null ? AiExecutionMode.SYNC_BLOCKING.name() : current.executionMode());
        boolean enabled = command.enabled() != null ? command.enabled() : current == null || current.enabled();
        BigDecimal temperature = normalizeDecimal(command.temperature(), current == null ? BigDecimal.valueOf(0.2) : current.temperature(), "temperature");
        String systemPrompt = normalizeOptionalText(command.systemPrompt(), current == null ? null : current.systemPrompt(), 4000);
        String promptTemplateName = normalizeOptionalCode(command.promptTemplateName(), current == null ? null : current.promptTemplateName());
        if (promptTemplateName != null) {
            ensureActivePromptTemplate(taskType, promptTemplateName);
        }
        String extraConfigJson = normalizeJson(command.extraConfigJson(), current == null ? null : current.extraConfigJson(), false);
        if (routePolicy != null && (!Objects.equals(routePolicy.taskType(), taskType) || !Objects.equals(routePolicy.sceneCode(), sceneCode))) {
            throw new ApiException("BIZ-1001", "route policy scene mismatch", HttpStatus.BAD_REQUEST);
        }
        return new NormalizedRoute(
                routeCode,
                taskType,
                sceneCode,
                sceneRoutePolicyId,
                providerConfigId,
                modelName,
                priorityNo,
                candidateWeight,
                executionMode,
                enabled,
                temperature,
                systemPrompt,
                promptTemplateName,
                extraConfigJson
        );
    }

    private RouteDisplayContext loadRouteDisplayContext(AiModelRouteRepository.ModelRouteRow row) {
        AiProviderConfigRepository.ProviderConfigRow provider = aiProviderConfigRepository.findProviderById(row.providerConfigId())
                .orElseThrow(() -> new ApiException("BIZ-1002", "provider not found", HttpStatus.NOT_FOUND));
        SceneRoutePolicyRepository.SceneRoutePolicyRow routePolicy = row.sceneRoutePolicyId() == null
                ? null
                : sceneRoutePolicyRepository.findRoutePolicyById(row.sceneRoutePolicyId()).orElse(null);
        Integer activePromptTemplateVersionNo = resolveActivePromptTemplateVersion(row.taskType(), row.promptTemplateName());
        return new RouteDisplayContext(provider, routePolicy, activePromptTemplateVersionNo);
    }

    private Integer resolveActivePromptTemplateVersion(String taskType, String promptTemplateName) {
        if (promptTemplateName == null || promptTemplateName.isBlank()) {
            return null;
        }
        return promptTemplateRepository.findActivePromptTemplate(taskType, promptTemplateName)
                .map(PromptTemplateRepository.PromptTemplateRow::versionNo)
                .orElse(null);
    }

    private NormalizedPromptTemplate normalizePromptTemplate(UpsertPromptTemplateCommand command, PromptTemplateRepository.PromptTemplateRow current) {
        String taskType = normalizeTaskType(command.taskType(), current == null ? null : current.taskType());
        String templateName = normalizeCode(command.templateName(), current == null ? null : current.templateName(), "templateName");
        int versionNo = normalizePositiveInt(command.versionNo(), current == null ? 1 : current.versionNo(), 1, 1000, "versionNo");
        String status = parsePromptTemplateStatus(command.status(), current == null ? "DRAFT" : current.status());
        String templateFormat = normalizePromptTemplateFormat(command.templateFormat(), current == null ? null : current.templateFormat());
        String content = normalizeTemplateContent(
                command.content(),
                current == null ? null : current.content(),
                templateFormat
        );
        String description = normalizeOptionalText(command.description(), current == null ? null : current.description(), 255);
        String variablesJson = normalizeJson(command.variablesJson(), current == null ? null : current.variablesJson(), false);
        String bundleJson = normalizeTemplateBundleJson(
                command.bundleJson(),
                current == null ? null : current.bundleJson(),
                templateFormat
        );
        return new NormalizedPromptTemplate(taskType, templateName, versionNo, status, templateFormat, content, description, variablesJson, bundleJson);
    }

    private AiProviderType parseProviderType(String rawValue, String fallback) {
        String candidate = rawValue == null || rawValue.isBlank() ? fallback : rawValue;
        try {
            return AiProviderType.from(candidate);
        } catch (IllegalArgumentException ex) {
            throw new ApiException("BIZ-1001", "providerType invalid", HttpStatus.BAD_REQUEST);
        }
    }

    private long normalizeProviderConfigId(Long rawValue, long fallback) {
        long resolved = rawValue == null ? fallback : rawValue;
        if (resolved <= 0) {
            throw new ApiException("BIZ-1001", "providerConfigId invalid", HttpStatus.BAD_REQUEST);
        }
        aiProviderConfigRepository.findProviderById(resolved)
                .orElseThrow(() -> new ApiException("BIZ-1002", "provider not found", HttpStatus.NOT_FOUND));
        return resolved;
    }

    private void ensureProviderModelConfigured(long providerConfigId, String modelName) {
        AiProviderConfigRepository.ProviderConfigRow provider = aiProviderConfigRepository.findProviderById(providerConfigId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "provider not found", HttpStatus.NOT_FOUND));
        List<AiProviderModelRepository.ProviderModelRow> enabledModels = aiProviderModelRepository.findProviderModelsByProviderId(providerConfigId).stream()
                .filter(AiProviderModelRepository.ProviderModelRow::enabled)
                .toList();
        if (enabledModels.isEmpty()) {
            throw new ApiException("BIZ-1001", "provider models missing", HttpStatus.BAD_REQUEST);
        }
        boolean matched = enabledModels.stream()
                .anyMatch(model -> safe(model.modelCode()).trim().equalsIgnoreCase(safe(modelName).trim()));
        if (!matched) {
            throw new ApiException(
                    "BIZ-1001",
                    "modelName not configured for provider " + normalizeOptionalCode(provider.providerCode(), "UNKNOWN_PROVIDER"),
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private Long normalizeOptionalRoutePolicyId(Long rawValue, Long fallback) {
        Long resolved = rawValue == null ? fallback : rawValue;
        if (resolved == null) {
            return null;
        }
        if (resolved <= 0) {
            throw new ApiException("BIZ-1001", "sceneRoutePolicyId invalid", HttpStatus.BAD_REQUEST);
        }
        return resolved;
    }

    private String normalizeTaskType(String rawValue, String fallback) {
        String candidate = rawValue == null || rawValue.isBlank() ? fallback : rawValue;
        try {
            return AiGatewayTaskType.from(candidate).name();
        } catch (IllegalArgumentException ex) {
            throw new ApiException("BIZ-1001", "taskType invalid", HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizeExecutionMode(String rawValue, String fallback) {
        String candidate = rawValue == null || rawValue.isBlank() ? fallback : rawValue;
        try {
            return AiExecutionMode.from(candidate).name();
        } catch (IllegalArgumentException ex) {
            throw new ApiException("BIZ-1001", "executionMode invalid", HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizeRouteStrategyType(String rawValue) {
        return normalizeRouteStrategyType(rawValue, "SINGLE");
    }

    private String normalizeRouteStrategyType(String rawValue, String fallback) {
        String candidate = rawValue == null || rawValue.isBlank() ? fallback : rawValue;
        String normalized = candidate.trim().toUpperCase(Locale.ROOT);
        if (!List.of("SINGLE", "FAILOVER", "WEIGHTED").contains(normalized)) {
            throw new ApiException("BIZ-1001", "routeStrategyType invalid", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeRoutePolicyTier(String rawValue) {
        return normalizeRoutePolicyTier(rawValue, "ALL");
    }

    private String normalizeRoutePolicyTier(String rawValue, String fallback) {
        String candidate = rawValue == null || rawValue.isBlank() ? fallback : rawValue;
        String normalized = candidate.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ALL", "FREE", "PREMIUM").contains(normalized)) {
            throw new ApiException("BIZ-1001", "userTier invalid", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeOptionalReasoningEffort(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return AiReasoningEffort.fromNullable(rawValue).name();
        } catch (IllegalArgumentException ex) {
            throw new ApiException("BIZ-1001", "reasoningEffort invalid", HttpStatus.BAD_REQUEST);
        }
    }

    private Integer normalizeOptionalThinkingBudget(Integer rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue < 0 || rawValue > 1_000_000) {
            throw new ApiException("BIZ-1001", "thinkingBudget invalid", HttpStatus.BAD_REQUEST);
        }
        return rawValue;
    }

    private String normalizePromptTemplateFormat(String rawValue, String fallback) {
        String candidate = rawValue == null || rawValue.isBlank() ? fallback : rawValue;
        try {
            return AiPromptTemplateFormat.from(candidate).name();
        } catch (IllegalArgumentException ex) {
            throw new ApiException("BIZ-1001", "templateFormat invalid", HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizeTemplateContent(String rawValue, String fallback, String templateFormat) {
        if (AiPromptTemplateFormat.MESSAGE_BUNDLE.name().equals(templateFormat)) {
            String candidate = rawValue == null ? fallback : rawValue;
            if (candidate == null) {
                return "";
            }
            String normalized = candidate.trim();
            if (normalized.length() > 12000) {
                throw new ApiException("BIZ-1001", "content too long", HttpStatus.BAD_REQUEST);
            }
            return normalized;
        }
        return normalizeRequiredText(rawValue, fallback, "content", 12000);
    }

    private String normalizeTemplateBundleJson(String rawValue, String fallback, String templateFormat) {
        boolean required = AiPromptTemplateFormat.MESSAGE_BUNDLE.name().equals(templateFormat);
        return normalizeJson(rawValue, fallback, required);
    }

    private String normalizeCode(String rawValue, String fallback, String fieldName) {
        String candidate = rawValue == null || rawValue.isBlank() ? fallback : rawValue;
        if (candidate == null || candidate.isBlank()) {
            throw new ApiException("BIZ-1001", fieldName + " invalid", HttpStatus.BAD_REQUEST);
        }
        String normalized = candidate.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_\\-]", "_");
        if (normalized.length() > 60) {
            throw new ApiException("BIZ-1001", fieldName + " invalid", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeOptionalCode(String rawValue, String fallback) {
        String candidate = rawValue == null ? fallback : rawValue;
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        String normalized = candidate.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_\\-]", "_");
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeRequiredCode(String rawValue, String fallback, String fieldName) {
        String normalized = normalizeOptionalCode(rawValue, fallback);
        if (normalized == null) {
            throw new ApiException("BIZ-1001", fieldName + " invalid", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeRequiredText(String rawValue, String fallback, String fieldName, int maxLength) {
        String candidate = rawValue == null || rawValue.isBlank() ? fallback : rawValue;
        if (candidate == null || candidate.isBlank()) {
            throw new ApiException("BIZ-1001", fieldName + " invalid", HttpStatus.BAD_REQUEST);
        }
        String normalized = candidate.trim();
        if (normalized.length() > maxLength) {
            throw new ApiException("BIZ-1001", fieldName + " too long", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeOptionalText(String rawValue, String fallback, int maxLength) {
        String candidate = rawValue == null ? fallback : rawValue;
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        String normalized = candidate.trim();
        if (normalized.length() > maxLength) {
            throw new ApiException("BIZ-1001", "text too long", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private int normalizePositiveInt(Integer rawValue, int fallback, int min, int max, String fieldName) {
        int resolved = rawValue == null ? fallback : rawValue;
        if (resolved < min || resolved > max) {
            throw new ApiException("BIZ-1001", fieldName + " invalid", HttpStatus.BAD_REQUEST);
        }
        return resolved;
    }

    private Integer normalizeOptionalInt(Integer rawValue, int min, int max, String fieldName) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue < min || rawValue > max) {
            throw new ApiException("BIZ-1001", fieldName + " invalid", HttpStatus.BAD_REQUEST);
        }
        return rawValue;
    }

    private BigDecimal normalizeDecimal(String rawValue, BigDecimal fallback, String fieldName) {
        if (rawValue == null || rawValue.isBlank()) {
            return decimalOrDefault(fallback, BigDecimal.ZERO);
        }
        try {
            BigDecimal value = new BigDecimal(rawValue.trim());
            if (value.compareTo(BigDecimal.ZERO) < 0) {
                throw new ApiException("BIZ-1001", fieldName + " invalid", HttpStatus.BAD_REQUEST);
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new ApiException("BIZ-1001", fieldName + " invalid", HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizeJson(String rawValue, String fallback, boolean required) {
        String candidate = rawValue == null ? fallback : rawValue;
        if (candidate == null || candidate.isBlank()) {
            if (required) {
                throw new ApiException("BIZ-1001", "json invalid", HttpStatus.BAD_REQUEST);
            }
            return null;
        }
        try {
            JsonNode jsonNode = objectMapper.readTree(candidate);
            return objectMapper.writeValueAsString(jsonNode);
        } catch (Exception ex) {
            throw new ApiException("BIZ-1001", "json invalid", HttpStatus.BAD_REQUEST);
        }
    }

    private AiThinkingConfig readRoutePolicyThinkingConfig(String extraConfigJson) {
        AiThinkingConfig configured = thinkingPolicyResolver.readConfiguredOverride(extraConfigJson, ROUTE_POLICY_THINKING_SOURCE);
        return configured != null && configured.isConfigured() ? configured : null;
    }

    private String mergeThinkingConfigIntoExtraConfig(
            String baseExtraConfigJson,
            String reasoningEffort,
            Integer thinkingBudget,
            String thinkingLevel
    ) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            if (baseExtraConfigJson != null && !baseExtraConfigJson.isBlank()) {
                JsonNode existingRoot = objectMapper.readTree(baseExtraConfigJson);
                if (!existingRoot.isObject()) {
                    throw new ApiException("BIZ-1001", "routePolicy extraConfigJson invalid", HttpStatus.BAD_REQUEST);
                }
                root.setAll((ObjectNode) existingRoot);
            }
            root.remove("reasoningEffort");
            root.remove("thinkingMode");
            root.remove("reasoningMode");
            root.remove("thinkingBudget");
            root.remove("thinkingLevel");
            root.remove("thinking");
            if (reasoningEffort != null || thinkingBudget != null || thinkingLevel != null) {
                ObjectNode thinkingNode = objectMapper.createObjectNode();
                if (reasoningEffort != null) {
                    thinkingNode.put("reasoningEffort", reasoningEffort);
                }
                if (thinkingBudget != null) {
                    thinkingNode.put("thinkingBudget", thinkingBudget);
                }
                if (thinkingLevel != null) {
                    thinkingNode.put("thinkingLevel", thinkingLevel);
                }
                root.set("thinking", thinkingNode);
            }
            return root.isEmpty() ? null : objectMapper.writeValueAsString(root);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException("BIZ-1001", "routePolicy thinking config invalid", HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizeSupportedTaskTypesJson(List<String> taskTypes) {
        if (taskTypes == null || taskTypes.isEmpty()) {
            return null;
        }
        LinkedHashSet<String> normalizedTaskTypes = new LinkedHashSet<>();
        for (String taskType : taskTypes) {
            normalizedTaskTypes.add(normalizeTaskType(taskType, taskType));
        }
        try {
            return objectMapper.writeValueAsString(normalizedTaskTypes);
        } catch (Exception ex) {
            throw new ApiException("BIZ-1001", "supportedTaskTypes invalid", HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizeOptionalTaskType(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return normalizeTaskType(rawValue, rawValue);
    }

    private String normalizeOptionalModel(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String normalized = rawValue.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeRequiredModelCode(String rawValue, String fallback) {
        String normalized = normalizeOptionalModel(rawValue == null || rawValue.isBlank() ? fallback : rawValue);
        if (normalized == null) {
            throw new ApiException("BIZ-1001", "modelCode invalid", HttpStatus.BAD_REQUEST);
        }
        if (normalized.length() > 120) {
            throw new ApiException("BIZ-1001", "modelCode invalid", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private Long normalizeOptionalId(Long rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue <= 0) {
            throw new ApiException("BIZ-1001", "userId invalid", HttpStatus.BAD_REQUEST);
        }
        return rawValue;
    }

    private PromptTemplateItem activatePromptTemplate(PromptTemplateRepository.PromptTemplateRow target) {
        promptTemplateRepository.updatePromptTemplate(
                target.id(),
                target.taskType(),
                target.templateName(),
                target.versionNo(),
                "ACTIVE",
                target.templateFormat(),
                target.content(),
                target.description(),
                target.variablesJson(),
                target.bundleJson()
        );
        promptTemplateRepository.deactivateOtherPromptTemplates(target.taskType(), target.templateName(), target.id());
        evictAiGatewayConfigCachesNowAndAfterCommit();
        return promptTemplateRepository.findPromptTemplateById(target.id())
                .map(this::toPromptTemplateItem)
                .orElseThrow(() -> new ApiException("BIZ-1002", "prompt template not found", HttpStatus.NOT_FOUND));
    }

    private void evictAiGatewayConfigCachesNowAndAfterCommit() {
        aiRouteCacheService.evictAllNow();
        aiGatewayAdminListCacheService.evictAllNow();
        aiRouteCacheService.evictAllAfterCommit();
        aiGatewayAdminListCacheService.evictAllAfterCommit();
    }

    private void evictAdminWorkbenchCacheNowAndAfterCommit() {
        adminWorkbenchCacheService.evictAllNow();
        adminWorkbenchCacheService.evictAllAfterCommit();
    }

    private void ensureSamePromptTemplateFamily(
            PromptTemplateRepository.PromptTemplateRow source,
            PromptTemplateRepository.PromptTemplateRow target
    ) {
        if (!source.taskType().equals(target.taskType()) || !source.templateName().equals(target.templateName())) {
            throw new ApiException("BIZ-1001", "rollback target invalid", HttpStatus.BAD_REQUEST);
        }
    }

    private void ensureActivePromptTemplate(String taskType, String templateName) {
        promptTemplateRepository.findActivePromptTemplate(taskType, templateName)
                .orElseThrow(() -> new ApiException("BIZ-1002", "active prompt template not found", HttpStatus.NOT_FOUND));
    }

    private String parsePromptTemplateStatus(String rawValue, String fallback) {
        String candidate = rawValue == null || rawValue.isBlank() ? fallback : rawValue;
        String normalized = candidate == null ? "DRAFT" : candidate.trim().toUpperCase(Locale.ROOT);
        if (!List.of("DRAFT", "ACTIVE", "INACTIVE").contains(normalized)) {
            throw new ApiException("BIZ-1001", "promptTemplateStatus invalid", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String defaultRoutePolicyCode(String taskType, String sceneCode, String userTier) {
        return normalizeCode(taskType + "_" + sceneCode + "_" + userTier, null, "policyCode");
    }

    private String blankToNull(String rawValue) {
        return rawValue == null || rawValue.isBlank() ? null : rawValue;
    }


    private String resolveDayLabel(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> "Mon";
            case TUESDAY -> "Tue";
            case WEDNESDAY -> "Wed";
            case THURSDAY -> "Thu";
            case FRIDAY -> "Fri";
            case SATURDAY -> "Sat";
            case SUNDAY -> "Sun";
        };
    }

    private String normalizePeriod(String rawValue) {
        String candidate = rawValue == null || rawValue.isBlank() ? "today" : rawValue.trim().toLowerCase(Locale.ROOT);
        if (!List.of("today", "week", "month").contains(candidate)) {
            throw new ApiException("BIZ-1001", "period invalid", HttpStatus.BAD_REQUEST);
        }
        return candidate;
    }

    private TimeWindow resolveTimeWindow(String period) {
        LocalDate today = LocalDate.now();
        LocalDate startDate;
        switch (period) {
            case "month" -> startDate = today.withDayOfMonth(1);
            case "week" -> startDate = today.with(DayOfWeek.MONDAY);
            default -> startDate = today;
        }
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = LocalDateTime.of(today, LocalTime.MAX);
        return new TimeWindow(Timestamp.valueOf(start), Timestamp.valueOf(end));
    }

    private BigDecimal decimalOrDefault(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

    private void appendMetricLines(StringBuilder builder, String section, List<MetricItem> metrics) {
        for (MetricItem item : metrics) {
            builder.append(csvLine(section, item.name(), item.calls(), item.cost(), ""));
        }
    }

    private String csvLine(String section, String name, long calls, String cost, String extra) {
        return escapeCsv(section)
                + ","
                + escapeCsv(name)
                + ","
                + calls
                + ","
                + escapeCsv(cost)
                + ","
                + escapeCsv(extra)
                + "\n";
    }

    private String escapeCsv(String value) {
        String safeValue = value == null ? "" : value;
        String escaped = safeValue.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            throw new ApiException("BIZ-1001", "json invalid", HttpStatus.BAD_REQUEST);
        }
    }

    private String safe(String rawValue) {
        return rawValue == null ? "" : rawValue;
    }

    public record UpsertProviderCommand(
            String providerCode,
            String providerType,
            String displayName,
            String baseUrl,
            String apiKey,
            Boolean enabled,
            Integer timeoutMs,
            Integer maxRetries,
            String costPer1kInput,
            String costPer1kOutput,
            String extraConfigJson,
            List<UpsertProviderModelCommand> models
    ) {
    }

    public record UpsertProviderModelCommand(
            String modelCode,
            String displayName,
            Boolean enabled,
            String inputCostPer1k,
            String outputCostPer1k,
            Integer contextWindow,
            Integer maxOutputTokens,
            List<String> supportedTaskTypes,
            String notes
    ) {
    }

    public record UpsertRoutePolicyCommand(
            String policyCode,
            String taskType,
            String sceneCode,
            String userTier,
            String strategyType,
            Boolean enabled,
            String reasoningEffort,
            Integer thinkingBudget,
            String thinkingLevel,
            String notes
    ) {
    }

    public record UpsertRouteCommand(
            String routeCode,
            String taskType,
            String sceneCode,
            Long sceneRoutePolicyId,
            Long providerConfigId,
            String modelName,
            Integer priorityNo,
            Integer candidateWeight,
            String executionMode,
            Boolean enabled,
            String temperature,
            String systemPrompt,
            String promptTemplateName,
            String extraConfigJson
    ) {
    }

    public record UpsertPromptTemplateCommand(
            String taskType,
            String templateName,
            Integer versionNo,
            String status,
            String templateFormat,
            String content,
            String description,
            String variablesJson,
            String bundleJson
    ) {
    }

    public record RollbackPromptTemplateCommand(Long targetTemplateId) {
    }

    public record UpdateRuntimeSettingsCommand(
            Boolean debugModeEnabled,
            Boolean aiRequestLogEnabled,
            String defaultReasoningEffort,
            Integer defaultThinkingBudget,
            String defaultThinkingLevel
    ) {
    }

    public record ProviderListPayload(List<ProviderItem> records) {
    }

    public record RouteListPayload(List<RouteItem> records) {
    }

    public record RoutePolicyListPayload(List<RoutePolicyItem> records) {
    }

    public record PromptTemplateListPayload(List<PromptTemplateItem> records) {
    }

    public record AdminMetaPayload(
            List<ProviderOption> providerTypes,
            List<TaskTypeOption> taskTypes,
            List<ExecutionModeOption> executionModes,
            List<TierOption> tierOptions,
            List<RouteStrategyOption> routeStrategyTypes
    ) {
    }

    public record ProviderOption(String code, String label) {
    }

    public record TaskTypeOption(String code, String label) {
    }

    public record ExecutionModeOption(String code, String label) {
    }

    public record TierOption(String code, String label) {
    }

    public record RouteStrategyOption(String code, String label) {
    }

    public record ProviderItem(
            long id,
            String providerCode,
            String providerType,
            String displayName,
            String baseUrl,
            boolean enabled,
            int timeoutMs,
            int maxRetries,
            String costPer1kInput,
            String costPer1kOutput,
            String apiKeyMasked,
            boolean hasApiKey,
            String extraConfigJson,
            List<ProviderModelItem> models,
            Long createdAt,
            Long updatedAt
    ) {
    }

    public record ProviderModelItem(
            long id,
            long providerConfigId,
            String modelCode,
            String displayName,
            boolean enabled,
            String inputCostPer1k,
            String outputCostPer1k,
            Integer contextWindow,
            Integer maxOutputTokens,
            String supportedTaskTypesJson,
            String notes,
            Long createdAt,
            Long updatedAt
    ) {
    }

    public record ProviderConnectivityPayload(
            long providerId,
            String providerCode,
            String providerDisplayName,
            String providerType,
            String baseUrl,
            String probeUrl,
            boolean reachable,
            boolean authenticated,
            boolean available,
            Integer httpStatus,
            long latencyMs,
            String status,
            String message,
            Long checkedAt
    ) {
    }

    public record ProviderRuntimeStatsPayload(
            String timezone,
            int hours,
            long totalProviders,
            long healthyProviders,
            long degradedProviders,
            long downProviders,
            long idleProviders,
            long disabledProviders,
            List<ProviderRuntimeItem> providers
    ) {
    }

    public record ProviderRuntimeItem(
            long providerId,
            String providerCode,
            String providerDisplayName,
            String providerType,
            boolean enabled,
            String runtimeStatus,
            String successRate,
            long totalCalls,
            long avgLatencyMs,
            Long lastEventAt,
            List<ProviderRuntimeBlock> blocks
    ) {
    }

    public record ProviderRuntimeBlock(
            String label,
            long calls,
            long successCalls,
            long avgLatencyMs,
            String status
    ) {
    }

    public record RouteItem(
            long id,
            Long sceneRoutePolicyId,
            String routePolicyCode,
            String routePolicyUserTier,
            String strategyType,
            String routeCode,
            String taskType,
            String sceneCode,
            long providerConfigId,
            String providerCode,
            String providerType,
            String providerDisplayName,
            String modelName,
            int priorityNo,
            int candidateWeight,
            String executionMode,
            boolean enabled,
            String temperature,
            String systemPrompt,
            String promptTemplateName,
            Integer promptTemplateVersionNo,
            String extraConfigJson,
            Long createdAt,
            Long updatedAt
    ) {
    }

    public record RouteCandidateItem(
            long id,
            Long sceneRoutePolicyId,
            String routeCode,
            long providerConfigId,
            String providerCode,
            String providerDisplayName,
            String providerType,
            String modelName,
            int priorityNo,
            int candidateWeight,
            String executionMode,
            boolean enabled,
            String promptTemplateName,
            Integer promptTemplateVersionNo,
            String systemPrompt,
            String extraConfigJson,
            Long createdAt,
            Long updatedAt
    ) {
    }

    public record RoutePolicyItem(
            long id,
            String policyCode,
            String channelCode,
            String sceneDisplayName,
            String ownerDomain,
            String frontEntry,
            String sceneSummary,
            String taskType,
            String sceneCode,
            String userTier,
            String strategyType,
            boolean enabled,
            String reasoningEffort,
            Integer thinkingBudget,
            String thinkingLevel,
            String notes,
            List<RouteCandidateItem> candidates,
            Long createdAt,
            Long updatedAt
    ) {
    }

    public record PromptTemplateItem(
            long id,
            String taskType,
            String templateName,
            int versionNo,
            String status,
            String templateFormat,
            String content,
            String description,
            String variablesJson,
            String bundleJson,
            Long createdAt,
            Long updatedAt
    ) {
    }

    public record AiLogQueryCommand(
            Integer page,
            Integer size,
            String taskType,
            String sceneCode,
            String provider,
            String status,
            Long userId,
            String traceId
    ) {
    }

    public record RouteResolvePreviewCommand(
            String taskType,
            String sceneCode,
            String modelPreference,
            String userTier
    ) {
    }

    public record PromptTemplateRenderPreviewCommand(
            String taskType,
            String templateFormat,
            String content,
            String variablesJson,
            String bundleJson,
            String renderVariablesJson
    ) {
    }

    public record AiLogListPayload(
            List<AiLogItem> records,
            long total,
            int page,
            int size
    ) {
    }

    public record AiLogItem(
            long id,
            String traceId,
            long userId,
            String userEmail,
            String userDisplayName,
            String taskType,
            String sceneCode,
            String routeCode,
            String routePolicyCode,
            String provider,
            String model,
            String status,
            String errorCode,
            long latencyMs,
            int requestTokens,
            int responseTokens,
            int totalTokens,
            int thoughtsTokens,
            String reasoningEffort,
            Integer thinkingBudget,
            String thinkingLevel,
            String estimatedCost,
            int chargedPoints,
            int quotaWeight,
            String resultSummary,
            String userTier,
            Long createdAt
    ) {
    }


    public record AiLogDetailPayload(
            long id,
            String traceId,
            long userId,
            String userEmail,
            String userDisplayName,
            String taskType,
            String sceneCode,
            String routeCode,
            String routePolicyCode,
            String provider,
            String model,
            String status,
            String errorCode,
            long latencyMs,
            int requestTokens,
            int responseTokens,
            int totalTokens,
            int thoughtsTokens,
            String reasoningEffort,
            Integer thinkingBudget,
            String thinkingLevel,
            String estimatedCost,
            int chargedPoints,
            int quotaWeight,
            String resultSummary,
            String resultPayloadJson,
            String userTier,
            Long createdAt,
            GovernanceTraceSummaryPayload governanceTraceSummary,
            CurrentRouteSnapshotPayload currentRouteSnapshot
        ) {
    }

    public record CurrentRouteSnapshotPayload(
            String routePolicyCode,
            String routePolicyUserTier,
            String routeStrategyType,
            String routeCode,
            String sceneCode,
            String providerCode,
            String providerDisplayName,
            String providerType,
            String model,
            String executionMode,
            String promptTemplateName,
            Integer promptTemplateVersionNo,
            String reasoningEffort,
            Integer thinkingBudget,
            String thinkingLevel,
            String thinkingSource,
            String costPer1kInput,
            String costPer1kOutput,
            String costCurrency
    ) {
    }

    public record GovernanceTraceSummaryPayload(
            long auditCount,
            List<String> recentActionTypes,
            Long latestAuditAt
    ) {
    }

    public record TrafficHeatmapPayload(
            String timezone,
            int days,
            long totalCalls,
            long successCalls,
            String successRate,
            long monthTotalCalls,
            long maxCallsPerHour,
            List<HeatmapDayPayload> rows
    ) {
    }

    public record HeatmapDayPayload(
            String date,
            String dayLabel,
            long totalCalls,
            List<HeatmapHourPayload> hours
    ) {
    }

    public record HeatmapHourPayload(
            int hour,
            long calls,
            long successCalls,
            int intensity
    ) {
    }

    public record CostDashboardPayload(
            String period,
            long totalCalls,
            String totalCost,
            List<MetricItem> byModel,
            List<MetricItem> byProvider,
            List<MetricItem> byTaskType,
            List<MetricItem> byTier,
            List<TopUserItem> topUsers
    ) {
    }

    public record ApplicationSceneMetricsPayload(
            int days,
            long totalCalls,
            String totalCost,
            List<ApplicationSceneMetricItem> records
    ) {
    }

    public record ApplicationOpsPayload(
            int days,
            ApplicationOpsSummary summary,
            List<ApplicationOpsSceneItem> hotScenes,
            List<ApplicationOpsSceneItem> costScenes,
            List<ApplicationOpsSceneItem> riskScenes,
            List<ApplicationOpsSceneItem> dormantScenes,
            List<ApplicationOpsSceneItem> records
    ) {
    }

    public record ApplicationOpsSummary(
            long totalChannels,
            long readyChannels,
            long activeChannels,
            long followUpChannels,
            long dormantChannels,
            long plannedChannels,
            long totalCalls,
            String totalCost,
            String overallSuccessRate,
            long avgLatencyMs
    ) {
    }

    public record ApplicationOpsSceneItem(
            String channelCode,
            String displayName,
            String ownerDomain,
            String frontEntry,
            String summary,
            String taskType,
            String sceneCode,
            long calls,
            long successCalls,
            String successRate,
            long avgLatencyMs,
            String totalCost,
            Long lastCallAt,
            int routeCount,
            String primaryRouteCode,
            String primaryProviderDisplayName,
            String primaryModelName,
            boolean usesBuiltinPrompt,
            boolean hasActiveTemplate,
            String activeTemplateName,
            Integer activeTemplateVersionNo,
            String status,
            String statusLabel,
            String riskType,
            String riskLabel,
            boolean followUp,
            boolean active,
            boolean ready,
            boolean dormant,
            boolean planned,
            String chainSummary,
            String nextActionLabel,
            String nextActionTo
    ) {
    }

    public record ApplicationSceneMetricItem(
            String taskType,
            String sceneCode,
            long calls,
            long successCalls,
            String successRate,
            long avgLatencyMs,
            String totalCost,
            Long lastCallAt
    ) {
    }

    public record RuntimeSettingsPayload(
            boolean debugModeEnabled,
            boolean aiRequestLogEnabled,
            boolean defaultDebugModeEnabled,
            boolean defaultAiRequestLogEnabled,
            String defaultReasoningEffort,
            Integer defaultThinkingBudget,
            String defaultThinkingLevel,
            Long updatedAt
    ) {
    }

    public record PromptTemplateRenderPreviewPayload(
            String taskType,
            String templateFormat,
            String renderedContent,
            String renderedBundleJson,
            List<String> placeholderVariables,
            List<String> missingVariables,
            String resolvedVariablesJson
    ) {
    }

    public record CostDashboardExportPayload(
            String filename,
            byte[] content
    ) {
    }

    public record MetricItem(
            String name,
            long calls,
            String cost
    ) {
    }

    public record TopUserItem(
            long userId,
            String email,
            String displayName,
            long calls,
            String cost
    ) {
    }

    public record RoutePreviewPayload(
            String taskType,
            String sceneCode,
            String routePolicyCode,
            String routePolicyUserTier,
            String routeStrategyType,
            String routeCode,
            String providerCode,
            String providerDisplayName,
            String providerType,
            String baseUrl,
            String model,
            long timeoutMs,
            int maxRetries,
            String executionMode,
            String temperature,
            String systemPrompt,
            String promptTemplateName,
            Integer promptTemplateVersionNo,
            String reasoningEffort,
            Integer thinkingBudget,
            String thinkingLevel,
            String thinkingSource,
            String costPer1kInput,
            String costPer1kOutput,
            String costCurrency,
            String providerExtraConfigJson,
            String routeExtraConfigJson
    ) {
    }

    private record TimeWindow(Timestamp startAt, Timestamp endAt) {
    }

    private record ProviderProbeRequest(
            String probeUrl,
            Map<String, String> headers
    ) {
    }

    private static final class ProviderRuntimeAccumulator {
        private final AiProviderConfigRepository.ProviderConfigRow provider;
        private final long[] calls;
        private final long[] successCalls;
        private final long[] failureCalls;
        private final long[] latencyTotals;
        private long totalCalls;
        private long totalSuccessCalls;
        private long totalLatency;
        private Instant lastEventAt;

        private ProviderRuntimeAccumulator(AiProviderConfigRepository.ProviderConfigRow provider, int hours) {
            this.provider = provider;
            this.calls = new long[hours];
            this.successCalls = new long[hours];
            this.failureCalls = new long[hours];
            this.latencyTotals = new long[hours];
        }
    }

    private record NormalizedProvider(
            String providerCode,
            AiProviderType providerType,
            String displayName,
            String baseUrl,
            String apiKeyCiphertext,
            String apiKeyMasked,
            boolean enabled,
            int timeoutMs,
            int maxRetries,
            BigDecimal costPer1kInput,
            BigDecimal costPer1kOutput,
            String extraConfigJson,
            boolean replaceModels,
            List<AiProviderModelRepository.ProviderModelMutation> models
    ) {
    }

    private record RouteDisplayContext(
            AiProviderConfigRepository.ProviderConfigRow provider,
            SceneRoutePolicyRepository.SceneRoutePolicyRow routePolicy,
            Integer activePromptTemplateVersionNo
    ) {
    }

    private record NormalizedRoutePolicy(
            String policyCode,
            String taskType,
            String sceneCode,
            String userTier,
            String strategyType,
            boolean enabled,
            String notes,
            String extraConfigJson
    ) {
    }

    private record NormalizedRoute(
            String routeCode,
            String taskType,
            String sceneCode,
            Long sceneRoutePolicyId,
            long providerConfigId,
            String modelName,
            int priorityNo,
            int candidateWeight,
            String executionMode,
            boolean enabled,
            BigDecimal temperature,
            String systemPrompt,
            String promptTemplateName,
            String extraConfigJson
    ) {
    }

    private record NormalizedPromptTemplate(
            String taskType,
            String templateName,
            int versionNo,
            String status,
            String templateFormat,
            String content,
            String description,
            String variablesJson,
            String bundleJson
    ) {
    }
}
