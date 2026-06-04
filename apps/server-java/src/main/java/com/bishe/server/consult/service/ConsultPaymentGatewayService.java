package com.bishe.server.consult.service;

import com.bishe.server.common.exception.ApiException;
import com.bishe.server.consult.AlipaySandboxGatewayClient;
import com.bishe.server.consult.ConsultOrderStatus;
import com.bishe.server.consult.PaymentMode;
import com.bishe.server.consult.dto.AdminConsultPaymentCloseResponse;
import com.bishe.server.consult.dto.AdminConsultPaymentQueryResponse;
import com.bishe.server.consult.dto.AdminConsultRefundQueryResponse;
import com.bishe.server.consult.repository.ConsultRepository;
import com.bishe.server.dashboard.AdminOperationsDashboardCacheService;
import com.bishe.server.mentor.service.MentorDashboardCacheService;
import com.bishe.server.mentor.schedule.service.MentorScheduleService;
import com.bishe.server.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * 咨询支付网关编排服务：统一处理支付宝沙箱查询、退款、退款查询、关单与本地状态同步。
 */
@Service
public class ConsultPaymentGatewayService {

    private static final String AUDIT_TRADE_QUERY = "ADMIN_SANDBOX_TRADE_QUERY";
    private static final String AUDIT_TRADE_CLOSE = "ADMIN_SANDBOX_TRADE_CLOSE";
    private static final String AUDIT_REFUND_QUERY = "ADMIN_SANDBOX_REFUND_QUERY";
    private static final String AUDIT_REFUND_EXECUTE = "ADMIN_SANDBOX_REFUND_EXECUTE";
    private static final String STUDENT_AUDIT_TRADE_QUERY = "STUDENT_SANDBOX_TRADE_QUERY";
    private static final String STUDENT_AUDIT_TRADE_CLOSE = "STUDENT_SANDBOX_TRADE_CLOSE";
    private static final String CLOSE_IDEMPOTENCY_PREFIX = "CLOSE:";
    private static final String REFUND_IDEMPOTENCY_PREFIX = "REFUND:";
    private static final String PAYMENT_CLOSE_IN_PROGRESS_CODE = "PAY-1007";
    private static final String PAYMENT_REFUND_IN_PROGRESS_CODE = "PAY-1008";

    private final ConsultRepository consultRepository;
    private final NotificationService notificationService;
    private final MentorScheduleService mentorScheduleService;
    private final AlipaySandboxGatewayClient alipaySandboxGatewayClient;
    private final ObjectMapper objectMapper;
    private final ConsultPaymentOperationGuardService consultPaymentOperationGuardService;
    private final MentorDashboardCacheService mentorDashboardCacheService;
    private final AdminOperationsDashboardCacheService adminOperationsDashboardCacheService;

    public ConsultPaymentGatewayService(
            ConsultRepository consultRepository,
            NotificationService notificationService,
            MentorScheduleService mentorScheduleService,
            AlipaySandboxGatewayClient alipaySandboxGatewayClient,
            ObjectMapper objectMapper,
            ConsultPaymentOperationGuardService consultPaymentOperationGuardService,
            MentorDashboardCacheService mentorDashboardCacheService,
            AdminOperationsDashboardCacheService adminOperationsDashboardCacheService
    ) {
        this.consultRepository = consultRepository;
        this.notificationService = notificationService;
        this.mentorScheduleService = mentorScheduleService;
        this.alipaySandboxGatewayClient = alipaySandboxGatewayClient;
        this.objectMapper = objectMapper;
        this.consultPaymentOperationGuardService = consultPaymentOperationGuardService;
        this.mentorDashboardCacheService = mentorDashboardCacheService;
        this.adminOperationsDashboardCacheService = adminOperationsDashboardCacheService;
    }

