package com.bishe.server.consult.service;

import com.bishe.server.common.exception.ApiException;
import com.bishe.server.consult.ConsultOrderStatus;
import com.bishe.server.consult.PaymentProperties;
import com.bishe.server.consult.dto.AdminConsultOrderDetailResponse;
import com.bishe.server.consult.dto.AdminConsultPaymentCloseResponse;
import com.bishe.server.consult.dto.AdminConsultPaymentQueryResponse;
import com.bishe.server.consult.dto.AdminConsultRefundQueryResponse;
import com.bishe.server.consult.dto.AdminConsultOrderListResponse;
import com.bishe.server.consult.dto.AdminConsultOrderRefundRequest;
import com.bishe.server.consult.dto.AdminConsultOrderRefundResponse;
import com.bishe.server.consult.repository.ConsultRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 管理员侧咨询订单与售后服务。
 */
@Service
public class AdminConsultService {

    private static final String REFUND_AUDIT_ACTION = "ADMIN_CONSULT_REFUND";

    private final ConsultRepository consultRepository;
    private final ConsultAfterSalesService consultAfterSalesService;
    private final PaymentProperties paymentProperties;
    private final ObjectMapper objectMapper;
    private final ConsultPaymentGatewayService consultPaymentGatewayService;

    public AdminConsultService(
            ConsultRepository consultRepository,
            ConsultAfterSalesService consultAfterSalesService,
            PaymentProperties paymentProperties,
            ObjectMapper objectMapper,
            ConsultPaymentGatewayService consultPaymentGatewayService
    ) {
        this.consultRepository = consultRepository;
        this.consultAfterSalesService = consultAfterSalesService;
        this.paymentProperties = paymentProperties;
        this.objectMapper = objectMapper;
        this.consultPaymentGatewayService = consultPaymentGatewayService;
    }

    public AdminConsultOrderListResponse getOrders(int page, int size, String keyword, String status) {
        consultAfterSalesService.reconcileTimedOutMentorReplyOrders();
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        ConsultOrderStatus statusFilter = parseOptionalStatus(status);
        long total = consultRepository.countAdminOrders(keyword, statusFilter);
        return new AdminConsultOrderListResponse(
                consultRepository.findAdminOrders(keyword, statusFilter, safePage, safeSize).stream()
                        .map(item -> new AdminConsultOrderListResponse.OrderItem(
                                item.orderNo(),
                                item.studentUserId(),
                                item.studentDisplayName(),
                                item.mentorUserId(),
                                item.mentorDisplayName(),
                                item.amountFen(),
                                item.status().name(),
                                item.questionText(),
                                item.paymentMode(),
                                toIso(item.appointmentStartAt()),
                                toIso(item.appointmentEndAt()),
                                toIso(item.createdAt()),
                                toIso(item.paidAt()),
                                toIso(item.closedAt())
                        ))
                        .toList(),
                total,
                safePage,
                safeSize
        );
    }

    public AdminConsultOrderDetailResponse getOrderDetail(String orderNo) {
        ConsultRepository.OrderDetailRow order = consultRepository.findOrderDetail(orderNo)
                .orElseThrow(() -> new ApiException("BIZ-1002", "order not found", HttpStatus.NOT_FOUND));
        return toDetailResponse(order);
    }

    public AdminConsultOrderRefundResponse refundOrder(String traceId, long operatorUserId, String orderNo, AdminConsultOrderRefundRequest request) {
        return consultAfterSalesService.refundOrder(traceId, operatorUserId, orderNo, request.reason());
    }


    public AdminConsultPaymentQueryResponse querySandboxTrade(String traceId, long operatorUserId, String orderNo) {
        return consultPaymentGatewayService.querySandboxTrade(traceId, operatorUserId, orderNo);
    }

    public AdminConsultPaymentCloseResponse closeSandboxTrade(String traceId, long operatorUserId, String orderNo) {
        return consultPaymentGatewayService.closeSandboxTrade(traceId, operatorUserId, orderNo);
    }

