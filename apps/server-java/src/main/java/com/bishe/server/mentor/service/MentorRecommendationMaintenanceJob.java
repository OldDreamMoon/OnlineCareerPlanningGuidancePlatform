package com.bishe.server.mentor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 推荐运行留痕维护作业：按保留期清理旧的推荐运行与事件。
 */
@Component
public class MentorRecommendationMaintenanceJob {

    private static final Logger log = LoggerFactory.getLogger(MentorRecommendationMaintenanceJob.class);

    private final MentorRecommendationMaintenanceService maintenanceService;
    private final boolean retentionEnabled;
    private final Duration retentionDuration;
    private final int batchSize;

    public MentorRecommendationMaintenanceJob(
            MentorRecommendationMaintenanceService maintenanceService,
            @Value("${mentor.recommendation.retention.enabled:true}") boolean retentionEnabled,
            @Value("${mentor.recommendation.retention.max-age-ms:2592000000}") long retentionMaxAgeMs,
            @Value("${mentor.recommendation.retention.batch-size:500}") int batchSize
    ) {
        this.maintenanceService = maintenanceService;
        this.retentionEnabled = retentionEnabled;
        this.retentionDuration = Duration.ofMillis(Math.max(retentionMaxAgeMs, Duration.ofDays(1).toMillis()));
        this.batchSize = Math.max(batchSize, 1);
    }

    @Scheduled(
            initialDelayString = "${mentor.recommendation.retention.initial-delay-ms:3600000}",
            fixedDelayString = "${mentor.recommendation.retention.fixed-delay-ms:3600000}"
    )
    public void purgeRuns() {
        if (!retentionEnabled) {
            return;
        }
        MentorRecommendationMaintenanceService.RetentionPurgeResult result = maintenanceService.purgeRuns(
                retentionDuration,
                batchSize
        );
        if (result.deletedRunCount() <= 0 && result.deletedEventCount() <= 0) {
            return;
        }
        log.info(
                "mentor recommendation retention purge completed deletedRuns={}, deletedEvents={}, purgeBefore={}",
                result.deletedRunCount(),
                result.deletedEventCount(),
                result.purgeBefore()
        );
    }
}
