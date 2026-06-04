package com.bishe.server.consult.repository.jpa;

import com.bishe.server.consult.repository.jpa.entity.ConsultOrderEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ConsultOrderJpaRepository extends JpaRepository<ConsultOrderEntity, Long>, JpaSpecificationExecutor<ConsultOrderEntity> {

    Optional<ConsultOrderEntity> findByOrderNo(String orderNo);

    Optional<ConsultOrderEntity> findByIdAndOrderNo(Long id, String orderNo);

    @Query("""
            select entity.orderNo
              from ConsultOrderEntity entity
             where entity.mentorUserId = :mentorUserId
          order by entity.id asc
            """)
    List<String> findOrderNosByMentorUserId(@Param("mentorUserId") long mentorUserId);

    List<ConsultOrderEntity> findAllByOrderByCreatedAtDescIdDesc();

    List<ConsultOrderEntity> findAllByStatusOrderByCreatedAtDescIdDesc(String status);

    List<ConsultOrderEntity> findByStudentUserIdOrderByCreatedAtDescIdDesc(long studentUserId, Pageable pageable);

    List<ConsultOrderEntity> findByStudentUserIdAndStatusOrderByCreatedAtDescIdDesc(
            long studentUserId,
            String status,
            Pageable pageable
    );

    List<ConsultOrderEntity> findByMentorUserIdOrderByCreatedAtDescIdDesc(long mentorUserId, Pageable pageable);

    List<ConsultOrderEntity> findByMentorUserIdAndStatusOrderByCreatedAtDescIdDesc(
            long mentorUserId,
            String status,
            Pageable pageable
    );

    List<ConsultOrderEntity> findByMentorUserIdAndReviewRatingIsNotNullOrderByReviewCreatedAtDescIdDesc(
            long mentorUserId,
            Pageable pageable
    );

    long countByStudentUserId(long studentUserId);

    long countByStudentUserIdAndStatus(long studentUserId, String status);

    long countByMentorUserId(long mentorUserId);

    long countByMentorUserIdAndStatus(long mentorUserId, String status);

    List<ConsultOrderEntity> findByMentorUserIdInOrderByMentorUserIdAscIdAsc(Collection<Long> mentorUserIds);

    List<ConsultOrderEntity> findByPaidAtIsNullAndStatusInAndCreatedAtLessThanEqualOrderByCreatedAtAscIdAsc(
            Collection<String> statuses,
            Instant cutoff
    );

    List<ConsultOrderEntity> findByStatusAndPaidAtIsNotNullAndPaidAtLessThanEqualOrderByPaidAtAscIdAsc(
            String status,
            Instant cutoff
    );

    @Query("""
            select coalesce(sum(entity.amountFen), 0)
              from ConsultOrderEntity entity
             where entity.mentorUserId = :mentorUserId
               and entity.paidAt is not null
               and entity.status <> 'REFUNDED'
            """)
    Long sumPaidRevenueFenByMentorUserId(@Param("mentorUserId") long mentorUserId);

    @Query("""
            select distinct orderEntity.studentUserId
              from ConsultOrderEntity orderEntity
             where orderEntity.paidAt is not null
               and orderEntity.paidAt >= :startAt
               and orderEntity.paidAt <= :endAt
          order by orderEntity.studentUserId asc
            """)
    List<Long> findPaidStudentUserIds(@Param("startAt") Instant startAt, @Param("endAt") Instant endAt);

    @Query("""
            select coalesce(sum(case when entity.paidAt is not null and entity.status <> 'REFUNDED' then entity.amountFen else 0 end), 0) as totalRevenueFen,
                   coalesce(sum(case when entity.paidAt is not null and entity.status <> 'REFUNDED' then 1 else 0 end), 0) as totalRevenueOrderCount,
                   coalesce(sum(case when entity.status = 'CLOSED' then entity.amountFen else 0 end), 0) as completedIncomeFen,
                   coalesce(sum(case when entity.status = 'CLOSED' then 1 else 0 end), 0) as closedCount,
                   coalesce(sum(case when entity.status = 'REFUNDED' then entity.amountFen else 0 end), 0) as refundedAmountFen,
                   coalesce(sum(case when entity.status = 'REFUNDED' then 1 else 0 end), 0) as refundedOrderCount,
                   coalesce(sum(case when entity.status = 'ANSWERED' then 1 else 0 end), 0) as answeredCount
              from ConsultOrderEntity entity
             where entity.mentorUserId = :mentorUserId
            """)
    MentorFinanceAggregateProjection summarizeMentorFinance(@Param("mentorUserId") long mentorUserId);

    @Query("""
            select coalesce(sum(case when entity.status = 'CLOSED' then entity.amountFen else 0 end), 0)
              from ConsultOrderEntity entity
             where entity.mentorUserId = :mentorUserId
            """)
    Long sumClosedAmountFenByMentorUserId(@Param("mentorUserId") long mentorUserId);

    @Query("""
            select avg(entity.reviewRating)
              from ConsultOrderEntity entity
             where entity.mentorUserId = :mentorUserId
               and entity.reviewRating is not null
            """)
    Double findAverageReviewRatingValueByMentorUserId(@Param("mentorUserId") long mentorUserId);

    @Query("""
            select entity
              from ConsultOrderEntity entity
             where entity.mentorUserId = :mentorUserId
               and (
                   (
                       entity.status in ('PAID', 'ANSWERED', 'CLOSED')
                       and entity.paidAt is not null
                       and entity.paidAt >= :startInclusive
                       and entity.paidAt < :endExclusive
                   )
                   or
                   (
                       entity.status = 'REFUNDED'
                       and (
                           (
                               entity.closedAt is not null
                               and entity.closedAt >= :startInclusive
                               and entity.closedAt < :endExclusive
                           )
                           or
                           (
                               entity.closedAt is null
                               and entity.paidAt is not null
                               and entity.paidAt >= :startInclusive
                               and entity.paidAt < :endExclusive
                           )
                       )
                   )
               )
          order by entity.id asc
            """)
    List<ConsultOrderEntity> findFinanceTrendOrders(
            @Param("mentorUserId") long mentorUserId,
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive
    );

    interface MentorFinanceAggregateProjection {

        Long getTotalRevenueFen();

        Long getTotalRevenueOrderCount();

        Long getCompletedIncomeFen();

        Long getClosedCount();

        Long getRefundedAmountFen();

        Long getRefundedOrderCount();

        Long getAnsweredCount();
    }
}
