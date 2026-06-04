package com.bishe.server.consult.api;

import com.bishe.server.common.ApiResponse;
import com.bishe.server.common.TraceId;
import com.bishe.server.consult.dto.AdminConsultAfterSalesRequestListResponse;
import com.bishe.server.consult.dto.AdminConsultAfterSalesReviewRequest;
import com.bishe.server.consult.dto.AdminConsultAfterSalesReviewResponse;
import com.bishe.server.consult.service.ConsultAfterSalesService;
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
 * 管理员咨询售后申请接口。
 */
@Tag(name = "AdminConsultAfterSales", description = "管理员咨询售后申请接口")
@Validated
@RestController
@RequestMapping(path = "/api/v1/admin/consult/after-sales/requests", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminConsultAfterSalesController {

    private final ConsultAfterSalesService consultAfterSalesService;

    public AdminConsultAfterSalesController(ConsultAfterSalesService consultAfterSalesService) {
        this.consultAfterSalesService = consultAfterSalesService;
    }

    @Operation(summary = "查询咨询售后申请列表")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ApiResponse<AdminConsultAfterSalesRequestListResponse> getRequests(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(consultAfterSalesService.getAdminRequests(page, size, keyword, status), TraceId.next());
    }

    @Operation(summary = "审核咨询售后申请")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/{requestId}/review", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AdminConsultAfterSalesReviewResponse> reviewRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable long requestId,
            @Valid @RequestBody AdminConsultAfterSalesReviewRequest request
    ) {
        String traceId = TraceId.next();
        return ApiResponse.ok(consultAfterSalesService.reviewRequest(traceId, principal.getUserId(), requestId, request), traceId);
    }
}
