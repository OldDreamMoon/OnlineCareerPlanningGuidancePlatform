package com.bishe.server.consult.repository;

import com.bishe.server.auth.repository.jpa.UserAccountJpaRepository;
import com.bishe.server.auth.repository.jpa.entity.UserAccountEntity;
import com.bishe.server.consult.ConsultOrderStatus;
import com.bishe.server.consult.repository.jpa.AuditLogJpaRepository;
import com.bishe.server.consult.repository.jpa.ConsultAfterSalesRequestJpaRepository;
import com.bishe.server.consult.repository.jpa.ConsultMessageJpaRepository;
import com.bishe.server.consult.repository.jpa.ConsultOrderAttachmentJpaRepository;
import com.bishe.server.consult.repository.jpa.ConsultOrderJpaRepository;
import com.bishe.server.consult.repository.jpa.PaymentRecordJpaRepository;
import com.bishe.server.consult.repository.jpa.entity.ConsultOrderEntity;
import com.bishe.server.consult.repository.jpa.entity.ConsultAfterSalesRequestEntity;
import com.bishe.server.consult.repository.jpa.entity.AuditLogEntity;
import com.bishe.server.consult.repository.jpa.entity.ConsultMessageEntity;
import com.bishe.server.consult.repository.jpa.entity.ConsultOrderAttachmentEntity;
import com.bishe.server.consult.repository.jpa.entity.PaymentRecordEntity;
import com.bishe.server.mentor.repository.jpa.MentorProfileJpaRepository;
import com.bishe.server.mentor.repository.jpa.entity.MentorProfileEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 咨询与支付仓储，负责订单、消息、支付记录与评价持久化。
 */
@Repository
public class ConsultRepository {

    private static final List<String> UNPAID_ORDER_STATUSES = List.of("CREATED", "PAYING");
    private final ConsultOrderJpaRepository consultOrderJpaRepository;
    private final ConsultMessageJpaRepository consultMessageJpaRepository;
    private final ConsultOrderAttachmentJpaRepository consultOrderAttachmentJpaRepository;
    private final PaymentRecordJpaRepository paymentRecordJpaRepository;
    private final ConsultAfterSalesRequestJpaRepository consultAfterSalesRequestJpaRepository;
    private final AuditLogJpaRepository auditLogJpaRepository;
    private final MentorProfileJpaRepository mentorProfileJpaRepository;
    private final UserAccountJpaRepository userAccountJpaRepository;

    public ConsultRepository(
            ConsultOrderJpaRepository consultOrderJpaRepository,
            ConsultMessageJpaRepository consultMessageJpaRepository,
            ConsultOrderAttachmentJpaRepository consultOrderAttachmentJpaRepository,
            PaymentRecordJpaRepository paymentRecordJpaRepository,
            ConsultAfterSalesRequestJpaRepository consultAfterSalesRequestJpaRepository,
            AuditLogJpaRepository auditLogJpaRepository,
            MentorProfileJpaRepository mentorProfileJpaRepository,
            UserAccountJpaRepository userAccountJpaRepository
    ) {
        this.consultOrderJpaRepository = consultOrderJpaRepository;
        this.consultMessageJpaRepository = consultMessageJpaRepository;
        this.consultOrderAttachmentJpaRepository = consultOrderAttachmentJpaRepository;
        this.paymentRecordJpaRepository = paymentRecordJpaRepository;
        this.consultAfterSalesRequestJpaRepository = consultAfterSalesRequestJpaRepository;
        this.auditLogJpaRepository = auditLogJpaRepository;
        this.mentorProfileJpaRepository = mentorProfileJpaRepository;
        this.userAccountJpaRepository = userAccountJpaRepository;
    }

    public long createOrder(CreateOrderCommand command) {
        ConsultOrderEntity entity = consultOrderJpaRepository.saveAndFlush(ConsultOrderEntity.create(
                command.orderNo(),
                command.studentUserId(),
                command.mentorUserId(),
                command.sceneCode(),
                command.sourcePage(),
                command.amountFen(),
                command.questionText(),
                command.questionPayloadJson(),
                command.problemSummary(),
                command.coreQuestionsJson(),
                command.expectedOutcomesJson(),
                command.selectedMaterialTypes(),
                command.prepSheetSnapshotJson(),
                command.servicePackageSnapshotJson(),
                command.appointmentStartAt(),
                command.appointmentEndAt()
        ));
        return requireGeneratedId(entity.getId(), "consult order");
    }

    public long createMessage(long orderId, String orderNo, long senderUserId, String senderRole, String messageText) {
        ConsultMessageEntity entity = consultMessageJpaRepository.saveAndFlush(
                ConsultMessageEntity.create(orderId, orderNo, senderUserId, senderRole, messageText)
        );
        return requireGeneratedId(entity.getId(), "consult message");
    }

    public long createMessage(String orderNo, long senderUserId, String senderRole, String messageText) {
        return createMessage(requireOrderIdByOrderNo(orderNo), orderNo, senderUserId, senderRole, messageText);
    }

    public List<OrderListRow> findOrdersForStudent(long studentUserId, ConsultOrderStatus status, int page, int size) {
        return findOrdersByParticipant(true, studentUserId, status, page, size);
    }

    public List<OrderListRow> findOrdersForMentor(long mentorUserId, ConsultOrderStatus status, int page, int size) {
        return findOrdersByParticipant(false, mentorUserId, status, page, size);
    }

    public long countOrdersForStudent(long studentUserId, ConsultOrderStatus status) {
        return countOrdersByParticipant(true, studentUserId, status);
    }

    public long countOrdersForMentor(long mentorUserId, ConsultOrderStatus status) {
        return countOrdersByParticipant(false, mentorUserId, status);
    }

    public MentorWorkbenchSummaryRow summarizeMentorWorkbench(long mentorUserId, int replyTimeoutHours, int expiringWindowHours) {
        Set<String> afterSalesOrderNos = findAfterSalesOrderNosForMentor(mentorUserId);
        Instant expiringPaidAtThreshold = resolveExpiringPaidAtThreshold(replyTimeoutHours, expiringWindowHours);
        long pendingReplyCount = consultOrderJpaRepository.count(
                hasMentorUserId(mentorUserId).and(hasStatus(ConsultOrderStatus.PAID))
        );
        long expiringSoonCount = consultOrderJpaRepository.count(
                hasMentorUserId(mentorUserId)
                        .and(hasStatus(ConsultOrderStatus.PAID))
                        .and(paidAtOnOrBefore(expiringPaidAtThreshold))
        );
        long waitingConfirmationCount = consultOrderJpaRepository.count(
                hasMentorUserId(mentorUserId).and(hasStatus(ConsultOrderStatus.ANSWERED))
        );
        long afterSalesImpactCount = consultOrderJpaRepository.count(
                hasMentorUserId(mentorUserId).and(hasRefundedOrAfterSales(afterSalesOrderNos))
        );
        return new MentorWorkbenchSummaryRow(
                pendingReplyCount,
                expiringSoonCount,
                waitingConfirmationCount,
                afterSalesImpactCount
        );
    }

    public List<String> findMentorWorkbenchPaymentModes(long mentorUserId) {
        return paymentRecordJpaRepository.findDistinctLatestModesByMentorUserId(mentorUserId);
    }