    @Transactional
    public String handleSandboxCallback(Map<String, String> form) {
        // 支付宝回调只接受成功态和金额完全匹配的沙箱订单。
        String orderNo = firstNonBlank(form.get("orderNo"), form.get("out_trade_no"));
        String providerTradeNo = firstNonBlank(form.get("tradeNo"), form.get("trade_no"));
        String totalAmount = firstNonBlank(form.get("totalAmount"), form.get("total_amount"));
        String tradeStatus = firstNonBlank(form.get("tradeStatus"), form.get("trade_status"));
        if (orderNo == null || providerTradeNo == null || totalAmount == null || tradeStatus == null) {
            return "failure";
        }
        if (!"TRADE_SUCCESS".equalsIgnoreCase(tradeStatus) && !"SUCCESS".equalsIgnoreCase(tradeStatus)) {
            return "failure";
        }
        ConsultRepository.OrderDetailRow order = consultRepository.findOrderDetail(orderNo).orElse(null);
        if (order == null) {
            return "failure";
        }
        if (consultRepository.findPaymentRecordByIdempotencyKey(providerTradeNo).isPresent()) {
            return "success";
        }
        int callbackAmountFen = parseFen(totalAmount);
        if (callbackAmountFen <= 0 || callbackAmountFen != order.amountFen()) {
            return "failure";
        }
        ConsultPaymentOperationGuardService.GuardDecision guardDecision = consultPaymentOperationGuardService.begin(
                ConsultPaymentOperationGuardService.Operation.CALLBACK_SUCCESS,
                providerTradeNo
        );
        // guard 防止回调、手动查询和重复通知同时推进同一笔支付成功。
        if (guardDecision.shouldUseDoneState()) {
            return "success";
        }
        if (!guardDecision.shouldProceed()) {
            return consultRepository.findPaymentRecordByIdempotencyKey(providerTradeNo).isPresent() ? "success" : "failure";
        }
        try {
            applySandboxTradeSuccess(order, providerTradeNo, callbackAmountFen, form.toString());
            consultPaymentOperationGuardService.markDoneAfterCommit(
                    ConsultPaymentOperationGuardService.Operation.CALLBACK_SUCCESS,
                    providerTradeNo
            );
            return "success";
        } catch (ApiException ex) {
            consultPaymentOperationGuardService.releaseNow(
                    ConsultPaymentOperationGuardService.Operation.CALLBACK_SUCCESS,
                    providerTradeNo
            );
            return "failure";
        } catch (RuntimeException ex) {
            consultPaymentOperationGuardService.releaseNow(
                    ConsultPaymentOperationGuardService.Operation.CALLBACK_SUCCESS,
                    providerTradeNo
            );
            throw ex;
        }
    }

    @Transactional
    public AdminConsultPaymentQueryResponse querySandboxTrade(String traceId, long operatorUserId, String orderNo) {
        return querySandboxTradeInternal(traceId, operatorUserId, orderNo, AUDIT_TRADE_QUERY);
    }

    @Transactional
    public AdminConsultPaymentQueryResponse querySandboxTradeForStudent(String traceId, long operatorUserId, String orderNo) {
        return querySandboxTradeInternal(traceId, operatorUserId, orderNo, STUDENT_AUDIT_TRADE_QUERY);
    }