    public AdminConsultRefundQueryResponse querySandboxRefund(String traceId, long operatorUserId, String orderNo) {
        return consultPaymentGatewayService.querySandboxRefund(traceId, operatorUserId, orderNo);
    }

    private AdminConsultOrderDetailResponse toDetailResponse(ConsultRepository.OrderDetailRow order) {
        AdminConsultOrderDetailResponse.PaymentSummary payment = consultRepository.findLatestPaymentRecord(order.orderId(), order.orderNo())
                .map(row -> new AdminConsultOrderDetailResponse.PaymentSummary(
                        row.channel(),
                        row.mode(),
                        row.status(),
                        row.providerTradeNo(),
                        row.idempotencyKey(),
                        toIso(row.createdAt())
                ))
                .orElse(null);
        AdminConsultOrderDetailResponse.ReviewSummary review = order.reviewRating() == null
                ? null
                : new AdminConsultOrderDetailResponse.ReviewSummary(order.reviewRating(), order.reviewComment(), toIso(order.reviewCreatedAt()));
        AdminConsultOrderDetailResponse.RefundSummary refund = consultRepository.findLatestAuditLog(REFUND_AUDIT_ACTION, "ORDER", order.orderNo())
                .map(this::toRefundSummary)
                .orElse(null);
        return new AdminConsultOrderDetailResponse(
                order.orderNo(),
                order.studentUserId(),
                order.studentDisplayName(),
                order.mentorUserId(),
                order.mentorDisplayName(),
                order.amountFen(),
                order.status().name(),
                order.questionText(),
                order.paymentMode(),
                toIso(order.appointmentStartAt()),
                toIso(order.appointmentEndAt()),
                toIso(order.createdAt()),
                toIso(order.paidAt()),
                toIso(order.closedAt()),
                resolveAutoCancelAt(order.status(), order.createdAt(), order.paidAt()),
                consultAfterSalesService.resolveMentorReplyDeadlineAt(order),
                payment,
                review,
                refund,
                consultAfterSalesService.getOrderRequestSummaries(order.orderId(), order.orderNo())
        );
    }

    private AdminConsultOrderDetailResponse.RefundSummary toRefundSummary(ConsultRepository.AuditLogRow row) {
        try {
            JsonNode detail = row.detailJson() == null || row.detailJson().isBlank()
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(row.detailJson());
            return new AdminConsultOrderDetailResponse.RefundSummary(
                    detail.path("reason").asText(""),
                    row.operatorUserId(),
                    detail.path("previousStatus").asText(""),
                    detail.path("slotReleased").asBoolean(false),
                    detail.path("reviewRemoved").asBoolean(false),
                    toIso(row.createdAt())
            );
        } catch (Exception ex) {
            return new AdminConsultOrderDetailResponse.RefundSummary("", row.operatorUserId(), "", false, false, toIso(row.createdAt()));
        }
    }

    private ConsultOrderStatus parseOptionalStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        try {
            return ConsultOrderStatus.parse(status.trim());
        } catch (IllegalArgumentException ex) {
            throw new ApiException("BIZ-1001", "status invalid", HttpStatus.BAD_REQUEST);
        }
    }

    private Long resolveAutoCancelAt(ConsultOrderStatus status, Instant createdAt, Instant paidAt) {
        if (paidAt != null) {
            return null;
        }
        if (status != ConsultOrderStatus.CREATED && status != ConsultOrderStatus.PAYING) {
            return null;
        }
        int timeoutMinutes = paymentProperties.getUnpaidTimeoutMinutes();
        if (timeoutMinutes <= 0 || createdAt == null) {
            return null;
        }
        return toIso(createdAt.plusSeconds(timeoutMinutes * 60L));
    }

    private Long toIso(Instant instant) {
        return com.bishe.server.common.TimePayloads.toEpochMillis(instant);
    }
}
