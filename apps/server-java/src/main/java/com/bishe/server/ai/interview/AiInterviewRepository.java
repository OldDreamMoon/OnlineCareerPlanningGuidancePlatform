package com.bishe.server.ai.interview;

import com.bishe.server.ai.interview.jpa.InterviewMessageJpaRepository;
import com.bishe.server.ai.interview.jpa.InterviewSessionJpaRepository;
import com.bishe.server.ai.interview.jpa.entity.InterviewMessageEntity;
import com.bishe.server.ai.interview.jpa.entity.InterviewSessionEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 文本面试会话与消息仓储。
 */
@Repository
@Transactional(readOnly = true)
public class AiInterviewRepository {

    private final InterviewSessionJpaRepository interviewSessionJpaRepository;
    private final InterviewMessageJpaRepository interviewMessageJpaRepository;

    public AiInterviewRepository(
            InterviewSessionJpaRepository interviewSessionJpaRepository,
            InterviewMessageJpaRepository interviewMessageJpaRepository
    ) {
        this.interviewSessionJpaRepository = interviewSessionJpaRepository;
        this.interviewMessageJpaRepository = interviewMessageJpaRepository;
    }

    @Transactional
    public void createSession(
            String sessionId,
            long userId,
            String targetRole,
            String mode,
            String resumeContextJson,
            String sessionContextJson,
            int replyRoundLimit,
            int prepaidPoints,
            int reservedQuotaWeight
    ) {
        interviewSessionJpaRepository.saveAndFlush(InterviewSessionEntity.create(
                sessionId,
                userId,
                targetRole,
                mode,
                resumeContextJson,
                sessionContextJson,
                replyRoundLimit,
                prepaidPoints,
                reservedQuotaWeight
        ));
    }

    public Optional<InterviewSessionRow> findSessionBySessionId(long userId, String sessionId) {
        return interviewSessionJpaRepository.findFirstByStudentUserIdAndSessionIdAndUserDeletedAtIsNull(userId, sessionId)
                .map(this::toSessionRow);
    }

    public Optional<InterviewSessionRow> findLatestCompletedSummary(long userId) {
        return interviewSessionJpaRepository
                .findFirstByStudentUserIdAndSummaryGeneratedFlagAndUserDeletedAtIsNullOrderBySummaryGeneratedAtDescIdDesc(userId, 1)
                .map(this::toSessionRow);
    }

    public void insertMessage(long sessionPk, String senderRole, String messageText, Integer scoreHint) {
        insertMessage(sessionPk, senderRole, messageText, null, scoreHint, null);
    }

    public void insertMessage(long sessionPk, String senderRole, String messageText, Integer scoreHint, String audioObjectKey) {
        insertMessage(sessionPk, senderRole, messageText, null, scoreHint, audioObjectKey);
    }

    @Transactional
    public void insertMessage(
            long sessionPk,
            String senderRole,
            String messageText,
            String coachFeedback,
            Integer scoreHint,
            String audioObjectKey
    ) {
        interviewMessageJpaRepository.saveAndFlush(InterviewMessageEntity.create(
                sessionPk,
                senderRole,
                messageText,
                coachFeedback,
                scoreHint,
                audioObjectKey
        ));
        touchSession(sessionPk);
    }

    public List<InterviewMessageRow> findMessages(long sessionPk) {
        return interviewMessageJpaRepository.findBySessionPkOrderByIdAsc(sessionPk).stream()
                .map(this::toMessageRow)
                .toList();
    }

    @Transactional
    public void deleteMessages(long sessionPk) {
        interviewMessageJpaRepository.deleteBySessionPk(sessionPk);
        touchSession(sessionPk);
    }

    @Transactional
    public void incrementReplyRoundUsed(long sessionPk) {
        interviewSessionJpaRepository.incrementReplyRoundUsed(sessionPk, Instant.now());
    }

    @Transactional
    public void setReplyRoundUsed(long sessionPk, int replyRoundUsed) {
        interviewSessionJpaRepository.setReplyRoundUsed(sessionPk, replyRoundUsed, Instant.now());
    }

    @Transactional
    public void markSummaryGenerated(
            long sessionPk,
            int overallScore,
            String strengthsJson,
            String weaknessesJson,
            String suggestionsJson,
            String provider,
            String model,
            long latencyMs,
            String finishReason,
            boolean endedByAi
    ) {
        Instant now = Instant.now();
        interviewSessionJpaRepository.markSummaryGenerated(
                sessionPk,
                overallScore,
                strengthsJson,
                weaknessesJson,
                suggestionsJson,
                provider,
                model,
                latencyMs,
                finishReason,
                endedByAi ? 1 : 0,
                now,
                now
        );
    }

    @Transactional
    public boolean softDeleteSession(long userId, String sessionId) {
        Instant now = Instant.now();
        return interviewSessionJpaRepository.softDeleteSession(userId, sessionId, now, now) > 0;
    }

    @Transactional
    public void touchSession(long sessionPk) {
        interviewSessionJpaRepository.touchSession(sessionPk, Instant.now());
    }

    private InterviewSessionRow toSessionRow(InterviewSessionEntity entity) {
        return new InterviewSessionRow(
                entity.getId(),
                entity.getSessionId(),
                entity.getStudentUserId(),
                entity.getTargetRole(),
                entity.getMode(),
                entity.getResumeContextJson(),
                entity.getSessionContextJson(),
                entity.getStatus(),
                entity.getReplyRoundLimit(),
                entity.getReplyRoundUsed(),
                entity.isSummaryGenerated(),
                entity.getPrepaidPoints(),
                entity.getReservedQuotaWeight(),
                entity.getSummaryOverallScore(),
                entity.getSummaryStrengthsJson(),
                entity.getSummaryWeaknessesJson(),
                entity.getSummarySuggestionsJson(),
                entity.getSummaryProvider(),
                entity.getSummaryModel(),
                entity.getSummaryLatencyMs(),
                toTimestamp(entity.getSummaryGeneratedAt()),
                entity.getFinishReason(),
                entity.isEndedByAi(),
                toTimestamp(entity.getUserDeletedAt()),
                toTimestamp(entity.getCreatedAt()),
                toTimestamp(entity.getUpdatedAt())
        );
    }

    private InterviewMessageRow toMessageRow(InterviewMessageEntity entity) {
        return new InterviewMessageRow(
                entity.getId(),
                entity.getSessionPk(),
                entity.getSenderRole(),
                entity.getMessageText(),
                entity.getCoachFeedback(),
                entity.getScoreHint(),
                entity.getAudioObjectKey(),
                toTimestamp(entity.getCreatedAt())
        );
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    public record InterviewSessionRow(
            long id,
            String sessionId,
            long studentUserId,
            String targetRole,
            String mode,
            String resumeContextJson,
            String sessionContextJson,
            String status,
            int replyRoundLimit,
            int replyRoundUsed,
            boolean summaryGenerated,
            int prepaidPoints,
            int reservedQuotaWeight,
            Integer summaryOverallScore,
            String summaryStrengthsJson,
            String summaryWeaknessesJson,
            String summarySuggestionsJson,
            String summaryProvider,
            String summaryModel,
            Long summaryLatencyMs,
            Timestamp summaryGeneratedAt,
            String finishReason,
            boolean endedByAi,
            Timestamp userDeletedAt,
            Timestamp createdAt,
            Timestamp updatedAt
    ) {
    }

    public record InterviewMessageRow(
            long id,
            long sessionPk,
            String senderRole,
            String messageText,
            String coachFeedback,
            Integer scoreHint,
            String audioObjectKey,
            Timestamp createdAt
    ) {
    }
}
