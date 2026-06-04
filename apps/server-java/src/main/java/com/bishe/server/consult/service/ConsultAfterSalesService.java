package com.bishe.server.consult.service;

import com.bishe.server.common.TraceId;
import com.bishe.server.common.exception.ApiException;
import com.bishe.server.common.util.TextListCodec;
import com.bishe.server.consult.ConsultAfterSalesRequestStatus;
import com.bishe.server.consult.ConsultAfterSalesRequestType;
import com.bishe.server.consult.ConsultOrderStatus;
import com.bishe.server.consult.ConsultProperties;
import com.bishe.server.consult.dto.AdminConsultAfterSalesRequestListResponse;
import com.bishe.server.consult.dto.AdminConsultAfterSalesReviewRequest;
import com.bishe.server.consult.dto.AdminConsultAfterSalesReviewResponse;
import com.bishe.server.consult.dto.AdminConsultOrderRefundResponse;
import com.bishe.server.consult.dto.ConsultAfterSalesRequestCreateRequest;
import com.bishe.server.consult.dto.ConsultAfterSalesRequestCreateResponse;
import com.bishe.server.consult.dto.ConsultAfterSalesRequestSummary;
import com.bishe.server.consult.repository.ConsultAfterSalesRepository;
import com.bishe.server.consult.repository.ConsultRepository;
import com.bishe.server.dashboard.AdminWorkbenchCacheService;
import com.bishe.server.mentor.repository.MentorRepository;
import com.bishe.server.mentor.service.MentorPublicListCacheService;
import com.bishe.server.mentor.service.MentorDashboardCacheService;
import com.bishe.server.mentor.service.MentorPublicDetailCacheService;
import com.bishe.server.mentor.schedule.service.MentorScheduleService;
import com.bishe.server.notification.service.NotificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * 咨询售后与退款服务。
 */
@Service
public class ConsultAfterSalesService {

    private static final Logger log = LoggerFactory.getLogger(ConsultAfterSalesService.class);

    private static final String REFUND_AUDIT_ACTION = "ADMIN_CONSULT_REFUND";
    private static final String AFTER_SALES_CREATE_AUDIT_ACTION = "CONSULT_AFTER_SALES_CREATED";
    private static final String AFTER_SALES_REVIEW_AUDIT_ACTION = "CONSULT_AFTER_SALES_REVIEWED";
    private static final String AFTER_SALES_AUTO_DEFERRED_AUDIT_ACTION = "CONSULT_AFTER_SALES_AUTO_REFUND_DEFERRED";
    private static final long SYSTEM_OPERATOR_USER_ID = 0L;
    private static final String AUTO_TIMEOUT_REASON = "导师在规定时限内未正式答复，系统已自动发起售后退款";
    private static final String AUTO_TIMEOUT_REVIEW_NOTE = "系统自动审批：导师超时未答";

    private final ConsultRepository consultRepository;
    private final ConsultAfterSalesRepository consultAfterSalesRepository;
    private final MentorRepository mentorRepository;
    private final MentorScheduleService mentorScheduleService;
    private final NotificationService notificationService;
    private final ConsultProperties consultProperties;
    private final ObjectMapper objectMapper;
    private final ConsultPaymentGatewayService consultPaymentGatewayService;
    private final MentorPublicDetailCacheService mentorPublicDetailCacheService;
    private final MentorPublicListCacheService mentorPublicListCacheService;
    private final MentorDashboardCacheService mentorDashboardCacheService;
    private final AdminWorkbenchCacheService adminWorkbenchCacheService;