    private AdminConsultPaymentQueryResponse querySandboxTradeInternal(String traceId, long operatorUserId, String orderNo, String auditAction) {
        ConsultRepository.OrderDetailRow order = getSandboxOrder(orderNo);
        ConsultRepository.PaymentRecordRow latestRecord = getRequiredSandboxPaymentRecord(order);
        AlipaySandboxGatewayClient.TradeQueryResult result = alipaySandboxGatewayClient.queryTrade(orderNo, latestRecord.providerTradeNo());
        boolean syncedToPaid = false;
        if (result.success() && "TRADE_SUCCESS".equalsIgnoreCase(result.tradeStatus())) {
            // 查询到外部成功时同步本地订单，学生手动查询和后台查询走同一逻辑。
            syncedToPaid = applySandboxTradeSuccess(order, result.providerTradeNo(), result.totalAmountFen(), result.rawBody());
        }
        consultRepository.insertAuditLog(
                traceId,
                operatorUserId,
                auditAction,
                "ORDER",
                orderNo,
                toJson(Map.of(
                        "gatewayCode", safe(result.code()),
                        "gatewayMessage", safe(result.message()),
                        "subCode", safe(result.subCode()),
                        "subMessage", safe(result.subMessage()),
                        "tradeStatus", safe(result.tradeStatus()),
                        "providerTradeNo", safe(result.providerTradeNo()),
                        "syncedToPaid", syncedToPaid
                ))
        );
        ConsultRepository.OrderDetailRow refreshed = consultRepository.findOrderDetail(orderNo).orElse(order);
        return new AdminConsultPaymentQueryResponse(
                orderNo,
                refreshed.status().name(),
                PaymentMode.SANDBOX.name(),
                emptyToNull(result.providerTradeNo()),
                emptyToNull(result.tradeStatus()),
                result.code(),
                firstNonBlank(result.subMessage(), result.message()),
                syncedToPaid,
                toIso(refreshed.paidAt()),
                Instant.now().toEpochMilli()
        );
    }

    @Transactional
    public AdminConsultPaymentCloseResponse closeSandboxTrade(String traceId, long operatorUserId, String orderNo) {
        return closeSandboxTradeInternal(
                traceId,
                operatorUserId,
                orderNo,
                AUDIT_TRADE_CLOSE,
                "管理员已关闭该未支付沙箱交易，订单同步取消。",
                "某未支付咨询订单已由管理员关闭，相关预约时段已释放。"
        );
    }

    @Transactional
    public AdminConsultPaymentCloseResponse closeSandboxTradeForStudent(String traceId, long operatorUserId, String orderNo) {
        return closeSandboxTradeInternal(
                traceId,
                operatorUserId,
                orderNo,
                STUDENT_AUDIT_TRADE_CLOSE,
                "你已关闭当前未支付沙箱交易，订单已同步取消。",
                "学生已关闭当前未支付沙箱交易，相关预约时段已释放。"
        );
    }

