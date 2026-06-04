package com.bishe.server.ai.history;

import com.bishe.server.ai.interview.jpa.InterviewSessionJpaRepository;
import com.bishe.server.ai.interview.jpa.entity.InterviewSessionEntity;
import com.bishe.server.ai.quota.jpa.AiCallLogJpaRepository;
import com.bishe.server.ai.quota.jpa.entity.AiCallLogEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * AI 历史仓储：聚合 ai_call_logs 与 interview_sessions 的列表、详情与软删除能力。
 */
@Repository
@Transactional(readOnly = true)
public class AiHistoryRepository {

    private static final String RESUME_TASK_TYPE = "RESUME";
    private static final String INTERVIEW_TASK_TYPE = "INTERVIEW_TEXT";
    private static final String SUCCESS_STATUS = "SUCCESS";
    private static final Comparator<AiHistoryRow> HISTORY_ORDER = Comparator
            .comparing(AiHistoryRow::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(Comparator.comparingLong(AiHistoryRow::id).reversed());

    private final AiCallLogJpaRepository aiCallLogJpaRepository;
    private final InterviewSessionJpaRepository interviewSessionJpaRepository;

    public AiHistoryRepository(
            AiCallLogJpaRepository aiCallLogJpaRepository,
            InterviewSessionJpaRepository interviewSessionJpaRepository
    ) {
        this.aiCallLogJpaRepository = aiCallLogJpaRepository;
        this.interviewSessionJpaRepository = interviewSessionJpaRepository;
    }

    public List<AiHistoryRow> findHistory(long userId, String taskType, int limit, int offset) {
        int safeLimit = Math.max(limit, 0);
        int safeOffset = Math.max(offset, 0);
        if (safeLimit == 0) {
            return List.of();
        }

        String normalizedTaskType = normalizeTaskType(taskType);
        int fetchSize = safeLimit + safeOffset;

        if (RESUME_TASK_TYPE.equals(normalizedTaskType)) {
            return loadResumeHistoryRows(userId, fetchSize).stream()
                    .skip(safeOffset)
                    .limit(safeLimit)
                    .toList();
        }
        if (INTERVIEW_TASK_TYPE.equals(normalizedTaskType)) {
            return loadInterviewHistoryRows(userId, fetchSize).stream()
                    .skip(safeOffset)
                    .limit(safeLimit)
                    .toList();
        }
        if (normalizedTaskType != null) {
            return List.of();
        }

        return Stream.concat(
                        loadResumeHistoryRows(userId, fetchSize).stream(),
                        loadInterviewHistoryRows(userId, fetchSize).stream()
                )
                .sorted(HISTORY_ORDER)
                .skip(safeOffset)
                .limit(safeLimit)
                .toList();
    }

    public long countHistory(long userId, String taskType) {
        String normalizedTaskType = normalizeTaskType(taskType);
        if (RESUME_TASK_TYPE.equals(normalizedTaskType)) {
            return aiCallLogJpaRepository.countByUserIdAndTaskTypeAndStatusAndUserDeletedAtIsNull(
                    userId,
                    RESUME_TASK_TYPE,
                    SUCCESS_STATUS
            );
        }
        if (INTERVIEW_TASK_TYPE.equals(normalizedTaskType)) {
            return interviewSessionJpaRepository.countByStudentUserIdAndUserDeletedAtIsNull(userId);
        }
        if (normalizedTaskType != null) {
            return 0L;
        }
        return aiCallLogJpaRepository.countByUserIdAndTaskTypeAndStatusAndUserDeletedAtIsNull(
                userId,
                RESUME_TASK_TYPE,
                SUCCESS_STATUS
        ) + interviewSessionJpaRepository.countByStudentUserIdAndUserDeletedAtIsNull(userId);
    }

    public Optional<ResumeHistoryDetailRow> findResumeDetail(long userId, long recordId) {
        return aiCallLogJpaRepository.findFirstByIdAndUserIdAndTaskTypeAndStatusAndUserDeletedAtIsNull(
                        recordId,
                        userId,
                        RESUME_TASK_TYPE,
                        SUCCESS_STATUS
                )
                .map(this::toResumeDetailRow);
    }

    public Optional<ResumeHistoryDetailRow> findLatestResumeDetail(long userId) {
        return aiCallLogJpaRepository.findFirstByUserIdAndTaskTypeAndStatusAndUserDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                        userId,
                        RESUME_TASK_TYPE,
                        SUCCESS_STATUS
                )
                .map(this::toResumeDetailRow);
    }

    @Transactional
    public boolean softDeleteResumeRecord(long userId, long recordId) {
        return aiCallLogJpaRepository.softDeleteResumeRecord(userId, recordId, Instant.now()) > 0;
    }

    private List<AiHistoryRow> loadResumeHistoryRows(long userId, int fetchSize) {
        if (fetchSize <= 0) {
            return List.of();
        }
        return aiCallLogJpaRepository.findResumeHistory(userId, PageRequest.of(0, fetchSize)).stream()
                .map(this::toResumeHistoryRow)
                .toList();
    }

    private List<AiHistoryRow> loadInterviewHistoryRows(long userId, int fetchSize) {
        if (fetchSize <= 0) {
            return List.of();
        }
        return interviewSessionJpaRepository.findByStudentUserIdAndUserDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                        userId,
                        PageRequest.of(0, fetchSize)
                ).stream()
                .map(this::toInterviewHistoryRow)
                .toList();
    }

    private AiHistoryRow toResumeHistoryRow(AiCallLogEntity entity) {
        String summary = StringUtils.hasText(entity.getResultSummary()) ? entity.getResultSummary() : "简历优化";
        return new AiHistoryRow(
                entity.getId(),
                entity.getTaskType(),
                summary,
                entity.getChargedPoints(),
                null,
                null,
                entity.getCreatedAt()
        );
    }

    private AiHistoryRow toInterviewHistoryRow(InterviewSessionEntity entity) {
        String statusSuffix = "COMPLETED".equals(entity.getStatus()) ? "（已完成）" : "（进行中）";
        return new AiHistoryRow(
                entity.getId(),
                INTERVIEW_TASK_TYPE,
                "文本面试 - " + entity.getTargetRole() + statusSuffix,
                entity.getPrepaidPoints(),
                entity.getSessionId(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }

    private ResumeHistoryDetailRow toResumeDetailRow(AiCallLogEntity entity) {
        return new ResumeHistoryDetailRow(
                entity.getId(),
                entity.getResultSummary(),
                entity.getResultPayloadJson(),
                entity.getChargedPoints(),
                entity.getProvider(),
                entity.getModel(),
                entity.getLatencyMs(),
                entity.getCreatedAt()
        );
    }

    private String normalizeTaskType(String taskType) {
        if (!StringUtils.hasText(taskType)) {
            return null;
        }
        return taskType.trim().toUpperCase();
    }

    public record AiHistoryRow(
            long id,
            String taskType,
            String summary,
            int pointsConsumed,
            String sessionId,
            String sessionStatus,
            Instant createdAt
    ) {
    }

    public record ResumeHistoryDetailRow(
            long id,
            String resultSummary,
            String resultPayloadJson,
            int chargedPoints,
            String provider,
            String model,
            long latencyMs,
            Instant createdAt
    ) {
    }
}
