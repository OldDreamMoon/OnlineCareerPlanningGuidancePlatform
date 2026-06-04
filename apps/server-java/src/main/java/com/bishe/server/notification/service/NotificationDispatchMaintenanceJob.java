package com.bishe.server.notification.service;

import com.bishe.server.notification.NotificationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * 通知派发维护作业：按保留期清理终态派发任务与尝试记录。
 */
@Component
public class NotificationDispatchMaintenanceJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchMaintenanceJob.class);

    private final NotificationDispatchService dispatchService;
    private final boolean retentionEnabled;
    private final Duration retentionDuration;
    private final int batchSize;

    public NotificationDispatchMaintenanceJob(
            NotificationDispatchService dispatchService,
            NotificationProperties notificationProperties
    ) {
        NotificationProperties.Retention retention = notificationProperties.getDispatch().getRetention();
        this.dispatchService = dispatchService;
        this.retentionEnabled = retention.isEnabled();
        this.retentionDuration = Duration.ofMillis(Math.max(retention.getMaxAgeMs(), Duration.ofDays(1).toMillis()));
        this.batchSize = Math.max(retention.getBatchSize(), 1);
    }

    @Scheduled(
            initialDelayString = "${notification.dispatch.retention.initial-delay-ms:3600000}",
            fixedDelayString = "${notification.dispatch.retention.fixed-delay-ms:3600000}"
    )
    public void purgeTerminalJobs() {
        if (!retentionEnabled) {
            return;
        }
        NotificationDispatchService.DispatchRetentionPurgeResult result = dispatchService.purgeTerminalJobsBefore(
                Instant.now().minus(retentionDuration),
                batchSize
        );
        if (result.deletedJobCount() <= 0 && result.deletedAttemptCount() <= 0) {
            return;
        }
        log.info(
                "notification dispatch retention purge completed deletedJobs={}, deletedAttempts={}, purgeBefore={}",
                result.deletedJobCount(),
                result.deletedAttemptCount(),
                result.purgeBefore()
        );
    }
}
