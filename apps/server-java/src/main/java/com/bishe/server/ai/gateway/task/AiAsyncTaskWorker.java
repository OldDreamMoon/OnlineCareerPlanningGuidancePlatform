package com.bishe.server.ai.gateway.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 异步 AI 任务轮询 worker：当前先用数据库队列承接离线型任务。
 */
@Component
public class AiAsyncTaskWorker {

    private static final Logger log = LoggerFactory.getLogger(AiAsyncTaskWorker.class);

    private final AiAsyncTaskService taskService;
    private final List<AiAsyncTaskProcessor> processors;
    private final String workerId;
    private final int batchSize;
    private final Duration leaseDuration;

    public AiAsyncTaskWorker(
            AiAsyncTaskService taskService,
            List<AiAsyncTaskProcessor> processors,
            @Value("${ai.gateway.async-task.batch-size:4}") int batchSize,
            @Value("${ai.gateway.async-task.lease-ms:60000}") long leaseMs
    ) {
        this.taskService = taskService;
        this.processors = processors;
        this.workerId = "ai-gateway-worker-" + UUID.randomUUID().toString().substring(0, 8);
        this.batchSize = Math.max(batchSize, 1);
        this.leaseDuration = Duration.ofMillis(Math.max(leaseMs, 5000L));
    }

    @Scheduled(fixedDelayString = "${ai.gateway.async-task.poll-interval-ms:2000}")
    public void poll() {
        List<AiAsyncTaskService.TaskSnapshot> tasks = taskService.claimRunnableTasks(workerId, batchSize, leaseDuration);
        for (AiAsyncTaskService.TaskSnapshot task : tasks) {
            processTask(task);
        }
    }

    protected void processTask(AiAsyncTaskService.TaskSnapshot task) {
        try {
            AiAsyncTaskProcessor processor = findProcessor(task).orElse(null);
            if (processor == null) {
                log.warn(
                        "ai async task processor missing taskId={}, taskType={}, sceneCode={}, executionMode={}",
                        task.taskId(),
                        task.taskType(),
                        task.sceneCode(),
                        task.executionMode()
                );
                taskService.markFailed(task.id(), "AI-2004", "async task processor missing");
                return;
            }
            AiAsyncTaskProcessor.ProcessResult result = processor.process(task);
            if (result.succeeded()) {
                taskService.markSucceeded(task.id(), result.resultSummary(), result.resultPayloadJson());
                return;
            }
            boolean canRetry = result.retryable() && task.currentAttempt() < task.maxAttempts();
            if (canRetry) {
                taskService.markRetry(task.id(), result.errorCode(), result.errorMessage(), result.retryDelay());
                return;
            }
            taskService.markFailed(task.id(), result.errorCode(), result.errorMessage());
        } catch (Exception ex) {
            log.error(
                    "ai async task worker unexpected failure taskId={}, taskType={}, sceneCode={}, attempt={}, maxAttempts={}, errorType={}, errorMessage={}",
                    task.taskId(),
                    task.taskType(),
                    task.sceneCode(),
                    task.currentAttempt(),
                    task.maxAttempts(),
                    ex.getClass().getSimpleName(),
                    ex.getMessage(),
                    ex
            );
            if (task.currentAttempt() < task.maxAttempts()) {
                taskService.markRetry(task.id(), "AI-2001", summarizeException(ex), Duration.ofSeconds(10));
                return;
            }
            taskService.markFailed(task.id(), "AI-2001", summarizeException(ex));
        }
    }

    private Optional<AiAsyncTaskProcessor> findProcessor(AiAsyncTaskService.TaskSnapshot task) {
        return processors.stream().filter(processor -> processor.supports(task)).findFirst();
    }

    private String summarizeException(Exception ex) {
        if (ex == null || ex.getMessage() == null || ex.getMessage().isBlank()) {
            return "async task execution failed";
        }
        String message = ex.getMessage().replaceAll("\\s+", " ").trim();
        return message.length() <= 300 ? message : message.substring(0, 300) + "...";
    }
}