    public long countMentorWorkbenchOrders(
            long mentorUserId,
            MentorWorkbenchQuery query,
            int replyTimeoutHours,
            int expiringWindowHours
    ) {
        MentorWorkbenchFilterContext context = buildMentorWorkbenchFilterContext(mentorUserId, query, false);
        return consultOrderJpaRepository.count(
                buildMentorWorkbenchSpecification(mentorUserId, query, replyTimeoutHours, expiringWindowHours, context, false)
        );
    }

    public List<MentorWorkbenchOrderRow> findMentorWorkbenchOrders(
            long mentorUserId,
            MentorWorkbenchQuery query,
            int replyTimeoutHours,
            int expiringWindowHours
    ) {
        MentorWorkbenchFilterContext context = buildMentorWorkbenchFilterContext(mentorUserId, query, true);
        Page<ConsultOrderEntity> pageResult = consultOrderJpaRepository.findAll(
                buildMentorWorkbenchSpecification(mentorUserId, query, replyTimeoutHours, expiringWindowHours, context, true),
                PageRequest.of(Math.max(query.page() - 1, 0), Math.max(query.size(), 1))
        );
        List<ConsultOrderEntity> orders = pageResult.getContent();
        if (orders.isEmpty()) {
            return List.of();
        }
        Map<Long, String> displayNameByUserId = findDisplayNameByUserId(
                orders.stream().map(ConsultOrderEntity::getStudentUserId).toList()
        );
        Map<String, String> latestPaymentModeByOrderNo = findLatestPaymentModeByOrderNo(
                orders.stream().map(ConsultOrderEntity::getOrderNo).toList()
        );
        Map<String, AfterSalesAggregate> afterSalesAggregateByOrderNo = loadAfterSalesAggregateByOrderNo(
                orders.stream().map(ConsultOrderEntity::getOrderNo).toList()
        );
        return orders.stream()
                .map(order -> toMentorWorkbenchOrderRow(
                        order,
                        displayNameByUserId.get(order.getStudentUserId()),
                        latestPaymentModeByOrderNo.get(order.getOrderNo()),
                        afterSalesAggregateByOrderNo.get(order.getOrderNo()),
                        replyTimeoutHours
                ))
                .toList();
    }

    public List<AdminOrderListRow> findAdminOrders(String keyword, ConsultOrderStatus status, int page, int size) {
        Page<ConsultOrderEntity> pageResult = consultOrderJpaRepository.findAll(
                buildAdminOrderSpecification(keyword, status),
                PageRequest.of(
                        Math.max(page - 1, 0),
                        Math.max(size, 1),
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
                )
        );
        List<ConsultOrderEntity> orders = pageResult.getContent();
        if (orders.isEmpty()) {
            return List.of();
        }
        Map<Long, String> displayNameByUserId = findDisplayNameByUserId(
                orders.stream()
                        .flatMap(order -> java.util.stream.Stream.of(order.getStudentUserId(), order.getMentorUserId()))
                        .toList()
        );
        Map<String, String> latestPaymentModeByOrderNo = findLatestPaymentModeByOrderNo(
                orders.stream().map(ConsultOrderEntity::getOrderNo).toList()
        );
        return orders.stream()
                .map(order -> toAdminOrderListRow(order, displayNameByUserId, latestPaymentModeByOrderNo.get(order.getOrderNo())))
                .toList();
    }

    public long countAdminOrders(String keyword, ConsultOrderStatus status) {
        return consultOrderJpaRepository.count(buildAdminOrderSpecification(keyword, status));
    }

    public Optional<OrderDetailRow> findOrderDetail(Long orderId, String orderNo) {
        Optional<ConsultOrderEntity> order = findResolvedOrder(orderId, orderNo);
        if (order.isEmpty()) {
            return Optional.empty();
        }
        Map<Long, String> displayNameByUserId = findDisplayNameByUserId(List.of(
                order.get().getStudentUserId(),
                order.get().getMentorUserId()
        ));
        return Optional.of(toOrderDetailRow(
                order.get(),
                displayNameByUserId,
                paymentRecordJpaRepository.findResolvedOrderByIdDesc(
                        requireGeneratedId(order.get().getId(), "consult order"),
                        order.get().getOrderNo()
                ).stream().findFirst().orElse(null)
        ));
    }

    public Optional<OrderDetailRow> findOrderDetail(String orderNo) {
        return findOrderDetail(null, orderNo);
    }

    public List<MessageRow> findMessages(Long orderId, String orderNo) {
        List<ConsultMessageEntity> messages = consultMessageJpaRepository.findByResolvedOrderOrderByCreatedAtAscIdAsc(orderId, orderNo);
        if (messages.isEmpty()) {
            return List.of();
        }
        List<Long> senderUserIds = messages.stream()
                .map(ConsultMessageEntity::getSenderUserId)
                .distinct()
                .toList();
        Map<Long, String> displayNameByUserId = new HashMap<>();
        for (UserAccountEntity user : userAccountJpaRepository.findAllById(senderUserIds)) {
            displayNameByUserId.put(user.getId(), user.getDisplayName());
        }
        return messages.stream()
                .map(message -> toMessageRow(message, displayNameByUserId.get(message.getSenderUserId())))
                .toList();
    }

    public List<MessageRow> findMessages(String orderNo) {
        return findMessages(optionalOrderIdByOrderNo(orderNo), orderNo);
    }

    public Optional<OrderAttachmentRow> findAttachmentById(Long orderId, String orderNo, long attachmentId) {
        return consultOrderAttachmentJpaRepository.findByResolvedOrderAndId(orderId, orderNo, attachmentId)
                .map(this::toOrderAttachmentRow);
    }

    public Optional<OrderAttachmentRow> findAttachmentById(String orderNo, long attachmentId) {
        return findAttachmentById(optionalOrderIdByOrderNo(orderNo), orderNo, attachmentId);
    }

    public Optional<OrderAttachmentRow> findCurrentAttachmentBySlot(Long orderId, String orderNo, String slotCode) {
        return consultOrderAttachmentJpaRepository
                .findResolvedBySlotAndLifecycleStatus(orderId, orderNo, slotCode, "CURRENT")
                .stream()
                .findFirst()
                .map(this::toOrderAttachmentRow);
    }

    public Optional<OrderAttachmentRow> findCurrentAttachmentBySlot(String orderNo, String slotCode) {
        return findCurrentAttachmentBySlot(optionalOrderIdByOrderNo(orderNo), orderNo, slotCode);
    }

    public long createAttachment(long orderId, CreateAttachmentCommand command) {
        ConsultOrderAttachmentEntity entity = consultOrderAttachmentJpaRepository.saveAndFlush(
                ConsultOrderAttachmentEntity.create(
                        orderId,
                        command.orderNo(),
                        command.uploadedByUserId(),
                        command.attachmentType(),
                        command.slotCode(),
                        command.sourceStage(),
                        command.originalFilename(),
                        command.contentType(),
                        command.sizeBytes(),
                        command.storageBucket(),
                        command.objectKey(),
                        command.description(),
                        command.lifecycleStatus(),
                        command.replacedAttachmentId()
                )
        );
        return requireGeneratedId(entity.getId(), "consult attachment");
    }

    public long createAttachment(CreateAttachmentCommand command) {
        return createAttachment(requireOrderIdByOrderNo(command.orderNo()), command);
    }

