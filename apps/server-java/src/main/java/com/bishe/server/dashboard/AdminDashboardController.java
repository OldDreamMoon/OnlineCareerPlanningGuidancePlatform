package com.bishe.server.dashboard;

import com.bishe.server.common.ApiResponse;
import com.bishe.server.common.TraceId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端首页数据看板接口。
 */
@Tag(name = "AdminDashboard", description = "管理员首页与运营数据看板接口")
@RestController
@RequestMapping(path = "/api/v1/admin/dashboard", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    @Operation(summary = "管理员待办工作台摘要")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/workbench")
    public ApiResponse<AdminDashboardService.WorkbenchPayload> getWorkbench(
            @RequestParam(defaultValue = "24") Integer hours,
            @RequestParam(defaultValue = "Asia/Shanghai") String timezone
    ) {
        // 后台首页顶部待办卡只读聚合摘要，不承载明细分页。
        return ApiResponse.ok(adminDashboardService.getWorkbench(hours, timezone), TraceId.next());
    }

    @Operation(summary = "运营数据看板")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/operations")
    public ApiResponse<AdminDashboardService.OperationsDashboardPayload> getOperationsDashboard(
            @RequestParam(defaultValue = "week") String period
    ) {
        // period 只允许 today/week/month，由 Service 统一校验并命中缓存。
        return ApiResponse.ok(adminDashboardService.getOperationsDashboard(period), TraceId.next());
    }
}
