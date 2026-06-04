package com.bishe.server.consult.repository;

import com.bishe.server.consult.ConsultOrderStatus;
import com.bishe.server.auth.repository.jpa.UserAccountJpaRepository;
import com.bishe.server.auth.repository.jpa.entity.UserAccountEntity;
import com.bishe.server.consult.repository.jpa.AuditLogJpaRepository;
import com.bishe.server.consult.repository.jpa.ConsultOrderJpaRepository;
import com.bishe.server.consult.repository.jpa.PaymentRecordJpaRepository;
import com.bishe.server.consult.repository.jpa.entity.AuditLogEntity;
import com.bishe.server.consult.repository.jpa.entity.ConsultOrderEntity;
import com.bishe.server.consult.repository.jpa.entity.PaymentRecordEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理员支付对账仓储，提供支付记录、对账候选单与人工处理审计读取能力。
 */
@Repository
public class AdminPaymentRepository {

    private static final List<String> MANUAL_ACTION_TYPES = List.of(
            "ADMIN_PAYMENT_MARK_PAID",
            "ADMIN_PAYMENT_CANCEL_UNPAID",
            "ADMIN_PAYMENT_CONFIRM_EXTERNAL_REFUND",
            "ADMIN_PAYMENT_MARK_REVIEWED"
    );

    private final ConsultOrderJpaRepository consultOrderJpaRepository;
    private final PaymentRecordJpaRepository paymentRecordJpaRepository;
    private final AuditLogJpaRepository auditLogJpaRepository;
    private final UserAccountJpaRepository userAccountJpaRepository;

    public AdminPaymentRepository(
            ConsultOrderJpaRepository consultOrderJpaRepository,
            PaymentRecordJpaRepository paymentRecordJpaRepository,
            AuditLogJpaRepository auditLogJpaRepository,
            UserAccountJpaRepository userAccountJpaRepository
    ) {
        this.consultOrderJpaRepository = consultOrderJpaRepository;
        this.paymentRecordJpaRepository = paymentRecordJpaRepository;
        this.auditLogJpaRepository = auditLogJpaRepository;
        this.userAccountJpaRepository = userAccountJpaRepository;
    }

    public List<PaymentReconciliationOrderRow> findOrders(String keyword, ConsultOrderStatus status) {
        List<ConsultOrderEntity> orders = loadOrders(status);
        if (orders.isEmpty()) {
            return List.of();
        }
        Map<Long, UserAccountEntity> userAccountMap = loadUserAccountMap(collectUserIds(orders));
        List<String> orderNos = collectOrderNos(orders);
        Map<String, List<PaymentRecordEntity>> paymentRecordsByOrderNo = paymentRecordJpaRepository.findByOrderNoInOrderByOrderNoAscIdDesc(orderNos)
                .stream()
                .collect(Collectors.groupingBy(PaymentRecordEntity::getOrderNo, Collectors.toList()));
        Map<String, AuditLogEntity> latestManualAuditByOrderNo = loadLatestManualAuditByOrderNo(orderNos);

        List<PaymentReconciliationOrderRow> rows = orders.stream()
                .map(order -> toRow(
                        order,
                        userAccountMap.get(order.getStudentUserId()),
                        userAccountMap.get(order.getMentorUserId()),
                        paymentRecordsByOrderNo.getOrDefault(order.getOrderNo(), List.of()),
                        latestManualAuditByOrderNo.get(order.getOrderNo())
                ))
                .toList();
        if (keyword == null || keyword.isBlank()) {
            return rows;
        }
        String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
        return rows.stream()
                .filter(row -> contains(row.orderNo(), normalizedKeyword)
                        || contains(row.studentDisplayName(), normalizedKeyword)
                        || contains(row.mentorDisplayName(), normalizedKeyword))
                .toList();
    }

    public List<PaymentRecordEventRow> findPaymentRecords(String orderNo) {
        return paymentRecordJpaRepository.findByOrderNoOrderByIdDesc(orderNo)
                .stream()
                .map(this::toPaymentRecordEventRow)
                .toList();
    }

    public Optional<ManualAuditRow> findLatestManualAudit(String orderNo) {
        return auditLogJpaRepository.findFirstByTargetTypeAndTargetIdAndActionTypeInOrderByIdDesc("ORDER", orderNo, MANUAL_ACTION_TYPES)
                .map(this::toManualAuditRow);
    }

    private List<ConsultOrderEntity> loadOrders(ConsultOrderStatus status) {
        if (status == null) {
            return consultOrderJpaRepository.findAllByOrderByCreatedAtDescIdDesc();
        }
        return consultOrderJpaRepository.findAllByStatusOrderByCreatedAtDescIdDesc(status.name());
    }

    private Map<Long, UserAccountEntity> loadUserAccountMap(Collection<Long> userIds) {
        return userAccountJpaRepository.findAllById(userIds)
                .stream()
                .collect(Collectors.toMap(UserAccountEntity::getId, Function.identity(), (left, right) -> right));
    }

