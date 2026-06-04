package com.bishe.server.consult.repository.jpa;

import com.bishe.server.consult.repository.jpa.entity.ConsultAfterSalesRequestEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 咨询售后申请 JPA 仓储。
 */
public interface ConsultAfterSalesRequestJpaRepository extends JpaRepository<ConsultAfterSalesRequestEntity, Long> {

    List<ConsultAfterSalesRequestEntity> findByOrderNoOrderByCreatedAtDescIdDesc(String orderNo);

    @Query("""
            select entity
              from ConsultAfterSalesRequestEntity entity
             where entity.orderId = :orderId
                or (entity.orderId is null and entity.orderNo = :orderNo)
          order by entity.createdAt desc, entity.id desc
            """)
    List<ConsultAfterSalesRequestEntity> findByResolvedOrderOrderByCreatedAtDescIdDesc(
            @Param("orderId") Long orderId,
            @Param("orderNo") String orderNo
    );

    List<ConsultAfterSalesRequestEntity> findByOrderNoInOrderByOrderNoAscCreatedAtAscIdAsc(Collection<String> orderNos);

    @Query("""
            select distinct entity.orderNo
              from ConsultAfterSalesRequestEntity entity
             where entity.orderNo in :orderNos
          order by entity.orderNo asc
            """)
    List<String> findDistinctOrderNosByOrderNoIn(@Param("orderNos") Collection<String> orderNos);

    @Query("""
            select distinct entity.orderNo
              from ConsultAfterSalesRequestEntity entity
             where entity.orderNo in :orderNos
               and entity.status = :status
          order by entity.orderNo asc
            """)
    List<String> findDistinctOrderNosByOrderNoInAndStatus(
            @Param("orderNos") Collection<String> orderNos,
            @Param("status") String status
    );

    boolean existsByOrderNoAndStatus(String orderNo, String status);

    @Query("""
            select (count(entity.id) > 0)
              from ConsultAfterSalesRequestEntity entity
             where entity.status = :status
               and (
                   entity.orderId = :orderId
                   or (entity.orderId is null and entity.orderNo = :orderNo)
               )
            """)
    boolean existsByResolvedOrderAndStatus(
            @Param("orderId") Long orderId,
            @Param("orderNo") String orderNo,
            @Param("status") String status
    );

    Optional<ConsultAfterSalesRequestEntity> findFirstByOrderNoAndStatusOrderByCreatedAtAscIdAsc(String orderNo, String status);

    @Query("""
            select entity
              from ConsultAfterSalesRequestEntity entity
             where entity.status = :status
               and (
                   entity.orderId = :orderId
                   or (entity.orderId is null and entity.orderNo = :orderNo)
               )
          order by entity.createdAt asc, entity.id asc
            """)
    List<ConsultAfterSalesRequestEntity> findResolvedOrderAndStatusOrderByCreatedAtAscIdAsc(
            @Param("orderId") Long orderId,
            @Param("orderNo") String orderNo,
            @Param("status") String status,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ConsultAfterSalesRequestEntity entity
               set entity.status = :status,
                   entity.reviewNote = :reviewNote,
                   entity.reviewerUserId = :reviewerUserId,
                   entity.reviewedAt = :reviewedAt,
                   entity.updatedAt = :updatedAt
             where entity.id = :requestId
               and entity.status = 'PENDING'
            """)
    int reviewRequest(
            @Param("requestId") long requestId,
            @Param("status") String status,
            @Param("reviewNote") String reviewNote,
            @Param("reviewerUserId") Long reviewerUserId,
            @Param("reviewedAt") Instant reviewedAt,
            @Param("updatedAt") Instant updatedAt
    );

    @Query("""
            select entity.id as id,
                   entity.orderNo as orderNo,
                   orderEntity.status as orderStatus,
                   orderEntity.amountFen as amountFen,
                   orderEntity.studentUserId as studentUserId,
                   student.displayName as studentDisplayName,
                   orderEntity.mentorUserId as mentorUserId,
                   mentor.displayName as mentorDisplayName,
                   entity.requesterUserId as requesterUserId,
                   entity.requestType as requestType,
                   entity.status as status,
                   entity.reason as reason,
                   entity.reviewNote as reviewNote,
                   entity.reviewerUserId as reviewerUserId,
                   entity.autoTriggered as autoTriggered,
                   entity.createdAt as createdAt,
                   entity.reviewedAt as reviewedAt
              from ConsultAfterSalesRequestEntity entity
             join ConsultOrderEntity orderEntity
                on orderEntity.id = entity.orderId
                or (entity.orderId is null and orderEntity.orderNo = entity.orderNo)
              join UserAccountEntity student on student.id = orderEntity.studentUserId
              join UserAccountEntity mentor on mentor.id = orderEntity.mentorUserId
             where (:status = '' or entity.status = :status)
               and (
                   :keyword = ''
                   or lower(entity.orderNo) like concat('%', :keyword, '%')
                   or lower(student.displayName) like concat('%', :keyword, '%')
                   or lower(mentor.displayName) like concat('%', :keyword, '%')
               )
          order by case when entity.status = 'PENDING' then 0 else 1 end asc,
                   entity.createdAt desc,
                   entity.id desc
            """)
    List<AdminAfterSalesRequestProjection> findAdminRequests(
            @Param("keyword") String keyword,
            @Param("status") String status,
            Pageable pageable
    );

    @Query("""
            select count(entity.id)
              from ConsultAfterSalesRequestEntity entity
              join ConsultOrderEntity orderEntity
                on orderEntity.id = entity.orderId
                or (entity.orderId is null and orderEntity.orderNo = entity.orderNo)
              join UserAccountEntity student on student.id = orderEntity.studentUserId
              join UserAccountEntity mentor on mentor.id = orderEntity.mentorUserId
             where (:status = '' or entity.status = :status)
               and (
                   :keyword = ''
                   or lower(entity.orderNo) like concat('%', :keyword, '%')
                   or lower(student.displayName) like concat('%', :keyword, '%')
                   or lower(mentor.displayName) like concat('%', :keyword, '%')
               )
            """)
    long countAdminRequests(
            @Param("keyword") String keyword,
            @Param("status") String status
    );

    /**
     * 管理员售后申请列表投影。
     */
    interface AdminAfterSalesRequestProjection {

        long getId();

        String getOrderNo();

        String getOrderStatus();

        int getAmountFen();

        long getStudentUserId();

        String getStudentDisplayName();

        long getMentorUserId();

        String getMentorDisplayName();

        long getRequesterUserId();

        String getRequestType();

        String getStatus();

        String getReason();

        String getReviewNote();

        Long getReviewerUserId();

        boolean getAutoTriggered();

        Instant getCreatedAt();

        Instant getReviewedAt();
    }
}
