package com.bishe.server.ai.gateway.task;

import com.bishe.server.ai.gateway.AiExecutionMode;
import com.bishe.server.common.TimePayloads;
import com.bishe.server.common.exception.ApiException;
import com.bishe.server.notification.model.NotificationCategory;
import com.bishe.server.notification.model.NotificationPriority;
import com.bishe.server.notification.service.NotificationService;
import com.bishe.server.notification.service.PlatformNotificationPublishService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 异步 AI 任务服务：负责提交任务、认领任务、推进状态和输出事件。
 */
@Service
public class AiAsyncTaskService {

    private static final String DEFAULT_EVENT_DELIVERY_STATUS = "PENDING";

    private final AiAsyncTaskRepository repository;
    private final AiAsyncTaskCoordinationService coordinationService;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    public AiAsyncTaskService(
            AiAsyncTaskRepository repository,
            AiAsyncTaskCoordinationService coordinationService,
            ObjectMapper objectMapper,
            NotificationService notificationService
    ) {
        this.repository = repository;
        this.coordinationService = coordinationService;
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
    }

    @Transactional
    public TaskTicket submitTask(SubmitTaskCommand command) {
        // 提交阶段只接受异步任务模式，避免同步调用误写入任务表。
        AiExecutionMode executionMode = normalizeExecutionMode(command.executionMode());
        if (executionMode != AiExecutionMode.ASYNC_JOB) {
            throw new ApiException("BIZ-1001", "async task executionMode must be ASYNC_JOB", HttpStatus.BAD_REQUEST);
        }
        String taskType = requireText(command.taskType(), "taskType required", 40);
        long userId = command.userId() == null ? 0L : command.userId();
        if (userId <= 0) {
            throw new ApiException("BIZ-1001", "userId invalid", HttpStatus.BAD_REQUEST);
        }
        String taskId = "aitk_" + UUID.randomUUID().toString().replace("-", "");
        Timestamp now = Timestamp.from(Instant.now());
        // 把输入、上下文、Prompt 和路由快照一起固化，后续复盘时能解释这次任务如何执行。
        long jobId = repository.insertJob(
                taskId,
                userId,
                taskType,
                normalizeOptional(command.sceneCode(), 60),
                normalizeOptional(command.routeCode(), 60),
                executionMode.name(),
                AiAsyncTaskStatus.PENDING.name(),
                normalizeOptional(command.providerCode(), 60),
                normalizeOptional(command.providerType(), 40),
                normalizeOptional(command.modelName(), 120),
                normalizeOptional(command.promptTemplateName(), 80),
                command.promptTemplateVersionNo(),
                normalizeJson(command.inputSnapshotJson()),
                normalizeJson(command.contextJson()),
                normalizeJson(command.promptSnapshotJson()),
                normalizeJson(command.routeSnapshotJson()),
                command.maxAttempts() == null ? 3 : Math.max(command.maxAttempts(), 1),
                now
        );
        TaskSnapshot snapshot = repository.findJobById(jobId)
                .map(this::toSnapshot)
                .orElseThrow(() -> new ApiException("AI-2001", "async task submit failed", HttpStatus.INTERNAL_SERVER_ERROR));
        coordinationService.schedulePendingAfterCommit(snapshot.id(), snapshot.nextRunAt());
        // 事件表保留任务生命周期轨迹，管理员排查时不用只看最终状态。
        appendEvent(snapshot, AiAsyncTaskEventType.SUBMITTED, Map.of(
                "status", snapshot.status().name(),
                "taskType", snapshot.taskType(),
                "sceneCode", safe(snapshot.sceneCode())
        ));
        return new TaskTicket(snapshot.taskId(), snapshot.status().name(), TimePayloads.toEpochMillis(snapshot.createdAt()));
    }

    @Transactional(readOnly = true)
    public Optional<TaskSnapshot> findTask(String taskId) {
        // 管理端查询不限定用户，学生侧查询走 findTaskForUser 防止越权。
        return repository.findJobByTaskId(taskId).map(this::toSnapshot);
    }

