package com.bishe.server.consult.repository.jpa;

import com.bishe.server.consult.repository.jpa.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AuditLogJpaRepository extends JpaRepository<AuditLogEntity, Long> {

    Optional<AuditLogEntity> findFirstByActionTypeAndTargetTypeAndTargetIdOrderByIdDesc(
            String actionType,
            String targetType,
            String targetId
    );

    List<AuditLogEntity> findByTargetTypeAndTargetIdInAndActionTypeInOrderByTargetIdAscIdDesc(
            String targetType,
            Collection<String> targetIds,
            Collection<String> actionTypes
    );

    Optional<AuditLogEntity> findFirstByTargetTypeAndTargetIdAndActionTypeInOrderByIdDesc(
            String targetType,
            String targetId,
            Collection<String> actionTypes
    );
}
