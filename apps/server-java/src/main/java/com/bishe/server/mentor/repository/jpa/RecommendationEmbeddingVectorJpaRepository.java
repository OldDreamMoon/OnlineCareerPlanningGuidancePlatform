package com.bishe.server.mentor.repository.jpa;

import com.bishe.server.mentor.repository.jpa.entity.RecommendationEmbeddingVectorEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecommendationEmbeddingVectorJpaRepository extends JpaRepository<RecommendationEmbeddingVectorEntity, Long> {

    Optional<RecommendationEmbeddingVectorEntity> findByEntityTypeAndEntityIdAndModelCode(
            String entityType,
            long entityId,
            String modelCode
    );
}
