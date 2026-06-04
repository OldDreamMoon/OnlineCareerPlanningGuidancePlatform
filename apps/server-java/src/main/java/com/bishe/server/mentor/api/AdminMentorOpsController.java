package com.bishe.server.mentor.api;

import com.bishe.server.common.ApiResponse;
import com.bishe.server.common.TraceId;
import com.bishe.server.mentor.dto.MentorWithdrawalStatusUpdateRequest;
import com.bishe.server.mentor.service.AdminMentorOpsService;
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

@Tag(name = "Admin Mentor Ops", description = "管理员导师经营治理接口")
@Validated
@RestController
@RequestMapping(path = "/api/v1/admin/mentors/operations", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminMentorOpsController {

    private final AdminMentorOpsService adminMentorOpsService;

    public AdminMentorOpsController(AdminMentorOpsService adminMentorOpsService) {
        this.adminMentorOpsService = adminMentorOpsService;
    }

    @Operation(summary = "获取导师经营治理台概览")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/overview")
    public ApiResponse<AdminMentorOpsService.MentorOpsOverviewPayload> getOverview() {
        return ApiResponse.ok(adminMentorOpsService.getOverview(), TraceId.next());
    }

    @Operation(summary = "获取导师经营治理列表")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ApiResponse<AdminMentorOpsService.MentorOpsListPayload> listMentors(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be >= 1") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size must be >= 1") @Max(value = 50, message = "size must be <= 50") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String approvalStatus,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String withdrawalStatus
    ) {
        return ApiResponse.ok(
                adminMentorOpsService.listMentors(page, size, keyword, approvalStatus, riskLevel, withdrawalStatus),
                TraceId.next()
        );
    }

    @Operation(summary = "平台处理导师提现状态")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/withdrawals/{withdrawalId}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AdminMentorOpsService.WithdrawalManageResponse> manageWithdrawal(
            @PathVariable long withdrawalId,
            @Valid @RequestBody MentorWithdrawalStatusUpdateRequest request
    ) {
        return ApiResponse.ok(
                "admin mentor withdrawal updated",
                adminMentorOpsService.manageWithdrawal(withdrawalId, request),
                TraceId.next()
        );
    }
}
