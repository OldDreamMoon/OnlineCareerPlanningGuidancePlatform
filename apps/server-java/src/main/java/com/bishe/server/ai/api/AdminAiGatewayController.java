package com.bishe.server.ai.api;

import com.bishe.server.ai.gateway.AiGatewayAdminService;
import com.bishe.server.ai.quota.AiQuotaAdminService;
import com.bishe.server.common.ApiResponse;
import com.bishe.server.common.TraceId;
import com.bishe.server.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 网关后台管理接口。
 */
@Tag(name = "AdminAiGateway", description = "管理员维护 AI provider、路由与模板配置")
@Validated
@RestController
@RequestMapping(path = "/api/v1/admin/ai", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminAiGatewayController {

    private final AiGatewayAdminService aiGatewayAdminService;
    private final AiQuotaAdminService aiQuotaAdminService;

    public AdminAiGatewayController(
            AiGatewayAdminService aiGatewayAdminService,
            AiQuotaAdminService aiQuotaAdminService
    ) {
        this.aiGatewayAdminService = aiGatewayAdminService;
        this.aiQuotaAdminService = aiQuotaAdminService;
    }

    @Operation(summary = "AI 网关元信息")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/meta")
    public ApiResponse<AiGatewayAdminService.AdminMetaPayload> getMeta() {
        return ApiResponse.ok(aiGatewayAdminService.getMeta(), TraceId.next());
    }

    @Operation(summary = "AI provider 列表")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/providers")
    public ApiResponse<AiGatewayAdminService.ProviderListPayload> getProviders() {
        // provider 列表包含模型和成本字段，后台路由候选编辑直接复用。
        return ApiResponse.ok(aiGatewayAdminService.listProviders(), TraceId.next());
    }

    @Operation(summary = "创建 AI provider")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/providers", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AiGatewayAdminService.ProviderItem> createProvider(@Valid @RequestBody ProviderUpsertRequest request) {
        return ApiResponse.ok(aiGatewayAdminService.createProvider(request.toCommand()), TraceId.next());
    }

    @Operation(summary = "更新 AI provider")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping(path = "/providers/{providerId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AiGatewayAdminService.ProviderItem> updateProvider(
            @PathVariable @Min(1) long providerId,
            @Valid @RequestBody ProviderUpsertRequest request
    ) {
        return ApiResponse.ok(aiGatewayAdminService.updateProvider(providerId, request.toCommand()), TraceId.next());
    }

    @Operation(summary = "测试 AI provider 连通性")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/providers/{providerId}/connectivity-test")
    public ApiResponse<AiGatewayAdminService.ProviderConnectivityPayload> testProviderConnectivity(
            @PathVariable @Min(1) long providerId
    ) {
        // 连通探测只验证当前配置，不改变路由策略和调用日志。
        return ApiResponse.ok(aiGatewayAdminService.testProviderConnectivity(providerId), TraceId.next());
    }

    @Operation(summary = "AI 路由列表")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/routes")
    public ApiResponse<AiGatewayAdminService.RouteListPayload> getRoutes() {
        // routes 是具体模型候选，是否被命中还要看场景策略和 userTier。
        return ApiResponse.ok(aiGatewayAdminService.listRoutes(), TraceId.next());
    }

    @Operation(summary = "AI 场景路由策略列表")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/route-policies")
    public ApiResponse<AiGatewayAdminService.RoutePolicyListPayload> getRoutePolicies() {
        return ApiResponse.ok(aiGatewayAdminService.listRoutePolicies(), TraceId.next());
    }

    @Operation(summary = "创建 AI 场景路由策略")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/route-policies", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AiGatewayAdminService.RoutePolicyItem> createRoutePolicy(
            @Valid @RequestBody RoutePolicyUpsertRequest request
    ) {
        // 策略层保存 SINGLE/FAILOVER/WEIGHTED 和 thinking 参数。
        return ApiResponse.ok(aiGatewayAdminService.createRoutePolicy(request.toCommand()), TraceId.next());
    }

    @Operation(summary = "更新 AI 场景路由策略")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping(path = "/route-policies/{policyId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AiGatewayAdminService.RoutePolicyItem> updateRoutePolicy(
            @PathVariable @Min(1) long policyId,
            @Valid @RequestBody RoutePolicyUpsertRequest request
    ) {
        return ApiResponse.ok(aiGatewayAdminService.updateRoutePolicy(policyId, request.toCommand()), TraceId.next());
    }

    @Operation(summary = "AI 模板列表")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/prompt-templates")
    public ApiResponse<AiGatewayAdminService.PromptTemplateListPayload> getPromptTemplates() {
        return ApiResponse.ok(aiGatewayAdminService.listPromptTemplates(), TraceId.next());
    }

    @Operation(summary = "创建 AI 模板")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/prompt-templates", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AiGatewayAdminService.PromptTemplateItem> createPromptTemplate(
            @Valid @RequestBody PromptTemplateUpsertRequest request
    ) {
        return ApiResponse.ok(aiGatewayAdminService.createPromptTemplate(request.toCommand()), TraceId.next());
    }

    @Operation(summary = "更新 AI 模板")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping(path = "/prompt-templates/{templateId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AiGatewayAdminService.PromptTemplateItem> updatePromptTemplate(
            @PathVariable @Min(1) long templateId,
            @Valid @RequestBody PromptTemplateUpsertRequest request
    ) {
        return ApiResponse.ok(aiGatewayAdminService.updatePromptTemplate(templateId, request.toCommand()), TraceId.next());
    }

    @Operation(summary = "发布指定 Prompt 模板版本")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/prompt-templates/{templateId}/publish")
    public ApiResponse<AiGatewayAdminService.PromptTemplateItem> publishPromptTemplate(
            @PathVariable @Min(1) long templateId
    ) {
        // 发布模板由服务层保证同一 templateName 只有一个 ACTIVE 版本。
        return ApiResponse.ok(aiGatewayAdminService.publishPromptTemplate(templateId), TraceId.next());
    }

    @Operation(summary = "回滚 Prompt 模板到指定历史版本")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/prompt-templates/{templateId}/rollback", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AiGatewayAdminService.PromptTemplateItem> rollbackPromptTemplate(
            @PathVariable @Min(1) long templateId,
            @Valid @RequestBody PromptTemplateRollbackRequest request
    ) {
        return ApiResponse.ok(aiGatewayAdminService.rollbackPromptTemplate(templateId, request.toCommand()), TraceId.next());
    }

    @Operation(summary = "查询 AI 运行时开关")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/runtime-settings")
    public ApiResponse<AiGatewayAdminService.RuntimeSettingsPayload> getRuntimeSettings() {
        return ApiResponse.ok(aiGatewayAdminService.getRuntimeSettings(), TraceId.next());
    }

    @Operation(summary = "更新 AI 运行时开关")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping(path = "/runtime-settings", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AiGatewayAdminService.RuntimeSettingsPayload> updateRuntimeSettings(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody RuntimeSettingsUpdateRequest request
    ) {
        // 运行设置影响日志采样和默认 thinking 参数，记录管理员操作者用于审计。
        return ApiResponse.ok(aiGatewayAdminService.updateRuntimeSettings(principal.getUserId(), request.toCommand()), TraceId.next());
    }

    @Operation(summary = "AI 场景级聚合指标")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/application-scene-metrics")
    public ApiResponse<AiGatewayAdminService.ApplicationSceneMetricsPayload> getApplicationSceneMetrics(
            @RequestParam(defaultValue = "7") Integer days
    ) {
        return ApiResponse.ok(aiGatewayAdminService.getApplicationSceneMetrics(days), TraceId.next());
    }

    @Operation(summary = "AI 应用运营聚合视图")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/application-ops")
    public ApiResponse<AiGatewayAdminService.ApplicationOpsPayload> getApplicationOps(
            @RequestParam(defaultValue = "7") Integer days
    ) {
        return ApiResponse.ok(aiGatewayAdminService.getApplicationOps(days), TraceId.next());
    }

    @Operation(summary = "AI 日志列表")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/logs")
    public ApiResponse<AiGatewayAdminService.AiLogListPayload> getLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String sceneCode,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String traceId
    ) {
        // AI 日志可按 traceId 联查内容治理和业务链路，是后台排障主入口。
        return ApiResponse.ok(
                aiGatewayAdminService.listLogs(new AiGatewayAdminService.AiLogQueryCommand(page, size, taskType, sceneCode, provider, status, userId, traceId)),
                TraceId.next()
        );
    }


    @Operation(summary = "AI 日志详情")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/logs/{logId}")
    public ApiResponse<AiGatewayAdminService.AiLogDetailPayload> getLogDetail(
            @PathVariable @Min(1) long logId
    ) {
        return ApiResponse.ok(aiGatewayAdminService.getLogDetail(logId), TraceId.next());
    }

    @Operation(summary = "AI 配额策略列表")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/quota-policies")
    public ApiResponse<AiQuotaAdminService.QuotaPolicyListPayload> getQuotaPolicies() {
        return ApiResponse.ok(aiQuotaAdminService.listQuotaPolicies(), TraceId.next());
    }

    @Operation(summary = "更新 AI 配额策略")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping(path = "/quota-policies/{policyId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AiQuotaAdminService.QuotaPolicyItem> updateQuotaPolicy(
            @PathVariable @Min(1) long policyId,
            @Valid @RequestBody QuotaPolicyUpdateRequest request
    ) {
        return ApiResponse.ok(aiQuotaAdminService.updateQuotaPolicy(policyId, request.toCommand()), TraceId.next());
    }

    @Operation(summary = "AI 成本看板")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/cost-dashboard")
    public ApiResponse<AiGatewayAdminService.CostDashboardPayload> getCostDashboard(
            @RequestParam(defaultValue = "today") String period
    ) {
        return ApiResponse.ok(aiGatewayAdminService.getCostDashboard(period), TraceId.next());
    }


    @Operation(summary = "AI 调用热力图")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/traffic-heatmap")
    public ApiResponse<AiGatewayAdminService.TrafficHeatmapPayload> getTrafficHeatmap(
            @RequestParam(defaultValue = "7") Integer days,
            @RequestParam(defaultValue = "Asia/Shanghai") String timezone
    ) {
        return ApiResponse.ok(aiGatewayAdminService.getTrafficHeatmap(days, timezone), TraceId.next());
    }

    @Operation(summary = "AI Provider 运行态统计")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/provider-runtime-stats")
    public ApiResponse<AiGatewayAdminService.ProviderRuntimeStatsPayload> getProviderRuntimeStats(
            @RequestParam(defaultValue = "24") Integer hours,
            @RequestParam(defaultValue = "Asia/Shanghai") String timezone
    ) {
        return ApiResponse.ok(aiGatewayAdminService.getProviderRuntimeStats(hours, timezone), TraceId.next());
    }

    @Operation(summary = "导出 AI 成本看板 CSV")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/cost-dashboard/export", produces = "text/csv")
    public ResponseEntity<byte[]> exportCostDashboard(
            @RequestParam(defaultValue = "today") String period
    ) {
        AiGatewayAdminService.CostDashboardExportPayload payload = aiGatewayAdminService.exportCostDashboard(period);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(payload.filename()).build().toString())
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(payload.content());
    }

    @Operation(summary = "预览 Prompt 模板渲染结果")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/prompt-templates/render-preview", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AiGatewayAdminService.PromptTemplateRenderPreviewPayload> previewPromptTemplateRender(
            @Valid @RequestBody PromptTemplateRenderPreviewRequest request
    ) {
        return ApiResponse.ok(aiGatewayAdminService.previewPromptTemplateRender(request.toCommand()), TraceId.next());
    }

    @Operation(summary = "预览 AI 路由命中结果")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/routes/resolve-preview", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AiGatewayAdminService.RoutePreviewPayload> previewRoute(
            @Valid @RequestBody RouteResolvePreviewRequest request
    ) {
        return ApiResponse.ok(aiGatewayAdminService.previewResolvedRoute(request.toCommand()), TraceId.next());
    }

    @Operation(summary = "创建 AI 路由")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/routes", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AiGatewayAdminService.RouteItem> createRoute(@Valid @RequestBody RouteUpsertRequest request) {
        return ApiResponse.ok(aiGatewayAdminService.createRoute(request.toCommand()), TraceId.next());
    }

    @Operation(summary = "更新 AI 路由")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping(path = "/routes/{routeId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AiGatewayAdminService.RouteItem> updateRoute(
            @PathVariable @Min(1) long routeId,
            @Valid @RequestBody RouteUpsertRequest request
    ) {
        return ApiResponse.ok(aiGatewayAdminService.updateRoute(routeId, request.toCommand()), TraceId.next());
    }

    public record RouteResolvePreviewRequest(
            String taskType,
            String sceneCode,
            String modelPreference,
            String userTier
    ) {
        public AiGatewayAdminService.RouteResolvePreviewCommand toCommand() {
            return new AiGatewayAdminService.RouteResolvePreviewCommand(taskType, sceneCode, modelPreference, userTier);
        }
    }

    public record RuntimeSettingsUpdateRequest(
            Boolean debugModeEnabled,
            Boolean aiRequestLogEnabled,
            String defaultReasoningEffort,
            Integer defaultThinkingBudget,
            String defaultThinkingLevel
    ) {
        public AiGatewayAdminService.UpdateRuntimeSettingsCommand toCommand() {
            return new AiGatewayAdminService.UpdateRuntimeSettingsCommand(
                    debugModeEnabled,
                    aiRequestLogEnabled,
                    defaultReasoningEffort,
                    defaultThinkingBudget,
                    defaultThinkingLevel
            );
        }
    }

    public record ProviderModelUpsertRequest(
            String modelCode,
            String displayName,
            Boolean enabled,
            String inputCostPer1k,
            String outputCostPer1k,
            Integer contextWindow,
            Integer maxOutputTokens,
            java.util.List<String> supportedTaskTypes,
            String notes
    ) {
        public AiGatewayAdminService.UpsertProviderModelCommand toCommand() {
            return new AiGatewayAdminService.UpsertProviderModelCommand(
                    modelCode,
                    displayName,
                    enabled,
                    inputCostPer1k,
                    outputCostPer1k,
                    contextWindow,
                    maxOutputTokens,
                    supportedTaskTypes,
                    notes
            );
        }
    }

    public record ProviderUpsertRequest(
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
            java.util.List<ProviderModelUpsertRequest> models
    ) {
        public AiGatewayAdminService.UpsertProviderCommand toCommand() {
            return new AiGatewayAdminService.UpsertProviderCommand(
                    providerCode,
                    providerType,
                    displayName,
                    baseUrl,
                    apiKey,
                    enabled,
                    timeoutMs,
                    maxRetries,
                    costPer1kInput,
                    costPer1kOutput,
                    extraConfigJson,
                    models == null ? null : models.stream().map(ProviderModelUpsertRequest::toCommand).toList()
            );
        }
    }

    public record PromptTemplateUpsertRequest(
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
        public AiGatewayAdminService.UpsertPromptTemplateCommand toCommand() {
            return new AiGatewayAdminService.UpsertPromptTemplateCommand(
                    taskType,
                    templateName,
                    versionNo,
                    status,
                    templateFormat,
                    content,
                    description,
                    variablesJson,
                    bundleJson
            );
        }
    }

    public record PromptTemplateRenderPreviewRequest(
            String taskType,
            String templateFormat,
            String content,
            String variablesJson,
            String bundleJson,
            String renderVariablesJson
    ) {
        public AiGatewayAdminService.PromptTemplateRenderPreviewCommand toCommand() {
            return new AiGatewayAdminService.PromptTemplateRenderPreviewCommand(
                    taskType,
                    templateFormat,
                    content,
                    variablesJson,
                    bundleJson,
                    renderVariablesJson
            );
        }
    }

    public record PromptTemplateRollbackRequest(Long targetTemplateId) {
        public AiGatewayAdminService.RollbackPromptTemplateCommand toCommand() {
            return new AiGatewayAdminService.RollbackPromptTemplateCommand(targetTemplateId);
        }
    }

    public record QuotaPolicyUpdateRequest(
            Integer dailyFreeLimit,
            Integer pointsPerCall,
            Integer dailyMaxLimit,
            String modelPreference,
            Integer maxInputTokens
    ) {
        public AiQuotaAdminService.UpdateQuotaPolicyCommand toCommand() {
            return new AiQuotaAdminService.UpdateQuotaPolicyCommand(
                    dailyFreeLimit,
                    pointsPerCall,
                    dailyMaxLimit,
                    modelPreference,
                    maxInputTokens
            );
        }
    }

    public record RoutePolicyUpsertRequest(
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
        public AiGatewayAdminService.UpsertRoutePolicyCommand toCommand() {
            return new AiGatewayAdminService.UpsertRoutePolicyCommand(
                    policyCode,
                    taskType,
                    sceneCode,
                    userTier,
                    strategyType,
                    enabled,
                    reasoningEffort,
                    thinkingBudget,
                    thinkingLevel,
                    notes
            );
        }
    }

    public record RouteUpsertRequest(
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
        public AiGatewayAdminService.UpsertRouteCommand toCommand() {
            return new AiGatewayAdminService.UpsertRouteCommand(
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
    }
}
