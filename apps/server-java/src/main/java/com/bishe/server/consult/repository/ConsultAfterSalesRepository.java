package com.bishe.server.consult.repository;

import com.bishe.server.consult.ConsultAfterSalesRequestStatus;
import com.bishe.server.consult.ConsultAfterSalesRequestType;
import com.bishe.server.consult.ConsultOrderStatus;
import com.bishe.server.consult.repository.jpa.ConsultAfterSalesRequestJpaRepository;
import com.bishe.server.consult.repository.jpa.ConsultOrderJpaRepository;
import com.bishe.server.consult.repository.jpa.entity.ConsultAfterSalesRequestEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 咨询售后申请仓储。
 */
@Repository
public class ConsultAfterSalesRepository {

    private final ConsultAfterSalesRequestJpaRepository consultAfterSalesRequestJpaRepository;
    private final ConsultOrderJpaRepository consultOrderJpaRepository;

    public ConsultAfterSalesRepository(
            ConsultAfterSalesRequestJpaRepository consultAfterSalesRequestJpaRepository,
            ConsultOrderJpaRepository consultOrderJpaRepository
    ) {
        this.consultAfterSalesRequestJpaRepository = consultAfterSalesRequestJpaRepository;
        this.consultOrderJpaRepository = consultOrderJpaRepository;
    }

    public long createRequest(long orderId, String orderNo, long requesterUserId, ConsultAfterSalesRequestType requestType, String reason, boolean autoTriggered) {
        ConsultAfterSalesRequestEntity entity = ConsultAfterSalesRequestEntity.create(
                orderId,
                orderNo,
                requesterUserId,
                requestType.name(),
                ConsultAfterSalesRequestStatus.PENDING.name(),
                reason,
                autoTriggered
        );
        consultAfterSalesRequestJpaRepository.saveAndFlush(entity);
        if (entity.getId() == null) {
            throw new IllegalStateException("failed to extract generated id");
        }
        return entity.getId();
    }

    public long createRequest(String orderNo, long requesterUserId, ConsultAfterSalesRequestType requestType, String reason, boolean autoTriggered) {
        return createRequest(requireOrderIdByOrderNo(orderNo), orderNo, requesterUserId, requestType, reason, autoTriggered);
    }

    public Optional<AfterSalesRequestRow> findById(long requestId) {
        return consultAfterSalesRequestJpaRepository.findById(requestId)
                .map(this::toAfterSalesRequestRow);
    }

    public List<AfterSalesRequestRow> findByOrder(Long orderId, String orderNo) {
        return consultAfterSalesRequestJpaRepository.findByResolvedOrderOrderByCreatedAtDescIdDesc(orderId, orderNo).stream()
                .map(this::toAfterSalesRequestRow)
                .toList();
    }

    public List<AfterSalesRequestRow> findByOrderNo(String orderNo) {
        return findByOrder(optionalOrderIdByOrderNo(orderNo), orderNo);
    }

    public boolean existsPendingRequest(Long orderId, String orderNo) {
        return consultAfterSalesRequestJpaRepository.existsByResolvedOrderAndStatus(
                orderId,
                orderNo,
                ConsultAfterSalesRequestStatus.PENDING.name()
        );
    }

    public boolean existsPendingRequest(String orderNo) {
        return existsPendingRequest(optionalOrderIdByOrderNo(orderNo), orderNo);
    }

    public Optional<AfterSalesRequestRow> findPendingRequest(Long orderId, String orderNo) {
        return consultAfterSalesRequestJpaRepository.findResolvedOrderAndStatusOrderByCreatedAtAscIdAsc(
                        orderId,
                        orderNo,
                        ConsultAfterSalesRequestStatus.PENDING.name(),
                        PageRequest.of(0, 1)
                )
                .stream()
                .findFirst()
                .map(this::toAfterSalesRequestRow);
    }

    public Optional<AfterSalesRequestRow> findPendingRequest(String orderNo) {
        return findPendingRequest(optionalOrderIdByOrderNo(orderNo), orderNo);
    }

    public boolean reviewRequest(long requestId, ConsultAfterSalesRequestStatus status, String reviewNote, Long reviewerUserId, Instant reviewedAt) {
        Instant updatedAt = reviewedAt == null ? Instant.now() : reviewedAt;
        return consultAfterSalesRequestJpaRepository.reviewRequest(
                requestId,
                status.name(),
                reviewNote,
                reviewerUserId,
                reviewedAt,
                updatedAt
        ) > 0;
    }