    @Transactional(readOnly = true)
    public Optional<TaskSnapshot> findTaskForUser(long userId, String taskId) {
        // 学生查询任务时必须带 userId 条件，taskId 不能作为越权读取凭据。
        return repository.findJobByTaskIdAndUserId(taskId, userId).map(this::toSnapshot);
    }

    @Transactional
    public List<TaskSnapshot> claimRunnableTasks(String workerId, int limit, Duration leaseDuration) {
        Instant now = Instant.now();
        Duration effectiveLease = leaseDuration == null || leaseDuration.isNegative() || leaseDuration.isZero()
                ? Duration.ofSeconds(60)
                : leaseDuration;
        Instant leaseExpiresAt = now.plus(effectiveLease);

        // Redis ready queue 提供快速唤醒，DB claim 仍是最终租约裁决和兜底来源。
        List<TaskSnapshot> claimed = new ArrayList<>(claimFromRedis(workerId, limit, effectiveLease, now, leaseExpiresAt));
        if (claimed.size() < limit) {
            // Redis 队列漏唤醒时，再扫 DB 中到期任务，保证任务不会永久卡住。
            claimed.addAll(claimFromDatabase(workerId, limit - claimed.size(), now, leaseExpiresAt));
        }
        return claimed;
    }

    @Transactional
    public void markSucceeded(long jobId, String resultSummary, String resultPayloadJson) {
        TaskSnapshot snapshot = repository.findJobById(jobId)
                .map(this::toSnapshot)
                .orElseThrow(() -> new ApiException("BIZ-1002", "async task not found", HttpStatus.NOT_FOUND));
        // 成功后先落结果，再清协调状态，最后写事件和通知，保证用户看到的是已持久化结果。
        repository.markSucceeded(jobId, normalizeOptional(resultSummary, 1000), normalizeJson(resultPayloadJson), Timestamp.from(Instant.now()));
        coordinationService.clearAfterCommit(jobId);
        TaskSnapshot updated = repository.findJobById(jobId).map(this::toSnapshot).orElse(snapshot);
        appendEvent(updated, AiAsyncTaskEventType.SUCCEEDED, Map.of(
                "status", updated.status().name(),
                "resultSummary", safe(updated.resultSummary())
        ));
        publishTaskNotification(updated, true);
    }

    @Transactional
    public void markRetry(long jobId, String errorCode, String errorMessage, Duration retryDelay) {
        TaskSnapshot snapshot = repository.findJobById(jobId)
                .map(this::toSnapshot)
                .orElseThrow(() -> new ApiException("BIZ-1002", "async task not found", HttpStatus.NOT_FOUND));
        Duration effectiveDelay = retryDelay == null || retryDelay.isNegative() || retryDelay.isZero() ? Duration.ofSeconds(10) : retryDelay;
        Instant nextRunAt = Instant.now().plus(effectiveDelay);
        // 可恢复错误进入 RETRY_WAIT，并重新调度下一次可运行时间。
        repository.markRetryWait(jobId, normalizeOptional(errorCode, 40), normalizeOptional(errorMessage, 500), Timestamp.from(nextRunAt));
        coordinationService.scheduleRetryAfterCommit(jobId, nextRunAt);
        TaskSnapshot updated = repository.findJobById(jobId).map(this::toSnapshot).orElse(snapshot);
        appendEvent(updated, AiAsyncTaskEventType.RETRY_SCHEDULED, Map.of(
                "status", updated.status().name(),
                "errorCode", safe(updated.errorCode()),
                "errorMessage", safe(updated.errorMessage()),
                "nextRunAt", TimePayloads.toEpochMillis(updated.nextRunAt())
        ));
    }

