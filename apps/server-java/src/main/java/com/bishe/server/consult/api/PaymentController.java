package com.bishe.server.consult.api;

import com.bishe.server.common.ApiResponse;
import com.bishe.server.common.TraceId;
import com.bishe.server.consult.dto.PaymentCreateResponse;
import com.bishe.server.consult.dto.PaymentSuccessResponse;
import com.bishe.server.consult.service.ConsultService;
import com.bishe.server.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 支付接口：支付发起、模拟支付成功与沙箱回调契约。
 */
@Tag(name = "Payment", description = "咨询订单支付接口")
@RestController
public class PaymentController {

    private final ConsultService consultService;

    public PaymentController(ConsultService consultService) {
        this.consultService = consultService;
    }

    @Operation(summary = "发起支付")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/api/v1/pay/orders/{orderNo}/create", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<PaymentCreateResponse> createPayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String orderNo
    ) {
        return ApiResponse.ok(consultService.createPayment(principal.getUserId(), orderNo), TraceId.next());
    }

    @Operation(summary = "模拟支付成功")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/api/v1/pay/mock/orders/{orderNo}/success", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<PaymentSuccessResponse> mockPaymentSuccess(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String orderNo,
            @RequestParam(required = false) String reason
    ) {
        String traceId = TraceId.next();
        return ApiResponse.ok(consultService.mockPaymentSuccess(traceId, principal.getUserId(), orderNo, reason), traceId);
    }

    @Operation(summary = "支付宝沙箱回调契约")
    @PostMapping(path = "/api/v1/pay/alipay/callback", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> alipayCallback(@RequestParam Map<String, String> form) {
        return ResponseEntity.ok(consultService.handleSandboxCallback(form));
    }
}