    public List<AdminAfterSalesRequestRow> findAdminRequests(String keyword, ConsultAfterSalesRequestStatus status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(Math.max(page - 1, 0), Math.max(size, 1));
        return consultAfterSalesRequestJpaRepository.findAdminRequests(
                        normalizeKeyword(keyword),
                        normalizeStatus(status),
                        pageRequest
                ).stream()
                .map(this::toAdminAfterSalesRequestRow)
                .toList();
    }

    public long countAdminRequests(String keyword, ConsultAfterSalesRequestStatus status) {
        return consultAfterSalesRequestJpaRepository.countAdminRequests(
                normalizeKeyword(keyword),
                normalizeStatus(status)
        );
    }

    private AfterSalesRequestRow toAfterSalesRequestRow(ConsultAfterSalesRequestEntity entity) {
        return new AfterSalesRequestRow(
                entity.getId(),
                entity.getOrderNo(),
                entity.getRequesterUserId(),
                ConsultAfterSalesRequestType.parse(entity.getRequestType()),
                ConsultAfterSalesRequestStatus.parse(entity.getStatus()),
                entity.getReason(),
                entity.getReviewNote(),
                entity.getReviewerUserId(),
                entity.isAutoTriggered(),
                entity.getCreatedAt(),
                entity.getReviewedAt(),
                entity.getUpdatedAt()
        );
    }

    private AdminAfterSalesRequestRow toAdminAfterSalesRequestRow(
            ConsultAfterSalesRequestJpaRepository.AdminAfterSalesRequestProjection projection
    ) {
        return new AdminAfterSalesRequestRow(
                projection.getId(),
                projection.getOrderNo(),
                ConsultOrderStatus.parse(projection.getOrderStatus()),
                projection.getAmountFen(),
                projection.getStudentUserId(),
                projection.getStudentDisplayName(),
                projection.getMentorUserId(),
                projection.getMentorDisplayName(),
                projection.getRequesterUserId(),
                ConsultAfterSalesRequestType.parse(projection.getRequestType()),
                ConsultAfterSalesRequestStatus.parse(projection.getStatus()),
                projection.getReason(),
                projection.getReviewNote(),
                projection.getReviewerUserId(),
                projection.getAutoTriggered(),
                projection.getCreatedAt(),
                projection.getReviewedAt()
        );
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return "";
        }
        String trimmed = keyword.trim().toLowerCase();
        return trimmed.isEmpty() ? "" : trimmed;
    }

    private String normalizeStatus(ConsultAfterSalesRequestStatus status) {
        return status == null ? "" : status.name();
    }

    private Long requireOrderIdByOrderNo(String orderNo) {
        return consultOrderJpaRepository.findByOrderNo(orderNo)
                .map(entity -> {
                    if (entity.getId() == null) {
                        throw new IllegalStateException("consult order id missing");
                    }
                    return entity.getId();
                })
                .orElseThrow(() -> new IllegalStateException("consult order not found for orderNo=" + orderNo));
    }

    private Long optionalOrderIdByOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return null;
        }
        return consultOrderJpaRepository.findByOrderNo(orderNo)
                .map(entity -> {
                    if (entity.getId() == null) {
                        throw new IllegalStateException("consult order id missing");
                    }
                    return entity.getId();
                })
                .orElse(null);
    }

    /**
     * 售后申请行。
     */
    public record AfterSalesRequestRow(
            long id,
            String orderNo,
            long requesterUserId,
            ConsultAfterSalesRequestType requestType,
            ConsultAfterSalesRequestStatus status,
            String reason,
            String reviewNote,
            Long reviewerUserId,
            boolean autoTriggered,
            Instant createdAt,
            Instant reviewedAt,
            Instant updatedAt
    ) {
    }

    /**
     * 管理员售后申请列表行。
     */
    public record AdminAfterSalesRequestRow(
            long id,
            String orderNo,
            ConsultOrderStatus orderStatus,
            int amountFen,
            long studentUserId,
            String studentDisplayName,
            long mentorUserId,
            String mentorDisplayName,
            long requesterUserId,
            ConsultAfterSalesRequestType requestType,
            ConsultAfterSalesRequestStatus status,
            String reason,
            String reviewNote,
            Long reviewerUserId,
            boolean autoTriggered,
            Instant createdAt,
            Instant reviewedAt
    ) {
    }
}
