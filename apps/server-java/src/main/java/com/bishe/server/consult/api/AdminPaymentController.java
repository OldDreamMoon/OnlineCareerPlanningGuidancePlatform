package com.bishe.server.consult.api;

import com.bishe.server.common.ApiResponse;
import com.bishe.server.common.TraceId;
import com.bishe.server.consult.dto.AdminPaymentHandleRequest;
import com.bishe.server.consult.dto.AdminPaymentHandleResponse;
import com.bishe.server.consult.dto.AdminPaymentReconciliationDetailResponse;
import com.bishe.server.consult.dto.AdminPaymentReconciliationListResponse;
import com.bishe.server.consult.service.AdminPaymentService;
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
 * 管理员支付对账与异常单人工处理接口。
 */
@Tag(name = "AdminPayments", description = "管理员支付对账与人工处理接口")
@Validated
@RestController
@RequestMapping(path = "/api/v1/admin/payments/reconciliation", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminPaymentController {

    private final AdminPaymentService adminPaymentService;

    public AdminPaymentController(AdminPaymentService adminPaymentService) {
        this.adminPaymentService = adminPaymentService;
    }

    @Operation(summary = "支付对账列表")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ApiResponse<AdminPaymentReconciliationListResponse> getOrders(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String orderStatus,
            @RequestParam(required = false) String reconciliationStatus
    ) {
        return ApiResponse.ok(adminPaymentService.getOrders(page, size, keyword, orderStatus, reconciliationStatus), TraceId.next());
    }

    @Operation(summary = "支付对账详情")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/{orderNo}")
    public ApiResponse<AdminPaymentReconciliationDetailResponse> getOrderDetail(@PathVariable String orderNo) {
        return ApiResponse.ok(adminPaymentService.getOrderDetail(orderNo), TraceId.next());
    }

    @Operation(summary = "支付异常单人工处理")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/{orderNo}/handle", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AdminPaymentHandleResponse> handle(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String orderNo,
            @Valid @RequestBody AdminPaymentHandleRequest request
    ) {
        String traceId = TraceId.next();
        return ApiResponse.ok(adminPaymentService.handle(traceId, principal.getUserId(), orderNo, request), traceId);
    }
}
