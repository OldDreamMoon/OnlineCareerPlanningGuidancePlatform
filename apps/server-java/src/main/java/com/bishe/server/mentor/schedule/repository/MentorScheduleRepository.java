package com.bishe.server.mentor.schedule.repository;

import com.bishe.server.consult.repository.jpa.ConsultOrderJpaRepository;
import com.bishe.server.mentor.schedule.repository.jpa.MentorScheduleSlotJpaRepository;
import com.bishe.server.mentor.schedule.repository.jpa.entity.MentorScheduleSlotEntity;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 导师排期仓储。
 */
@Repository
public class MentorScheduleRepository {

    private final MentorScheduleSlotJpaRepository mentorScheduleSlotJpaRepository;
    private final ConsultOrderJpaRepository consultOrderJpaRepository;
    private final EntityManager entityManager;

    public MentorScheduleRepository(
            MentorScheduleSlotJpaRepository mentorScheduleSlotJpaRepository,
            ConsultOrderJpaRepository consultOrderJpaRepository,
            EntityManager entityManager
    ) {
        this.mentorScheduleSlotJpaRepository = mentorScheduleSlotJpaRepository;
        this.consultOrderJpaRepository = consultOrderJpaRepository;
        this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long createSlot(long mentorUserId, Instant startAt, Instant endAt) {
        try {
            MentorScheduleSlotEntity entity = MentorScheduleSlotEntity.create(mentorUserId, startAt, endAt);
            return mentorScheduleSlotJpaRepository.saveAndFlush(entity).getId();
        } catch (DataIntegrityViolationException ex) {
            // 批量建时段会依赖“重复即跳过并继续”，冲突后需要清理持久化上下文，避免后续 flush 失败。
            entityManager.clear();
            if (isDuplicateSlotViolation(ex)) {
                throw new DuplicateKeyException("mentor schedule slot already exists", ex);
            }
            throw ex;
        }
    }

    public List<ScheduleSlotRow> findSlotsForMentor(long mentorUserId, Instant dateFrom, Instant dateTo, boolean onlyAvailable) {
        Specification<MentorScheduleSlotEntity> specification = Specification
                .where(hasMentorUserId(mentorUserId))
                .and(endsAtOnOrAfter(dateFrom))
                .and(startsAtOnOrBefore(dateTo))
                .and(hasStatusAvailableWhenRequired(onlyAvailable));
        return mentorScheduleSlotJpaRepository.findAll(
                        specification,
                        Sort.by(
                                Sort.Order.asc("startAt"),
                                Sort.Order.asc("id")
                        )
                )
                .stream()
                .map(this::toRow)
                .toList();
    }

    public Optional<ScheduleSlotRow> findSlotById(long slotId) {
        return mentorScheduleSlotJpaRepository.findById(slotId)
                .map(this::toRow);
    }

    public List<Long> findBookedMentorUserIds(Long orderId, String orderNo) {
        return mentorScheduleSlotJpaRepository.findBookedMentorUserIdsByResolvedOrder(orderId, orderNo);
    }

    public List<Long> findBookedMentorUserIdsByOrderNo(String orderNo) {
        return findBookedMentorUserIds(optionalOrderIdByOrderNo(orderNo), orderNo);
    }

    @Transactional
    public boolean reserveSlot(long slotId, long mentorUserId, Long orderId, String orderNo) {
        return mentorScheduleSlotJpaRepository.reserveSlot(
                slotId,
                mentorUserId,
                orderId,
                orderNo,
                Instant.now()
        ) > 0;
    }

    @Transactional
    public boolean reserveSlot(long slotId, long mentorUserId, String orderNo) {
        return reserveSlot(slotId, mentorUserId, optionalOrderIdByOrderNo(orderNo), orderNo);
    }

    @Transactional
    public int bindReservedOrder(long orderId, String orderNo) {
        return mentorScheduleSlotJpaRepository.bindBookedOrderIdByOrderNo(orderId, orderNo, Instant.now());
    }

    @Transactional
    public int releaseSlot(Long orderId, String orderNo) {
        return mentorScheduleSlotJpaRepository.releaseSlotByResolvedOrder(orderId, orderNo, Instant.now());
    }

    @Transactional
    public int releaseSlotByOrderNo(String orderNo) {
        return releaseSlot(optionalOrderIdByOrderNo(orderNo), orderNo);
    }

    @Transactional
    public int releaseUpcomingSlot(Long orderId, String orderNo, Instant now) {
        return mentorScheduleSlotJpaRepository.releaseUpcomingSlotByResolvedOrder(orderId, orderNo, now, now);
    }

    @Transactional
    public int releaseUpcomingSlotByOrderNo(String orderNo, Instant now) {
        return releaseUpcomingSlot(optionalOrderIdByOrderNo(orderNo), orderNo, now);
    }

    @Transactional
    public boolean deleteAvailableSlot(long slotId, long mentorUserId) {
        return mentorScheduleSlotJpaRepository.deleteAvailableSlot(slotId, mentorUserId) > 0;
    }

    private ScheduleSlotRow toRow(MentorScheduleSlotEntity entity) {
        return new ScheduleSlotRow(
                entity.getId(),
                entity.getMentorUserId(),
                entity.getStartAt(),
                entity.getEndAt(),
                entity.getStatus(),
                entity.getBookedOrderNo()
        );
    }

    private Specification<MentorScheduleSlotEntity> hasMentorUserId(long mentorUserId) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("mentorUserId"), mentorUserId);
    }

    private Specification<MentorScheduleSlotEntity> endsAtOnOrAfter(Instant dateFrom) {
        if (dateFrom == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.greaterThanOrEqualTo(root.get("endAt"), dateFrom);
    }

    private Specification<MentorScheduleSlotEntity> startsAtOnOrBefore(Instant dateTo) {
        if (dateTo == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.lessThanOrEqualTo(root.get("startAt"), dateTo);
    }

    private Specification<MentorScheduleSlotEntity> hasStatusAvailableWhenRequired(boolean onlyAvailable) {
        if (!onlyAvailable) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("status"), "AVAILABLE");
    }

    private boolean isDuplicateSlotViolation(DataIntegrityViolationException ex) {
        Throwable cursor = ex;
        while (cursor != null) {
            if (cursor instanceof ConstraintViolationException constraintViolationException) {
                String constraintName = constraintViolationException.getConstraintName();
                if ("uq_mentor_schedule_slots".equalsIgnoreCase(constraintName)) {
                    return true;
                }
                SQLException sqlException = constraintViolationException.getSQLException();
                if (sqlException != null && "23505".equals(sqlException.getSQLState())) {
                    return true;
                }
            }
            if (cursor instanceof SQLException sqlException && "23505".equals(sqlException.getSQLState())) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private Long optionalOrderIdByOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return null;
        }
        return consultOrderJpaRepository.findByOrderNo(orderNo)
                .map(MentorScheduleRepository::requireOrderId)
                .orElse(null);
    }

    private static Long requireOrderId(com.bishe.server.consult.repository.jpa.entity.ConsultOrderEntity entity) {
        if (entity.getId() == null) {
            throw new IllegalStateException("consult order id missing");
        }
        return entity.getId();
    }

    public record ScheduleSlotRow(
            long id,
            long mentorUserId,
            Instant startAt,
            Instant endAt,
            String status,
            String bookedOrderNo
    ) {
    }
}
