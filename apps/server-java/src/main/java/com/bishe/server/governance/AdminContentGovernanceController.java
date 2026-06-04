package com.bishe.server.governance;

import com.bishe.server.common.ApiResponse;
import com.bishe.server.common.TraceId;
import com.bishe.server.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 后台内容治理接口：举报中心、待审队列、策略与敏感词。
 */
@Tag(name = "AdminContentGovernance", description = "后台内容治理接口")
@Validated
@RestController
@RequestMapping(path = "/api/v1/admin/content", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminContentGovernanceController {

    private final ContentGovernanceService contentGovernanceService;

    public AdminContentGovernanceController(ContentGovernanceService contentGovernanceService) {
        this.contentGovernanceService = contentGovernanceService;
    }

    @Operation(summary = "查询举报中心列表")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/reports")
    public ApiResponse<AdminContentReportListResponse> getReports(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String targetType
    ) {
        // 举报列表只给处置台做分页入口，详情和动作历史按需补拉。
        return ApiResponse.ok(contentGovernanceService.getAdminReports(page, size, status, targetType), TraceId.next());
    }

    @Operation(summary = "查询举报详情")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/reports/{reportId}")
    public ApiResponse<AdminContentReportDetailResponse> getReportDetail(@PathVariable long reportId) {
        String traceId = TraceId.next();
        return ApiResponse.ok(contentGovernanceService.getReportDetail(traceId, reportId), traceId);
    }

    @Operation(summary = "查询举报处理历史")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/reports/{reportId}/actions")
    public ApiResponse<AdminReportActionHistoryResponse> getReportActions(@PathVariable long reportId) {
        String traceId = TraceId.next();
        return ApiResponse.ok(contentGovernanceService.getReportActions(traceId, reportId), traceId);
    }

    @Operation(summary = "查询内容治理审计日志")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/audit-logs")
    public ApiResponse<AdminAuditLogListResponse> getAuditLogs(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String traceId
    ) {
        // 审计日志支持 traceId，便于从 AI 日志或举报记录追到同一次治理动作。
        return ApiResponse.ok(contentGovernanceService.getAuditLogs(page, size, targetType, targetId, actionType, traceId), TraceId.next());
    }

    @Operation(summary = "处置举报")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/reports/{reportId}/decision", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Void> decideReport(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable long reportId,
            @Valid @RequestBody AdminReportDecisionRequest request
    ) {
        String traceId = TraceId.next();
        // 举报处置会更新目标状态、写动作历史，并按需通知内容作者。
        contentGovernanceService.decideReport(traceId, principal.getUserId(), reportId, request);
        return ApiResponse.ok("report decided", null, traceId);
    }

    @Operation(summary = "查询待审队列")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/review-queue")
    public ApiResponse<AdminReviewQueueResponse> getReviewQueue(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @RequestParam(required = false) String sourceType
    ) {
        // 待审队列承接治理策略产生的 REVIEW 内容，人工决定 PASS 或 BLOCK。
        return ApiResponse.ok(contentGovernanceService.getReviewQueue(page, size, sourceType), TraceId.next());
    }

    @Operation(summary = "查询待审项详情")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/review-queue/{itemId}")
    public ApiResponse<AdminReviewQueueDetailResponse> getReviewQueueItem(@PathVariable long itemId) {
        String traceId = TraceId.next();
        return ApiResponse.ok(contentGovernanceService.getReviewQueueItem(traceId, itemId), traceId);
    }

    @Operation(summary = "处置待审内容")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/review-queue/{itemId}/decision", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Void> decideReviewQueueItem(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable long itemId,
            @Valid @RequestBody AdminReviewDecisionRequest request
    ) {
        String traceId = TraceId.next();
        // 待审处置只处理 queue item，落库和通知由治理服务保持一致。
        contentGovernanceService.decideReviewQueueItem(traceId, principal.getUserId(), itemId, request);
        return ApiResponse.ok("review decided", null, traceId);
    }

    @Operation(summary = "查询治理策略")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/moderation/policies")
    public ApiResponse<ModerationPoliciesResponse> getPolicies() {
        return ApiResponse.ok(contentGovernanceService.getPolicies(), TraceId.next());
    }

    @Operation(summary = "更新治理策略")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping(path = "/moderation/policies", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<ModerationPoliciesResponse> updatePolicies(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ModerationPoliciesResponse request
    ) {
        String traceId = TraceId.next();
        // 治理策略影响社区、AI 输入和 AI 输出链路，更新后立即作为运行态生效。
        ModerationPoliciesResponse data = contentGovernanceService.updatePolicies(principal.getUserId(), request);
        return ApiResponse.ok(data, traceId);
    }

    @Operation(summary = "查询敏感词列表")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/sensitive-terms")
    public ApiResponse<SensitiveTermsResponse> listSensitiveTerms(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String termType,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String status
    ) {
        // 敏感词列表带 summary，后台首页卡片和表格筛选共用同一响应。
        return ApiResponse.ok(
                contentGovernanceService.listSensitiveTerms(page, size, keyword, termType, riskLevel, status),
                TraceId.next()
        );
    }

    @Operation(summary = "新增敏感词")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/sensitive-terms", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<SensitiveTermsResponse.TermItem> createSensitiveTerm(@Valid @RequestBody SensitiveTermCreateRequest request) {
        return ApiResponse.ok("sensitive term created", contentGovernanceService.createSensitiveTerm(request), TraceId.next());
    }

    @Operation(summary = "更新敏感词")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping(path = "/sensitive-terms/{termId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<SensitiveTermsResponse.TermItem> updateSensitiveTerm(@PathVariable long termId, @Valid @RequestBody SensitiveTermCreateRequest request) {
        return ApiResponse.ok("sensitive term updated", contentGovernanceService.updateSensitiveTerm(termId, request), TraceId.next());
    }

    @Operation(summary = "批量更新敏感词启停状态")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/sensitive-terms/batch-status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<SensitiveTermBatchOperationResponse> batchUpdateSensitiveTermStatus(@Valid @RequestBody SensitiveTermBatchUpdateRequest request) {
        int affectedCount = contentGovernanceService.batchUpdateSensitiveTermStatus(request.termIds(), request.enabled());
        return ApiResponse.ok("sensitive terms status updated", new SensitiveTermBatchOperationResponse(affectedCount), TraceId.next());
    }

    @Operation(summary = "批量删除敏感词")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/sensitive-terms/batch-delete", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<SensitiveTermBatchOperationResponse> batchDeleteSensitiveTerms(@Valid @RequestBody SensitiveTermBatchDeleteRequest request) {
        int affectedCount = contentGovernanceService.batchDeleteSensitiveTerms(request.termIds());
        return ApiResponse.ok("sensitive terms deleted", new SensitiveTermBatchOperationResponse(affectedCount), TraceId.next());
    }

    @Operation(summary = "批量导入敏感词 CSV")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/sensitive-terms/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SensitiveTermImportResponse> importSensitiveTerms(
            @AuthenticationPrincipal UserPrincipal principal,
            @org.springframework.web.bind.annotation.RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String fileFormat,
            @RequestParam(required = false) String termType,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String sourceScope,
            @RequestParam(required = false) Boolean whitelist,
            @RequestParam(required = false) Boolean enabled
    ) {
        // CSV 使用文件内配置，纯文本导入时用请求参数补齐默认治理动作。
        return ApiResponse.ok(
                "sensitive terms imported",
                contentGovernanceService.importSensitiveTerms(
                        principal.getUserId(),
                        file,
                        fileFormat,
                        termType,
                        riskLevel,
                        action,
                        sourceScope,
                        whitelist,
                        enabled
                ),
                TraceId.next()
        );
    }

    @Operation(summary = "导出敏感词 CSV")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/sensitive-terms/export", produces = "text/csv")
    public ResponseEntity<byte[]> exportSensitiveTerms() {
        // 导出保留 UTF-8 CSV，便于后台批量整理后再导回。
        ContentGovernanceService.SensitiveTermsExportPayload payload = contentGovernanceService.exportSensitiveTerms();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(payload.filename()).build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(payload.content());
    }

    @Operation(summary = "删除敏感词")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping(path = "/sensitive-terms/{termId}")
    public ApiResponse<Void> deleteSensitiveTerm(@PathVariable long termId) {
        contentGovernanceService.deleteSensitiveTerm(termId);
        return ApiResponse.ok("deleted", null, TraceId.next());
    }
}
