package com.bishe.server.notification.api;

import com.bishe.server.common.ApiResponse;
import com.bishe.server.common.TraceId;
import com.bishe.server.notification.dto.AdminNotificationAnnouncementRequest;
import com.bishe.server.notification.dto.AdminNotificationAnnouncementResponse;
import com.bishe.server.notification.service.AdminNotificationOpsService;
import com.bishe.server.notification.service.NotificationAnnouncementService;
import com.bishe.server.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员通知操作接口。
 */
@Tag(name = "Admin Notification", description = "管理员系统公告接口")
@RestController
@RequestMapping(path = "/api/v1/admin/notifications", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminNotificationController {

    private final NotificationAnnouncementService notificationAnnouncementService;
    private final AdminNotificationOpsService adminNotificationOpsService;

    public AdminNotificationController(
            NotificationAnnouncementService notificationAnnouncementService,
            AdminNotificationOpsService adminNotificationOpsService
    ) {
        this.notificationAnnouncementService = notificationAnnouncementService;
        this.adminNotificationOpsService = adminNotificationOpsService;
    }

    @Operation(summary = "获取通知运营台概览")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/overview")
    public ApiResponse<AdminNotificationOpsService.NotificationOpsOverviewPayload> getOverview() {
        return ApiResponse.ok(adminNotificationOpsService.getOverview(), TraceId.next());
    }

    @Operation(summary = "获取最近系统公告列表")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/announcements")
    public ApiResponse<AdminNotificationOpsService.AnnouncementListPayload> listAnnouncements(
            @RequestParam(defaultValue = "8") Integer limit
    ) {
        return ApiResponse.ok(adminNotificationOpsService.listAnnouncements(limit), TraceId.next());
    }

    @Operation(summary = "获取通知派发任务列表")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/dispatch-jobs")
    public ApiResponse<AdminNotificationOpsService.DispatchJobListPayload> listDispatchJobs(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channel
    ) {
        return ApiResponse.ok(
                adminNotificationOpsService.listDispatchJobs(page, size, status, channel),
                TraceId.next()
        );
    }

    @Operation(summary = "人工重投失败通知任务")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/dispatch-jobs/{jobId}/retry")
    public ApiResponse<AdminNotificationOpsService.DispatchJobRetryResponse> retryDispatchJob(
            @PathVariable String jobId
    ) {
        return ApiResponse.ok(
                "dispatch job requeued",
                adminNotificationOpsService.retryDispatchJob(jobId),
                TraceId.next()
        );
    }

    @Operation(summary = "清理旧的终态派发任务")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/dispatch-jobs/purge-terminal")
    public ApiResponse<AdminNotificationOpsService.DispatchJobCleanupResponse> purgeTerminalDispatchJobs(
            @RequestParam(defaultValue = "0") @Min(0) Integer olderThanHours
    ) {
        return ApiResponse.ok(
                "terminal dispatch jobs purged",
                adminNotificationOpsService.purgeTerminalDispatchJobs(olderThanHours),
                TraceId.next()
        );
    }

    @Operation(summary = "发布系统公告")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/announcements", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AdminNotificationAnnouncementResponse> publishAnnouncement(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AdminNotificationAnnouncementRequest request
    ) {
        return ApiResponse.ok(
                "system announcement published",
                notificationAnnouncementService.publishAnnouncement(principal.getUserId(), request),
                TraceId.next()
        );
    }
}
