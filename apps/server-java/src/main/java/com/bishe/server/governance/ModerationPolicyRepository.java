package com.bishe.server.governance;

import com.bishe.server.governance.jpa.ModerationPolicyJpaRepository;
import com.bishe.server.governance.jpa.entity.ModerationPolicyEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Repository
@Transactional(readOnly = true)
public class ModerationPolicyRepository {

    private final ModerationPolicyJpaRepository moderationPolicyJpaRepository;

    public ModerationPolicyRepository(ModerationPolicyJpaRepository moderationPolicyJpaRepository) {
        this.moderationPolicyJpaRepository = moderationPolicyJpaRepository;
    }

    public Map<String, String> findPolicyMap() {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        moderationPolicyJpaRepository.findAllByOrderByIdAsc().forEach(entity -> {
            if (entity.getPolicyKey() != null && entity.getPolicyValue() != null) {
                result.put(entity.getPolicyKey(), entity.getPolicyValue());
            }
        });
        return result;
    }

    @Transactional
    public void upsertPolicy(String policyKey, String policyValue, String description, long updatedBy) {
        ModerationPolicyEntity entity = moderationPolicyJpaRepository.findByPolicyKey(policyKey)
                .map(existing -> {
                    existing.update(policyValue, description, updatedBy);
                    return existing;
                })
                .orElseGet(() -> ModerationPolicyEntity.create(policyKey, policyValue, description, updatedBy));
        moderationPolicyJpaRepository.save(entity);
    }
}
