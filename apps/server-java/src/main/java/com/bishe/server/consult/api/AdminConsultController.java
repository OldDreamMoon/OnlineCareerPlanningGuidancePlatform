package com.bishe.server.consult.api;

import com.bishe.server.common.ApiResponse;
import com.bishe.server.common.TraceId;
import com.bishe.server.consult.dto.AdminConsultOrderDetailResponse;
import com.bishe.server.consult.dto.AdminConsultPaymentCloseResponse;
import com.bishe.server.consult.dto.AdminConsultPaymentQueryResponse;
import com.bishe.server.consult.dto.AdminConsultRefundQueryResponse;
import com.bishe.server.consult.dto.AdminConsultOrderListResponse;
import com.bishe.server.consult.dto.AdminConsultOrderRefundRequest;
import com.bishe.server.consult.dto.AdminConsultOrderRefundResponse;
import com.bishe.server.consult.service.AdminConsultService;
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
 * 管理员咨询订单与售后接口。
 */
@Tag(name = "AdminConsult", description = "管理员咨询订单与售后接口")
@Validated
@RestController
@RequestMapping(path = "/api/v1/admin/consult/orders", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminConsultController {

    private final AdminConsultService adminConsultService;

    public AdminConsultController(AdminConsultService adminConsultService) {
        this.adminConsultService = adminConsultService;
    }

    @Operation(summary = "查询咨询订单列表")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ApiResponse<AdminConsultOrderListResponse> getOrders(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(adminConsultService.getOrders(page, size, keyword, status), TraceId.next());
    }

    @Operation(summary = "查询咨询订单详情")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/{orderNo}")
    public ApiResponse<AdminConsultOrderDetailResponse> getOrderDetail(@PathVariable String orderNo) {
        return ApiResponse.ok(adminConsultService.getOrderDetail(orderNo), TraceId.next());
    }

    @Operation(summary = "管理员手工退款/售后处理")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/{orderNo}/refund", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AdminConsultOrderRefundResponse> refundOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String orderNo,
            @Valid @RequestBody AdminConsultOrderRefundRequest request
    ) {
        String traceId = TraceId.next();
        return ApiResponse.ok(adminConsultService.refundOrder(traceId, principal.getUserId(), orderNo, request), traceId);
    }

    @Operation(summary = "查询支付宝沙箱交易")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/{orderNo}/payment/query")
    public ApiResponse<AdminConsultPaymentQueryResponse> querySandboxTrade(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String orderNo
    ) {
        String traceId = TraceId.next();
        return ApiResponse.ok(adminConsultService.querySandboxTrade(traceId, principal.getUserId(), orderNo), traceId);
    }

    @Operation(summary = "关闭支付宝沙箱交易")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/{orderNo}/payment/close")
    public ApiResponse<AdminConsultPaymentCloseResponse> closeSandboxTrade(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String orderNo
    ) {
        String traceId = TraceId.next();
        return ApiResponse.ok(adminConsultService.closeSandboxTrade(traceId, principal.getUserId(), orderNo), traceId);
    }

    @Operation(summary = "查询支付宝沙箱退款状态")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/{orderNo}/payment/refund-query")
    public ApiResponse<AdminConsultRefundQueryResponse> querySandboxRefund(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String orderNo
    ) {
        String traceId = TraceId.next();
        return ApiResponse.ok(adminConsultService.querySandboxRefund(traceId, principal.getUserId(), orderNo), traceId);
    }
}
