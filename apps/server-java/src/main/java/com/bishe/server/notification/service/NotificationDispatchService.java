package com.bishe.server.notification.service;

import com.bishe.server.notification.model.NotificationDispatchStatus;
import com.bishe.server.notification.repository.NotificationDispatchJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 通知渠道任务调度服务。
 */
@Service
public class NotificationDispatchService {

    private final NotificationDispatchJobRepository dispatchJobRepository;
    private final NotificationDispatchCoordinationService coordinationService;

    public NotificationDispatchService(
            NotificationDispatchJobRepository dispatchJobRepository,
            NotificationDispatchCoordinationService coordinationService
    ) {
        this.dispatchJobRepository = dispatchJobRepository;
        this.coordinationService = coordinationService;
    }

    @Transactional
    public List<DispatchJobSnapshot> claimRunnableJobs(String workerId, int limit, Duration leaseDuration) {
        Instant now = Instant.now();
        Duration effectiveLease = leaseDuration == null || leaseDuration.isNegative() || leaseDuration.isZero()
                ? Duration.ofSeconds(30)
                : leaseDuration;
        Instant leaseExpiresAt = now.plus(effectiveLease);

        List<DispatchJobSnapshot> claimed = new ArrayList<>(claimFromRedis(workerId, limit, effectiveLease, now, leaseExpiresAt));
        if (claimed.size() < limit) {
            claimed.addAll(claimFromDatabase(workerId, limit - claimed.size(), now, leaseExpiresAt));
        }
        return claimed;
    }

    @Transactional
    public void markSent(long jobId, NotificationChannelDispatcher.DispatchResult result) {
        dispatchJobRepository.markSent(jobId, java.sql.Timestamp.from(Instant.now()));
        appendAttempt(jobId, result);
        coordinationService.clearAfterCommit(jobId);
    }

    @Transactional
    public void markRetry(long jobId, NotificationChannelDispatcher.DispatchResult result) {
        Duration retryDelay = result.retryDelay() == null || result.retryDelay().isNegative() || result.retryDelay().isZero()
                ? Duration.ofSeconds(10)
                : result.retryDelay();
        Instant nextRunAt = Instant.now().plus(retryDelay);
        dispatchJobRepository.markRetryWait(
                jobId,
                result.errorCode(),
                result.errorMessage(),
                Timestamp.from(nextRunAt)
        );
        appendAttempt(jobId, result);
        coordinationService.scheduleRetryAfterCommit(jobId, nextRunAt);
    }

    @Transactional
    public void markSkipped(long jobId, NotificationChannelDispatcher.DispatchResult result) {
        dispatchJobRepository.markSkipped(jobId, result.errorCode(), result.errorMessage(), java.sql.Timestamp.from(Instant.now()));
        appendAttempt(jobId, result);
        coordinationService.clearAfterCommit(jobId);
    }

    @Transactional
    public void markDead(long jobId, NotificationChannelDispatcher.DispatchResult result) {
        dispatchJobRepository.markDead(jobId, result.errorCode(), result.errorMessage(), java.sql.Timestamp.from(Instant.now()));
        appendAttempt(jobId, result);
        coordinationService.clearAfterCommit(jobId);
    }

    @Transactional
    public void acknowledgeWebSocketJob(String jobId, long userId) {
        if (!coordinationService.tryAcquireAck(userId, jobId)) {
            return;
        }
        dispatchJobRepository.markAcked(jobId, userId, java.sql.Timestamp.from(Instant.now()));
    }

    @Transactional
    public DispatchRetentionPurgeResult purgeTerminalJobsBefore(Instant purgeBefore, int batchSize) {
        Instant effectivePurgeBefore = purgeBefore == null ? Instant.now() : purgeBefore;
        int safeBatchSize = Math.max(batchSize, 1);
        List<Long> candidateJobIds = dispatchJobRepository.findTerminalJobIdsBefore(
                Timestamp.from(effectivePurgeBefore),
                safeBatchSize
        );
        if (candidateJobIds.isEmpty()) {
            return new DispatchRetentionPurgeResult(0L, 0L, effectivePurgeBefore);
        }
        long deletedAttemptCount = dispatchJobRepository.deleteAttemptsByJobIds(candidateJobIds);
        long deletedJobCount = dispatchJobRepository.deleteJobsByIds(candidateJobIds);
        for (Long jobId : candidateJobIds) {
            if (jobId != null && jobId > 0) {
                coordinationService.clearAfterCommit(jobId);
            }
        }
        return new DispatchRetentionPurgeResult(deletedJobCount, deletedAttemptCount, effectivePurgeBefore);
    }

