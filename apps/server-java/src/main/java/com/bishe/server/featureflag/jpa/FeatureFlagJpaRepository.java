package com.bishe.server.featureflag.jpa;

import com.bishe.server.featureflag.jpa.entity.FeatureFlagEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 功能开关 JPA 仓储。
 */
public interface FeatureFlagJpaRepository extends JpaRepository<FeatureFlagEntity, Long> {

    List<FeatureFlagEntity> findAllByOrderByUpdatedAtDescFlagKeyAsc();

    Optional<FeatureFlagEntity> findByFlagKey(String flagKey);
}