    private AdminConsultPaymentCloseResponse closeSandboxTradeInternal(
            String traceId,
            long operatorUserId,
            String orderNo,
            String auditAction,
            String studentNotificationContent,
            String mentorNotificationContent
    ) {
        ConsultRepository.OrderDetailRow order = getSandboxOrder(orderNo);
        String closeIdempotencyKey = CLOSE_IDEMPOTENCY_PREFIX + orderNo;
        ConsultRepository.PaymentRecordRow existingCloseRecord = consultRepository.findPaymentRecordByIdempotencyKey(closeIdempotencyKey).orElse(null);
        ConsultPaymentOperationGuardService.GuardDecision guardDecision = null;
        if (existingCloseRecord == null) {
            // 关单以订单号为幂等键，重复点击不会重复写关闭流水。
            guardDecision = consultPaymentOperationGuardService.begin(
                    ConsultPaymentOperationGuardService.Operation.CLOSE_TRADE,
                    orderNo
            );
            if (guardDecision.shouldUseDoneState()) {
                existingCloseRecord = consultRepository.findPaymentRecordByIdempotencyKey(closeIdempotencyKey).orElse(null);
            }
            if (!guardDecision.shouldProceed() && existingCloseRecord == null) {
                throw new ApiException(PAYMENT_CLOSE_IN_PROGRESS_CODE, "payment close already in progress", HttpStatus.CONFLICT);
            }
        }
        boolean alreadyClosed = existingCloseRecord != null;
        if (order.paidAt() != null || ((order.status() != ConsultOrderStatus.CREATED && order.status() != ConsultOrderStatus.PAYING) && !alreadyClosed)) {
            throw new ApiException("BIZ-1001", "order not closable", HttpStatus.BAD_REQUEST);
        }
        try {
            ConsultRepository.PaymentRecordRow latestRecord = getRequiredSandboxPaymentRecord(order);
            AlipaySandboxGatewayClient.TradeCloseResult result;
            if (existingCloseRecord != null) {
                result = new AlipaySandboxGatewayClient.TradeCloseResult(
                        "10000",
                        "Success",
                        "",
                        "",
                        existingCloseRecord.providerTradeNo(),
                        orderNo,
                        existingCloseRecord.rawCallback()
                );
            } else {
                result = alipaySandboxGatewayClient.closeTrade(orderNo, latestRecord.providerTradeNo());
            }
            if (!result.success()) {
                throw new ApiException("PAY-1004", firstNonBlank(result.subMessage(), result.message(), "sandbox trade close failed"), HttpStatus.BAD_GATEWAY);
            }
            if (!alreadyClosed) {
                try {
                    // 关闭流水写入成功后，本地订单才同步取消并释放预约时段。
                    consultRepository.insertPaymentRecord(
                            order.orderId(),
                            orderNo,
                            "ALIPAY",
                            PaymentMode.SANDBOX.name(),
                            emptyToNull(result.providerTradeNo()),
                            order.amountFen(),
                            "CLOSED",
                            closeIdempotencyKey,
                            result.rawBody()
                    );
                } catch (DuplicateKeyException ex) {
                    existingCloseRecord = consultRepository.findPaymentRecordByIdempotencyKey(closeIdempotencyKey).orElse(null);
                    if (existingCloseRecord == null) {
                        throw ex;
                    }
                    alreadyClosed = true;
                    result = new AlipaySandboxGatewayClient.TradeCloseResult(
                            "10000",
                            "Success",
                            "",
                            "",
                            existingCloseRecord.providerTradeNo(),
                            orderNo,
                            existingCloseRecord.rawCallback()
                    );
                }
                consultPaymentOperationGuardService.markDoneAfterCommit(
                        ConsultPaymentOperationGuardService.Operation.CLOSE_TRADE,
                        orderNo
                );
            }
            cancelPendingOrderAsGatewayClosed(order, studentNotificationContent, mentorNotificationContent);
            consultRepository.insertAuditLog(
                    traceId,
                    operatorUserId,
                    auditAction,
                    "ORDER",
                    orderNo,
                    toJson(Map.of(
                            "gatewayCode", safe(result.code()),
                            "gatewayMessage", safe(result.message()),
                            "subCode", safe(result.subCode()),
                            "subMessage", safe(result.subMessage()),
                            "providerTradeNo", safe(result.providerTradeNo()),
                            "alreadyClosed", alreadyClosed
                    ))
            );
            ConsultRepository.OrderDetailRow refreshed = consultRepository.findOrderDetail(order.orderId(), orderNo).orElse(order);
            return new AdminConsultPaymentCloseResponse(
                    orderNo,
                    refreshed.status().name(),
                    PaymentMode.SANDBOX.name(),
                    emptyToNull(result.providerTradeNo()),
                    result.code(),
                    firstNonBlank(result.subMessage(), result.message()),
                    true,
                    Instant.now().toEpochMilli()
            );
        } catch (RuntimeException ex) {
            if (guardDecision != null && guardDecision.shouldProceed()) {
                consultPaymentOperationGuardService.releaseNow(
                        ConsultPaymentOperationGuardService.Operation.CLOSE_TRADE,
                        orderNo
                );
            }
            throw ex;
        }
    }

