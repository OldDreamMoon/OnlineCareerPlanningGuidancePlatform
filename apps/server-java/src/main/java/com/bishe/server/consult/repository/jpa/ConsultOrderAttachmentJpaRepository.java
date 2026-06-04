package com.bishe.server.consult.repository.jpa;

import com.bishe.server.consult.repository.jpa.entity.ConsultOrderAttachmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConsultOrderAttachmentJpaRepository extends JpaRepository<ConsultOrderAttachmentEntity, Long> {

    @Query("""
            select entity
              from ConsultOrderAttachmentEntity entity
             where entity.id = :attachmentId
               and (
                   entity.orderId = :orderId
                   or (entity.orderId is null and entity.orderNo = :orderNo)
               )
            """)
    Optional<ConsultOrderAttachmentEntity> findByResolvedOrderAndId(
            @Param("orderId") Long orderId,
            @Param("orderNo") String orderNo,
            @Param("attachmentId") Long attachmentId
    );

    @Query("""
            select entity
              from ConsultOrderAttachmentEntity entity
             where entity.slotCode = :slotCode
               and entity.lifecycleStatus = :lifecycleStatus
               and (
                   entity.orderId = :orderId
                   or (entity.orderId is null and entity.orderNo = :orderNo)
               )
          order by entity.createdAt desc, entity.id desc
            """)
    List<ConsultOrderAttachmentEntity> findResolvedBySlotAndLifecycleStatus(
            @Param("orderId") Long orderId,
            @Param("orderNo") String orderNo,
            @Param("slotCode") String slotCode,
            @Param("lifecycleStatus") String lifecycleStatus
    );

    @Query("""
            select entity
              from ConsultOrderAttachmentEntity entity
             where entity.orderId = :orderId
                or (entity.orderId is null and entity.orderNo = :orderNo)
          order by entity.createdAt desc, entity.id desc
            """)
    List<ConsultOrderAttachmentEntity> findResolvedOrderByCreatedAtDescIdDesc(
            @Param("orderId") Long orderId,
            @Param("orderNo") String orderNo
    );

    @Query("""
            select entity
              from ConsultOrderAttachmentEntity entity
             where entity.lifecycleStatus = :lifecycleStatus
               and (
                   entity.orderId = :orderId
                   or (entity.orderId is null and entity.orderNo = :orderNo)
               )
          order by entity.createdAt desc, entity.id desc
            """)
    List<ConsultOrderAttachmentEntity> findResolvedOrderAndLifecycleStatusOrderByCreatedAtDescIdDesc(
            @Param("orderId") Long orderId,
            @Param("orderNo") String orderNo,
            @Param("lifecycleStatus") String lifecycleStatus
    );

    @Query("""
            select entity
              from ConsultOrderAttachmentEntity entity
             where entity.lifecycleStatus <> :lifecycleStatus
               and (
                   entity.orderId = :orderId
                   or (entity.orderId is null and entity.orderNo = :orderNo)
               )
          order by entity.createdAt desc, entity.id desc
            """)
    List<ConsultOrderAttachmentEntity> findResolvedOrderAndLifecycleStatusNotOrderByCreatedAtDescIdDesc(
            @Param("orderId") Long orderId,
            @Param("orderNo") String orderNo,
            @Param("lifecycleStatus") String lifecycleStatus
    );

    @Query("""
            select count(entity)
              from ConsultOrderAttachmentEntity entity
             where entity.lifecycleStatus = :lifecycleStatus
               and (
                   entity.orderId = :orderId
                   or (entity.orderId is null and entity.orderNo = :orderNo)
               )
            """)
    long countByResolvedOrderAndLifecycleStatus(
            @Param("orderId") Long orderId,
            @Param("orderNo") String orderNo,
            @Param("lifecycleStatus") String lifecycleStatus
    );
}
