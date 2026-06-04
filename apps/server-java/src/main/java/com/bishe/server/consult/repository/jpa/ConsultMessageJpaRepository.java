package com.bishe.server.consult.repository.jpa;

import com.bishe.server.consult.repository.jpa.entity.ConsultMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ConsultMessageJpaRepository extends JpaRepository<ConsultMessageEntity, Long> {

    @Query("""
            select entity
              from ConsultMessageEntity entity
             where entity.orderId = :orderId
                or (entity.orderId is null and entity.orderNo = :orderNo)
          order by entity.createdAt asc, entity.id asc
            """)
    List<ConsultMessageEntity> findByResolvedOrderOrderByCreatedAtAscIdAsc(
            @Param("orderId") Long orderId,
            @Param("orderNo") String orderNo
    );
}
