package com.bishe.server.mentor.api;

import com.bishe.server.common.ApiResponse;
import com.bishe.server.common.TraceId;
import com.bishe.server.mentor.dto.MentorFinanceOverviewResponse;
import com.bishe.server.mentor.dto.MentorWithdrawalCreateRequest;
import com.bishe.server.mentor.dto.MentorWithdrawalListResponse;
import com.bishe.server.mentor.dto.MentorWithdrawalRecordResponse;
import com.bishe.server.mentor.dto.MentorWithdrawalStatusUpdateRequest;
import com.bishe.server.mentor.service.MentorFinanceService;
import com.bishe.server.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 导师财务中心接口：服务端持久化的提现演示流转管理。
 */
@Tag(name = "MentorFinance", description = "导师财务中心接口")
@Validated
@RestController
@RequestMapping(path = "/api/v1/mentor/finance", produces = MediaType.APPLICATION_JSON_VALUE)
public class MentorFinanceController {

    private final MentorFinanceService mentorFinanceService;

    public MentorFinanceController(MentorFinanceService mentorFinanceService) {
        this.mentorFinanceService = mentorFinanceService;
    }

    @Operation(summary = "获取导师财务中心聚合概览")
    @PreAuthorize("hasRole('MENTOR')")
    @GetMapping(path = "/overview")
    public ApiResponse<MentorFinanceOverviewResponse> getOverview(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "8") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) String trendRange
    ) {
        // 财务概览把指标、趋势和账单分页一起返回，前端筛选时保持同一口径。
        return ApiResponse.ok(
                mentorFinanceService.getOverview(
                        principal.getUserId(),
                        page,
                        size,
                        keyword,
                        status,
                        range,
                        trendRange
                ),
                TraceId.next()
        );
    }

    @Operation(summary = "获取导师提现演示记录")
    @PreAuthorize("hasRole('MENTOR')")
    @GetMapping(path = "/withdrawals")
    public ApiResponse<MentorWithdrawalListResponse> listWithdrawals(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(mentorFinanceService.listWithdrawals(principal.getUserId()), TraceId.next());
    }

    @Operation(summary = "创建导师提现演示申请")
    @PreAuthorize("hasRole('MENTOR')")
    @PostMapping(path = "/withdrawals", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<MentorWithdrawalRecordResponse> createWithdrawal(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody MentorWithdrawalCreateRequest request
    ) {
        // 提现演示流转仍落库保存，便于刷新页面后继续查看状态。
        return ApiResponse.ok("withdrawal created", mentorFinanceService.createWithdrawal(principal.getUserId(), request), TraceId.next());
    }

    @Operation(summary = "推进导师提现演示状态")
    @PreAuthorize("hasRole('MENTOR')")
    @PostMapping(path = "/withdrawals/{withdrawalId}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<MentorWithdrawalRecordResponse> updateWithdrawalStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable long withdrawalId,
            @Valid @RequestBody MentorWithdrawalStatusUpdateRequest request
    ) {
        return ApiResponse.ok("withdrawal status updated", mentorFinanceService.updateWithdrawalStatus(principal.getUserId(), withdrawalId, request), TraceId.next());
    }
}
