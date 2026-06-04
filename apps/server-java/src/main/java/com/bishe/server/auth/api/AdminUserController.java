package com.bishe.server.auth.api;

import com.bishe.server.auth.dto.AdminResetPasswordRequest;
import com.bishe.server.auth.dto.AdminUpdateUserApprovalStatusRequest;
import com.bishe.server.auth.dto.AdminUpdateUserStatusRequest;
import com.bishe.server.auth.dto.AdminUpdateUserTierRequest;
import com.bishe.server.auth.dto.AdminUserDetailResponse;
import com.bishe.server.auth.dto.AdminUserListResponse;
import com.bishe.server.auth.dto.AdminUserSummaryResponse;
import com.bishe.server.auth.service.AdminUserService;
import com.bishe.server.common.ApiResponse;
import com.bishe.server.common.TraceId;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员用户管理接口。
 */
@Tag(name = "AdminUsers", description = "管理员用户管理接口")
@Validated
@RestController
@RequestMapping(path = "/api/v1/admin/users", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @Operation(summary = "查询用户列表")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ApiResponse<AdminUserListResponse> getUsers(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String approvalStatus
    ) {
        return ApiResponse.ok(adminUserService.getUsers(page, size, keyword, role, status, approvalStatus), TraceId.next());
    }

    @Operation(summary = "查询用户概览统计")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/summary")
    public ApiResponse<AdminUserSummaryResponse> getUserSummary() {
        return ApiResponse.ok(adminUserService.getUserSummary(), TraceId.next());
    }

    @Operation(summary = "查询用户详情")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/{userId}")
    public ApiResponse<AdminUserDetailResponse> getUserDetail(@PathVariable long userId) {
        return ApiResponse.ok(adminUserService.getUserDetail(userId), TraceId.next());
    }

    @Operation(summary = "更新用户状态")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/{userId}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Void> updateStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable long userId,
            @Valid @RequestBody AdminUpdateUserStatusRequest request
    ) {
        String traceId = TraceId.next();
        adminUserService.updateStatus(principal.getUserId(), userId, request.status());
        return ApiResponse.ok("user status updated", null, traceId);
    }

    @Operation(summary = "更新用户认证状态")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/{userId}/approval-status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Void> updateApprovalStatus(
            @PathVariable long userId,
            @Valid @RequestBody AdminUpdateUserApprovalStatusRequest request
    ) {
        String traceId = TraceId.next();
        adminUserService.updateApprovalStatus(userId, request.approvalStatus());
        return ApiResponse.ok("user approval status updated", null, traceId);
    }

    @Operation(summary = "更新用户会员等级")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/{userId}/tier", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Void> updateTier(
            @PathVariable long userId,
            @Valid @RequestBody AdminUpdateUserTierRequest request
    ) {
        String traceId = TraceId.next();
        adminUserService.updateTier(userId, request.tier());
        return ApiResponse.ok("user tier updated", null, traceId);
    }

    @Operation(summary = "重置用户密码")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/{userId}/reset-password", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Void> resetPassword(@PathVariable long userId, @Valid @RequestBody AdminResetPasswordRequest request) {
        String traceId = TraceId.next();
        adminUserService.resetPassword(userId, request.newPassword());
        return ApiResponse.ok("password reset", null, traceId);
    }
}
