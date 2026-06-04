package com.bishe.server.ai.quota.jpa;

import com.bishe.server.ai.quota.jpa.entity.AiCallLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * AI 调用日志 JPA 仓储。
 */
public interface AiCallLogJpaRepository extends JpaRepository<AiCallLogEntity, Long> {

    @Query("""
            SELECT COALESCE(SUM(log.quotaWeight), 0)
              FROM AiCallLogEntity log
             WHERE log.userId = :userId
               AND log.taskType = :taskType
               AND log.status = 'SUCCESS'
               AND (:sceneCode IS NULL OR log.sceneCode = :sceneCode)
               AND log.createdAt >= :startAt
               AND log.createdAt <= :endAt
            """)
    Long sumSuccessfulQuotaWeightForDay(
            @Param("userId") long userId,
            @Param("taskType") String taskType,
            @Param("sceneCode") String sceneCode,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt
    );

    @Query("""
            select log
              from AiCallLogEntity log
             where log.userId = :userId
               and log.taskType = 'RESUME'
               and log.status = 'SUCCESS'
               and log.userDeletedAt is null
          order by log.createdAt desc, log.id desc
            """)
    List<AiCallLogEntity> findResumeHistory(@Param("userId") long userId, Pageable pageable);

    long countByUserIdAndTaskTypeAndStatusAndUserDeletedAtIsNull(long userId, String taskType, String status);

    Optional<AiCallLogEntity> findFirstByIdAndUserIdAndTaskTypeAndStatusAndUserDeletedAtIsNull(
            long id,
            long userId,
            String taskType,
            String status
    );

    Optional<AiCallLogEntity> findFirstByUserIdAndTaskTypeAndStatusAndUserDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            long userId,
            String taskType,
            String status
    );

    long countByUserIdAndTaskTypeAndStatusAndUserDeletedAtIsNullAndCreatedAtBetween(
            long userId,
            String taskType,
            String status,
            Instant startAt,
            Instant endAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update AiCallLogEntity log
               set log.userDeletedAt = :deletedAt
             where log.id = :recordId
               and log.userId = :userId
               and log.taskType = 'RESUME'
               and log.status = 'SUCCESS'
               and log.userDeletedAt is null
            """)
    int softDeleteResumeRecord(
            @Param("userId") long userId,
            @Param("recordId") long recordId,
            @Param("deletedAt") Instant deletedAt
    );
}