    public List<OrderAttachmentRow> findAttachments(Long orderId, String orderNo, boolean currentOnly, boolean includeSuperseded) {
        List<ConsultOrderAttachmentEntity> attachments;
        if (currentOnly) {
            attachments = consultOrderAttachmentJpaRepository.findResolvedOrderAndLifecycleStatusOrderByCreatedAtDescIdDesc(
                    orderId,
                    orderNo,
                    "CURRENT"
            );
        } else if (!includeSuperseded) {
            attachments = consultOrderAttachmentJpaRepository.findResolvedOrderAndLifecycleStatusNotOrderByCreatedAtDescIdDesc(
                    orderId,
                    orderNo,
                    "SUPERSEDED"
            );
        } else {
            attachments = consultOrderAttachmentJpaRepository.findResolvedOrderByCreatedAtDescIdDesc(orderId, orderNo);
        }
        return attachments.stream()
                .map(this::toOrderAttachmentRow)
                .toList();
    }

    public List<OrderAttachmentRow> findAttachments(String orderNo, boolean currentOnly, boolean includeSuperseded) {
        return findAttachments(optionalOrderIdByOrderNo(orderNo), orderNo, currentOnly, includeSuperseded);
    }

    public int countCurrentAttachments(Long orderId, String orderNo) {
        return Math.toIntExact(consultOrderAttachmentJpaRepository.countByResolvedOrderAndLifecycleStatus(orderId, orderNo, "CURRENT"));
    }

    public int countCurrentAttachments(String orderNo) {
        return countCurrentAttachments(optionalOrderIdByOrderNo(orderNo), orderNo);
    }

    public boolean markAttachmentSuperseded(Long orderId, String orderNo, long attachmentId) {
        Optional<ConsultOrderAttachmentEntity> attachment = consultOrderAttachmentJpaRepository.findByResolvedOrderAndId(orderId, orderNo, attachmentId);
        if (attachment.isEmpty()) {
            return false;
        }
        if (!attachment.get().markSuperseded()) {
            return false;
        }
        consultOrderAttachmentJpaRepository.saveAndFlush(attachment.get());
        return true;
    }

    public boolean markAttachmentSuperseded(String orderNo, long attachmentId) {
        return markAttachmentSuperseded(optionalOrderIdByOrderNo(orderNo), orderNo, attachmentId);
    }

    public boolean markAttachmentDeleted(Long orderId, String orderNo, long attachmentId) {
        Optional<ConsultOrderAttachmentEntity> attachment = consultOrderAttachmentJpaRepository.findByResolvedOrderAndId(orderId, orderNo, attachmentId);
        if (attachment.isEmpty()) {
            return false;
        }
        if (!attachment.get().markDeleted()) {
            return false;
        }
        consultOrderAttachmentJpaRepository.saveAndFlush(attachment.get());
        return true;
    }

    public boolean markAttachmentDeleted(String orderNo, long attachmentId) {
        return markAttachmentDeleted(optionalOrderIdByOrderNo(orderNo), orderNo, attachmentId);
    }

    public void markOrderPaying(Long orderId, String orderNo) {
        updateOrder(orderId, orderNo, ConsultOrderEntity::markPaying);
    }

    public void markOrderPaying(String orderNo) {
        markOrderPaying(null, orderNo);
    }

    public void markOrderPaid(Long orderId, String orderNo) {
        updateOrder(orderId, orderNo, ConsultOrderEntity::markPaid);
    }

    public void markOrderPaid(String orderNo) {
        markOrderPaid(null, orderNo);
    }

    public void markOrderAnswered(Long orderId, String orderNo) {
        updateOrder(orderId, orderNo, ConsultOrderEntity::markAnswered);
    }

    public void markOrderAnswered(String orderNo) {
        markOrderAnswered(null, orderNo);
    }

    public boolean markOrderFailed(Long orderId, String orderNo) {
        return updateOrder(orderId, orderNo, ConsultOrderEntity::markFailed);
    }

    public boolean markOrderFailed(String orderNo) {
        return markOrderFailed(null, orderNo);
    }

    public void markOrderClosed(Long orderId, String orderNo) {
        updateOrder(orderId, orderNo, ConsultOrderEntity::markClosed);
    }

    public void markOrderClosed(String orderNo) {
        markOrderClosed(null, orderNo);
    }

    public boolean markOrderCanceled(Long orderId, String orderNo) {
        return updateOrder(orderId, orderNo, ConsultOrderEntity::markCanceled);
    }

    public boolean markOrderCanceled(String orderNo) {
        return markOrderCanceled(null, orderNo);
    }

    public boolean markOrderRefunded(Long orderId, String orderNo) {
        return updateOrder(orderId, orderNo, ConsultOrderEntity::markRefunded);
    }

    public boolean markOrderRefunded(String orderNo) {
        return markOrderRefunded(null, orderNo);
    }

    public List<String> findTimedOutUnpaidOrderNos(Instant cutoff) {
        return consultOrderJpaRepository
                .findByPaidAtIsNullAndStatusInAndCreatedAtLessThanEqualOrderByCreatedAtAscIdAsc(
                        UNPAID_ORDER_STATUSES,
                        cutoff
                )
                .stream()
                .map(ConsultOrderEntity::getOrderNo)
                .toList();
    }

    public List<String> findTimedOutPaidOrderNos(Instant cutoff) {
        return consultOrderJpaRepository
                .findByStatusAndPaidAtIsNotNullAndPaidAtLessThanEqualOrderByPaidAtAscIdAsc("PAID", cutoff)
                .stream()
                .map(ConsultOrderEntity::getOrderNo)
                .toList();
    }

    public void insertPaymentRecord(
            long orderId,
            String orderNo,
            String channel,
            String mode,
            String providerTradeNo,
            int amountFen,
            String status,
            String idempotencyKey,
            String rawCallback
    ) {
        paymentRecordJpaRepository.saveAndFlush(PaymentRecordEntity.create(
                orderId,
                orderNo,
                channel,
                mode,
                providerTradeNo,
                amountFen,
                status,
                idempotencyKey,
                rawCallback
        ));
    }

    public void insertPaymentRecord(
            String orderNo,
            String channel,
            String mode,
            String providerTradeNo,
            int amountFen,
            String status,
            String idempotencyKey,
            String rawCallback
    ) {
        insertPaymentRecord(
                requireOrderIdByOrderNo(orderNo),
                orderNo,
                channel,
                mode,
                providerTradeNo,
                amountFen,
                status,
                idempotencyKey,
                rawCallback
        );
    }

    public Optional<PaymentRecordRow> findLatestPaymentRecord(Long orderId, String orderNo) {
        return paymentRecordJpaRepository.findResolvedOrderByIdDesc(orderId, orderNo).stream()
                .findFirst()
                .map(this::toPaymentRecordRow);
    }

    public Optional<PaymentRecordRow> findLatestPaymentRecord(String orderNo) {
        return findLatestPaymentRecord(optionalOrderIdByOrderNo(orderNo), orderNo);
    }

    public Optional<PaymentRecordRow> findPaymentRecordByIdempotencyKey(String idempotencyKey) {
        return paymentRecordJpaRepository.findFirstByIdempotencyKey(idempotencyKey)
                .map(this::toPaymentRecordRow);
    }

    public Optional<PaymentRecordRow> findLatestPaymentRecordByStatus(Long orderId, String orderNo, String status) {
        return paymentRecordJpaRepository.findResolvedOrderAndStatusByIdDesc(orderId, orderNo, status).stream()
                .findFirst()
                .map(this::toPaymentRecordRow);
    }