    private Map<String, AuditLogEntity> loadLatestManualAuditByOrderNo(List<String> orderNos) {
        Map<String, AuditLogEntity> auditMap = new java.util.LinkedHashMap<>();
        for (AuditLogEntity entity : auditLogJpaRepository.findByTargetTypeAndTargetIdInAndActionTypeInOrderByTargetIdAscIdDesc(
                "ORDER",
                orderNos,
                MANUAL_ACTION_TYPES
        )) {
            auditMap.putIfAbsent(entity.getTargetId(), entity);
        }
        return auditMap;
    }

    private Collection<Long> collectUserIds(List<ConsultOrderEntity> orders) {
        return orders.stream()
                .flatMap(order -> java.util.stream.Stream.of(order.getStudentUserId(), order.getMentorUserId()))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<String> collectOrderNos(List<ConsultOrderEntity> orders) {
        return orders.stream()
                .map(ConsultOrderEntity::getOrderNo)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private PaymentReconciliationOrderRow toRow(
            ConsultOrderEntity order,
            UserAccountEntity studentUser,
            UserAccountEntity mentorUser,
            List<PaymentRecordEntity> paymentRecords,
            AuditLogEntity latestManualAudit
    ) {
        PaymentRecordEntity latestPayment = paymentRecords.isEmpty() ? null : paymentRecords.get(0);
        boolean hasSuccessPayment = paymentRecords.stream()
                .anyMatch(record -> "SUCCESS".equals(record.getStatus()));
        return new PaymentReconciliationOrderRow(
                order.getOrderNo(),
                order.getStudentUserId() == null ? 0L : order.getStudentUserId(),
                resolveDisplayName(studentUser),
                order.getMentorUserId() == null ? 0L : order.getMentorUserId(),
                resolveDisplayName(mentorUser),
                order.getAmountFen() == null ? 0 : order.getAmountFen(),
                ConsultOrderStatus.parse(order.getStatus()),
                order.getQuestionText(),
                order.getAppointmentStartAt(),
                order.getAppointmentEndAt(),
                order.getCreatedAt(),
                order.getPaidAt(),
                order.getClosedAt(),
                latestPayment == null ? null : latestPayment.getChannel(),
                latestPayment == null ? null : latestPayment.getMode(),
                latestPayment == null ? null : latestPayment.getStatus(),
                latestPayment == null ? null : latestPayment.getProviderTradeNo(),
                latestPayment == null ? null : latestPayment.getAmountFen(),
                latestPayment == null ? null : latestPayment.getCreatedAt(),
                hasSuccessPayment,
                latestManualAudit == null ? null : latestManualAudit.getActionType(),
                latestManualAudit == null ? null : latestManualAudit.getOperatorUserId(),
                latestManualAudit == null ? null : latestManualAudit.getDetailJson(),
                latestManualAudit == null ? null : latestManualAudit.getCreatedAt()
        );
    }

    private PaymentRecordEventRow toPaymentRecordEventRow(PaymentRecordEntity entity) {
        return new PaymentRecordEventRow(
                entity.getChannel(),
                entity.getMode(),
                entity.getStatus(),
                entity.getProviderTradeNo(),
                entity.getAmountFen() == null ? 0 : entity.getAmountFen(),
                entity.getIdempotencyKey(),
                entity.getRawCallback(),
                entity.getCreatedAt()
        );
    }

    private ManualAuditRow toManualAuditRow(AuditLogEntity entity) {
        return new ManualAuditRow(
                entity.getActionType(),
                entity.getOperatorUserId() == null ? 0L : entity.getOperatorUserId(),
                entity.getDetailJson(),
                entity.getCreatedAt()
        );
    }

    private String resolveDisplayName(UserAccountEntity user) {
        if (user == null || user.getDisplayName() == null) {
            return "";
        }
        return user.getDisplayName();
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    public record PaymentReconciliationOrderRow(
            String orderNo,
            long studentUserId,
            String studentDisplayName,
            long mentorUserId,
            String mentorDisplayName,
            int amountFen,
            ConsultOrderStatus orderStatus,
            String questionText,
            Instant appointmentStartAt,
            Instant appointmentEndAt,
            Instant createdAt,
            Instant paidAt,
            Instant closedAt,
            String latestPaymentChannel,
            String latestPaymentMode,
            String latestPaymentStatus,
            String latestProviderTradeNo,
            Integer latestPaymentAmountFen,
            Instant latestPaymentCreatedAt,
            boolean hasSuccessPayment,
            String latestManualActionType,
            Long latestManualOperatorUserId,
            String latestManualDetailJson,
            Instant latestManualCreatedAt
    ) {
    }

    public record PaymentRecordEventRow(
            String channel,
            String mode,
            String status,
            String providerTradeNo,
            int amountFen,
            String idempotencyKey,
            String rawCallback,
            Instant createdAt
    ) {
    }

    public record ManualAuditRow(
            String actionType,
            long operatorUserId,
            String detailJson,
            Instant createdAt
    ) {
    }
}
