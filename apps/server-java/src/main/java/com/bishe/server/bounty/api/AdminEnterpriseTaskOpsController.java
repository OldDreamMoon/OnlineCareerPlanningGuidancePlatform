package com.bishe.server.bounty.api;

import com.bishe.server.bounty.dto.BountyTaskManageRequest;
import com.bishe.server.bounty.dto.BountyTaskManageResponse;
import com.bishe.server.bounty.service.AdminEnterpriseTaskOpsService;
import com.bishe.server.common.ApiResponse;
import com.bishe.server.common.TraceId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Enterprise Task Ops", description = "管理员企业任务治理接口")
@Validated
@RestController
@RequestMapping(path = "/api/v1/admin/enterprise/tasks", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminEnterpriseTaskOpsController {

    private final AdminEnterpriseTaskOpsService adminEnterpriseTaskOpsService;

    public AdminEnterpriseTaskOpsController(AdminEnterpriseTaskOpsService adminEnterpriseTaskOpsService) {
        this.adminEnterpriseTaskOpsService = adminEnterpriseTaskOpsService;
    }

    @Operation(summary = "获取企业任务治理台概览")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/overview")
    public ApiResponse<AdminEnterpriseTaskOpsService.EnterpriseTaskOpsOverviewPayload> getOverview() {
        // 概览由后端统一推导高风险、积压和临期指标，前端只展示结果。
        return ApiResponse.ok(adminEnterpriseTaskOpsService.getOverview(), TraceId.next());
    }

    @Operation(summary = "获取企业任务治理列表")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ApiResponse<AdminEnterpriseTaskOpsService.EnterpriseTaskOpsListPayload> listTasks(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be >= 1") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size must be >= 1") @Max(value = 50, message = "size must be <= 50") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String riskLevel
    ) {
        // 治理列表保留服务端分页和风险筛选，适合后台按问题任务巡检。
        return ApiResponse.ok(
                adminEnterpriseTaskOpsService.listTasks(page, size, keyword, status, riskLevel),
                TraceId.next()
        );
    }

    @Operation(summary = "获取企业任务治理详情")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{taskId}")
    public ApiResponse<AdminEnterpriseTaskOpsService.TaskOpsRecord> getTaskDetail(@PathVariable long taskId) {
        // 详情补充提交流转摘要和风险信号，弹层打开时再读取。
        return ApiResponse.ok(adminEnterpriseTaskOpsService.getTaskDetail(taskId), TraceId.next());
    }

    @Operation(summary = "平台管理企业任务状态")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/{taskId}/manage", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<BountyTaskManageResponse> manageTask(
            @PathVariable long taskId,
            @Valid @RequestBody BountyTaskManageRequest request
    ) {
        // 平台管理只提供关闭/重开这类最小干预动作，业务审核仍归企业工作区。
        return ApiResponse.ok(
                "admin enterprise task updated",
                adminEnterpriseTaskOpsService.manageTask(taskId, request),
                TraceId.next()
        );
    }
}