    public Optional<PaymentRecordRow> findLatestPaymentRecordByStatus(String orderNo, String status) {
        return findLatestPaymentRecordByStatus(optionalOrderIdByOrderNo(orderNo), orderNo, status);
    }

    public Optional<BigDecimal> findMentorAverageRating(long mentorUserId) {
        return mentorProfileJpaRepository.findByUserId(mentorUserId)
                .map(MentorProfileEntity::getAvgRating);
    }

    public int sumPaidRevenueForMentor(long mentorUserId) {
        Long sum = consultOrderJpaRepository.sumPaidRevenueFenByMentorUserId(mentorUserId);
        return sum == null ? 0 : Math.toIntExact(sum);
    }

    public List<OrderListRow> findRecentOrdersForMentor(long mentorUserId, int limit) {
        return findOrdersByParticipant(false, mentorUserId, null, 1, Math.max(limit, 1));
    }

    public void createReview(Long orderId, String orderNo, int rating, String comment) {
        boolean updated = updateOrder(orderId, orderNo, order -> order.createReview(rating, comment));
        if (!updated) {
            throw new IllegalStateException("failed to persist consult review");
        }
    }

    public void createReview(String orderNo, int rating, String comment) {
        createReview(null, orderNo, rating, comment);
    }

    public boolean deleteReview(Long orderId, String orderNo) {
        return updateOrder(orderId, orderNo, ConsultOrderEntity::deleteReview);
    }

    public boolean deleteReview(String orderNo) {
        return deleteReview(null, orderNo);
    }

    public Optional<AuditLogRow> findLatestAuditLog(String actionType, String targetType, String targetId) {
        return auditLogJpaRepository.findFirstByActionTypeAndTargetTypeAndTargetIdOrderByIdDesc(
                        actionType,
                        targetType,
                        targetId
                )
                .map(this::toAuditLogRow);
    }

    public void insertAuditLog(String traceId, long operatorUserId, String actionType, String targetType, String targetId, String detailJson) {
        auditLogJpaRepository.saveAndFlush(AuditLogEntity.create(
                traceId,
                operatorUserId,
                actionType,
                targetType,
                targetId,
                detailJson
        ));
    }

    private List<OrderListRow> findOrdersByParticipant(boolean studentSide, long userId, ConsultOrderStatus status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(Math.max(page - 1, 0), Math.max(size, 1));
        List<ConsultOrderEntity> orders = studentSide
                ? findStudentOrders(userId, status, pageRequest)
                : findMentorOrders(userId, status, pageRequest);
        if (orders.isEmpty()) {
            return List.of();
        }

        List<Long> counterpartUserIds = orders.stream()
                .map(order -> studentSide ? order.getMentorUserId() : order.getStudentUserId())
                .distinct()
                .toList();
        Map<Long, String> displayNameByUserId = findDisplayNameByUserId(counterpartUserIds);
        Map<String, String> latestPaymentModeByOrderNo = findLatestPaymentModeByOrderNo(
                orders.stream().map(ConsultOrderEntity::getOrderNo).toList()
        );

        return orders.stream()
                .map(order -> {
                    long counterpartUserId = studentSide ? order.getMentorUserId() : order.getStudentUserId();
                    return toOrderListRow(
                            order,
                            counterpartUserId,
                            displayNameByUserId.get(counterpartUserId),
                            latestPaymentModeByOrderNo.get(order.getOrderNo())
                    );
                })
                .toList();
    }

    private long countOrdersByParticipant(boolean studentSide, long userId, ConsultOrderStatus status) {
        if (studentSide) {
            return status == null
                    ? consultOrderJpaRepository.countByStudentUserId(userId)
                    : consultOrderJpaRepository.countByStudentUserIdAndStatus(userId, status.name());
        }
        return status == null
                ? consultOrderJpaRepository.countByMentorUserId(userId)
                : consultOrderJpaRepository.countByMentorUserIdAndStatus(userId, status.name());
    }

    private List<ConsultOrderEntity> findStudentOrders(long studentUserId, ConsultOrderStatus status, PageRequest pageRequest) {
        return status == null
                ? consultOrderJpaRepository.findByStudentUserIdOrderByCreatedAtDescIdDesc(studentUserId, pageRequest)
                : consultOrderJpaRepository.findByStudentUserIdAndStatusOrderByCreatedAtDescIdDesc(
                studentUserId,
                status.name(),
                pageRequest
        );
    }

    private List<ConsultOrderEntity> findMentorOrders(long mentorUserId, ConsultOrderStatus status, PageRequest pageRequest) {
        return status == null
                ? consultOrderJpaRepository.findByMentorUserIdOrderByCreatedAtDescIdDesc(mentorUserId, pageRequest)
                : consultOrderJpaRepository.findByMentorUserIdAndStatusOrderByCreatedAtDescIdDesc(
                mentorUserId,
                status.name(),
                pageRequest
        );
    }

    private Map<Long, String> findDisplayNameByUserId(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> displayNameByUserId = new HashMap<>();
        for (UserAccountEntity user : userAccountJpaRepository.findAllById(userIds)) {
            displayNameByUserId.put(user.getId(), user.getDisplayName());
        }
        return displayNameByUserId;
    }

    private Map<String, String> findLatestPaymentModeByOrderNo(Collection<String> orderNos) {
        if (orderNos.isEmpty()) {
            return Map.of();
        }
        Map<String, String> latestPaymentModeByOrderNo = new LinkedHashMap<>();
        for (PaymentRecordEntity paymentRecord : paymentRecordJpaRepository.findByOrderNoInOrderByOrderNoAscIdDesc(orderNos)) {
            if (paymentRecord.getOrderNo() == null || latestPaymentModeByOrderNo.containsKey(paymentRecord.getOrderNo())) {
                continue;
            }
            latestPaymentModeByOrderNo.put(paymentRecord.getOrderNo(), paymentRecord.getMode());
        }
        return latestPaymentModeByOrderNo;
    }

    private MentorWorkbenchFilterContext buildMentorWorkbenchFilterContext(
            long mentorUserId,
            MentorWorkbenchQuery query,
            boolean includeSortSignals
    ) {
        String normalizedKeyword = normalizeKeyword(query.keyword());
        Set<Long> matchedStudentUserIds = normalizedKeyword == null
                ? Set.of()
                : new LinkedHashSet<>(userAccountJpaRepository.findIdsByDisplayNameContainingIgnoreCaseAndDeletedFalse(normalizedKeyword));
        boolean needMentorOrderNos = query.paymentMode() != null
                || "AFTER_SALES".equals(query.riskFilter())
                || (includeSortSignals && "PRIORITY".equals(query.sortMode()));
        Set<String> mentorOrderNos = needMentorOrderNos ? findMentorOrderNos(mentorUserId) : Set.of();
        Set<String> paymentMatchedOrderNos = query.paymentMode() == null
                ? Set.of()
                : resolvePaymentModeMatchedOrderNos(query.paymentMode(), mentorOrderNos);
        Set<String> afterSalesOrderNos = "AFTER_SALES".equals(query.riskFilter()) || (includeSortSignals && "PRIORITY".equals(query.sortMode()))
                ? findAfterSalesOrderNos(mentorOrderNos)
                : Set.of();
        Set<String> pendingAfterSalesOrderNos = includeSortSignals && "PRIORITY".equals(query.sortMode())
                ? findPendingAfterSalesOrderNos(mentorOrderNos)
                : Set.of();
        return new MentorWorkbenchFilterContext(
                matchedStudentUserIds,
                paymentMatchedOrderNos,
                afterSalesOrderNos,
                pendingAfterSalesOrderNos
        );
    }

