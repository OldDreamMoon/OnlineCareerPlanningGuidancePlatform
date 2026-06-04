package com.bishe.server.ai.gateway.jpa;

import com.bishe.server.ai.gateway.jpa.entity.SceneRoutePolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * AI 场景路由策略 JPA 仓储。
 */
public interface SceneRoutePolicyJpaRepository extends JpaRepository<SceneRoutePolicyEntity, Long> {

    @Query("""
            SELECT p
              FROM SceneRoutePolicyEntity p
          ORDER BY p.taskType ASC,
                   p.sceneCode ASC,
                   CASE
                       WHEN p.userTier = 'ALL' THEN 0
                       WHEN p.userTier = 'FREE' THEN 1
                       WHEN p.userTier = 'PREMIUM' THEN 2
                       ELSE 3
                   END ASC,
                   p.id ASC
            """)
    List<SceneRoutePolicyEntity> findAllOrdered();

    Optional<SceneRoutePolicyEntity> findByPolicyCode(String policyCode);

    Optional<SceneRoutePolicyEntity> findByTaskTypeAndSceneCodeAndUserTier(String taskType, String sceneCode, String userTier);
}
