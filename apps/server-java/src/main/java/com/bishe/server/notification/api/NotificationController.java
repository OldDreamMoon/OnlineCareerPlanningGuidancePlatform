package com.bishe.server.notification.api;

import com.bishe.server.auth.model.UserRole;
import com.bishe.server.common.ApiResponse;
import com.bishe.server.common.TraceId;
import com.bishe.server.notification.dto.NotificationListResponse;
import com.bishe.server.notification.dto.NotificationPreferenceUpdateRequest;
import com.bishe.server.notification.dto.NotificationPreferencesResponse;
import com.bishe.server.notification.dto.NotificationReadAllResponse;
import com.bishe.server.notification.dto.NotificationReadResponse;
import com.bishe.server.notification.dto.NotificationSyncResponse;
import com.bishe.server.notification.dto.NotificationUnreadCountResponse;
import com.bishe.server.notification.dto.NotificationWebSocketTicketResponse;
import com.bishe.server.notification.service.NotificationService;
import com.bishe.server.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
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
 * 通知接口：列表、未读数与已读回写。
 */
@Tag(name = "Notification", description = "站内通知接口")
@Validated
@RestController
@RequestMapping(path = "/api/v1/notifications", produces = MediaType.APPLICATION_JSON_VALUE)
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "获取通知列表")
    @PreAuthorize("hasAnyRole('STUDENT','MENTOR','ENTERPRISE')")
    @GetMapping
    public ApiResponse<NotificationListResponse> listNotifications(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) String category
    ) {
        // 通知列表是收件箱真相源，WebSocket 只负责实时提醒和同步触发。
        return ApiResponse.ok(notificationService.listNotifications(principal.getUserId(), page, size, unreadOnly, category), TraceId.next());
    }

    @Operation(summary = "获取未读通知数量")
    @PreAuthorize("hasAnyRole('STUDENT','MENTOR','ENTERPRISE')")
    @GetMapping(path = "/unread-count")
    public ApiResponse<NotificationUnreadCountResponse> getUnreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(notificationService.getUnreadCount(principal.getUserId()), TraceId.next());
    }

    @Operation(summary = "增量同步通知")
    @PreAuthorize("hasAnyRole('STUDENT','MENTOR','ENTERPRISE')")
    @GetMapping(path = "/sync")
    public ApiResponse<NotificationSyncResponse> syncNotifications(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Long afterId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit
    ) {
        // 前端 WS 断线或收到 SYNC_REQUIRED 后，用 afterId 补拉缺失通知。
        return ApiResponse.ok(notificationService.syncNotifications(principal.getUserId(), afterId, limit), TraceId.next());
    }

    @Operation(summary = "标记单条通知已读")
    @PreAuthorize("hasAnyRole('STUDENT','MENTOR','ENTERPRISE')")
    @PostMapping(path = "/{notificationId}/read")
    public ApiResponse<NotificationReadResponse> markRead(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable long notificationId
    ) {
        return ApiResponse.ok(notificationService.markRead(principal.getUserId(), notificationId), TraceId.next());
    }

    @Operation(summary = "全部标记已读")
    @PreAuthorize("hasAnyRole('STUDENT','MENTOR','ENTERPRISE')")
    @PostMapping(path = "/read-all")
    public ApiResponse<NotificationReadAllResponse> markAllRead(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(notificationService.markAllRead(principal.getUserId()), TraceId.next());
    }

    @Operation(summary = "获取通知偏好")
    @PreAuthorize("hasAnyRole('STUDENT','MENTOR','ENTERPRISE')")
    @GetMapping(path = "/preferences")
    public ApiResponse<NotificationPreferencesResponse> listPreferences(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(
                notificationService.listPreferences(principal.getUserId(), UserRole.parse(principal.getRole())),
                TraceId.next()
        );
    }

    @Operation(summary = "更新通知偏好")
    @PreAuthorize("hasAnyRole('STUDENT','MENTOR','ENTERPRISE')")
    @PutMapping(path = "/preferences", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<NotificationPreferencesResponse.PreferenceItem> updatePreference(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody NotificationPreferenceUpdateRequest request
    ) {
        // 偏好按角色矩阵更新，邮件/WS/桌面弹窗是否派发都在发布服务读取。
        return ApiResponse.ok(
                "notification preference updated",
                notificationService.updatePreference(principal.getUserId(), UserRole.parse(principal.getRole()), request),
                TraceId.next()
        );
    }

    @Operation(summary = "获取通知 WebSocket 短票据")
    @PreAuthorize("hasAnyRole('STUDENT','MENTOR','ENTERPRISE')")
    @PostMapping(path = "/ws-ticket")
    public ApiResponse<NotificationWebSocketTicketResponse> issueWebSocketTicket(@AuthenticationPrincipal UserPrincipal principal) {
        // WS 只签短票据，避免长期 access token 出现在 websocket URL。
        return ApiResponse.ok(
                notificationService.issueWebSocketTicket(principal.getUserId(), UserRole.parse(principal.getRole())),
                TraceId.next()
        );
    }
}
