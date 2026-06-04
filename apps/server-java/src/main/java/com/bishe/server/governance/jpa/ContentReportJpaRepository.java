package com.bishe.server.governance.jpa;

import com.bishe.server.governance.jpa.entity.ContentReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ContentReportJpaRepository extends JpaRepository<ContentReportEntity, Long> {

    interface ClosedReportDurationView {
        Instant getCreatedAt();

        Instant getClosedAt();
    }

    @Query("""
            select report.createdAt as createdAt,
                   report.closedAt as closedAt
              from ContentReportEntity report
             where report.closedAt is not null
               and report.closedAt >= :startAt
               and report.closedAt <= :endAt
          order by report.closedAt asc, report.id asc
            """)
    List<ClosedReportDurationView> findClosedReportDurations(
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt
    );

    Optional<ContentReportEntity> findFirstByReporterUserIdAndTargetTypeAndTargetIdAndReasonCode(
            long reporterUserId,
            String targetType,
            String targetId,
            String reasonCode
    );

    long countByReporterUserIdAndCreatedAtGreaterThanEqual(long reporterUserId, Instant createdAt);

    List<ContentReportEntity> findByReporterUserIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAscIdAsc(
            long reporterUserId,
            Instant createdAt
    );

    long countByTargetTypeAndTargetIdAndStatusIn(String targetType, String targetId, Collection<String> statuses);
}