    @Transactional
    public void markFailed(long jobId, String errorCode, String errorMessage) {
        TaskSnapshot snapshot = repository.findJobById(jobId)
                .map(this::toSnapshot)
                .orElseThrow(() -> new ApiException("BIZ-1002", "async task not found", HttpStatus.NOT_FOUND));
        // 最终失败也发布通知，避免学生一直等待一个不会再完成的任务。
        repository.markFailed(jobId, normalizeOptional(errorCode, 40), normalizeOptional(errorMessage, 500), Timestamp.from(Instant.now()));
        coordinationService.clearAfterCommit(jobId);
        TaskSnapshot updated = repository.findJobById(jobId).map(this::toSnapshot).orElse(snapshot);
        appendEvent(updated, AiAsyncTaskEventType.FAILED, Map.of(
                "status", updated.status().name(),
                "errorCode", safe(updated.errorCode()),
                "errorMessage", safe(updated.errorMessage())
        ));
        publishTaskNotification(updated, false);
    }

    @Transactional
    public TaskRetentionPurgeResult purgeTerminalTasks(Duration retention, int batchSize) {
        Duration effectiveRetention = retention == null || retention.isNegative() || retention.isZero()
                ? Duration.ofDays(30)
                : retention;
        int safeBatchSize = Math.max(batchSize, 1);
        Instant purgeBefore = Instant.now().minus(effectiveRetention);
        List<Long> candidateJobIds = repository.findTerminalJobIdsBefore(Timestamp.from(purgeBefore), safeBatchSize);
        if (candidateJobIds.isEmpty()) {
            return new TaskRetentionPurgeResult(0L, 0L, purgeBefore);
        }
        // 清理终态任务时先删事件再删任务，避免留下无主事件。
        long deletedEventCount = repository.deleteEventsByJobIds(candidateJobIds);
        long deletedJobCount = repository.deleteJobsByIds(candidateJobIds);
        for (Long jobId : candidateJobIds) {
            if (jobId != null && jobId > 0) {
                coordinationService.clearAfterCommit(jobId);
            }
        }
        return new TaskRetentionPurgeResult(deletedJobCount, deletedEventCount, purgeBefore);
    }

    private void publishTaskNotification(TaskSnapshot snapshot, boolean succeeded) {
        Long linkedRecordId = extractLinkedRecordId(snapshot.resultPayloadJson());
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        // 通知 payload 尽量带足跳转和展示信息，前端只做 actionCode 解析，不再补业务查询。
        payload.put("taskId", snapshot.taskId());
        payload.put("taskType", snapshot.taskType());
        payload.put("status", snapshot.status().name());
        payload.put("sceneCode", snapshot.sceneCode());
        payload.put("resultSummary", snapshot.resultSummary());
        payload.put("errorCode", snapshot.errorCode());
        payload.put("errorMessage", snapshot.errorMessage());
        if (linkedRecordId != null && linkedRecordId > 0) {
            payload.put("linkedRecordId", linkedRecordId);
        }

        String content = succeeded
                ? "你的 AI 简历任务已完成，可前往通知中心查看结果摘要。"
                : "你的 AI 简历任务执行失败，可在通知中心查看状态并重新发起。";
        // 任务终态通过通知中心回流到 AI 复盘页，页面无需长时间阻塞等待模型结果。
        notificationService.publish(new PlatformNotificationPublishService.NotificationPublishCommand(
                succeeded ? "AI_RESUME_TASK_SUCCEEDED" : "AI_RESUME_TASK_FAILED",
                NotificationCategory.AI_TASK,
                "AI_ASYNC_TASK",
                snapshot.taskId(),
                null,
                List.of(snapshot.userId()),
                succeeded ? NotificationPriority.NORMAL : NotificationPriority.HIGH,
                null,
                content,
                null,
                snapshot.taskId(),
                "VIEW_AI_REVIEW_CENTER",
                payload,
                "ai-task:" + snapshot.taskId() + ":" + (succeeded ? "SUCCEEDED" : "FAILED"),
                Instant.now()
        ));
    }

