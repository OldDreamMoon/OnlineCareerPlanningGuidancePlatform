package com.bishe.server.ai.quota.jpa;

import com.bishe.server.ai.quota.jpa.entity.AiQuotaPolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * AI 配额策略 JPA 仓储。
 */
public interface AiQuotaPolicyJpaRepository extends JpaRepository<AiQuotaPolicyEntity, Long> {

    Optional<AiQuotaPolicyEntity> findFirstByTierAndTaskTypeAndSceneCode(String tier, String taskType, String sceneCode);

    Optional<AiQuotaPolicyEntity> findFirstByTierAndTaskTypeAndSceneCodeIsNull(String tier, String taskType);

    List<AiQuotaPolicyEntity> findAllByTierAndSceneCodeIsNullOrderByTaskTypeAsc(String tier);

    @Query("""
            SELECT policy
              FROM AiQuotaPolicyEntity policy
          ORDER BY policy.tier ASC,
                   policy.taskType ASC,
                   CASE WHEN policy.sceneCode IS NULL THEN 0 ELSE 1 END ASC,
                   policy.sceneCode ASC,
                   policy.id ASC
            """)
    List<AiQuotaPolicyEntity> findAllOrdered();
}