    @Transactional
    public AdminConsultRefundQueryResponse querySandboxRefund(String traceId, long operatorUserId, String orderNo) {
        ConsultRepository.OrderDetailRow order = getSandboxOrder(orderNo);
        ConsultRepository.PaymentRecordRow successRecord = getRequiredSuccessfulSandboxPaymentRecord(order);
        String refundRequestNo = buildRefundRequestNo(orderNo);
        AlipaySandboxGatewayClient.RefundQueryResult result = alipaySandboxGatewayClient.queryRefund(orderNo, successRecord.providerTradeNo(), refundRequestNo);
        consultRepository.insertAuditLog(
                traceId,
                operatorUserId,
                AUDIT_REFUND_QUERY,
                "ORDER",
                orderNo,
                toJson(Map.of(
                        "gatewayCode", safe(result.code()),
                        "gatewayMessage", safe(result.message()),
                        "subCode", safe(result.subCode()),
                        "subMessage", safe(result.subMessage()),
                        "refundStatus", safe(result.refundStatus()),
                        "providerTradeNo", safe(result.providerTradeNo()),
                        "refundRequestNo", refundRequestNo
                ))
        );
        return new AdminConsultRefundQueryResponse(
                orderNo,
                PaymentMode.SANDBOX.name(),
                emptyToNull(result.providerTradeNo()),
                refundRequestNo,
                result.code(),
                firstNonBlank(result.subMessage(), result.message()),
                emptyToNull(result.refundStatus()),
                result.refundAmountFen(),
                Instant.now().toEpochMilli()
        );
    }

    @Transactional(noRollbackFor = ApiException.class)
    public RefundExecutionResult refundSandboxTrade(String traceId, long operatorUserId, ConsultRepository.OrderDetailRow order, String reason) {
        if (order == null || order.paymentMode() == null || !PaymentMode.SANDBOX.name().equalsIgnoreCase(order.paymentMode())) {
            return RefundExecutionResult.notApplicable();
        }
        ConsultRepository.PaymentRecordRow successRecord = getRequiredSuccessfulSandboxPaymentRecord(order);
        String refundIdempotencyKey = REFUND_IDEMPOTENCY_PREFIX + order.orderNo();
        ConsultRepository.PaymentRecordRow existingRefundRecord = consultRepository.findPaymentRecordByIdempotencyKey(refundIdempotencyKey).orElse(null);
        ConsultPaymentOperationGuardService.GuardDecision guardDecision = null;
        if (existingRefundRecord == null) {
            // 退款按订单号幂等，售后自动退款和后台手工退款不会重复打款。
            guardDecision = consultPaymentOperationGuardService.begin(
                    ConsultPaymentOperationGuardService.Operation.REFUND,
                    order.orderNo()
            );
            if (guardDecision.shouldUseDoneState()) {
                existingRefundRecord = consultRepository.findPaymentRecordByIdempotencyKey(refundIdempotencyKey).orElse(null);
            }
            if (!guardDecision.shouldProceed() && existingRefundRecord == null) {
                throw new ApiException(PAYMENT_REFUND_IN_PROGRESS_CODE, "payment refund already in progress", HttpStatus.CONFLICT);
            }
        }
        if (existingRefundRecord != null) {
            return new RefundExecutionResult(
                    true,
                    existingRefundRecord.providerTradeNo(),
                    buildRefundRequestNo(order.orderNo()),
                    "REFUND_SUCCESS"
            );
        }
        try {
            AlipaySandboxGatewayClient.TradeRefundResult result = alipaySandboxGatewayClient.refundTrade(
                    order.orderNo(),
                    successRecord.providerTradeNo(),
                    order.amountFen(),
                    reason,
                    buildRefundRequestNo(order.orderNo())
            );
            if (!result.refundSucceeded()) {
                throw new ApiException("PAY-1005", firstNonBlank(result.subMessage(), result.message(), "sandbox refund failed"), HttpStatus.BAD_GATEWAY);
            }
            try {
                consultRepository.insertPaymentRecord(
                        order.orderId(),
                        order.orderNo(),
                        "ALIPAY",
                        PaymentMode.SANDBOX.name(),
                        emptyToNull(result.providerTradeNo()),
                        order.amountFen(),
                        "REFUND_SUCCESS",
                        refundIdempotencyKey,
                        result.rawBody()
                );
            } catch (DuplicateKeyException ex) {
                existingRefundRecord = consultRepository.findPaymentRecordByIdempotencyKey(refundIdempotencyKey).orElse(null);
                if (existingRefundRecord == null) {
                    throw ex;
                }
                return new RefundExecutionResult(
                        true,
                        existingRefundRecord.providerTradeNo(),
                        buildRefundRequestNo(order.orderNo()),
                        "REFUND_SUCCESS"
                );
            }
            consultPaymentOperationGuardService.markDoneAfterCommit(
                    ConsultPaymentOperationGuardService.Operation.REFUND,
                    order.orderNo()
            );
            consultRepository.insertAuditLog(
                    traceId,
                    operatorUserId,
                    AUDIT_REFUND_EXECUTE,
                    "ORDER",
                    order.orderNo(),
                    toJson(Map.of(
                            "gatewayCode", safe(result.code()),
                            "gatewayMessage", safe(result.message()),
                            "subCode", safe(result.subCode()),
                            "subMessage", safe(result.subMessage()),
                            "providerTradeNo", safe(result.providerTradeNo()),
                            "refundRequestNo", safe(result.outRequestNo()),
                            "refundAmountFen", result.refundAmountFen()
                    ))
            );
            return new RefundExecutionResult(true, result.providerTradeNo(), result.outRequestNo(), "REFUND_SUCCESS");
        } catch (RuntimeException ex) {
            if (guardDecision != null && guardDecision.shouldProceed()) {
                consultPaymentOperationGuardService.releaseNow(
                        ConsultPaymentOperationGuardService.Operation.REFUND,
                        order.orderNo()
                );
            }
            throw ex;
        }
    }