    private Long extractLinkedRecordId(String resultPayloadJson) {
        if (resultPayloadJson == null || resultPayloadJson.isBlank()) {
            return null;
        }
        try {
            // 兼容不同处理器输出字段，优先 linkedRecordId，旧格式 recordId 也能回流。
            JsonNode root = objectMapper.readTree(resultPayloadJson);
            JsonNode linkedRecordNode = root.path("linkedRecordId");
            if (linkedRecordNode.canConvertToLong() && linkedRecordNode.longValue() > 0) {
                return linkedRecordNode.longValue();
            }
            JsonNode recordIdNode = root.path("recordId");
            if (recordIdNode.canConvertToLong() && recordIdNode.longValue() > 0) {
                return recordIdNode.longValue();
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private void appendEvent(TaskSnapshot snapshot, AiAsyncTaskEventType eventType, Map<String, Object> extraPayload) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        // 事件 payload 保留执行上下文，后续可以按 taskId 串起提交、认领、重试和终态。
        payload.put("taskId", snapshot.taskId());
        payload.put("userId", snapshot.userId());
        payload.put("taskType", snapshot.taskType());
        payload.put("sceneCode", snapshot.sceneCode());
        payload.put("status", snapshot.status().name());
        payload.put("executionMode", snapshot.executionMode().name());
        payload.put("providerCode", snapshot.providerCode());
        payload.put("providerType", snapshot.providerType());
        payload.put("modelName", snapshot.modelName());
        payload.put("currentAttempt", snapshot.currentAttempt());
        payload.put("maxAttempts", snapshot.maxAttempts());
        payload.put("createdAt", TimePayloads.toEpochMillis(snapshot.createdAt()));
        if (extraPayload != null && !extraPayload.isEmpty()) {
            payload.putAll(extraPayload);
        }
        repository.insertEvent(
                "aievt_" + UUID.randomUUID().toString().replace("-", ""),
                snapshot.id(),
                snapshot.taskId(),
                snapshot.userId(),
                eventType.name(),
                DEFAULT_EVENT_DELIVERY_STATUS,
                writeJson(payload)
        );
    }

    private List<TaskSnapshot> claimFromRedis(
            String workerId,
            int limit,
            Duration leaseDuration,
            Instant now,
            Instant leaseExpiresAt
    ) {
        List<Long> candidateIds = coordinationService.claimDueJobIds(workerId, limit, leaseDuration);
        if (candidateIds.isEmpty()) {
            return List.of();
        }
        List<TaskSnapshot> claimed = new java.util.ArrayList<>();
        for (Long candidateId : candidateIds) {
            if (candidateId == null || candidateId <= 0) {
                continue;
            }
            boolean dbClaimed = repository.claimJob(
                    candidateId,
                    workerId,
                    Timestamp.from(now),
                    Timestamp.from(leaseExpiresAt)
            );
            if (dbClaimed) {
                // 只有 DB 租约成功才算真正认领，Redis 只是提醒 worker 有任务可抢。
                repository.findJobById(candidateId)
                        .map(this::toSnapshot)
                        .map(snapshot -> appendStartedEvent(snapshot, workerId))
                        .ifPresent(claimed::add);
                continue;
            }
            handleRedisClaimMiss(candidateId);
        }
        return claimed;
    }

    private List<TaskSnapshot> claimFromDatabase(
            String workerId,
            int limit,
            Instant now,
            Instant leaseExpiresAt
    ) {
        if (limit <= 0) {
            return List.of();
        }
        return repository.findRunnableJobIds(Timestamp.from(now), limit).stream()
                .filter(Objects::nonNull)
                .filter(jobId -> repository.claimJob(jobId.longValue(), workerId, Timestamp.from(now), Timestamp.from(leaseExpiresAt)))
                .map(jobId -> repository.findJobById(jobId.longValue()))
                .flatMap(Optional::stream)
                .map(this::toSnapshot)
                .map(snapshot -> appendStartedEvent(snapshot, workerId))
                .toList();
    }

    private void handleRedisClaimMiss(long jobId) {
        repository.findJobById(jobId).ifPresentOrElse(row -> {
            AiAsyncTaskStatus status = AiAsyncTaskStatus.from(row.status());
            if (status == AiAsyncTaskStatus.PENDING || status == AiAsyncTaskStatus.RETRY_WAIT) {
                // Redis 弹出了任务但 DB 没抢到，说明可能被别的 worker 抢走或租约竞争失败，重新入队兜底。
                coordinationService.releaseLeaseNow(jobId);
                coordinationService.scheduleNow(jobId, row.nextRunAt());
                return;
            }
            coordinationService.clearNow(jobId);
        }, () -> coordinationService.clearNow(jobId));
    }

    private TaskSnapshot appendStartedEvent(TaskSnapshot snapshot, String workerId) {
        appendEvent(snapshot, AiAsyncTaskEventType.STARTED, Map.of(
                "status", snapshot.status().name(),
                "workerId", safe(workerId),
                "attempt", snapshot.currentAttempt()
        ));
        return snapshot;
    }

    private TaskSnapshot toSnapshot(AiAsyncTaskRepository.AsyncTaskJobRow row) {
        // Repository row 转成不可变快照，worker 和业务层都只消费稳定视图。
        return new TaskSnapshot(
                row.id(),
                row.taskId(),
                row.userId(),
                row.taskType(),
                row.sceneCode(),
                row.routeCode(),
                AiExecutionMode.from(row.executionMode()),
                AiAsyncTaskStatus.from(row.status()),
                row.providerCode(),
                row.providerType(),
                row.modelName(),
                row.promptTemplateName(),
                row.promptTemplateVersionNo(),
                row.inputSnapshotJson(),
                row.contextJson(),
                row.promptSnapshotJson(),
                row.routeSnapshotJson(),
                row.resultSummary(),
                row.resultPayloadJson(),
                row.errorCode(),
                row.errorMessage(),
                row.currentAttempt(),
                row.maxAttempts(),
                row.nextRunAt(),
                row.leaseOwner(),
                row.leaseExpiresAt(),
                row.queuedAt(),
                row.startedAt(),
                row.finishedAt(),
                row.createdAt(),
                row.updatedAt()
        );
    }

    private AiExecutionMode normalizeExecutionMode(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return AiExecutionMode.ASYNC_JOB;
        }
        try {
            return AiExecutionMode.from(rawValue);
        } catch (IllegalArgumentException ex) {
            throw new ApiException("BIZ-1001", "executionMode invalid", HttpStatus.BAD_REQUEST);
        }
    }

    private String requireText(String rawValue, String message, int maxLength) {
        String normalized = normalizeOptional(rawValue, maxLength);
        if (normalized == null || normalized.isBlank()) {
            throw new ApiException("BIZ-1001", message, HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeOptional(String rawValue, int maxLength) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String normalized = rawValue.trim();
        if (normalized.length() > maxLength) {
            throw new ApiException("BIZ-1001", "text too long", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeJson(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return null;
        }
        try {
            // 提交时先验证 JSON 格式，避免 worker 执行时才发现快照不可解析。
            objectMapper.readTree(rawJson);
            return rawJson.trim();
        } catch (Exception ex) {
            throw new ApiException("BIZ-1001", "json invalid", HttpStatus.BAD_REQUEST);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            throw new ApiException("AI-2001", "async task event payload invalid", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String safe(String rawValue) {
        return rawValue == null ? "" : rawValue;
    }

    public record SubmitTaskCommand(
            Long userId,
            String taskType,
            String sceneCode,
            String routeCode,
            String executionMode,
            String providerCode,
            String providerType,
            String modelName,
            String promptTemplateName,
            Integer promptTemplateVersionNo,
            String inputSnapshotJson,
            String contextJson,
            String promptSnapshotJson,
            String routeSnapshotJson,
            Integer maxAttempts
    ) {
    }

    public record TaskTicket(
            String taskId,
            String status,
            Long createdAt
    ) {
    }

    public record TaskSnapshot(
            long id,
            String taskId,
            long userId,
            String taskType,
            String sceneCode,
            String routeCode,
            AiExecutionMode executionMode,
            AiAsyncTaskStatus status,
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

    public record TaskRetentionPurgeResult(
            long deletedJobCount,
            long deletedEventCount,
            Instant purgeBefore
    ) {
    }
}
