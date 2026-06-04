package com.bishe.server.growth.api;

import com.bishe.server.common.ApiResponse;
import com.bishe.server.common.TraceId;
import com.bishe.server.growth.dto.DailyTaskCompleteResponse;
import com.bishe.server.growth.dto.DailyTaskItemResponse;
import com.bishe.server.growth.dto.GrowthCheckinResponse;
import com.bishe.server.growth.dto.GrowthCheckinOverviewResponse;
import com.bishe.server.growth.dto.PointsLedgerResponse;
import com.bishe.server.growth.service.GrowthService;
import com.bishe.server.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 成长中心接口：签到、每日任务、积分账本。
 */
@Tag(name = "Growth", description = "签到、每日任务与积分账本接口")
@Validated
@RestController
@RequestMapping(path = "/api/v1/growth", produces = MediaType.APPLICATION_JSON_VALUE)
public class GrowthController {

    private final GrowthService growthService;

    public GrowthController(GrowthService growthService) {
        this.growthService = growthService;
    }

    @Operation(summary = "每日签到")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/checkin")
    public ApiResponse<GrowthCheckinResponse> checkin(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(growthService.checkin(principal.getUserId()), TraceId.next());
    }

    @Operation(summary = "查询签到总览")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping(path = "/checkin/overview")
    public ApiResponse<GrowthCheckinOverviewResponse> getCheckinOverview(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(growthService.getCheckinOverview(principal.getUserId()), TraceId.next());
    }

    @Operation(summary = "查询每日任务")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping(path = "/tasks/daily")
    public ApiResponse<List<DailyTaskItemResponse>> getDailyTasks(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(growthService.getDailyTasks(principal.getUserId()), TraceId.next());
    }

    @Operation(summary = "完成每日任务")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/tasks/{taskId}/complete")
    public ApiResponse<DailyTaskCompleteResponse> completeDailyTask(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable long taskId
    ) {
        return ApiResponse.ok(growthService.completeDailyTask(principal.getUserId(), taskId), TraceId.next());
    }

    @Operation(summary = "查询积分账本")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping(path = "/points/ledger")
    public ApiResponse<PointsLedgerResponse> getPointsLedger(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size
    ) {
        return ApiResponse.ok(growthService.getPointsLedger(principal.getUserId(), page, size), TraceId.next());
    }
}
