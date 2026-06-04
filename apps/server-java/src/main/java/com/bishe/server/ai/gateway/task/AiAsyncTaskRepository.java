package com.bishe.server.ai.gateway.task;

import com.bishe.server.ai.gateway.task.jpa.AiAsyncTaskEventJpaRepository;
import com.bishe.server.ai.gateway.task.jpa.AiAsyncTaskJobJpaRepository;
import com.bishe.server.ai.gateway.task.jpa.entity.AiAsyncTaskEventEntity;
import com.bishe.server.ai.gateway.task.jpa.entity.AiAsyncTaskJobEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 异步 AI 任务仓储。
 */
@Repository
public class AiAsyncTaskRepository {

    private static final List<String> CLAIMABLE_STATUSES = List.of(
            AiAsyncTaskStatus.PENDING.name(),
            AiAsyncTaskStatus.RETRY_WAIT.name()
    );
    private static final List<String> TERMINAL_STATUSES = List.of(
            AiAsyncTaskStatus.SUCCEEDED.name(),
            AiAsyncTaskStatus.FAILED.name(),
            AiAsyncTaskStatus.CANCELLED.name()
    );

    private final AiAsyncTaskJobJpaRepository aiAsyncTaskJobJpaRepository;
    private final AiAsyncTaskEventJpaRepository aiAsyncTaskEventJpaRepository;

    public AiAsyncTaskRepository(
            AiAsyncTaskJobJpaRepository aiAsyncTaskJobJpaRepository,
            AiAsyncTaskEventJpaRepository aiAsyncTaskEventJpaRepository
    ) {
        this.aiAsyncTaskJobJpaRepository = aiAsyncTaskJobJpaRepository;
        this.aiAsyncTaskEventJpaRepository = aiAsyncTaskEventJpaRepository;
    }

    public long insertJob(
            String taskId,
            long userId,
            String taskType,
            String sceneCode,
            String routeCode,
            String executionMode,
            String status,
            String providerCode,
            String providerType,
            String modelName,
            String promptTemplateName,
            Integer promptTemplateVersionNo,
            String inputSnapshotJson,
            String contextJson,
            String promptSnapshotJson,
            String routeSnapshotJson,
            int maxAttempts,
            Timestamp nextRunAt
    ) {
        AiAsyncTaskJobEntity entity = AiAsyncTaskJobEntity.create(
                taskId,
                userId,
                taskType,
                sceneCode,
                routeCode,
                executionMode,
                status,
                providerCode,
                providerType,
                modelName,
                promptTemplateName,
                promptTemplateVersionNo,
                inputSnapshotJson,
                contextJson,
                promptSnapshotJson,
                routeSnapshotJson,
                maxAttempts,
                toInstant(nextRunAt)
        );
        aiAsyncTaskJobJpaRepository.saveAndFlush(entity);
        return entity.getId() == null ? 0L : entity.getId();
    }

    public long insertEvent(
            String eventId,
            long taskJobId,
            String taskId,
            long userId,
            String eventType,
            String deliveryStatus,
            String payloadJson
    ) {
        AiAsyncTaskEventEntity entity = AiAsyncTaskEventEntity.create(
                eventId,
                taskJobId,
                taskId,
                userId,
                eventType,
                deliveryStatus,
                payloadJson
        );
        aiAsyncTaskEventJpaRepository.saveAndFlush(entity);
        return entityId(entity);
    }

    public Optional<AsyncTaskJobRow> findJobById(long id) {
        return aiAsyncTaskJobJpaRepository.findById(id).map(this::toAsyncTaskJobRow);
    }

    public Optional<AsyncTaskJobRow> findJobByTaskId(String taskId) {
        return aiAsyncTaskJobJpaRepository.findByTaskId(taskId).map(this::toAsyncTaskJobRow);
    }

    public Optional<AsyncTaskJobRow> findJobByTaskIdAndUserId(String taskId, long userId) {
        return aiAsyncTaskJobJpaRepository.findByTaskIdAndUserId(taskId, userId).map(this::toAsyncTaskJobRow);
    }

    public List<Long> findRunnableJobIds(Timestamp now, int limit) {
        return aiAsyncTaskJobJpaRepository.findRunnableJobIds(
                CLAIMABLE_STATUSES,
                toInstant(now),
                PageRequest.of(0, Math.max(limit, 1))
        );
    }

    public boolean claimJob(long jobId, String workerId, Timestamp now, Timestamp leaseExpiresAt) {
        Instant claimAt = toInstant(now);
        Instant updatedAt = Instant.now();
        return aiAsyncTaskJobJpaRepository.claimJob(
                jobId,
                workerId,
                toInstant(leaseExpiresAt),
                claimAt,
                claimAt,
                updatedAt,
                AiAsyncTaskStatus.RUNNING.name(),
                CLAIMABLE_STATUSES
        ) > 0;
    }

