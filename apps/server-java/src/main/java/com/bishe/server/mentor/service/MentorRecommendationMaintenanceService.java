package com.bishe.server.mentor.service;

import com.bishe.server.mentor.repository.MentorRecommendationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 推荐运行留痕维护服务：按保留期清理旧的 run / event 留痕。
 */
@Service
public class MentorRecommendationMaintenanceService {

    private final MentorRecommendationRepository repository;

    public MentorRecommendationMaintenanceService(MentorRecommendationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public RetentionPurgeResult purgeRuns(Duration retention, int batchSize) {
        Duration effectiveRetention = retention == null || retention.isNegative() || retention.isZero()
                ? Duration.ofDays(30)
                : retention;
        return purgeRunsBefore(Instant.now().minus(effectiveRetention), batchSize);
    }

    @Transactional
    public RetentionPurgeResult purgeRunsBefore(Instant purgeBefore, int batchSize) {
        Instant effectivePurgeBefore = purgeBefore == null ? Instant.now() : purgeBefore;
        int safeBatchSize = Math.max(batchSize, 1);
        List<Long> candidateRunIds = repository.findRunIdsBefore(effectivePurgeBefore, safeBatchSize);
        if (candidateRunIds.isEmpty()) {
            return new RetentionPurgeResult(0L, 0L, effectivePurgeBefore);
        }
        long deletedEventCount = repository.deleteEventsByRunIds(candidateRunIds);
        long deletedRunCount = repository.deleteRunsByIds(candidateRunIds);
        return new RetentionPurgeResult(deletedRunCount, deletedEventCount, effectivePurgeBefore);
    }

    /**
     * 推荐运行留痕清理结果。
     */
    public record RetentionPurgeResult(
            long deletedRunCount,
            long deletedEventCount,
            Instant purgeBefore
    ) {
    }
}
