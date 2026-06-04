package com.bishe.server.adminconsole.api;

import com.bishe.server.adminconsole.AdminSystemConsoleService;
import com.bishe.server.common.ApiResponse;
import com.bishe.server.common.TraceId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端统一运行控制台接口。
 */
@Tag(name = "AdminSystemConsole", description = "管理员统一运行控制台聚合接口")
@RestController
@RequestMapping(path = "/api/v1/admin/system", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminSystemConsoleController {

    private final AdminSystemConsoleService adminSystemConsoleService;

    public AdminSystemConsoleController(AdminSystemConsoleService adminSystemConsoleService) {
        this.adminSystemConsoleService = adminSystemConsoleService;
    }

    @Operation(summary = "统一运行控制台快照")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/console-snapshot")
    public ApiResponse<AdminSystemConsoleService.ConsoleSnapshotPayload> getConsoleSnapshot() {
        return ApiResponse.ok(adminSystemConsoleService.getSnapshot(), TraceId.next());
    }
}
