package com.bishe.server.growth.repository.jpa;

import com.bishe.server.growth.repository.jpa.entity.PointsLedgerEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 积分账本 JPA 仓储。
 */
public interface PointsLedgerJpaRepository extends JpaRepository<PointsLedgerEntity, Long> {

    boolean existsByStudentUserIdAndReasonCodeAndCreatedAtBetween(
            long studentUserId,
            String reasonCode,
            Instant startAt,
            Instant endAt
    );

    @Query("""
            select distinct ledger.reasonCode
              from PointsLedgerEntity ledger
             where ledger.studentUserId = :studentUserId
               and ledger.reasonCode in :reasonCodes
               and ledger.createdAt >= :startAt
               and ledger.createdAt <= :endAt
            """)
    List<String> findDistinctReasonCodesForDay(
            @Param("studentUserId") long studentUserId,
            @Param("reasonCodes") Collection<String> reasonCodes,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt
    );

    Optional<PointsLedgerEntity> findFirstByStudentUserIdOrderByIdDesc(long studentUserId);

    @Query("""
            select ledger
              from PointsLedgerEntity ledger
             where ledger.studentUserId = :studentUserId
          order by ledger.id desc
            """)
    List<PointsLedgerEntity> findLedgerRecords(
            @Param("studentUserId") long studentUserId,
            Pageable pageable
    );

    long countByStudentUserId(long studentUserId);
}
