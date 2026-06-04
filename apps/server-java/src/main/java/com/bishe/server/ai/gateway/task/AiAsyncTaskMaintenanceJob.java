package com.bishe.server.ai.gateway.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 异步 AI 任务维护作业：按保留期清理终态任务与事件。
 */
@Component
public class AiAsyncTaskMaintenanceJob {

    private static final Logger log = LoggerFactory.getLogger(AiAsyncTaskMaintenanceJob.class);

    private final AiAsyncTaskService taskService;
    private final boolean retentionEnabled;
    private final Duration retentionDuration;
    private final int batchSize;

    public AiAsyncTaskMaintenanceJob(
            AiAsyncTaskService taskService,
            @Value("${ai.gateway.async-task.retention.enabled:true}") boolean retentionEnabled,
            @Value("${ai.gateway.async-task.retention.max-age-ms:2592000000}") long retentionMaxAgeMs,
            @Value("${ai.gateway.async-task.retention.batch-size:200}") int batchSize
    ) {
        this.taskService = taskService;
        this.retentionEnabled = retentionEnabled;
        this.retentionDuration = Duration.ofMillis(Math.max(retentionMaxAgeMs, Duration.ofDays(1).toMillis()));
        this.batchSize = Math.max(batchSize, 1);
    }

    @Scheduled(
            initialDelayString = "${ai.gateway.async-task.retention.initial-delay-ms:3600000}",
            fixedDelayString = "${ai.gateway.async-task.retention.fixed-delay-ms:3600000}"
    )
    public void purgeTerminalTasks() {
        if (!retentionEnabled) {
            return;
        }
        AiAsyncTaskService.TaskRetentionPurgeResult result = taskService.purgeTerminalTasks(retentionDuration, batchSize);
        if (result.deletedJobCount() <= 0 && result.deletedEventCount() <= 0) {
            return;
        }
        log.info(
                "ai async task retention purge completed deletedJobs={}, deletedEvents={}, purgeBefore={}",
                result.deletedJobCount(),
                result.deletedEventCount(),
                result.purgeBefore()
        );
    }
}
