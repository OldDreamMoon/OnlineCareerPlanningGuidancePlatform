package com.bishe.server.ai.gateway.jpa;

import com.bishe.server.ai.gateway.jpa.entity.AiModelRouteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * AI 模型路由 JPA 仓储。
 */
public interface AiModelRouteJpaRepository extends JpaRepository<AiModelRouteEntity, Long> {

    @Query("""
            select entity
              from AiModelRouteEntity entity
          order by entity.taskType asc,
                   case when entity.sceneCode is null or entity.sceneCode = '' then 1 else 0 end asc,
                   entity.sceneCode asc,
                   entity.priorityNo asc,
                   entity.id asc
            """)
    List<AiModelRouteEntity> findAllOrdered();

    Optional<AiModelRouteEntity> findByRouteCode(String routeCode);
}
