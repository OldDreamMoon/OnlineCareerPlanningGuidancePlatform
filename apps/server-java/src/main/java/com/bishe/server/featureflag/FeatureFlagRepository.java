package com.bishe.server.featureflag;

import com.bishe.server.featureflag.jpa.FeatureFlagJpaRepository;
import com.bishe.server.featureflag.jpa.entity.FeatureFlagEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 功能开关仓储：维护运行时可切换的业务总开关。
 */
@Repository
public class FeatureFlagRepository {

    private final FeatureFlagJpaRepository featureFlagJpaRepository;

    public FeatureFlagRepository(FeatureFlagJpaRepository featureFlagJpaRepository) {
        this.featureFlagJpaRepository = featureFlagJpaRepository;
    }

    public List<FeatureFlagRow> findAll() {
        return featureFlagJpaRepository.findAllByOrderByUpdatedAtDescFlagKeyAsc().stream()
                .map(this::toRow)
                .toList();
    }

    public Optional<FeatureFlagRow> findByKey(String key) {
        return featureFlagJpaRepository.findByFlagKey(key).map(this::toRow);
    }

    public void upsertFlag(String key, String value, String description, Long updatedBy) {
        FeatureFlagEntity entity = featureFlagJpaRepository.findByFlagKey(key)
                .orElseGet(() -> FeatureFlagEntity.create(key));
        entity.setFlagValue(value);
        entity.setDescription(description);
        entity.setUpdatedBy(updatedBy);
        featureFlagJpaRepository.saveAndFlush(entity);
    }

    private FeatureFlagRow toRow(FeatureFlagEntity entity) {
        return new FeatureFlagRow(
                entity.getFlagKey(),
                entity.getFlagValue(),
                entity.getDescription(),
                entity.getUpdatedBy(),
                entity.getUpdatedAt()
        );
    }

    public record FeatureFlagRow(
            String flagKey,
            String flagValue,
            String description,
            Long updatedBy,
            Instant updatedAt
    ) {
    }
}
