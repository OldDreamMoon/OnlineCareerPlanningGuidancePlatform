package com.bishe.server.mentor.schedule.repository.jpa;

import com.bishe.server.mentor.schedule.repository.jpa.entity.MentorScheduleSlotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.time.Instant;
import java.util.List;

/**
 * 导师排期槽位 JPA 仓储。
 */
public interface MentorScheduleSlotJpaRepository
        extends JpaRepository<MentorScheduleSlotEntity, Long>, JpaSpecificationExecutor<MentorScheduleSlotEntity> {

    List<MentorScheduleSlotEntity> findByMentorUserIdInAndStartAtGreaterThanEqualOrderByMentorUserIdAscStartAtAscIdAsc(
            Collection<Long> mentorUserIds,
            Instant startAt
    );

    @Query("""
            select distinct entity.mentorUserId
              from MentorScheduleSlotEntity entity
             where (
                       entity.bookedOrderId = :orderId
                       or (entity.bookedOrderId is null and entity.bookedOrderNo = :orderNo)
                   )
               and entity.status = 'BOOKED'
            """)
    List<Long> findBookedMentorUserIdsByResolvedOrder(
            @Param("orderId") Long orderId,
            @Param("orderNo") String orderNo
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update MentorScheduleSlotEntity entity
               set entity.status = 'BOOKED',
                   entity.bookedOrderId = :orderId,
                   entity.bookedOrderNo = :orderNo,
                   entity.updatedAt = :updatedAt
             where entity.id = :slotId
               and entity.mentorUserId = :mentorUserId
               and entity.status = 'AVAILABLE'
            """)
    int reserveSlot(
            @Param("slotId") long slotId,
            @Param("mentorUserId") long mentorUserId,
            @Param("orderId") Long orderId,
            @Param("orderNo") String orderNo,
            @Param("updatedAt") Instant updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update MentorScheduleSlotEntity entity
               set entity.bookedOrderId = :orderId,
                   entity.updatedAt = :updatedAt
             where entity.bookedOrderId is null
               and entity.bookedOrderNo = :orderNo
               and entity.status = 'BOOKED'
            """)
    int bindBookedOrderIdByOrderNo(
            @Param("orderId") Long orderId,
            @Param("orderNo") String orderNo,
            @Param("updatedAt") Instant updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update MentorScheduleSlotEntity entity
               set entity.status = 'AVAILABLE',
                   entity.bookedOrderId = null,
                   entity.bookedOrderNo = null,
                   entity.updatedAt = :updatedAt
             where (
                       entity.bookedOrderId = :orderId
                       or (entity.bookedOrderId is null and entity.bookedOrderNo = :orderNo)
                   )
               and entity.status = 'BOOKED'
            """)
    int releaseSlotByResolvedOrder(
            @Param("orderId") Long orderId,
            @Param("orderNo") String orderNo,
            @Param("updatedAt") Instant updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update MentorScheduleSlotEntity entity
               set entity.status = 'AVAILABLE',
                   entity.bookedOrderId = null,
                   entity.bookedOrderNo = null,
                   entity.updatedAt = :updatedAt
             where (
                       entity.bookedOrderId = :orderId
                       or (entity.bookedOrderId is null and entity.bookedOrderNo = :orderNo)
                   )
               and entity.status = 'BOOKED'
               and entity.endAt > :now
            """)
    int releaseUpcomingSlotByResolvedOrder(
            @Param("orderId") Long orderId,
            @Param("orderNo") String orderNo,
            @Param("now") Instant now,
            @Param("updatedAt") Instant updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete
              from MentorScheduleSlotEntity entity
             where entity.id = :slotId
               and entity.mentorUserId = :mentorUserId
               and entity.status = 'AVAILABLE'
            """)
    int deleteAvailableSlot(
            @Param("slotId") long slotId,
            @Param("mentorUserId") long mentorUserId
    );
}
