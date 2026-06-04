package com.bishe.server.notification.service;

import com.bishe.server.notification.NotificationProperties;
import com.bishe.server.notification.model.NotificationDispatchStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 通知渠道任务轮询 worker。
 */
@Component
public class NotificationDispatchWorker {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchWorker.class);

    private final NotificationDispatchService dispatchService;
    private final List<NotificationChannelDispatcher> dispatchers;
    private final String workerId;
    private final int batchSize;
    private final Duration leaseDuration;

    public NotificationDispatchWorker(
            NotificationDispatchService dispatchService,
            List<NotificationChannelDispatcher> dispatchers,
            NotificationProperties notificationProperties
    ) {
        this.dispatchService = dispatchService;
        this.dispatchers = dispatchers;
        this.workerId = "notify-worker-" + UUID.randomUUID().toString().substring(0, 8);
        this.batchSize = Math.max(notificationProperties.getDispatch().getBatchSize(), 1);
        this.leaseDuration = Duration.ofMillis(Math.max(notificationProperties.getDispatch().getLeaseMs(), 5_000L));
    }

    @Scheduled(fixedDelayString = "${notification.dispatch.poll-interval-ms:2000}")
    public void poll() {
        // claim 带租约，同一批可派发任务不会被多个 worker 重复处理。
        List<NotificationDispatchService.DispatchJobSnapshot> jobs = dispatchService.claimRunnableJobs(workerId, batchSize, leaseDuration);
        for (NotificationDispatchService.DispatchJobSnapshot job : jobs) {
            process(job);
        }
    }

    protected void process(NotificationDispatchService.DispatchJobSnapshot job) {
        try {
            NotificationChannelDispatcher dispatcher = findDispatcher(job).orElse(null);
            if (dispatcher == null) {
                // 通道未注册属于配置错误，直接转 DEAD，避免反复重试占用队列。
                dispatchService.markDead(job.id(), new NotificationChannelDispatcher.DispatchResult(
                        NotificationDispatchStatus.DEAD,
                        job.attemptCount(),
                        false,
                        null,
                        job.payloadJson(),
                        "{\"reason\":\"dispatcher missing\"}",
                        "NOTIFY-DISPATCH-404",
                        "notification dispatcher missing",
                        0L
                ));
                return;
            }
            NotificationChannelDispatcher.DispatchResult result = dispatcher.dispatch(job);
            if (result.status() == NotificationDispatchStatus.SENT) {
                // SENT/SKIPPED 是终态，后续只保留派发日志和管理端治理能力。
                dispatchService.markSent(job.id(), result);
                return;
            }
            if (result.status() == NotificationDispatchStatus.SKIPPED) {
                dispatchService.markSkipped(job.id(), result);
                return;
            }
            boolean canRetryWithoutLimit = job.channel() == com.bishe.server.notification.model.NotificationChannel.WEBSOCKET
                    && NotificationWebSocketDispatcher.NO_ACTIVE_SESSION_ERROR_CODE.equals(result.errorCode());
            // WebSocket 用户离线不按普通失败次数封顶，用户重新连接后仍有机会补发。
            boolean canRetry = result.retryable() && (canRetryWithoutLimit || job.attemptCount() < job.maxAttempts());
            if (canRetry) {
                dispatchService.markRetry(job.id(), result);
                return;
            }
            dispatchService.markDead(job.id(), result);
        } catch (Exception ex) {
            log.error("notification dispatch worker unexpected failure jobId={}, channel={}, message={}", job.jobId(), job.channel(), ex.getMessage(), ex);
            // 未预期异常先短延迟重试；超过 maxAttempts 后再进入 DEAD 供后台处理。
            NotificationChannelDispatcher.DispatchResult result = new NotificationChannelDispatcher.DispatchResult(
                    NotificationDispatchStatus.FAILED,
                    job.attemptCount(),
                    job.attemptCount() < job.maxAttempts(),
                    Duration.ofSeconds(10),
                    job.payloadJson(),
                    "{\"error\":\"" + ex.getClass().getSimpleName() + "\"}",
                    "NOTIFY-DISPATCH-500",
                    ex.getMessage() == null ? "notification dispatch failed" : ex.getMessage(),
                    0L
            );
            if (result.retryable()) {
                dispatchService.markRetry(job.id(), result);
                return;
            }
            dispatchService.markDead(job.id(), result);
        }
    }

    private Optional<NotificationChannelDispatcher> findDispatcher(NotificationDispatchService.DispatchJobSnapshot job) {
        return dispatchers.stream()
                .filter(dispatcher -> dispatcher.channel() == job.channel())
                .findFirst();
    }
}
