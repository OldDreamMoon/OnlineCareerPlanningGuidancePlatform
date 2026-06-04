package com.bishe.server.consult.service;

import com.bishe.server.common.exception.ApiException;
import com.bishe.server.common.util.TextListCodec;
import com.bishe.server.consult.ConsultOrderStatus;
import com.bishe.server.consult.PaymentProperties;
import com.bishe.server.consult.dto.AdminPaymentHandleRequest;
import com.bishe.server.consult.dto.AdminPaymentHandleResponse;
import com.bishe.server.consult.dto.AdminPaymentReconciliationDetailResponse;
import com.bishe.server.consult.dto.AdminPaymentReconciliationListResponse;
import com.bishe.server.consult.repository.AdminPaymentRepository;
import com.bishe.server.consult.repository.ConsultRepository;
import com.bishe.server.dashboard.AdminOperationsDashboardCacheService;
import com.bishe.server.dashboard.AdminWorkbenchCacheService;
import com.bishe.server.mentor.service.MentorDashboardCacheService;
import com.bishe.server.mentor.schedule.service.MentorScheduleService;
import com.bishe.server.notification.service.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 管理员支付对账与异常单人工处理服务。
 */
@Service
public class AdminPaymentService {

    private static final String ACTION_MARK_PAID = "MARK_PAID";
    private static final String ACTION_CANCEL_UNPAID = "CANCEL_UNPAID";
    private static final String ACTION_CONFIRM_EXTERNAL_REFUND = "CONFIRM_EXTERNAL_REFUND";
    private static final String ACTION_MARK_REVIEWED = "MARK_REVIEWED";

    private static final String AUDIT_MARK_PAID = "ADMIN_PAYMENT_MARK_PAID";
    private static final String AUDIT_CANCEL_UNPAID = "ADMIN_PAYMENT_CANCEL_UNPAID";
    private static final String AUDIT_CONFIRM_EXTERNAL_REFUND = "ADMIN_PAYMENT_CONFIRM_EXTERNAL_REFUND";
    private static final String AUDIT_MARK_REVIEWED = "ADMIN_PAYMENT_MARK_REVIEWED";

    private final AdminPaymentRepository adminPaymentRepository;
    private final ConsultRepository consultRepository;
    private final MentorScheduleService mentorScheduleService;
    private final NotificationService notificationService;
    private final PaymentProperties paymentProperties;
    private final ObjectMapper objectMapper;
    private final MentorDashboardCacheService mentorDashboardCacheService;
    private final AdminWorkbenchCacheService adminWorkbenchCacheService;
    private final AdminOperationsDashboardCacheService adminOperationsDashboardCacheService;

    public AdminPaymentService(
            AdminPaymentRepository adminPaymentRepository,
            ConsultRepository consultRepository,
            MentorScheduleService mentorScheduleService,
            NotificationService notificationService,
            PaymentProperties paymentProperties,
            ObjectMapper objectMapper,
            MentorDashboardCacheService mentorDashboardCacheService,
            AdminWorkbenchCacheService adminWorkbenchCacheService,
            AdminOperationsDashboardCacheService adminOperationsDashboardCacheService
    ) {
        this.adminPaymentRepository = adminPaymentRepository;
        this.consultRepository = consultRepository;
        this.mentorScheduleService = mentorScheduleService;
        this.notificationService = notificationService;
        this.paymentProperties = paymentProperties;
        this.objectMapper = objectMapper;
        this.mentorDashboardCacheService = mentorDashboardCacheService;
        this.adminWorkbenchCacheService = adminWorkbenchCacheService;
        this.adminOperationsDashboardCacheService = adminOperationsDashboardCacheService;
    }

    public AdminPaymentReconciliationListResponse getOrders(int page, int size, String keyword, String orderStatus, String reconciliationStatus) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        ConsultOrderStatus orderStatusFilter = parseOptionalOrderStatus(orderStatus);
        String reconciliationFilter = parseOptionalReconciliationStatus(reconciliationStatus);