    private List<DispatchJobSnapshot> claimFromRedis(
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
        List<DispatchJobSnapshot> claimed = new ArrayList<>();
        for (Long candidateId : candidateIds) {
            if (candidateId == null || candidateId <= 0) {
                continue;
            }
            boolean dbClaimed = dispatchJobRepository.claimJob(
                    candidateId,
                    workerId,
                    Timestamp.from(now),
                    Timestamp.from(leaseExpiresAt)
            );
            if (dbClaimed) {
                dispatchJobRepository.findById(candidateId)
                        .map(this::toSnapshot)
                        .ifPresent(claimed::add);
                continue;
            }
            handleRedisClaimMiss(candidateId);
        }
        return claimed;
    }

    private List<DispatchJobSnapshot> claimFromDatabase(
            String workerId,
            int limit,
            Instant now,
            Instant leaseExpiresAt
    ) {
        if (limit <= 0) {
            return List.of();
        }
        return dispatchJobRepository.findRunnableJobIds(Timestamp.from(now), limit).stream()
                .filter(Objects::nonNull)
                .filter(jobId -> dispatchJobRepository.claimJob(jobId.longValue(), workerId, Timestamp.from(now), Timestamp.from(leaseExpiresAt)))
                .map(jobId -> dispatchJobRepository.findById(jobId.longValue()))
                .flatMap(Optional::stream)
                .map(this::toSnapshot)
                .toList();
    }

    private void handleRedisClaimMiss(long jobId) {
        dispatchJobRepository.findById(jobId).ifPresentOrElse(row -> {
            if (row.status() == NotificationDispatchStatus.PENDING
                    || row.status() == NotificationDispatchStatus.RETRY_WAIT) {
                coordinationService.releaseLeaseNow(jobId);
                coordinationService.scheduleNow(jobId, row.nextRunAt());
                return;
            }
            coordinationService.clearNow(jobId);
        }, () -> coordinationService.clearNow(jobId));
    }

    private void appendAttempt(long jobId, NotificationChannelDispatcher.DispatchResult result) {
        dispatchJobRepository.insertAttempt(new NotificationDispatchJobRepository.DispatchAttemptInsertCommand(
                jobId,
                result.attemptNo(),
                result.status(),
                result.requestSnapshotJson(),
                result.responseSnapshotJson(),
                result.errorCode(),
                result.errorMessage(),
                result.latencyMs()
        ));
    }

    private DispatchJobSnapshot toSnapshot(NotificationDispatchJobRepository.DispatchJobRow row) {
        return new DispatchJobSnapshot(
                row.id(),
                row.jobId(),
                row.notificationId(),
                row.eventId(),
                row.userId(),
                row.channel(),
                row.status(),
                row.attemptCount(),
                row.maxAttempts(),
                row.payloadJson(),
                row.sentAt(),
                row.ackedAt(),
                row.errorCode(),
                row.errorMessage()
        );
    }

    /**
     * Worker 使用的任务快照。
     */
    public record DispatchJobSnapshot(
            long id,
            String jobId,
            long notificationId,
            String eventId,
            long userId,
            com.bishe.server.notification.model.NotificationChannel channel,
            NotificationDispatchStatus status,
            int attemptCount,
            int maxAttempts,
            String payloadJson,
            Instant sentAt,
            Instant ackedAt,
            String errorCode,
            String errorMessage
    ) {
    }

    /**
     * 通知派发终态清理结果。
     */
    public record DispatchRetentionPurgeResult(
            long deletedJobCount,
            long deletedAttemptCount,
            Instant purgeBefore
    ) {
    }
}