    private Specification<ConsultOrderEntity> buildMentorWorkbenchSpecification(
            long mentorUserId,
            MentorWorkbenchQuery query,
            int replyTimeoutHours,
            int expiringWindowHours,
            MentorWorkbenchFilterContext context,
            boolean applyOrdering
    ) {
        String normalizedKeyword = normalizeKeyword(query.keyword());
        Instant now = Instant.now();
        Instant createdSince = now.minus(7, ChronoUnit.DAYS);
        Instant paidSince = now.minus(7, ChronoUnit.DAYS);
        Instant expiringPaidAtThreshold = resolveExpiringPaidAtThreshold(replyTimeoutHours, expiringWindowHours);
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("mentorUserId"), mentorUserId));
            appendMentorWorkbenchKeywordPredicate(
                    root,
                    criteriaBuilder,
                    predicates,
                    normalizedKeyword,
                    context.matchedStudentUserIds()
            );
            if (query.status() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), query.status().name()));
            }
            if ("TEXT".equals(query.serviceFilter())) {
                predicates.add(criteriaBuilder.isNull(root.get("appointmentStartAt")));
                predicates.add(criteriaBuilder.isNull(root.get("appointmentEndAt")));
            } else if ("APPOINTMENT".equals(query.serviceFilter())) {
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.isNotNull(root.get("appointmentStartAt")),
                        criteriaBuilder.isNotNull(root.get("appointmentEndAt"))
                ));
            }
            if ("CREATED_7D".equals(query.timeFilter())) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), createdSince));
            } else if ("PAID_7D".equals(query.timeFilter())) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("paidAt"), paidSince));
            } else if ("UPCOMING_APPOINTMENT".equals(query.timeFilter())) {
                Expression<Instant> appointmentAt = criteriaBuilder.<Instant>coalesce()
                        .value(root.get("appointmentStartAt"))
                        .value(root.get("appointmentEndAt"));
                predicates.add(criteriaBuilder.isNotNull(appointmentAt));
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(appointmentAt, now));
            }
            if (query.paymentMode() != null) {
                predicates.add(orderNoInSet(root, context.paymentMatchedOrderNos(), criteriaBuilder));
            }
            if ("EXPIRING".equals(query.riskFilter())) {
                predicates.add(criteriaBuilder.equal(root.get("status"), ConsultOrderStatus.PAID.name()));
                predicates.add(criteriaBuilder.isNotNull(root.get("paidAt")));
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("paidAt"), expiringPaidAtThreshold));
            } else if ("NEED_CONFIRMATION".equals(query.riskFilter())) {
                predicates.add(criteriaBuilder.equal(root.get("status"), ConsultOrderStatus.ANSWERED.name()));
            } else if ("AFTER_SALES".equals(query.riskFilter())) {
                predicates.add(buildRefundedOrAfterSalesPredicate(root, criteriaBuilder, context.afterSalesOrderNos()));
            }
            if (applyOrdering && !isCountQuery(criteriaQuery)) {
                applyMentorWorkbenchOrdering(
                        root,
                        criteriaQuery,
                        criteriaBuilder,
                        query.sortMode(),
                        replyTimeoutHours,
                        expiringWindowHours,
                        context
                );
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void appendMentorWorkbenchKeywordPredicate(
            Root<ConsultOrderEntity> root,
            CriteriaBuilder criteriaBuilder,
            List<Predicate> predicates,
            String normalizedKeyword,
            Set<Long> matchedStudentUserIds
    ) {
        if (normalizedKeyword == null) {
            return;
        }
        String pattern = "%" + normalizedKeyword + "%";
        List<Predicate> keywordPredicates = new ArrayList<>();
        keywordPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("orderNo")), pattern));
        keywordPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(criteriaBuilder.coalesce(root.get("questionText"), "")), pattern));
        if (!matchedStudentUserIds.isEmpty()) {
            keywordPredicates.add(root.get("studentUserId").in(matchedStudentUserIds));
        }
        predicates.add(criteriaBuilder.or(keywordPredicates.toArray(Predicate[]::new)));
    }

    private Specification<ConsultOrderEntity> buildAdminOrderSpecification(String keyword, ConsultOrderStatus status) {
        String normalizedKeyword = normalizeKeyword(keyword);
        Set<Long> matchedUserIds = normalizedKeyword == null
                ? Set.of()
                : new LinkedHashSet<>(userAccountJpaRepository.findIdsByDisplayNameContainingIgnoreCaseAndDeletedFalse(normalizedKeyword));
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (normalizedKeyword != null) {
                String pattern = "%" + normalizedKeyword + "%";
                List<Predicate> keywordPredicates = new ArrayList<>();
                keywordPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("orderNo")), pattern));
                if (!matchedUserIds.isEmpty()) {
                    keywordPredicates.add(root.get("studentUserId").in(matchedUserIds));
                    keywordPredicates.add(root.get("mentorUserId").in(matchedUserIds));
                }
                predicates.add(criteriaBuilder.or(keywordPredicates.toArray(Predicate[]::new)));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status.name()));
            }
            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Set<String> findAfterSalesOrderNosForMentor(long mentorUserId) {
        return findAfterSalesOrderNos(findMentorOrderNos(mentorUserId));
    }

    private Set<String> findMentorOrderNos(long mentorUserId) {
        List<String> orderNos = consultOrderJpaRepository.findOrderNosByMentorUserId(mentorUserId);
        if (orderNos.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(orderNos);
    }

    private Set<String> resolvePaymentModeMatchedOrderNos(String paymentMode, Collection<String> mentorOrderNos) {
        if (mentorOrderNos.isEmpty()) {
            return Set.of();
        }
        Map<String, String> latestPaymentModeByOrderNo = findLatestPaymentModeByOrderNo(mentorOrderNos);
        Set<String> matchedOrderNos = new LinkedHashSet<>();
        if ("UNSET".equals(paymentMode)) {
            for (String orderNo : mentorOrderNos) {
                if (!latestPaymentModeByOrderNo.containsKey(orderNo) || latestPaymentModeByOrderNo.get(orderNo) == null) {
                    matchedOrderNos.add(orderNo);
                }
            }
            return matchedOrderNos;
        }
        for (Map.Entry<String, String> entry : latestPaymentModeByOrderNo.entrySet()) {
            if (paymentMode.equals(entry.getValue())) {
                matchedOrderNos.add(entry.getKey());
            }
        }
        return matchedOrderNos;
    }

    private Set<String> findAfterSalesOrderNos(Collection<String> orderNos) {
        if (orderNos.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(consultAfterSalesRequestJpaRepository.findDistinctOrderNosByOrderNoIn(orderNos));
    }

    private Set<String> findPendingAfterSalesOrderNos(Collection<String> orderNos) {
        if (orderNos.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(consultAfterSalesRequestJpaRepository.findDistinctOrderNosByOrderNoInAndStatus(orderNos, "PENDING"));
    }

    private Map<String, AfterSalesAggregate> loadAfterSalesAggregateByOrderNo(Collection<String> orderNos) {
        if (orderNos.isEmpty()) {
            return Map.of();
        }
        Map<String, AfterSalesAggregate> aggregateByOrderNo = new LinkedHashMap<>();
        for (ConsultAfterSalesRequestEntity entity : consultAfterSalesRequestJpaRepository.findByOrderNoInOrderByOrderNoAscCreatedAtAscIdAsc(orderNos)) {
            if (entity.getOrderNo() == null) {
                continue;
            }
            AfterSalesAggregate current = aggregateByOrderNo.get(entity.getOrderNo());
            int totalCount = current == null ? 1 : current.afterSalesCount() + 1;
            int pendingCount = current == null ? 0 : current.pendingAfterSalesCount();
            if ("PENDING".equals(entity.getStatus())) {
                pendingCount++;
            }
            aggregateByOrderNo.put(
                    entity.getOrderNo(),
                    new AfterSalesAggregate(totalCount, pendingCount, entity.getStatus())
            );
        }
        return aggregateByOrderNo;
    }

    private void applyMentorWorkbenchOrdering(
            Root<ConsultOrderEntity> root,
            CriteriaQuery<?> criteriaQuery,
            CriteriaBuilder criteriaBuilder,
            String sortMode,
            int replyTimeoutHours,
            int expiringWindowHours,
            MentorWorkbenchFilterContext context
    ) {
        List<Order> orders = new ArrayList<>();
        if ("AMOUNT_DESC".equals(sortMode)) {
            orders.add(criteriaBuilder.desc(root.get("amountFen")));
        } else if ("LATEST_PAID".equals(sortMode)) {
            orders.add(criteriaBuilder.asc(nullsLast(root.get("paidAt"), criteriaBuilder)));
            orders.add(criteriaBuilder.desc(root.get("paidAt")));
        } else if ("DEADLINE_ASC".equals(sortMode)) {
            orders.add(criteriaBuilder.asc(mentorReplyDeadlineNullsLast(root, criteriaBuilder)));
            orders.add(criteriaBuilder.asc(root.get("paidAt")));
        } else if ("LATEST_CREATED".equals(sortMode)) {
            orders.add(criteriaBuilder.desc(root.get("createdAt")));
            orders.add(criteriaBuilder.desc(root.get("id")));
            criteriaQuery.orderBy(orders);
            return;
        } else {
            orders.add(criteriaBuilder.desc(
                    buildMentorWorkbenchPriorityScore(root, criteriaBuilder, replyTimeoutHours, expiringWindowHours, context)
            ));
        }
        orders.add(criteriaBuilder.desc(root.get("createdAt")));
        orders.add(criteriaBuilder.desc(root.get("id")));
        criteriaQuery.orderBy(orders);
    }

    private Expression<Integer> mentorReplyDeadlineNullsLast(Root<ConsultOrderEntity> root, CriteriaBuilder criteriaBuilder) {
        return criteriaBuilder.<Integer>selectCase()
                .when(hasMentorReplyDeadline(root, criteriaBuilder), 0)
                .otherwise(1);
    }

    private Expression<Integer> buildMentorWorkbenchPriorityScore(
            Root<ConsultOrderEntity> root,
            CriteriaBuilder criteriaBuilder,
            int replyTimeoutHours,
            int expiringWindowHours,
            MentorWorkbenchFilterContext context
    ) {
        Instant expiredPaidAtThreshold = Instant.now().minus(replyTimeoutHours, ChronoUnit.HOURS);
        Instant expiringPaidAtThreshold = resolveExpiringPaidAtThreshold(replyTimeoutHours, expiringWindowHours);
        Predicate paidWithKnownTime = hasMentorReplyDeadline(root, criteriaBuilder);
        Expression<Integer> deadlineScore = criteriaBuilder.<Integer>selectCase()
                .when(
                        criteriaBuilder.and(
                                paidWithKnownTime,
                                criteriaBuilder.lessThanOrEqualTo(root.get("paidAt"), expiredPaidAtThreshold)
                        ),
                        1000
                )
                .when(
                        criteriaBuilder.and(
                                paidWithKnownTime,
                                criteriaBuilder.lessThanOrEqualTo(root.get("paidAt"), expiringPaidAtThreshold)
                        ),
                        900
                )
                .otherwise(0);
        Expression<Integer> afterSalesScore = criteriaBuilder.<Integer>selectCase()
                .when(orderNoInSet(root, context.pendingAfterSalesOrderNos(), criteriaBuilder), 820)
                .when(buildRefundedOrAfterSalesPredicate(root, criteriaBuilder, context.afterSalesOrderNos()), 720)
                .otherwise(0);
        Expression<Integer> statusScore = criteriaBuilder.<Integer>selectCase()
                .when(criteriaBuilder.equal(root.get("status"), ConsultOrderStatus.PAID.name()), 640)
                .when(criteriaBuilder.equal(root.get("status"), ConsultOrderStatus.ANSWERED.name()), 520)
                .when(criteriaBuilder.equal(root.get("status"), ConsultOrderStatus.REFUNDED.name()), 420)
                .otherwise(0);
        Expression<Integer> amountScore = criteriaBuilder.<Integer>selectCase()
                .when(criteriaBuilder.ge(root.get("amountFen"), 20_000), 200)
                .otherwise(criteriaBuilder.quot(root.get("amountFen"), 100).as(Integer.class));
        return criteriaBuilder.sum(
                criteriaBuilder.sum(deadlineScore, afterSalesScore),
                criteriaBuilder.sum(statusScore, amountScore)
        );
    }

    private Predicate hasMentorReplyDeadline(Root<ConsultOrderEntity> root, CriteriaBuilder criteriaBuilder) {
        return criteriaBuilder.and(
                criteriaBuilder.equal(root.get("status"), ConsultOrderStatus.PAID.name()),
                criteriaBuilder.isNotNull(root.get("paidAt"))
        );
    }

    private Predicate buildRefundedOrAfterSalesPredicate(
            Root<ConsultOrderEntity> root,
            CriteriaBuilder criteriaBuilder,
            Set<String> afterSalesOrderNos
    ) {
        Predicate refunded = criteriaBuilder.equal(root.get("status"), ConsultOrderStatus.REFUNDED.name());
        if (afterSalesOrderNos.isEmpty()) {
            return refunded;
        }
        return criteriaBuilder.or(refunded, root.get("orderNo").in(afterSalesOrderNos));
    }

    private Predicate orderNoInSet(Root<ConsultOrderEntity> root, Set<String> orderNos, CriteriaBuilder criteriaBuilder) {
        return orderNos.isEmpty() ? criteriaBuilder.disjunction() : root.get("orderNo").in(orderNos);
    }

    private Expression<Integer> nullsLast(Expression<?> expression, CriteriaBuilder criteriaBuilder) {
        return criteriaBuilder.<Integer>selectCase()
                .when(criteriaBuilder.isNull(expression), 1)
                .otherwise(0);
    }

    private boolean isCountQuery(CriteriaQuery<?> criteriaQuery) {
        Class<?> resultType = criteriaQuery.getResultType();
        return Long.class.equals(resultType) || long.class.equals(resultType);
    }

    private Instant resolveExpiringPaidAtThreshold(int replyTimeoutHours, int expiringWindowHours) {
        return Instant.now()
                .plus(expiringWindowHours, ChronoUnit.HOURS)
                .minus(replyTimeoutHours, ChronoUnit.HOURS);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String normalized = keyword.trim().toLowerCase();
        return normalized.isEmpty() ? null : normalized;
    }

    private Specification<ConsultOrderEntity> hasMentorUserId(long mentorUserId) {
        return (root, criteriaQuery, criteriaBuilder) -> criteriaBuilder.equal(root.get("mentorUserId"), mentorUserId);
    }

    private Specification<ConsultOrderEntity> hasStatus(ConsultOrderStatus status) {
        return (root, criteriaQuery, criteriaBuilder) -> criteriaBuilder.equal(root.get("status"), status.name());
    }

    private Specification<ConsultOrderEntity> paidAtOnOrBefore(Instant threshold) {
        return (root, criteriaQuery, criteriaBuilder) -> criteriaBuilder.lessThanOrEqualTo(root.get("paidAt"), threshold);
    }

    private Specification<ConsultOrderEntity> hasRefundedOrAfterSales(Set<String> afterSalesOrderNos) {
        return (root, criteriaQuery, criteriaBuilder) ->
                buildRefundedOrAfterSalesPredicate(root, criteriaBuilder, afterSalesOrderNos);
    }

    private long requireGeneratedId(Long id, String entityName) {
        if (id == null) {
            throw new IllegalStateException("failed to persist " + entityName);
        }
        return id;
    }

    private boolean updateOrder(Long orderId, String orderNo, OrderUpdater updater) {
        Optional<ConsultOrderEntity> order = findResolvedOrder(orderId, orderNo);
        if (order.isEmpty()) {
            return false;
        }
        if (!updater.apply(order.get())) {
            return false;
        }
        consultOrderJpaRepository.saveAndFlush(order.get());
        return true;
    }

    private Optional<ConsultOrderEntity> findResolvedOrder(Long orderId, String orderNo) {
        if (orderId != null) {
            if (orderNo == null || orderNo.isBlank()) {
                return consultOrderJpaRepository.findById(orderId);
            }
            return consultOrderJpaRepository.findByIdAndOrderNo(orderId, orderNo);
        }
        if (orderNo == null || orderNo.isBlank()) {
            return Optional.empty();
        }
        return consultOrderJpaRepository.findByOrderNo(orderNo);
    }

    private Optional<Long> findOrderIdByOrderNo(String orderNo) {
        return consultOrderJpaRepository.findByOrderNo(orderNo)
                .map(entity -> requireGeneratedId(entity.getId(), "consult order"));
    }

    private Long optionalOrderIdByOrderNo(String orderNo) {
        return findOrderIdByOrderNo(orderNo).orElse(null);
    }

    private Long requireOrderIdByOrderNo(String orderNo) {
        return findOrderIdByOrderNo(orderNo)
                .orElseThrow(() -> new IllegalStateException("consult order not found for orderNo=" + orderNo));
    }

    private PaymentRecordRow toPaymentRecordRow(PaymentRecordEntity entity) {
        return new PaymentRecordRow(
                entity.getId() == null ? 0L : entity.getId(),
                entity.getOrderNo(),
                entity.getChannel(),
                entity.getMode(),
                entity.getProviderTradeNo(),
                entity.getAmountFen() == null ? 0 : entity.getAmountFen(),
                entity.getStatus(),
                entity.getIdempotencyKey(),
                entity.getRawCallback(),
                entity.getCreatedAt()
        );
    }

    private AuditLogRow toAuditLogRow(AuditLogEntity entity) {
        return new AuditLogRow(
                entity.getOperatorUserId() == null ? 0L : entity.getOperatorUserId(),
                entity.getDetailJson(),
                entity.getCreatedAt()
        );
    }

    private MessageRow toMessageRow(ConsultMessageEntity entity, String senderDisplayName) {
        return new MessageRow(
                entity.getId() == null ? 0L : entity.getId(),
                entity.getSenderUserId() == null ? 0L : entity.getSenderUserId(),
                senderDisplayName,
                entity.getSenderRole(),
                entity.getMessageText(),
                entity.getCreatedAt()
        );
    }

    private OrderListRow toOrderListRow(
            ConsultOrderEntity entity,
            long counterpartUserId,
            String counterpartDisplayName,
            String paymentMode
    ) {
        return new OrderListRow(
                entity.getOrderNo(),
                counterpartUserId,
                counterpartDisplayName,
                entity.getAmountFen() == null ? 0 : entity.getAmountFen(),
                ConsultOrderStatus.parse(entity.getStatus()),
                entity.getQuestionText(),
                paymentMode,
                entity.getAppointmentStartAt(),
                entity.getAppointmentEndAt(),
                entity.getCreatedAt(),
                entity.getPaidAt(),
                entity.getClosedAt()
        );
    }

    private OrderDetailRow toOrderDetailRow(
            ConsultOrderEntity entity,
            Map<Long, String> displayNameByUserId,
            PaymentRecordEntity paymentRecord
    ) {
        return new OrderDetailRow(
                requireGeneratedId(entity.getId(), "consult order"),
                entity.getOrderNo(),
                entity.getStudentUserId() == null ? 0L : entity.getStudentUserId(),
                displayNameByUserId.get(entity.getStudentUserId()),
                entity.getMentorUserId() == null ? 0L : entity.getMentorUserId(),
                displayNameByUserId.get(entity.getMentorUserId()),
                entity.getAmountFen() == null ? 0 : entity.getAmountFen(),
                ConsultOrderStatus.parse(entity.getStatus()),
                entity.getSceneCode(),
                entity.getSourcePage(),
                entity.getQuestionText(),
                entity.getQuestionPayloadJson(),
                entity.getProblemSummary(),
                entity.getCoreQuestionsJson(),
                entity.getExpectedOutcomesJson(),
                entity.getSelectedMaterialTypes(),
                entity.getPrepSheetSnapshotJson(),
                paymentRecord == null ? null : paymentRecord.getMode(),
                entity.getAppointmentStartAt(),
                entity.getAppointmentEndAt(),
                entity.getCreatedAt(),
                entity.getPaidAt(),
                entity.getClosedAt(),
                entity.getReviewRating() == null ? null : entity.getReviewRating().intValue(),
                entity.getReviewComment(),
                entity.getReviewCreatedAt()
        );
    }

    private MentorWorkbenchOrderRow toMentorWorkbenchOrderRow(
            ConsultOrderEntity entity,
            String counterpartDisplayName,
            String paymentMode,
            AfterSalesAggregate afterSalesAggregate,
            int replyTimeoutHours
    ) {
        return new MentorWorkbenchOrderRow(
                entity.getOrderNo(),
                entity.getStudentUserId() == null ? 0L : entity.getStudentUserId(),
                counterpartDisplayName,
                entity.getAmountFen() == null ? 0 : entity.getAmountFen(),
                ConsultOrderStatus.parse(entity.getStatus()),
                entity.getQuestionText(),
                paymentMode,
                entity.getAppointmentStartAt(),
                entity.getAppointmentEndAt(),
                entity.getCreatedAt(),
                entity.getPaidAt(),
                entity.getClosedAt(),
                resolveMentorReplyDeadline(entity, replyTimeoutHours),
                afterSalesAggregate != null && afterSalesAggregate.afterSalesCount() > 0,
                afterSalesAggregate != null && afterSalesAggregate.pendingAfterSalesCount() > 0,
                afterSalesAggregate == null ? null : afterSalesAggregate.latestAfterSalesStatus()
        );
    }

    private AdminOrderListRow toAdminOrderListRow(
            ConsultOrderEntity entity,
            Map<Long, String> displayNameByUserId,
            String paymentMode
    ) {
        return new AdminOrderListRow(
                entity.getOrderNo(),
                entity.getStudentUserId() == null ? 0L : entity.getStudentUserId(),
                displayNameByUserId.get(entity.getStudentUserId()),
                entity.getMentorUserId() == null ? 0L : entity.getMentorUserId(),
                displayNameByUserId.get(entity.getMentorUserId()),
                entity.getAmountFen() == null ? 0 : entity.getAmountFen(),
                ConsultOrderStatus.parse(entity.getStatus()),
                entity.getQuestionText(),
                paymentMode,
                entity.getAppointmentStartAt(),
                entity.getAppointmentEndAt(),
                entity.getCreatedAt(),
                entity.getPaidAt(),
                entity.getClosedAt()
        );
    }

    private Instant resolveMentorReplyDeadline(ConsultOrderEntity entity, int replyTimeoutHours) {
        if (!ConsultOrderStatus.PAID.name().equals(entity.getStatus()) || entity.getPaidAt() == null) {
            return null;
        }
        return entity.getPaidAt().plus(replyTimeoutHours, ChronoUnit.HOURS);
    }

    private OrderAttachmentRow toOrderAttachmentRow(ConsultOrderAttachmentEntity entity) {
        return new OrderAttachmentRow(
                entity.getId() == null ? 0L : entity.getId(),
                entity.getOrderNo(),
                entity.getUploadedByUserId() == null ? 0L : entity.getUploadedByUserId(),
                entity.getAttachmentType(),
                entity.getSlotCode(),
                entity.getSourceStage(),
                entity.getOriginalFilename(),
                entity.getContentType(),
                entity.getSizeBytes() == null ? 0L : entity.getSizeBytes(),
                entity.getStorageBucket(),
                entity.getObjectKey(),
                entity.getDescription(),
                entity.getLifecycleStatus(),
                entity.getReplacedAttachmentId(),
                entity.getCreatedAt()
        );
    }

    private record MentorWorkbenchFilterContext(
            Set<Long> matchedStudentUserIds,
            Set<String> paymentMatchedOrderNos,
            Set<String> afterSalesOrderNos,
            Set<String> pendingAfterSalesOrderNos
    ) {
    }

    private record AfterSalesAggregate(
            int afterSalesCount,
            int pendingAfterSalesCount,
            String latestAfterSalesStatus
    ) {
    }

    /**
     * 订单列表行。
     */
    public record OrderListRow(
            String orderNo,
            long counterpartUserId,
            String counterpartDisplayName,
            int amountFen,
            ConsultOrderStatus status,
            String questionText,
            String paymentMode,
            Instant appointmentStartAt,
            Instant appointmentEndAt,
            Instant createdAt,
            Instant paidAt,
            Instant closedAt
    ) {
    }

    /**
     * 订单详情行。
     */
    public record OrderDetailRow(
            long orderId,
            String orderNo,
            long studentUserId,
            String studentDisplayName,
            long mentorUserId,
            String mentorDisplayName,
            int amountFen,
            ConsultOrderStatus status,
            String sceneCode,
            String sourcePage,
            String questionText,
            String questionPayloadJson,
            String problemSummary,
            String coreQuestionsJson,
            String expectedOutcomesJson,
            String selectedMaterialTypes,
            String prepSheetSnapshotJson,
            String paymentMode,
            Instant appointmentStartAt,
            Instant appointmentEndAt,
            Instant createdAt,
            Instant paidAt,
            Instant closedAt,
            Integer reviewRating,
            String reviewComment,
            Instant reviewCreatedAt
    ) {
    }

    /**
     * 订单材料行。
     */
    public record OrderAttachmentRow(
            long id,
            String orderNo,
            long uploadedByUserId,
            String attachmentType,
            String slotCode,
            String sourceStage,
            String originalFilename,
            String contentType,
            long sizeBytes,
            String storageBucket,
            String objectKey,
            String description,
            String lifecycleStatus,
            Long replacedAttachmentId,
            Instant createdAt
    ) {
    }

    /**
     * 消息行。
     */
    public record MessageRow(
            long id,
            long senderUserId,
            String senderDisplayName,
            String senderRole,
            String messageText,
            Instant createdAt
    ) {
    }

    /**
     * 支付记录行。
     */
    public record PaymentRecordRow(
            long id,
            String orderNo,
            String channel,
            String mode,
            String providerTradeNo,
            int amountFen,
            String status,
            String idempotencyKey,
            String rawCallback,
            Instant createdAt
    ) {
    }

    /**
     * 创建订单命令。
     */
    public record CreateOrderCommand(
            String orderNo,
            long studentUserId,
            long mentorUserId,
            String sceneCode,
            String sourcePage,
            int amountFen,
            String questionText,
            String questionPayloadJson,
            String problemSummary,
            String coreQuestionsJson,
            String expectedOutcomesJson,
            String selectedMaterialTypes,
            String prepSheetSnapshotJson,
            String servicePackageSnapshotJson,
            Instant appointmentStartAt,
            Instant appointmentEndAt
    ) {
    }

    /**
     * 创建订单材料命令。
     */
    public record CreateAttachmentCommand(
            String orderNo,
            long uploadedByUserId,
            String attachmentType,
            String slotCode,
            String sourceStage,
            String originalFilename,
            String contentType,
            long sizeBytes,
            String storageBucket,
            String objectKey,
            String description,
            String lifecycleStatus,
            Long replacedAttachmentId
    ) {
    }

    /**
     * 导师订单中心筛选查询。
     */
    public record MentorWorkbenchQuery(
            String keyword,
            ConsultOrderStatus status,
            String serviceFilter,
            String timeFilter,
            String paymentMode,
            String riskFilter,
            String sortMode,
            int page,
            int size
    ) {
    }

    /**
     * 导师订单中心列表行。
     */
    public record MentorWorkbenchOrderRow(
            String orderNo,
            long counterpartUserId,
            String counterpartDisplayName,
            int amountFen,
            ConsultOrderStatus status,
            String questionText,
            String paymentMode,
            Instant appointmentStartAt,
            Instant appointmentEndAt,
            Instant createdAt,
            Instant paidAt,
            Instant closedAt,
            Instant mentorReplyDeadlineAt,
            boolean afterSalesImpact,
            boolean pendingAfterSales,
            String latestAfterSalesStatus
    ) {
    }

    /**
     * 导师订单中心顶部摘要。
     */
    public record MentorWorkbenchSummaryRow(
            long pendingReplyCount,
            long expiringSoonCount,
            long waitingConfirmationCount,
            long afterSalesImpactCount
    ) {
    }

    /**
     * 管理员订单列表行。
     */
    public record AdminOrderListRow(
            String orderNo,
            long studentUserId,
            String studentDisplayName,
            long mentorUserId,
            String mentorDisplayName,
            int amountFen,
            ConsultOrderStatus status,
            String questionText,
            String paymentMode,
            Instant appointmentStartAt,
            Instant appointmentEndAt,
            Instant createdAt,
            Instant paidAt,
            Instant closedAt
    ) {
    }

    /**
     * 审计日志行。
     */
    public record AuditLogRow(
            long operatorUserId,
            String detailJson,
            Instant createdAt
    ) {
    }

    @FunctionalInterface
    private interface OrderUpdater {

        boolean apply(ConsultOrderEntity entity);
    }
}