    @Transactional
    public boolean applySandboxTradeSuccess(ConsultRepository.OrderDetailRow order, String providerTradeNo, int amountFen, String rawPayload) {
        if (order == null) {
            throw new ApiException("BIZ-1002", "order not found", HttpStatus.NOT_FOUND);
        }
        if (providerTradeNo == null || providerTradeNo.isBlank()) {
            throw new ApiException("PAY-1006", "provider trade no missing", HttpStatus.BAD_GATEWAY);
        }
        if (consultRepository.findPaymentRecordByIdempotencyKey(providerTradeNo).isPresent()) {
            return false;
        }
        if (amountFen != order.amountFen()) {
            throw new ApiException("PAY-1006", "payment amount mismatch", HttpStatus.BAD_GATEWAY);
        }
        if (order.status() != ConsultOrderStatus.PAYING
                && order.status() != ConsultOrderStatus.PAID
                && order.status() != ConsultOrderStatus.ANSWERED
                && order.status() != ConsultOrderStatus.CLOSED) {
            throw new ApiException("BIZ-1001", "order status invalid for payment success", HttpStatus.BAD_REQUEST);
        }
        try {
            consultRepository.insertPaymentRecord(
                    order.orderId(),
                    order.orderNo(),
                    "ALIPAY",
                    PaymentMode.SANDBOX.name(),
                    providerTradeNo,
                    order.amountFen(),
                    "SUCCESS",
                    providerTradeNo,
                    rawPayload
            );
        } catch (DuplicateKeyException ex) {
            if (consultRepository.findPaymentRecordByIdempotencyKey(providerTradeNo).isPresent()) {
                return false;
            }
            throw ex;
        }
        if (order.status() == ConsultOrderStatus.PAYING) {
            // 首次支付成功才推进订单状态并通知导师，后续重复成功流水只做幂等返回。
            consultRepository.markOrderPaid(order.orderId(), order.orderNo());
            evictMentorDashboardCache(order.mentorUserId());
            evictOperationsDashboardCache();
            notificationService.createNotification(order.mentorUserId(), "CONSULT_PAID", "新的咨询订单已支付，可开始答复。", order.orderNo());
            return true;
        }
        return false;
    }

