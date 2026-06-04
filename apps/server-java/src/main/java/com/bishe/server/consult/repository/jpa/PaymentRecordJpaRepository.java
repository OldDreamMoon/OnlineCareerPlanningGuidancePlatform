package com.bishe.server.consult.repository.jpa;

import com.bishe.server.consult.repository.jpa.entity.PaymentRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRecordJpaRepository extends JpaRepository<PaymentRecordEntity, Long> {

    List<PaymentRecordEntity> findByOrderNoOrderByIdDesc(String orderNo);

    List<PaymentRecordEntity> findByOrderNoInOrderByOrderNoAscIdDesc(Collection<String> orderNos);

    @Query("""
            select distinct payment.mode
              from PaymentRecordEntity payment
             where payment.mode is not null
               and payment.id = (
                   select max(innerPayment.id)
                     from PaymentRecordEntity innerPayment
                    where innerPayment.orderNo = payment.orderNo
               )
               and payment.orderNo in (
                   select orderEntity.orderNo
                     from ConsultOrderEntity orderEntity
                    where orderEntity.mentorUserId = :mentorUserId
               )
          order by payment.mode asc
            """)
    List<String> findDistinctLatestModesByMentorUserId(@Param("mentorUserId") long mentorUserId);

    @Query("""
            select entity
              from PaymentRecordEntity entity
             where entity.orderId = :orderId
                or (entity.orderId is null and entity.orderNo = :orderNo)
          order by entity.id desc
            """)
    List<PaymentRecordEntity> findResolvedOrderByIdDesc(
            @Param("orderId") Long orderId,
            @Param("orderNo") String orderNo
    );

    Optional<PaymentRecordEntity> findFirstByIdempotencyKey(String idempotencyKey);

    @Query("""
            select entity
              from PaymentRecordEntity entity
             where entity.status = :status
               and (
                   entity.orderId = :orderId
                   or (entity.orderId is null and entity.orderNo = :orderNo)
               )
          order by entity.id desc
            """)
    List<PaymentRecordEntity> findResolvedOrderAndStatusByIdDesc(
            @Param("orderId") Long orderId,
            @Param("orderNo") String orderNo,
            @Param("status") String status
    );
}