    public boolean markSucceeded(long jobId, String resultSummary, String resultPayloadJson, Timestamp finishedAt) {
        return aiAsyncTaskJobJpaRepository.markSucceeded(
                jobId,
                resultSummary,
                resultPayloadJson,
                toInstant(finishedAt),
                Instant.now(),
                AiAsyncTaskStatus.SUCCEEDED.name()
        ) > 0;
    }

    public boolean markRetryWait(long jobId, String errorCode, String errorMessage, Timestamp nextRunAt) {
        return aiAsyncTaskJobJpaRepository.markRetryWait(
                jobId,
                errorCode,
                errorMessage,
                toInstant(nextRunAt),
                Instant.now(),
                AiAsyncTaskStatus.RETRY_WAIT.name()
        ) > 0;
    }

    public boolean markFailed(long jobId, String errorCode, String errorMessage, Timestamp finishedAt) {
        return aiAsyncTaskJobJpaRepository.markFailed(
                jobId,
                errorCode,
                errorMessage,
                toInstant(finishedAt),
                Instant.now(),
                AiAsyncTaskStatus.FAILED.name()
        ) > 0;
    }

    public List<Long> findTerminalJobIdsBefore(Timestamp beforeTime, int limit) {
        Instant cutoff = beforeTime == null ? Instant.now() : beforeTime.toInstant();
        return aiAsyncTaskJobJpaRepository.findTerminalJobIdsBefore(
                TERMINAL_STATUSES,
                cutoff,
                PageRequest.of(0, Math.max(limit, 1))
        );
    }

    public long deleteEventsByJobIds(List<Long> jobIds) {
        return executeChunkedDelete(jobIds, true);
    }

    public long deleteJobsByIds(List<Long> jobIds) {
        return executeChunkedDelete(jobIds, false);
    }

    private AsyncTaskJobRow toAsyncTaskJobRow(AiAsyncTaskJobEntity entity) {
        return new AsyncTaskJobRow(
                entityId(entity),
                entity.getTaskId(),
                entity.getUserId() == null ? 0L : entity.getUserId(),
                entity.getTaskType(),
                entity.getSceneCode(),
                entity.getRouteCode(),
                entity.getExecutionMode(),
                entity.getStatus(),
                entity.getProviderCode(),
                entity.getProviderType(),
                entity.getModelName(),
                entity.getPromptTemplateName(),
                entity.getPromptTemplateVersionNo(),
                entity.getInputSnapshotJson(),
                entity.getContextJson(),
                entity.getPromptSnapshotJson(),
                entity.getRouteSnapshotJson(),
                entity.getResultSummary(),
                entity.getResultPayloadJson(),
                entity.getErrorCode(),
                entity.getErrorMessage(),
                entity.getCurrentAttempt() == null ? 0 : entity.getCurrentAttempt(),
                entity.getMaxAttempts() == null ? 0 : entity.getMaxAttempts(),
                entity.getNextRunAt(),
                entity.getLeaseOwner(),
                entity.getLeaseExpiresAt(),
                entity.getQueuedAt(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private static long entityId(AiAsyncTaskJobEntity entity) {
        return entity.getId() == null ? 0L : entity.getId();
    }

    private static long entityId(AiAsyncTaskEventEntity entity) {
        return entity.getId() == null ? 0L : entity.getId();
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private long executeChunkedDelete(List<Long> ids, boolean deleteEvents) {
        if (ids == null || ids.isEmpty()) {
            return 0L;
        }
        long deletedCount = 0L;
        int chunkSize = 200;
        for (int start = 0; start < ids.size(); start += chunkSize) {
            List<Long> chunk = ids.subList(start, Math.min(start + chunkSize, ids.size()));
            deletedCount += deleteEvents
                    ? aiAsyncTaskEventJpaRepository.deleteByTaskJobIdIn(chunk)
                    : aiAsyncTaskJobJpaRepository.deleteByIdIn(chunk);
        }
        return deletedCount;
    }

    public record AsyncTaskJobRow(
            long id,
            String taskId,
            long userId,
            String taskType,
            String sceneCode,
            String routeCode,
            String executionMode,
            String status,
            String providerCode,
            String providerType,
            String modelName,
            String promptTemplateName,
            Integer promptTemplateVersionNo,
            String inputSnapshotJson,
            String contextJson,
            String promptSnapshotJson,
            String routeSnapshotJson,
            String resultSummary,
            String resultPayloadJson,
            String errorCode,
            String errorMessage,
            int currentAttempt,
            int maxAttempts,
            Instant nextRunAt,
            String leaseOwner,
            Instant leaseExpiresAt,
            Instant queuedAt,
            Instant startedAt,
            Instant finishedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
