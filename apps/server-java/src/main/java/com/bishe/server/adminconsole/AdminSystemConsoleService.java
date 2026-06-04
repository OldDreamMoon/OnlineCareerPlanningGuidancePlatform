package com.bishe.server.adminconsole;

import com.bishe.server.ai.gateway.AiGatewayAdminService;
import com.bishe.server.common.TimePayloads;
import com.bishe.server.featureflag.FeatureFlagService;
import com.bishe.server.governance.ContentGovernanceService;
import com.bishe.server.governance.ModerationPoliciesResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 管理端统一运行控制台聚合服务。
 */
@Service
public class AdminSystemConsoleService {

    private final AdminConsoleSnapshotCacheService adminConsoleSnapshotCacheService;
    private final FeatureFlagService featureFlagService;
    private final AiGatewayAdminService aiGatewayAdminService;
    private final ContentGovernanceService contentGovernanceService;

    public AdminSystemConsoleService(
            AdminConsoleSnapshotCacheService adminConsoleSnapshotCacheService,
            FeatureFlagService featureFlagService,
            AiGatewayAdminService aiGatewayAdminService,
            ContentGovernanceService contentGovernanceService
    ) {
        this.adminConsoleSnapshotCacheService = adminConsoleSnapshotCacheService;
        this.featureFlagService = featureFlagService;
        this.aiGatewayAdminService = aiGatewayAdminService;
        this.contentGovernanceService = contentGovernanceService;
    }

    public ConsoleSnapshotPayload getSnapshot() {
        return adminConsoleSnapshotCacheService.getSnapshot(this::buildSnapshot);
    }

    private ConsoleSnapshotPayload buildSnapshot() {
        FeatureFlagService.FeatureFlagListPayload flags = featureFlagService.listFlags();
        AiGatewayAdminService.RuntimeSettingsPayload runtimeSettings = aiGatewayAdminService.getRuntimeSettings();
        ModerationPoliciesResponse moderationPolicies = contentGovernanceService.getPolicies();
        return new ConsoleSnapshotPayload(
                TimePayloads.toEpochMillis(Instant.now()),
                flags.records(),
                runtimeSettings,
                moderationPolicies,
                buildAiChannelOverview()
        );
    }

    private AiChannelOverviewPayload buildAiChannelOverview() {
        List<AiGatewayAdminService.ProviderItem> providers = aiGatewayAdminService.listProviders().records();
        List<AiGatewayAdminService.RouteItem> routes = aiGatewayAdminService.listRoutes().records();
        List<AiGatewayAdminService.PromptTemplateItem> promptTemplates = aiGatewayAdminService.listPromptTemplates().records();
        AiGatewayAdminService.ProviderRuntimeStatsPayload runtimeStats = aiGatewayAdminService.getProviderRuntimeStats(24, "Asia/Shanghai");

        long enabledProviders = providers.stream()
                .filter(AiGatewayAdminService.ProviderItem::enabled)
                .count();
        long enabledRoutes = routes.stream()
                .filter(AiGatewayAdminService.RouteItem::enabled)
                .count();
        long sceneBoundRoutes = routes.stream()
                .filter(route -> hasText(route.sceneCode()))
                .count();
        long syncBlockingRoutes = countRoutesByExecutionMode(routes, "SYNC_BLOCKING");
        long streamRoutes = countRoutesByExecutionMode(routes, "STREAM_SSE");
        long asyncRoutes = countRoutesByExecutionMode(routes, "ASYNC_JOB");
        long realtimeRoutes = countRoutesByExecutionMode(routes, "REALTIME_SESSION");
        long activePromptTemplates = countPromptTemplatesByStatus(promptTemplates, "ACTIVE");
        long draftPromptTemplates = countPromptTemplatesByStatus(promptTemplates, "DRAFT");
        long inactivePromptTemplates = countPromptTemplatesByStatus(promptTemplates, "INACTIVE");
        List<AiChannelProviderItem> keyProviders = runtimeStats.providers().stream()
                .limit(4)
                .map(provider -> new AiChannelProviderItem(
                        provider.providerId(),
                        provider.providerCode(),
                        provider.providerDisplayName(),
                        provider.providerType(),
                        provider.enabled(),
                        provider.runtimeStatus(),
                        provider.successRate(),
                        provider.totalCalls(),
                        provider.avgLatencyMs(),
                        provider.lastEventAt()
                ))
                .toList();

        return new AiChannelOverviewPayload(
                providers.size(),
                enabledProviders,
                runtimeStats.healthyProviders(),
                runtimeStats.degradedProviders(),
                runtimeStats.downProviders(),
                runtimeStats.idleProviders(),
                runtimeStats.disabledProviders(),
                routes.size(),
                enabledRoutes,
                sceneBoundRoutes,
                syncBlockingRoutes,
                streamRoutes,
                asyncRoutes,
                realtimeRoutes,
                promptTemplates.size(),
                activePromptTemplates,
                draftPromptTemplates,
                inactivePromptTemplates,
                keyProviders
        );
    }

    private long countRoutesByExecutionMode(List<AiGatewayAdminService.RouteItem> routes, String executionMode) {
        return routes.stream()
                .filter(route -> executionMode.equals(route.executionMode()))
                .count();
    }

    private long countPromptTemplatesByStatus(List<AiGatewayAdminService.PromptTemplateItem> promptTemplates, String status) {
        return promptTemplates.stream()
                .filter(template -> status.equals(template.status()))
                .count();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record ConsoleSnapshotPayload(
            Long generatedAt,
            List<FeatureFlagService.FeatureFlagItem> featureFlags,
            AiGatewayAdminService.RuntimeSettingsPayload runtimeSettings,
            ModerationPoliciesResponse moderationPolicies,
            AiChannelOverviewPayload aiChannels
    ) {
    }

    public record AiChannelOverviewPayload(
            long totalProviders,
            long enabledProviders,
            long healthyProviders,
            long degradedProviders,
            long downProviders,
            long idleProviders,
            long disabledProviders,
            long totalRoutes,
            long enabledRoutes,
            long sceneBoundRoutes,
            long syncBlockingRoutes,
            long streamRoutes,
            long asyncRoutes,
            long realtimeRoutes,
            long totalPromptTemplates,
            long activePromptTemplates,
            long draftPromptTemplates,
            long inactivePromptTemplates,
            List<AiChannelProviderItem> keyProviders
    ) {
    }

    public record AiChannelProviderItem(
            long providerId,
            String providerCode,
            String providerDisplayName,
            String providerType,
            boolean enabled,
            String runtimeStatus,
            String successRate,
            long totalCalls,
            long avgLatencyMs,
            Long lastEventAt
    ) {
    }
}
