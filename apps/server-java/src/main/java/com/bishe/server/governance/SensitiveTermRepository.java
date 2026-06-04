package com.bishe.server.governance;

import com.bishe.server.governance.jpa.SensitiveTermJpaRepository;
import com.bishe.server.governance.jpa.entity.SensitiveTermEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional(readOnly = true)
public class SensitiveTermRepository {

    private final SensitiveTermJpaRepository sensitiveTermJpaRepository;

    public SensitiveTermRepository(SensitiveTermJpaRepository sensitiveTermJpaRepository) {
        this.sensitiveTermJpaRepository = sensitiveTermJpaRepository;
    }

    public List<SensitiveTermRow> findEnabledTermsForSource(String sourceScope) {
        return sensitiveTermJpaRepository.findEnabledTermsForSource(sourceScope).stream()
                .map(this::toRow)
                .toList();
    }

    public List<SensitiveTermRow> listSensitiveTerms() {
        return sensitiveTermJpaRepository.findAllForGovernance().stream()
                .map(this::toRow)
                .toList();
    }

    public Optional<SensitiveTermRow> findSensitiveTerm(long termId) {
        return sensitiveTermJpaRepository.findById(termId).map(this::toRow);
    }

    public Optional<Long> findSensitiveTermId(String term, String sourceScope, boolean whitelist) {
        return sensitiveTermJpaRepository.findFirstByTermAndSourceScopeAndWhitelist(term, sourceScope, whitelist)
                .map(SensitiveTermEntity::getId);
    }

    @Transactional
    public long insertSensitiveTerm(
            String term,
            String termType,
            String riskLevel,
            String action,
            String sourceScope,
            boolean whitelist,
            boolean enabled
    ) {
        try {
            SensitiveTermEntity entity = SensitiveTermEntity.create(
                    term,
                    termType,
                    riskLevel,
                    action,
                    sourceScope,
                    whitelist,
                    enabled
            );
            return sensitiveTermJpaRepository.save(entity).getId();
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateKeyException("sensitive term already exists", ex);
        }
    }

    @Transactional
    public int updateSensitiveTerm(
            long termId,
            String term,
            String termType,
            String riskLevel,
            String action,
            String sourceScope,
            boolean whitelist,
            boolean enabled
    ) {
        return sensitiveTermJpaRepository.findById(termId)
                .map(entity -> {
                    try {
                        entity.update(term, termType, riskLevel, action, sourceScope, whitelist, enabled);
                        sensitiveTermJpaRepository.save(entity);
                        return 1;
                    } catch (DataIntegrityViolationException ex) {
                        throw new DuplicateKeyException("sensitive term already exists", ex);
                    }
                })
                .orElse(0);
    }

    @Transactional
    public int deleteSensitiveTerm(long termId) {
        if (!sensitiveTermJpaRepository.existsById(termId)) {
            return 0;
        }
        sensitiveTermJpaRepository.deleteById(termId);
        return 1;
    }

    @Transactional
    public int updateSensitiveTermsEnabled(List<Long> termIds, boolean enabled) {
        if (termIds == null || termIds.isEmpty()) {
            return 0;
        }
        return sensitiveTermJpaRepository.updateEnabledFlagByIdIn(termIds, enabled, Instant.now());
    }

    @Transactional
    public int batchDeleteSensitiveTerms(List<Long> termIds) {
        if (termIds == null || termIds.isEmpty()) {
            return 0;
        }
        return sensitiveTermJpaRepository.deleteByIdIn(termIds);
    }

    private SensitiveTermRow toRow(SensitiveTermEntity entity) {
        return new SensitiveTermRow(
                entity.getId(),
                entity.getTerm(),
                entity.getTermType(),
                entity.getRiskLevel(),
                entity.getAction(),
                entity.getSourceScope(),
                entity.isWhitelist(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
