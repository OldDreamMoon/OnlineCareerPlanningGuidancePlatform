package com.bishe.server.ai.interview.jpa;

import com.bishe.server.ai.interview.jpa.entity.InterviewSessionEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 文本面试会话 JPA 仓储。
 */
public interface InterviewSessionJpaRepository extends JpaRepository<InterviewSessionEntity, Long> {

    Optional<InterviewSessionEntity> findFirstByStudentUserIdAndSessionIdAndUserDeletedAtIsNull(long studentUserId, String sessionId);

    Optional<InterviewSessionEntity> findFirstByStudentUserIdAndSummaryGeneratedFlagAndUserDeletedAtIsNullOrderBySummaryGeneratedAtDescIdDesc(
            long studentUserId,
            int summaryGeneratedFlag
    );

    List<InterviewSessionEntity> findByStudentUserIdAndUserDeletedAtIsNullOrderByCreatedAtDescIdDesc(long studentUserId, Pageable pageable);

    long countByStudentUserIdAndUserDeletedAtIsNull(long studentUserId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update InterviewSessionEntity session
               set session.replyRoundUsed = session.replyRoundUsed + 1,
                   session.updatedAt = :updatedAt
             where session.id = :sessionPk
            """)
    int incrementReplyRoundUsed(@Param("sessionPk") long sessionPk, @Param("updatedAt") Instant updatedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update InterviewSessionEntity session
               set session.replyRoundUsed = :replyRoundUsed,
                   session.updatedAt = :updatedAt
             where session.id = :sessionPk
            """)
    int setReplyRoundUsed(
            @Param("sessionPk") long sessionPk,
            @Param("replyRoundUsed") int replyRoundUsed,
            @Param("updatedAt") Instant updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update InterviewSessionEntity session
               set session.summaryGeneratedFlag = 1,
                   session.status = 'COMPLETED',
                   session.summaryOverallScore = :overallScore,
                   session.summaryStrengthsJson = :strengthsJson,
                   session.summaryWeaknessesJson = :weaknessesJson,
                   session.summarySuggestionsJson = :suggestionsJson,
                   session.summaryProvider = :provider,
                   session.summaryModel = :model,
                   session.summaryLatencyMs = :latencyMs,
                   session.summaryGeneratedAt = :generatedAt,
                   session.finishReason = :finishReason,
                   session.endedByAiFlag = :endedByAiFlag,
                   session.updatedAt = :updatedAt
             where session.id = :sessionPk
            """)
    int markSummaryGenerated(
            @Param("sessionPk") long sessionPk,
            @Param("overallScore") int overallScore,
            @Param("strengthsJson") String strengthsJson,
            @Param("weaknessesJson") String weaknessesJson,
            @Param("suggestionsJson") String suggestionsJson,
            @Param("provider") String provider,
            @Param("model") String model,
            @Param("latencyMs") long latencyMs,
            @Param("finishReason") String finishReason,
            @Param("endedByAiFlag") int endedByAiFlag,
            @Param("generatedAt") Instant generatedAt,
            @Param("updatedAt") Instant updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update InterviewSessionEntity session
               set session.userDeletedAt = :deletedAt,
                   session.status = 'DELETED',
                   session.updatedAt = :updatedAt
             where session.studentUserId = :studentUserId
               and session.sessionId = :sessionId
               and session.userDeletedAt is null
            """)
    int softDeleteSession(
            @Param("studentUserId") long studentUserId,
            @Param("sessionId") String sessionId,
            @Param("deletedAt") Instant deletedAt,
            @Param("updatedAt") Instant updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update InterviewSessionEntity session
               set session.updatedAt = :updatedAt
             where session.id = :sessionPk
            """)
    int touchSession(@Param("sessionPk") long sessionPk, @Param("updatedAt") Instant updatedAt);
}
