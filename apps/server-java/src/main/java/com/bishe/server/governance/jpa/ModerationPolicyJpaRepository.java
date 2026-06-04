package com.bishe.server.governance.jpa;

import com.bishe.server.governance.jpa.entity.ModerationPolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ModerationPolicyJpaRepository extends JpaRepository<ModerationPolicyEntity, Long> {

    Optional<ModerationPolicyEntity> findByPolicyKey(String policyKey);

    List<ModerationPolicyEntity> findAllByOrderByIdAsc();
}