        List<AdminPaymentReconciliationListResponse.OrderItem> filtered = adminPaymentRepository.findOrders(keyword, orderStatusFilter)
                .stream()
                .map(this::toListItem)
                .filter(item -> reconciliationFilter == null || reconciliationFilter.equals(item.reconciliationStatus()))
                .toList();

        long total = filtered.size();
        int fromIndex = Math.min((safePage - 1) * safeSize, filtered.size());
        int toIndex = Math.min(fromIndex + safeSize, filtered.size());
        List<AdminPaymentReconciliationListResponse.OrderItem> pageRecords = filtered.subList(fromIndex, toIndex);
        return new AdminPaymentReconciliationListResponse(pageRecords, total, safePage, safeSize);
    }

    public AdminPaymentReconciliationDetailResponse getOrderDetail(String orderNo) {
        ConsultRepository.OrderDetailRow order = getRequiredOrder(orderNo);
        List<AdminPaymentRepository.PaymentRecordEventRow> paymentRecords = adminPaymentRepository.findPaymentRecords(orderNo);
        AdminPaymentRepository.ManualAuditRow latestManualAudit = adminPaymentRepository.findLatestManualAudit(orderNo).orElse(null);
        ReconciliationAssessment assessment = assess(order, paymentRecords, latestManualAudit);
        AdminPaymentReconciliationDetailResponse.ManualSummary manualSummary = latestManualAudit == null ? null : toManualSummary(latestManualAudit);
        return new AdminPaymentReconciliationDetailResponse(
                order.orderNo(),
                order.studentUserId(),
                order.studentDisplayName(),
                order.mentorUserId(),
                order.mentorDisplayName(),
                order.amountFen(),
                order.status().name(),
                order.questionText(),
                assessment.paymentMode(),
                assessment.paymentChannel(),
                assessment.latestPaymentStatus(),
                assessment.providerTradeNo(),
                toIso(order.appointmentStartAt()),
                toIso(order.appointmentEndAt()),
                toIso(order.createdAt()),
                toIso(order.paidAt()),
                toIso(order.closedAt()),
                assessment.reconciliationStatus(),
                assessment.issueTags(),
                assessment.recommendedActions(),
                manualSummary,
                paymentRecords.stream()
                        .map(item -> new AdminPaymentReconciliationDetailResponse.PaymentRecordItem(
                                item.channel(),
                                item.mode(),
                                item.status(),
                                item.providerTradeNo(),
                                item.amountFen(),
                                item.idempotencyKey(),
                                item.rawCallback(),
                                toIso(item.createdAt())
                        ))
                        .toList()
        );
    }

    @Transactional
    public AdminPaymentHandleResponse handle(String traceId, long operatorUserId, String orderNo, AdminPaymentHandleRequest request) {
        String action = parseRequiredAction(request.action());
        String note = TextListCodec.normalizeText(request.note());
        if (note == null || note.isBlank()) {
            throw new ApiException("BIZ-1001", "note required", HttpStatus.BAD_REQUEST);
        }

        ConsultRepository.OrderDetailRow order = getRequiredOrder(orderNo);
        List<AdminPaymentRepository.PaymentRecordEventRow> paymentRecords = adminPaymentRepository.findPaymentRecords(orderNo);
        Instant handledAt = Instant.now();

        switch (action) {
            case ACTION_MARK_PAID -> handleMarkPaid(traceId, operatorUserId, order, paymentRecords, note);
            case ACTION_CANCEL_UNPAID -> handleCancelUnpaid(traceId, operatorUserId, order, note);
            case ACTION_CONFIRM_EXTERNAL_REFUND -> handleConfirmExternalRefund(traceId, operatorUserId, order, paymentRecords, note);
            case ACTION_MARK_REVIEWED -> handleMarkReviewed(traceId, operatorUserId, order, paymentRecords, note);
            default -> throw new ApiException("BIZ-1001", "action invalid", HttpStatus.BAD_REQUEST);
        }
        evictAdminWorkbenchCache();
        evictOperationsDashboardCache();

        ConsultRepository.OrderDetailRow refreshed = getRequiredOrder(orderNo);
        ReconciliationAssessment assessment = assess(refreshed, adminPaymentRepository.findPaymentRecords(orderNo), adminPaymentRepository.findLatestManualAudit(orderNo).orElse(null));
        return new AdminPaymentHandleResponse(refreshed.orderNo(), action, refreshed.status().name(), assessment.reconciliationStatus(), handledAt.toEpochMilli(), note);
    }

    private void handleMarkPaid(String traceId, long operatorUserId, ConsultRepository.OrderDetailRow order, List<AdminPaymentRepository.PaymentRecordEventRow> paymentRecords, String note) {
        if (order.paidAt() != null || (order.status() != ConsultOrderStatus.CREATED && order.status() != ConsultOrderStatus.PAYING)) {
            throw new ApiException("BIZ-1001", "order not eligible for mark paid", HttpStatus.BAD_REQUEST);
        }
        if (paymentRecords.isEmpty()) {
            throw new ApiException("BIZ-1001", "payment not initialized", HttpStatus.BAD_REQUEST);
        }
        AdminPaymentRepository.PaymentRecordEventRow latestPayment = paymentRecords.get(0);
        consultRepository.insertPaymentRecord(
                order.orderId(),
                order.orderNo(),
                latestPayment.channel() == null || latestPayment.channel().isBlank() ? "MANUAL" : latestPayment.channel(),
                latestPayment.mode() == null || latestPayment.mode().isBlank() ? "MOCK" : latestPayment.mode(),
                "ADMIN-MANUAL-" + order.orderNo(),
                order.amountFen(),
                "SUCCESS",
                "ADMIN_MANUAL_PAID:" + order.orderNo(),
                note
        );
        consultRepository.markOrderPaid(order.orderId(), order.orderNo());
        evictMentorDashboardCache(order.mentorUserId());
        notificationService.createNotification(order.studentUserId(), "CONSULT_PAYMENT_RECONCILED", "管理员已人工确认该订单支付成功，请查看订单最新状态。", order.orderNo());
        notificationService.createNotification(order.mentorUserId(), "CONSULT_PAID", "新的咨询订单已由管理员人工确认支付成功，可开始答复。", order.orderNo());
        consultRepository.insertAuditLog(
                traceId,
                operatorUserId,
                AUDIT_MARK_PAID,
                "ORDER",
                order.orderNo(),
                toJson(new ManualAuditPayload(note, order.status().name(), false, latestPayment.status()))
        );
    }

    private void handleCancelUnpaid(String traceId, long operatorUserId, ConsultRepository.OrderDetailRow order, String note) {
        if (order.paidAt() != null || (order.status() != ConsultOrderStatus.CREATED && order.status() != ConsultOrderStatus.PAYING)) {
            throw new ApiException("BIZ-1001", "order not eligible for cancel", HttpStatus.BAD_REQUEST);
        }
        if (!consultRepository.markOrderCanceled(order.orderId(), order.orderNo())) {
            throw new ApiException("BIZ-1001", "order cancel failed", HttpStatus.BAD_REQUEST);
        }
        evictMentorDashboardCache(order.mentorUserId());
        boolean slotReleased = order.appointmentStartAt() != null || order.appointmentEndAt() != null;
        mentorScheduleService.releaseSlotForOrder(order.orderId(), order.orderNo());
        notificationService.createNotification(order.studentUserId(), "CONSULT_PAYMENT_EXCEPTION", "管理员已人工取消该未支付异常订单，请根据需要重新下单。", order.orderNo());
        if (slotReleased) {
            notificationService.createNotification(order.mentorUserId(), "CONSULT_PAYMENT_EXCEPTION", "某支付异常订单已由管理员取消，预约时段已释放。", order.orderNo());
        }
        consultRepository.insertAuditLog(
                traceId,
                operatorUserId,
                AUDIT_CANCEL_UNPAID,
                "ORDER",
                order.orderNo(),
                toJson(new ManualAuditPayload(note, order.status().name(), slotReleased, null))
        );
    }

    private void handleConfirmExternalRefund(String traceId, long operatorUserId, ConsultRepository.OrderDetailRow order, List<AdminPaymentRepository.PaymentRecordEventRow> paymentRecords, String note) {
        if (order.status() != ConsultOrderStatus.REFUNDED) {
            throw new ApiException("BIZ-1001", "order not refunded", HttpStatus.BAD_REQUEST);
        }
        AdminPaymentRepository.PaymentRecordEventRow latestPayment = paymentRecords.isEmpty() ? null : paymentRecords.get(0);
        if (latestPayment == null || !"SANDBOX".equalsIgnoreCase(latestPayment.mode())) {
            throw new ApiException("BIZ-1001", "external refund confirm only available for sandbox payments", HttpStatus.BAD_REQUEST);
        }
        consultRepository.insertAuditLog(
                traceId,
                operatorUserId,
                AUDIT_CONFIRM_EXTERNAL_REFUND,
                "ORDER",
                order.orderNo(),
                toJson(new ManualAuditPayload(note, order.status().name(), false, latestPayment.status()))
        );
        notificationService.createNotification(order.studentUserId(), "CONSULT_REFUND_CONFIRMED", "管理员已确认该订单的外部退款处理完成。", order.orderNo());
    }

    private void handleMarkReviewed(String traceId, long operatorUserId, ConsultRepository.OrderDetailRow order, List<AdminPaymentRepository.PaymentRecordEventRow> paymentRecords, String note) {
        String latestPaymentStatus = paymentRecords.isEmpty() ? null : paymentRecords.get(0).status();
        consultRepository.insertAuditLog(
                traceId,
                operatorUserId,
                AUDIT_MARK_REVIEWED,
                "ORDER",
                order.orderNo(),
                toJson(new ManualAuditPayload(note, order.status().name(), false, latestPaymentStatus))
        );
    }

    private AdminPaymentReconciliationListResponse.OrderItem toListItem(AdminPaymentRepository.PaymentReconciliationOrderRow row) {
        AdminPaymentRepository.ManualAuditRow latestManualAudit = row.latestManualActionType() == null
                ? null
                : new AdminPaymentRepository.ManualAuditRow(row.latestManualActionType(), row.latestManualOperatorUserId() == null ? 0L : row.latestManualOperatorUserId(), row.latestManualDetailJson(), row.latestManualCreatedAt());
        List<AdminPaymentRepository.PaymentRecordEventRow> latestPaymentList = new ArrayList<>();
        if (row.latestPaymentStatus() != null || row.latestPaymentMode() != null || row.latestPaymentChannel() != null || row.latestProviderTradeNo() != null || row.latestPaymentAmountFen() != null || row.latestPaymentCreatedAt() != null) {
            latestPaymentList.add(new AdminPaymentRepository.PaymentRecordEventRow(
                    row.latestPaymentChannel(),
                    row.latestPaymentMode(),
                    row.latestPaymentStatus(),
                    row.latestProviderTradeNo(),
                    row.latestPaymentAmountFen() == null ? row.amountFen() : row.latestPaymentAmountFen(),
                    null,
                    null,
                    row.latestPaymentCreatedAt()
            ));
        }
        boolean hasSuccessPayment = latestPaymentList.stream()
                .anyMatch(item -> "SUCCESS".equalsIgnoreCase(item.status()));
        if (row.hasSuccessPayment() && !hasSuccessPayment) {
            latestPaymentList.add(new AdminPaymentRepository.PaymentRecordEventRow(
                    row.latestPaymentChannel(),
                    row.latestPaymentMode(),
                    "SUCCESS",
                    row.latestProviderTradeNo(),
                    row.amountFen(),
                    null,
                    null,
                    row.latestPaymentCreatedAt()
            ));
        }
        ReconciliationAssessment assessment = assess(
                new ConsultRepository.OrderDetailRow(
                        0L,
                        row.orderNo(),
                        row.studentUserId(),
                        row.studentDisplayName(),
                        row.mentorUserId(),
                        row.mentorDisplayName(),
                        row.amountFen(),
                        row.orderStatus(),
                        null,
                        null,
                        row.questionText(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        row.latestPaymentMode(),
                        row.appointmentStartAt(),
                        row.appointmentEndAt(),
                        row.createdAt(),
                        row.paidAt(),
                        row.closedAt(),
                        null,
                        null,
                        null
                ),
                latestPaymentList,
                latestManualAudit
        );
        AdminPaymentReconciliationDetailResponse.ManualSummary manualSummary = latestManualAudit == null ? null : toManualSummary(latestManualAudit);
        return new AdminPaymentReconciliationListResponse.OrderItem(
                row.orderNo(),
                row.studentUserId(),
                row.studentDisplayName(),
                row.mentorUserId(),
                row.mentorDisplayName(),
                row.amountFen(),
                row.orderStatus().name(),
                assessment.paymentMode(),
                assessment.paymentChannel(),
                assessment.latestPaymentStatus(),
                assessment.providerTradeNo(),
                assessment.reconciliationStatus(),
                assessment.issueTags(),
                manualSummary == null ? null : manualSummary.action(),
                manualSummary == null ? null : manualSummary.note(),
                manualSummary == null ? null : manualSummary.processedAt(),
                toIso(row.createdAt()),
                toIso(row.paidAt()),
                toIso(row.closedAt()),
                toIso(row.latestPaymentCreatedAt())
        );
    }

    private ReconciliationAssessment assess(
            ConsultRepository.OrderDetailRow order,
            List<AdminPaymentRepository.PaymentRecordEventRow> paymentRecords,
            AdminPaymentRepository.ManualAuditRow latestManualAudit
    ) {
        AdminPaymentRepository.PaymentRecordEventRow latestPayment = paymentRecords.isEmpty() ? null : paymentRecords.get(0);
        AdminPaymentRepository.PaymentRecordEventRow latestSuccess = paymentRecords.stream()
                .filter(item -> "SUCCESS".equalsIgnoreCase(item.status()))
                .findFirst()
                .orElse(null);

        List<String> issueTags = new ArrayList<>();
        boolean paidLike = order.status() == ConsultOrderStatus.PAID
                || order.status() == ConsultOrderStatus.ANSWERED
                || order.status() == ConsultOrderStatus.CLOSED
                || order.status() == ConsultOrderStatus.REFUNDED
                || order.paidAt() != null;
        boolean pendingLike = (order.status() == ConsultOrderStatus.CREATED || order.status() == ConsultOrderStatus.PAYING) && order.paidAt() == null;
        boolean timedOutUnpaid = isTimedOutUnpaidOrder(order);
        String latestManualAction = normalizeManualActionType(latestManualAudit == null ? null : latestManualAudit.actionType());

        if (order.status() == ConsultOrderStatus.PAYING && latestPayment == null) {
            issueTags.add("PAYING_WITHOUT_RECORD");
        }
        if (pendingLike && latestSuccess != null) {
            issueTags.add("SUCCESS_NOT_APPLIED");
        }
        if (pendingLike && latestSuccess == null && timedOutUnpaid) {
            issueTags.add("UNPAID_STUCK");
        }
        if (paidLike && latestSuccess == null) {
            issueTags.add("PAID_WITHOUT_SUCCESS_RECORD");
        }
        if ((order.status() == ConsultOrderStatus.CANCELED || order.status() == ConsultOrderStatus.FAILED) && latestSuccess != null) {
            issueTags.add("SUCCESS_NOT_APPLIED");
        }
        if (latestPayment != null && latestPayment.amountFen() != order.amountFen()) {
            issueTags.add("PAYMENT_AMOUNT_MISMATCH");
        }
        if (order.status() == ConsultOrderStatus.REFUNDED && isExternalPayment(latestPayment) && !ACTION_CONFIRM_EXTERNAL_REFUND.equals(latestManualAction)) {
            issueTags.add("REFUND_EXTERNAL_PENDING");
        }

        List<String> recommendedActions = deriveRecommendedActions(issueTags);
        String reconciliationStatus;
        if (issueTags.isEmpty()) {
            if (pendingLike && latestPayment != null && !timedOutUnpaid && "INIT".equalsIgnoreCase(latestPayment.status())) {
                reconciliationStatus = "PENDING";
            } else if (ACTION_MARK_PAID.equals(latestManualAction)
                    || ACTION_CANCEL_UNPAID.equals(latestManualAction)
                    || ACTION_CONFIRM_EXTERNAL_REFUND.equals(latestManualAction)) {
                reconciliationStatus = "MANUALLY_RESOLVED";
            } else {
                reconciliationStatus = "MATCHED";
            }
        } else if (ACTION_MARK_REVIEWED.equals(latestManualAction)) {
            reconciliationStatus = "REVIEWED_PENDING";
        } else {
            reconciliationStatus = "REVIEW_REQUIRED";
        }

        return new ReconciliationAssessment(
                reconciliationStatus,
                issueTags,
                recommendedActions,
                latestPayment == null ? order.paymentMode() : latestPayment.mode(),
                latestPayment == null ? null : latestPayment.channel(),
                latestPayment == null ? null : latestPayment.status(),
                latestPayment == null ? null : latestPayment.providerTradeNo()
        );
    }

    private List<String> deriveRecommendedActions(List<String> issueTags) {
        Set<String> actions = new LinkedHashSet<>();
        for (String issueTag : issueTags) {
            switch (issueTag) {
                case "SUCCESS_NOT_APPLIED" -> {
                    actions.add(ACTION_MARK_PAID);
                    actions.add(ACTION_MARK_REVIEWED);
                }
                case "UNPAID_STUCK" -> {
                    actions.add(ACTION_CANCEL_UNPAID);
                    actions.add(ACTION_MARK_REVIEWED);
                }
                case "REFUND_EXTERNAL_PENDING" -> {
                    actions.add(ACTION_CONFIRM_EXTERNAL_REFUND);
                    actions.add(ACTION_MARK_REVIEWED);
                }
                case "PAYING_WITHOUT_RECORD", "PAYMENT_AMOUNT_MISMATCH", "PAID_WITHOUT_SUCCESS_RECORD" -> actions.add(ACTION_MARK_REVIEWED);
                default -> {
                }
            }
        }
        return List.copyOf(actions);
    }

    private ConsultRepository.OrderDetailRow getRequiredOrder(String orderNo) {
        return consultRepository.findOrderDetail(orderNo)
                .orElseThrow(() -> new ApiException("BIZ-1002", "order not found", HttpStatus.NOT_FOUND));
    }

    private boolean isTimedOutUnpaidOrder(ConsultRepository.OrderDetailRow order) {
        if (order.paidAt() != null) {
            return false;
        }
        if (order.status() != ConsultOrderStatus.CREATED && order.status() != ConsultOrderStatus.PAYING) {
            return false;
        }
        int timeoutMinutes = paymentProperties.getUnpaidTimeoutMinutes();
        if (timeoutMinutes <= 0 || order.createdAt() == null) {
            return false;
        }
        return !order.createdAt().isAfter(Instant.now().minusSeconds(timeoutMinutes * 60L));
    }

    private boolean isExternalPayment(AdminPaymentRepository.PaymentRecordEventRow latestPayment) {
        if (latestPayment == null) {
            return false;
        }
        return "SANDBOX".equalsIgnoreCase(latestPayment.mode()) || "ALIPAY".equalsIgnoreCase(latestPayment.channel());
    }

    private AdminPaymentReconciliationDetailResponse.ManualSummary toManualSummary(AdminPaymentRepository.ManualAuditRow row) {
        try {
            JsonNode detail = row.detailJson() == null || row.detailJson().isBlank()
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(row.detailJson());
            return new AdminPaymentReconciliationDetailResponse.ManualSummary(
                    normalizeManualActionType(row.actionType()),
                    detail.path("note").asText(""),
                    row.operatorUserId(),
                    toIso(row.createdAt())
            );
        } catch (Exception ex) {
            return new AdminPaymentReconciliationDetailResponse.ManualSummary(normalizeManualActionType(row.actionType()), "", row.operatorUserId(), toIso(row.createdAt()));
        }
    }

    private String parseRequiredAction(String rawAction) {
        String normalized = rawAction == null ? "" : rawAction.trim().toUpperCase(Locale.ROOT);
        if (ACTION_MARK_PAID.equals(normalized)
                || ACTION_CANCEL_UNPAID.equals(normalized)
                || ACTION_CONFIRM_EXTERNAL_REFUND.equals(normalized)
                || ACTION_MARK_REVIEWED.equals(normalized)) {
            return normalized;
        }
        throw new ApiException("BIZ-1001", "action invalid", HttpStatus.BAD_REQUEST);
    }

    private ConsultOrderStatus parseOptionalOrderStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank() || "ALL".equalsIgnoreCase(rawStatus)) {
            return null;
        }
        try {
            return ConsultOrderStatus.parse(rawStatus.trim());
        } catch (IllegalArgumentException ex) {
            throw new ApiException("BIZ-1001", "order status invalid", HttpStatus.BAD_REQUEST);
        }
    }

    private String parseOptionalReconciliationStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank() || "ALL".equalsIgnoreCase(rawStatus)) {
            return null;
        }
        String normalized = rawStatus.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "MATCHED", "PENDING", "REVIEW_REQUIRED", "REVIEWED_PENDING", "MANUALLY_RESOLVED" -> normalized;
            default -> throw new ApiException("BIZ-1001", "reconciliation status invalid", HttpStatus.BAD_REQUEST);
        };
    }

    private String normalizeManualActionType(String actionType) {
        if (actionType == null || actionType.isBlank()) {
            return null;
        }
        return switch (actionType) {
            case AUDIT_MARK_PAID -> ACTION_MARK_PAID;
            case AUDIT_CANCEL_UNPAID -> ACTION_CANCEL_UNPAID;
            case AUDIT_CONFIRM_EXTERNAL_REFUND -> ACTION_CONFIRM_EXTERNAL_REFUND;
            case AUDIT_MARK_REVIEWED -> ACTION_MARK_REVIEWED;
            default -> actionType;
        };
    }

    private String toJson(ManualAuditPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new ApiException("BIZ-1001", "manual audit serialize failed", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Long toIso(Instant instant) {
        return com.bishe.server.common.TimePayloads.toEpochMillis(instant);
    }

    private void evictMentorDashboardCache(long mentorUserId) {
        mentorDashboardCacheService.evictNow(mentorUserId);
        mentorDashboardCacheService.evictAfterCommit(mentorUserId);
    }

    private void evictAdminWorkbenchCache() {
        adminWorkbenchCacheService.evictAllNow();
        adminWorkbenchCacheService.evictAllAfterCommit();
    }

    private void evictOperationsDashboardCache() {
        adminOperationsDashboardCacheService.evictAllNow();
        adminOperationsDashboardCacheService.evictAllAfterCommit();
    }

    private record ManualAuditPayload(
            String note,
            String previousStatus,
            boolean slotReleased,
            String latestPaymentStatus
    ) {
    }

    private record ReconciliationAssessment(
            String reconciliationStatus,
            List<String> issueTags,
            List<String> recommendedActions,
            String paymentMode,
            String paymentChannel,
            String latestPaymentStatus,
            String providerTradeNo
    ) {
    }
}
