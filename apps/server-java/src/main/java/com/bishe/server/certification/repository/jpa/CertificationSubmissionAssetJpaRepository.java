package com.bishe.server.certification.repository.jpa;

import com.bishe.server.certification.repository.jpa.entity.CertificationSubmissionAssetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * 认证提交附件 JPA 仓储。
 */
public interface CertificationSubmissionAssetJpaRepository extends JpaRepository<CertificationSubmissionAssetEntity, Long> {

    List<CertificationSubmissionAssetEntity> findBySubmissionIdInOrderByCreatedAtDescIdDesc(Collection<Long> submissionIds);

    List<CertificationSubmissionAssetEntity> findBySubmissionIdAndLifecycleStatusOrderByCreatedAtDescIdDesc(long submissionId, String lifecycleStatus);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CertificationSubmissionAssetEntity entity
               set entity.lifecycleStatus = :lifecycleStatus,
                   entity.deleteReason = :deleteReason,
                   entity.deletedAt = :deletedAt,
                   entity.updatedAt = :updatedAt
             where entity.id in :assetIds
            """)
    int updateLifecycle(
            @Param("assetIds") Collection<Long> assetIds,
            @Param("lifecycleStatus") String lifecycleStatus,
            @Param("deleteReason") String deleteReason,
            @Param("deletedAt") Instant deletedAt,
            @Param("updatedAt") Instant updatedAt
    );
}