    public ConsultAfterSalesService(
            ConsultRepository consultRepository,
            ConsultAfterSalesRepository consultAfterSalesRepository,
            MentorRepository mentorRepository,
            MentorScheduleService mentorScheduleService,
            NotificationService notificationService,
            ConsultProperties consultProperties,
            ObjectMapper objectMapper,
            ConsultPaymentGatewayService consultPaymentGatewayService,
            MentorPublicDetailCacheService mentorPublicDetailCacheService,
            MentorPublicListCacheService mentorPublicListCacheService,
            MentorDashboardCacheService mentorDashboardCacheService,
            AdminWorkbenchCacheService adminWorkbenchCacheService
    ) {
        this.consultRepository = consultRepository;
        this.consultAfterSalesRepository = consultAfterSalesRepository;
        this.mentorRepository = mentorRepository;
        this.mentorScheduleService = mentorScheduleService;
        this.notificationService = notificationService;
        this.consultProperties = consultProperties;
        this.objectMapper = objectMapper;
        this.consultPaymentGatewayService = consultPaymentGatewayService;
        this.mentorPublicDetailCacheService = mentorPublicDetailCacheService;
        this.mentorPublicListCacheService = mentorPublicListCacheService;
        this.mentorDashboardCacheService = mentorDashboardCacheService;
        this.adminWorkbenchCacheService = adminWorkbenchCacheService;
    }

    @Transactional
    public ConsultAfterSalesRequestCreateResponse createRequest(String traceId, long studentUserId, String orderNo, ConsultAfterSalesRequestCreateRequest request) {
        ConsultRepository.OrderDetailRow order = consultRepository.findOrderDetail(orderNo)
                .orElseThrow(() -> new ApiException("BIZ-1002", "order not found", HttpStatus.NOT_FOUND));
        // 售后入口只允许订单学生本人触发，管理员退款走另一条审计路径。
        if (order.studentUserId() != studentUserId) {
            throw new ApiException("AUTH-1004", "permission denied", HttpStatus.FORBIDDEN);
        }
        ensureOrderSupportsAfterSalesRequest(order.status());
        if (consultAfterSalesRepository.existsPendingRequest(order.orderId(), orderNo)) {
            throw new ApiException("BIZ-1001", "pending after-sales request already exists", HttpStatus.BAD_REQUEST);
        }
        String reason = requireReason(request.reason());
        // 申请本身先落 PENDING，再写审计和通知，保证后台可追踪每次售后来源。
        long requestId = consultAfterSalesRepository.createRequest(order.orderId(), orderNo, studentUserId, ConsultAfterSalesRequestType.REFUND, reason, false);
        consultRepository.insertAuditLog(
                traceId,
                studentUserId,
                AFTER_SALES_CREATE_AUDIT_ACTION,
                "AFTER_SALES_REQUEST",
                String.valueOf(requestId),
                toJson(Map.of(
                        "orderNo", orderNo,
                        "requestType", ConsultAfterSalesRequestType.REFUND.name(),
                        "reason", reason,
                        "autoTriggered", false
                ))
        );
        notificationService.createNotification(order.studentUserId(), "CONSULT_AFTER_SALES_SUBMITTED", "你的咨询售后申请已提交，等待管理员审核。", orderNo);
        notificationService.createNotification(order.mentorUserId(), "CONSULT_AFTER_SALES_PENDING", "某咨询订单收到售后申请，平台将进行审核。", orderNo);
        evictAdminWorkbenchCache();
        ConsultAfterSalesRepository.AfterSalesRequestRow created = consultAfterSalesRepository.findById(requestId)
                .orElseThrow(() -> new IllegalStateException("after-sales request not found after create"));
        return new ConsultAfterSalesRequestCreateResponse(
                created.id(),
                created.orderNo(),
                created.requestType().name(),
                created.status().name(),
                created.autoTriggered(),
                created.reason(),
                toIso(created.createdAt())
        );
    }