    private void cancelPendingOrderAsGatewayClosed(
            ConsultRepository.OrderDetailRow order,
            String studentNotificationContent,
            String mentorNotificationContent
    ) {
        if (!consultRepository.markOrderCanceled(order.orderId(), order.orderNo())) {
            ConsultRepository.OrderDetailRow latest = consultRepository.findOrderDetail(order.orderId(), order.orderNo()).orElse(order);
            if (latest.status() != ConsultOrderStatus.CANCELED) {
                throw new ApiException("BIZ-1001", "order cancel failed", HttpStatus.BAD_REQUEST);
            }
            return;
        }
        // 关单成功后释放预约席位，并向学生和导师分别发通知。
        evictMentorDashboardCache(order.mentorUserId());
        mentorScheduleService.releaseSlotForOrder(order.orderId(), order.orderNo());
        notificationService.createNotification(order.studentUserId(), "CONSULT_CANCELED", studentNotificationContent, order.orderNo());
        if (order.appointmentStartAt() != null || order.appointmentEndAt() != null) {
            notificationService.createNotification(order.mentorUserId(), "CONSULT_CANCELED", mentorNotificationContent, order.orderNo());
        }
    }

    private ConsultRepository.OrderDetailRow getSandboxOrder(String orderNo) {
        ConsultRepository.OrderDetailRow order = consultRepository.findOrderDetail(orderNo)
                .orElseThrow(() -> new ApiException("BIZ-1002", "order not found", HttpStatus.NOT_FOUND));
        if (order.paymentMode() == null || !PaymentMode.SANDBOX.name().equalsIgnoreCase(order.paymentMode())) {
            throw new ApiException("BIZ-1001", "sandbox payment required", HttpStatus.BAD_REQUEST);
        }
        return order;
    }

    private ConsultRepository.PaymentRecordRow getRequiredSandboxPaymentRecord(ConsultRepository.OrderDetailRow order) {
        ConsultRepository.PaymentRecordRow record = consultRepository.findLatestPaymentRecord(order.orderId(), order.orderNo())
                .orElseThrow(() -> new ApiException("BIZ-1001", "payment not initialized", HttpStatus.BAD_REQUEST));
        if (!PaymentMode.SANDBOX.name().equalsIgnoreCase(record.mode())) {
            throw new ApiException("BIZ-1001", "sandbox payment required", HttpStatus.BAD_REQUEST);
        }
        return record;
    }

    private void evictMentorDashboardCache(long mentorUserId) {
        mentorDashboardCacheService.evictNow(mentorUserId);
        mentorDashboardCacheService.evictAfterCommit(mentorUserId);
    }

    private void evictOperationsDashboardCache() {
        adminOperationsDashboardCacheService.evictAllNow();
        adminOperationsDashboardCacheService.evictAllAfterCommit();
    }

    private ConsultRepository.PaymentRecordRow getRequiredSuccessfulSandboxPaymentRecord(ConsultRepository.OrderDetailRow order) {
        ConsultRepository.PaymentRecordRow record = consultRepository.findLatestPaymentRecordByStatus(order.orderId(), order.orderNo(), "SUCCESS")
                .orElseThrow(() -> new ApiException("BIZ-1001", "sandbox payment success record not found", HttpStatus.BAD_REQUEST));
        if (!PaymentMode.SANDBOX.name().equalsIgnoreCase(record.mode())) {
            throw new ApiException("BIZ-1001", "sandbox payment required", HttpStatus.BAD_REQUEST);
        }
        return record;
    }

    public String buildRefundRequestNo(String orderNo) {
        return "REFUND-" + orderNo;
    }

    private int parseFen(String amountText) {
        try {
            return new java.math.BigDecimal(amountText)
                    .multiply(java.math.BigDecimal.valueOf(100))
                    .setScale(0, java.math.RoundingMode.HALF_UP)
                    .intValueExact();
        } catch (Exception ex) {
            return -1;
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String toJson(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private Long toIso(Instant instant) {
        return com.bishe.server.common.TimePayloads.toEpochMillis(instant);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record RefundExecutionResult(
            boolean externalTriggered,
            String providerTradeNo,
            String refundRequestNo,
            String externalStatus
    ) {
        public static RefundExecutionResult notApplicable() {
            return new RefundExecutionResult(false, null, null, null);
        }
    }
}
