package com.bishe.server.ai.gateway.task.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * AI 异步任务实体。
 */
@Entity
@Table(
        name = "ai_async_task_jobs",
        indexes = {
                @Index(name = "idx_ai_async_task_jobs_status_next_run", columnList = "status, next_run_at, id"),
                @Index(name = "idx_ai_async_task_jobs_user_created", columnList = "user_id, created_at, id"),
                @Index(name = "idx_ai_async_task_jobs_task_scene", columnList = "task_type, scene_code, status, id")
        }
)
public class AiAsyncTaskJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "task_id", nullable = false, length = 64)
    private String taskId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "task_type", nullable = false, length = 40)
    private String taskType;

    @Column(name = "scene_code", length = 60)
    private String sceneCode;

    @Column(name = "route_code", length = 60)
    private String routeCode;

    @Column(name = "execution_mode", nullable = false, length = 32)
    private String executionMode;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "provider_code", length = 60)
    private String providerCode;

    @Column(name = "provider_type", length = 40)
    private String providerType;

    @Column(name = "model_name", length = 120)
    private String modelName;

    @Column(name = "prompt_template_name", length = 80)
    private String promptTemplateName;

    @Column(name = "prompt_template_version_no")
    private Integer promptTemplateVersionNo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot_json", columnDefinition = "jsonb")
    private String inputSnapshotJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_json", columnDefinition = "jsonb")
    private String contextJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "prompt_snapshot_json", columnDefinition = "jsonb")
    private String promptSnapshotJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "route_snapshot_json", columnDefinition = "jsonb")
    private String routeSnapshotJson;

    @Column(name = "result_summary", columnDefinition = "TEXT")
    private String resultSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_payload_json", columnDefinition = "jsonb")
    private String resultPayloadJson;

    @Column(name = "error_code", length = 40)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "current_attempt", nullable = false)
    private Integer currentAttempt;

    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts;

    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt;

    @Column(name = "lease_owner", length = 80)
    private String leaseOwner;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "queued_at", nullable = false)
    private Instant queuedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AiAsyncTaskJobEntity() {
    }

    public static AiAsyncTaskJobEntity create(
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
            Instant nextRunAt
    ) {
        Instant now = Instant.now();
        AiAsyncTaskJobEntity entity = new AiAsyncTaskJobEntity();
        entity.taskId = taskId;
        entity.userId = userId;
        entity.taskType = taskType;
        entity.sceneCode = sceneCode;
        entity.routeCode = routeCode;
        entity.executionMode = executionMode;
        entity.status = status;
        entity.providerCode = providerCode;
        entity.providerType = providerType;
        entity.modelName = modelName;
        entity.promptTemplateName = promptTemplateName;
        entity.promptTemplateVersionNo = promptTemplateVersionNo;
        entity.inputSnapshotJson = inputSnapshotJson;
        entity.contextJson = contextJson;
        entity.promptSnapshotJson = promptSnapshotJson;
        entity.routeSnapshotJson = routeSnapshotJson;
        entity.currentAttempt = 0;
        entity.maxAttempts = Math.max(maxAttempts, 1);
        entity.nextRunAt = nextRunAt == null ? now : nextRunAt;
        entity.queuedAt = now;
        return entity;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (nextRunAt == null) {
            nextRunAt = now;
        }
        if (queuedAt == null) {
            queuedAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getTaskId() {
        return taskId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTaskType() {
        return taskType;
    }

    public String getSceneCode() {
        return sceneCode;
    }

    public String getRouteCode() {
        return routeCode;
    }

    public String getExecutionMode() {
        return executionMode;
    }

    public String getStatus() {
        return status;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public String getProviderType() {
        return providerType;
    }

    public String getModelName() {
        return modelName;
    }

    public String getPromptTemplateName() {
        return promptTemplateName;
    }

    public Integer getPromptTemplateVersionNo() {
        return promptTemplateVersionNo;
    }

    public String getInputSnapshotJson() {
        return inputSnapshotJson;
    }

    public String getContextJson() {
        return contextJson;
    }

    public String getPromptSnapshotJson() {
        return promptSnapshotJson;
    }

    public String getRouteSnapshotJson() {
        return routeSnapshotJson;
    }

    public String getResultSummary() {
        return resultSummary;
    }

    public String getResultPayloadJson() {
        return resultPayloadJson;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Integer getCurrentAttempt() {
        return currentAttempt;
    }

    public Integer getMaxAttempts() {
        return maxAttempts;
    }

    public Instant getNextRunAt() {
        return nextRunAt;
    }

    public String getLeaseOwner() {
        return leaseOwner;
    }

    public Instant getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    public Instant getQueuedAt() {
        return queuedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
