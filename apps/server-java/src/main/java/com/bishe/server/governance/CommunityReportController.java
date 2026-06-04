package com.bishe.server.governance;

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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 社区举报接口。
 */
@Tag(name = "CommunityReport", description = "社区举报与我的举报接口")
@Validated
@RestController
@RequestMapping(path = "/api/v1/community/reports", produces = MediaType.APPLICATION_JSON_VALUE)
public class CommunityReportController {

    private final ContentGovernanceService contentGovernanceService;

    public CommunityReportController(ContentGovernanceService contentGovernanceService) {
        this.contentGovernanceService = contentGovernanceService;
    }

    @Operation(summary = "提交举报")
    @PreAuthorize("hasAnyRole('STUDENT', 'MENTOR', 'ENTERPRISE', 'ADMIN')")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<CommunityReportCreateResponse> createReport(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CommunityReportCreateRequest request
    ) {
        String traceId = TraceId.next();
        CommunityReportCreateResponse data = contentGovernanceService.submitReport(traceId, principal.getUserId(), request);
        return ApiResponse.ok("report submitted", data, traceId);
    }

    @Operation(summary = "查询我的举报")
    @PreAuthorize("hasAnyRole('STUDENT', 'MENTOR', 'ENTERPRISE', 'ADMIN')")
    @GetMapping(path = "/mine")
    public ApiResponse<CommunityReportListResponse> listMyReports(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(contentGovernanceService.getMyReports(principal.getUserId(), page, size, status), TraceId.next());
    }
}