    public AdminConsultAfterSalesRequestListResponse getAdminRequests(int page, int size, String keyword, String status) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        ConsultAfterSalesRequestStatus statusFilter = parseOptionalStatus(status);
        long total = consultAfterSalesRepository.countAdminRequests(keyword, statusFilter);
        return new AdminConsultAfterSalesRequestListResponse(
                consultAfterSalesRepository.findAdminRequests(keyword, statusFilter, safePage, safeSize).stream()
                        .map(item -> new AdminConsultAfterSalesRequestListResponse.RequestItem(
                                item.id(),
                                item.orderNo(),
                                item.orderStatus().name(),
                                item.amountFen(),
                                item.studentUserId(),
                                item.studentDisplayName(),
                                item.mentorUserId(),
                                item.mentorDisplayName(),
                                item.requestType().name(),
                                item.status().name(),
                                item.reason(),
                                item.reviewNote(),
                                item.reviewerUserId(),
                                item.autoTriggered(),
                                toIso(item.createdAt()),
                                toIso(item.reviewedAt())
                        ))
                        .toList(),
                total,
                safePage,
                safeSize
        );
    }

    public java.util.List<ConsultAfterSalesRequestSummary> getOrderRequestSummaries(long orderId, String orderNo) {
        return consultAfterSalesRepository.findByOrder(orderId, orderNo).stream()
                .map(this::toSummary)
                .toList();
    }

    public java.util.List<ConsultAfterSalesRequestSummary> getOrderRequestSummaries(String orderNo) {
        return consultAfterSalesRepository.findByOrderNo(orderNo).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public AdminConsultAfterSalesReviewResponse reviewRequest(
            String traceId,
            long operatorUserId,
            long requestId,
            AdminConsultAfterSalesReviewRequest request
    ) {
        ConsultAfterSalesRepository.AfterSalesRequestRow afterSalesRequest = consultAfterSalesRepository.findById(requestId)
                .orElseThrow(() -> new ApiException("BIZ-1002", "after-sales request not found", HttpStatus.NOT_FOUND));
        if (afterSalesRequest.status() != ConsultAfterSalesRequestStatus.PENDING) {
            throw new ApiException("BIZ-1001", "after-sales request already reviewed", HttpStatus.BAD_REQUEST);
        }
        String reviewNote = TextListCodec.normalizeText(request.reviewNote());
        AdminConsultOrderRefundResponse refund = null;
        ConsultAfterSalesRequestStatus reviewedStatus;
        Instant reviewedAt = Instant.now();
        if (Boolean.TRUE.equals(request.approved())) {
            // 审核通过直接进入退款执行链，订单状态和售后状态在同一事务里收口。
            RefundSource source = afterSalesRequest.autoTriggered() ? RefundSource.AUTO_TIMEOUT : RefundSource.AFTER_SALES_APPROVED;
            refund = refundOrderInternal(traceId, operatorUserId, afterSalesRequest.orderNo(), afterSalesRequest.reason(), false, source);
            reviewedStatus = ConsultAfterSalesRequestStatus.APPROVED;
        } else {
            // 驳回只更新售后单，不回滚订单本身，让学生在订单页查看审核备注。
            reviewedStatus = ConsultAfterSalesRequestStatus.REJECTED;
            ConsultRepository.OrderDetailRow order = consultRepository.findOrderDetail(afterSalesRequest.orderNo())
                    .orElseThrow(() -> new ApiException("BIZ-1002", "order not found", HttpStatus.NOT_FOUND));
            notificationService.createNotification(order.studentUserId(), "CONSULT_AFTER_SALES_REJECTED", "你的咨询售后申请未通过，请查看订单页中的审核备注。", order.orderNo());
        }
        if (!consultAfterSalesRepository.reviewRequest(requestId, reviewedStatus, reviewNote, operatorUserId, reviewedAt)) {
            throw new ApiException("BIZ-1001", "after-sales request review failed", HttpStatus.BAD_REQUEST);
        }
        consultRepository.insertAuditLog(
                traceId,
                operatorUserId,
                AFTER_SALES_REVIEW_AUDIT_ACTION,
                "AFTER_SALES_REQUEST",
                String.valueOf(requestId),
                toJson(Map.of(
                        "orderNo", afterSalesRequest.orderNo(),
                        "status", reviewedStatus.name(),
                        "reviewNote", reviewNote == null ? "" : reviewNote,
                        "requestType", afterSalesRequest.requestType().name(),
                        "autoTriggered", afterSalesRequest.autoTriggered()
                ))
        );
        evictAdminWorkbenchCache();
        return new AdminConsultAfterSalesReviewResponse(
                requestId,
                afterSalesRequest.orderNo(),
                reviewedStatus.name(),
                reviewNote,
                reviewedAt.toEpochMilli(),
                refund
        );
    }

    @Transactional
    public int reconcileTimedOutMentorReplyOrders() {
        int timeoutHours = consultProperties.getMentorReplyTimeoutHours();
        if (timeoutHours <= 0) {
            return 0;
        }
        Instant cutoff = Instant.now().minusSeconds(timeoutHours * 3600L);
        int handledCount = 0;
        // 定时任务和懒刷新都走这里，统一处理“已支付但导师超时未答”的订单。
        for (String orderNo : consultRepository.findTimedOutPaidOrderNos(cutoff)) {
            ConsultRepository.OrderDetailRow order = consultRepository.findOrderDetail(orderNo).orElse(null);
            if (order == null) {
                continue;
            }
            ConsultRepository.OrderDetailRow refreshed = refreshOrderIfMentorReplyTimedOut(order);
            if (order.status() != ConsultOrderStatus.REFUNDED && refreshed.status() == ConsultOrderStatus.REFUNDED) {
                handledCount++;
            }
        }
        return handledCount;
    }

    @Transactional
    public ConsultRepository.OrderDetailRow refreshOrderIfMentorReplyTimedOut(ConsultRepository.OrderDetailRow order) {
        if (!isMentorReplyTimedOut(order)) {
            return order;
        }
        // 导师超时未回复时自动进入售后退款链，减少人工巡检和学生等待成本。
        autoHandleMentorReplyTimeout(order);
        return consultRepository.findOrderDetail(order.orderId(), order.orderNo()).orElse(order);
    }

    public Long resolveMentorReplyDeadlineAt(ConsultRepository.OrderDetailRow order) {
        if (order == null || order.status() != ConsultOrderStatus.PAID || order.paidAt() == null) {
            return null;
        }
        int timeoutHours = consultProperties.getMentorReplyTimeoutHours();
        if (timeoutHours <= 0) {
            return null;
        }
        return toIso(order.paidAt().plusSeconds(timeoutHours * 3600L));
    }

    public int getMentorReplyTimeoutHours() {
        return consultProperties.getMentorReplyTimeoutHours();
    }

    @Transactional
    public AdminConsultOrderRefundResponse refundOrder(String traceId, long operatorUserId, String orderNo, String reasonValue) {
        return refundOrderInternal(traceId, operatorUserId, orderNo, reasonValue, true, RefundSource.ADMIN_MANUAL);
    }

    private AdminConsultOrderRefundResponse refundOrderInternal(
            String traceId,
            long operatorUserId,
            String orderNo,
            String reasonValue,
            boolean syncPendingRequest,
            RefundSource source
    ) {
        ConsultRepository.OrderDetailRow order = consultRepository.findOrderDetail(orderNo)
                .orElseThrow(() -> new ApiException("BIZ-1002", "order not found", HttpStatus.NOT_FOUND));
        String reason = requireReason(reasonValue);
        Long processedAt = Instant.now().toEpochMilli();
        if (order.status() == ConsultOrderStatus.REFUNDED) {
            // 幂等保护：重复退款请求直接返回当前状态，不再次触发外部退款。
            return new AdminConsultOrderRefundResponse(orderNo, ConsultOrderStatus.REFUNDED.name(), processedAt, false, false, reason, false, null, null, null);
        }
        ensureOrderSupportsRefundExecution(order.status());
        // 先处理沙箱/外部退款，再推进本地订单终态，避免本地状态先变但外部失败。
        ConsultPaymentGatewayService.RefundExecutionResult externalRefund = consultPaymentGatewayService.refundSandboxTrade(traceId, operatorUserId, order, reason);
        if (!consultRepository.markOrderRefunded(order.orderId(), orderNo)) {
            ConsultRepository.OrderDetailRow latestOrder = consultRepository.findOrderDetail(order.orderId(), orderNo).orElse(order);
            if (latestOrder.status() == ConsultOrderStatus.REFUNDED) {
                return new AdminConsultOrderRefundResponse(
                        orderNo,
                        ConsultOrderStatus.REFUNDED.name(),
                        processedAt,
                        false,
                        false,
                        reason,
                        externalRefund.externalTriggered(),
                        externalRefund.providerTradeNo(),
                        externalRefund.refundRequestNo(),
                        externalRefund.externalStatus()
                );
            }
            throw new ApiException("BIZ-1001", "order refund failed", HttpStatus.BAD_REQUEST);
        }
        boolean reviewRemoved = false;
        if (order.reviewRating() != null && consultRepository.deleteReview(order.orderId(), orderNo)) {
            // 已评价订单退款后要移除评价，并重算导师公开评分。
            reviewRemoved = true;
            mentorRepository.refreshAverageRating(order.mentorUserId());
        }
        if (order.status() == ConsultOrderStatus.CLOSED) {
            mentorRepository.decrementTotalOrders(order.mentorUserId());
        }
        if (reviewRemoved || order.status() == ConsultOrderStatus.CLOSED) {
            mentorPublicDetailCacheService.evictNow(order.mentorUserId());
            mentorPublicDetailCacheService.evictAfterCommit(order.mentorUserId());
            mentorPublicListCacheService.evictAllNow();
            mentorPublicListCacheService.evictAllAfterCommit();
        }
        mentorDashboardCacheService.evictNow(order.mentorUserId());
        mentorDashboardCacheService.evictAfterCommit(order.mentorUserId());
        boolean slotReleased = mentorScheduleService.releaseUpcomingSlotForOrder(order.orderId(), orderNo);
        notifyRefundResult(order, orderNo, source);
        consultRepository.insertAuditLog(
                traceId,
                operatorUserId,
                REFUND_AUDIT_ACTION,
                "ORDER",
                orderNo,
                toJson(Map.of(
                        "reason", reason,
                        "previousStatus", order.status().name(),
                        "slotReleased", slotReleased,
                        "reviewRemoved", reviewRemoved,
                        "source", source.name()
                ))
        );
        if (syncPendingRequest) {
            syncPendingRequestAsApproved(traceId, operatorUserId, order.orderId(), orderNo, reason);
        }
        return new AdminConsultOrderRefundResponse(
                orderNo,
                ConsultOrderStatus.REFUNDED.name(),
                processedAt,
                slotReleased,
                reviewRemoved,
                reason,
                externalRefund.externalTriggered(),
                externalRefund.providerTradeNo(),
                externalRefund.refundRequestNo(),
                externalRefund.externalStatus()
        );
    }

    private void autoHandleMentorReplyTimeout(ConsultRepository.OrderDetailRow order) {
        if (!isMentorReplyTimedOut(order)) {
            return;
        }
        String traceId = TraceId.next();
        Instant reviewedAt = Instant.now();
        ConsultAfterSalesRepository.AfterSalesRequestRow pendingRequest = consultAfterSalesRepository.findPendingRequest(order.orderId(), order.orderNo()).orElse(null);
        long requestId;
        String reason;
        boolean autoTriggered;
        if (pendingRequest == null) {
            // 没有人工售后单时，系统自动补一张售后申请并继续走同一套退款链。
            reason = AUTO_TIMEOUT_REASON;
            autoTriggered = true;
            requestId = consultAfterSalesRepository.createRequest(order.orderId(), order.orderNo(), order.studentUserId(), ConsultAfterSalesRequestType.REFUND, reason, true);
            consultRepository.insertAuditLog(
                    traceId,
                    SYSTEM_OPERATOR_USER_ID,
                    AFTER_SALES_CREATE_AUDIT_ACTION,
                    "AFTER_SALES_REQUEST",
                    String.valueOf(requestId),
                    toJson(Map.of(
                            "orderNo", order.orderNo(),
                            "requestType", ConsultAfterSalesRequestType.REFUND.name(),
                            "reason", reason,
                            "autoTriggered", true
                    ))
            );
            evictAdminWorkbenchCache();
        } else {
            requestId = pendingRequest.id();
            reason = pendingRequest.reason();
            autoTriggered = pendingRequest.autoTriggered();
            // 已有人处理中的售后申请就不重复自动审批，避免覆盖人工流程。
            return;
        }
        try {
            refundOrderInternal(traceId, SYSTEM_OPERATOR_USER_ID, order.orderNo(), reason, false, RefundSource.AUTO_TIMEOUT);
        } catch (ApiException ex) {
            log.warn("Auto timeout refund deferred for order {}: {}", order.orderNo(), ex.getMessage());
            consultRepository.insertAuditLog(
                    traceId,
                    SYSTEM_OPERATOR_USER_ID,
                    AFTER_SALES_AUTO_DEFERRED_AUDIT_ACTION,
                    "AFTER_SALES_REQUEST",
                    String.valueOf(requestId),
                    toJson(Map.of(
                            "orderNo", order.orderNo(),
                            "requestType", ConsultAfterSalesRequestType.REFUND.name(),
                            "reason", reason,
                            "autoTriggered", autoTriggered,
                            "errorCode", ex.getCode(),
                            "errorMessage", ex.getMessage()
                    ))
            );
            return;
        }
        if (consultAfterSalesRepository.reviewRequest(requestId, ConsultAfterSalesRequestStatus.APPROVED, AUTO_TIMEOUT_REVIEW_NOTE, null, reviewedAt)) {
            consultRepository.insertAuditLog(
                    traceId,
                    SYSTEM_OPERATOR_USER_ID,
                    AFTER_SALES_REVIEW_AUDIT_ACTION,
                    "AFTER_SALES_REQUEST",
                    String.valueOf(requestId),
                    toJson(Map.of(
                            "orderNo", order.orderNo(),
                            "status", ConsultAfterSalesRequestStatus.APPROVED.name(),
                            "reviewNote", AUTO_TIMEOUT_REVIEW_NOTE,
                            "requestType", ConsultAfterSalesRequestType.REFUND.name(),
                            "autoTriggered", autoTriggered
                    ))
            );
            evictAdminWorkbenchCache();
        }
    }

    private void syncPendingRequestAsApproved(String traceId, long operatorUserId, long orderId, String orderNo, String reason) {
        consultAfterSalesRepository.findPendingRequest(orderId, orderNo).ifPresent(request -> {
            Instant reviewedAt = Instant.now();
            if (consultAfterSalesRepository.reviewRequest(
                    request.id(),
                    ConsultAfterSalesRequestStatus.APPROVED,
                    "管理员已直接执行退款，售后申请同步通过。",
                    operatorUserId,
                    reviewedAt
            )) {
                consultRepository.insertAuditLog(
                        traceId,
                        operatorUserId,
                        AFTER_SALES_REVIEW_AUDIT_ACTION,
                        "AFTER_SALES_REQUEST",
                        String.valueOf(request.id()),
                        toJson(Map.of(
                                "orderNo", orderNo,
                                "status", ConsultAfterSalesRequestStatus.APPROVED.name(),
                                "reviewNote", "管理员已直接执行退款，售后申请同步通过。",
                                "requestType", request.requestType().name(),
                                "refundReason", reason,
                                "autoTriggered", request.autoTriggered()
                        ))
                );
                evictAdminWorkbenchCache();
            }
        });
    }

    private void evictAdminWorkbenchCache() {
        adminWorkbenchCacheService.evictAllNow();
        adminWorkbenchCacheService.evictAllAfterCommit();
    }

    private void notifyRefundResult(ConsultRepository.OrderDetailRow order, String orderNo, RefundSource source) {
        String studentContent;
        String mentorContent;
        switch (source) {
            case AFTER_SALES_APPROVED -> {
                studentContent = "你的咨询售后申请已审核通过，订单已退款。";
                mentorContent = "某咨询订单的售后申请已审核通过并完成退款。";
            }
            case AUTO_TIMEOUT -> {
                studentContent = "导师超时未答，系统已自动发起售后并完成退款。";
                mentorContent = "某咨询订单因超时未答已被系统自动退款。";
            }
            case ADMIN_MANUAL -> {
                studentContent = "管理员已处理该咨询订单的退款/售后，请查看订单最新状态。";
                mentorContent = "某咨询订单已由管理员退款处理，请关注后续售后沟通。";
            }
            default -> throw new IllegalStateException("unexpected refund source: " + source);
        }
        notificationService.createNotification(order.studentUserId(), "CONSULT_REFUNDED", studentContent, orderNo);
        notificationService.createNotification(order.mentorUserId(), "CONSULT_REFUNDED", mentorContent, orderNo);
    }

    private ConsultAfterSalesRequestSummary toSummary(ConsultAfterSalesRepository.AfterSalesRequestRow row) {
        return new ConsultAfterSalesRequestSummary(
                row.id(),
                row.requesterUserId(),
                row.requestType().name(),
                row.status().name(),
                row.reason(),
                row.reviewNote(),
                row.reviewerUserId(),
                row.autoTriggered(),
                toIso(row.createdAt()),
                toIso(row.reviewedAt())
        );
    }

    private boolean isMentorReplyTimedOut(ConsultRepository.OrderDetailRow order) {
        if (order.status() != ConsultOrderStatus.PAID || order.paidAt() == null) {
            return false;
        }
        int timeoutHours = consultProperties.getMentorReplyTimeoutHours();
        if (timeoutHours <= 0) {
            return false;
        }
        return !order.paidAt().isAfter(Instant.now().minusSeconds(timeoutHours * 3600L));
    }

    private void ensureOrderSupportsAfterSalesRequest(ConsultOrderStatus status) {
        if (status != ConsultOrderStatus.PAID && status != ConsultOrderStatus.ANSWERED && status != ConsultOrderStatus.CLOSED) {
            throw new ApiException("BIZ-1001", "order not refundable", HttpStatus.BAD_REQUEST);
        }
    }

    private void ensureOrderSupportsRefundExecution(ConsultOrderStatus status) {
        if (status != ConsultOrderStatus.PAID && status != ConsultOrderStatus.ANSWERED && status != ConsultOrderStatus.CLOSED) {
            throw new ApiException("BIZ-1001", "order not refundable", HttpStatus.BAD_REQUEST);
        }
    }

    private String requireReason(String value) {
        String reason = TextListCodec.normalizeText(value);
        if (reason == null) {
            throw new ApiException("BIZ-1001", "reason required", HttpStatus.BAD_REQUEST);
        }
        return reason;
    }

    private ConsultAfterSalesRequestStatus parseOptionalStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        try {
            return ConsultAfterSalesRequestStatus.parse(status.trim());
        } catch (IllegalArgumentException ex) {
            throw new ApiException("BIZ-1001", "status invalid", HttpStatus.BAD_REQUEST);
        }
    }

    private Long toIso(Instant instant) {
        return com.bishe.server.common.TimePayloads.toEpochMillis(instant);
    }

    private String toJson(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize audit detail", ex);
        }
    }

    private enum RefundSource {
        ADMIN_MANUAL,
        AFTER_SALES_APPROVED,
        AUTO_TIMEOUT
    }
}
