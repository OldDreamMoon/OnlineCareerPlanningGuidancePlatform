package com.bishe.server.governance.jpa;

import com.bishe.server.governance.jpa.entity.SensitiveTermEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SensitiveTermJpaRepository extends JpaRepository<SensitiveTermEntity, Long> {

    @Query("""
            select entity
              from SensitiveTermEntity entity
             where entity.enabled = true
               and (entity.sourceScope = :sourceScope or entity.sourceScope = 'ALL')
          order by entity.whitelist desc,
                   length(entity.term) desc,
                   entity.id asc
            """)
    List<SensitiveTermEntity> findEnabledTermsForSource(@Param("sourceScope") String sourceScope);

    @Query("""
            select entity
              from SensitiveTermEntity entity
          order by entity.enabled desc,
                   entity.whitelist desc,
                   entity.updatedAt desc,
                   entity.id desc
            """)
    List<SensitiveTermEntity> findAllForGovernance();

    Optional<SensitiveTermEntity> findFirstByTermAndSourceScopeAndWhitelist(String term, String sourceScope, boolean whitelist);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update SensitiveTermEntity entity
               set entity.enabled = :enabled,
                   entity.updatedAt = :updatedAt
             where entity.id in :ids
            """)
    int updateEnabledFlagByIdIn(
            @Param("ids") Collection<Long> ids,
            @Param("enabled") boolean enabled,
            @Param("updatedAt") Instant updatedAt
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from SensitiveTermEntity entity where entity.id in :ids")
    int deleteByIdIn(@Param("ids") Collection<Long> ids);
}
