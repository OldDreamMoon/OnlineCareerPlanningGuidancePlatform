package com.bishe.server.certification.repository.jpa;

import com.bishe.server.certification.repository.jpa.entity.CertificationSubmissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 认证提交 JPA 仓储。
 */
public interface CertificationSubmissionJpaRepository extends JpaRepository<CertificationSubmissionEntity, Long> {

    Optional<CertificationSubmissionEntity> findFirstByUserIdAndCurrentTrueOrderBySubmittedAtDescIdDesc(long userId);

    List<CertificationSubmissionEntity> findByUserIdOrderBySubmittedAtDescIdDesc(long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CertificationSubmissionEntity entity
               set entity.current = false,
                   entity.status = 'REPLACED',
                   entity.updatedAt = :updatedAt
             where entity.userId = :userId
               and entity.current = true
            """)
    int markCurrentSubmissionReplaced(
            @Param("userId") long userId,
            @Param("updatedAt") Instant updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CertificationSubmissionEntity entity
               set entity.status = :status,
                   entity.reviewNote = :reviewNote,
                   entity.reviewedBy = :reviewedBy,
                   entity.reviewedAt = :reviewedAt,
                   entity.updatedAt = :updatedAt
             where entity.id = :submissionId
            """)
    int updateSubmissionReview(
            @Param("submissionId") long submissionId,
            @Param("status") String status,
            @Param("reviewNote") String reviewNote,
            @Param("reviewedBy") Long reviewedBy,
            @Param("reviewedAt") Instant reviewedAt,
            @Param("updatedAt") Instant updatedAt
    );
}
