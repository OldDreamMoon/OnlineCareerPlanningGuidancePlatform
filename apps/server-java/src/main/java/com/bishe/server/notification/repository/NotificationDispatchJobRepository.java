package com.bishe.server.notification.repository;

import com.bishe.server.notification.model.NotificationChannel;
import com.bishe.server.notification.model.NotificationDispatchStatus;
import com.bishe.server.notification.repository.jpa.NotificationDispatchAttemptJpaRepository;
import com.bishe.server.notification.repository.jpa.NotificationDispatchJobJpaRepository;
import com.bishe.server.notification.repository.jpa.entity.NotificationDispatchAttemptEntity;
import com.bishe.server.notification.repository.jpa.entity.NotificationDispatchJobEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 通知渠道任务仓储。
 */
@Repository
public class NotificationDispatchJobRepository {

    private static final List<NotificationDispatchStatus> CLAIMABLE_STATUSES = List.of(
            NotificationDispatchStatus.PENDING,
            NotificationDispatchStatus.RETRY_WAIT
    );
    private static final List<NotificationDispatchStatus> ACKABLE_STATUSES = List.of(
            NotificationDispatchStatus.SENT,
            NotificationDispatchStatus.ACKED
    );
    private static final List<NotificationDispatchStatus> TERMINAL_STATUSES = List.of(
            NotificationDispatchStatus.SENT,
            NotificationDispatchStatus.ACKED,
            NotificationDispatchStatus.SKIPPED,
            NotificationDispatchStatus.FAILED,
            NotificationDispatchStatus.DEAD
    );
    private static final List<NotificationDispatchStatus> ADMIN_REQUEUE_STATUSES = List.of(
            NotificationDispatchStatus.DEAD,
            NotificationDispatchStatus.SKIPPED
    );

    private final NotificationDispatchJobJpaRepository notificationDispatchJobJpaRepository;
    private final NotificationDispatchAttemptJpaRepository notificationDispatchAttemptJpaRepository;

    public NotificationDispatchJobRepository(
            NotificationDispatchJobJpaRepository notificationDispatchJobJpaRepository,
            NotificationDispatchAttemptJpaRepository notificationDispatchAttemptJpaRepository
    ) {
        this.notificationDispatchJobJpaRepository = notificationDispatchJobJpaRepository;
        this.notificationDispatchAttemptJpaRepository = notificationDispatchAttemptJpaRepository;
    }

    public long createJob(DispatchJobInsertCommand command) {
        NotificationDispatchJobEntity entity = NotificationDispatchJobEntity.create(
                command.jobId(),
                command.notificationId(),
                command.eventId(),
                command.userId(),
                command.channel(),
                command.maxAttempts(),
                command.payloadJson()
        );
        notificationDispatchJobJpaRepository.saveAndFlush(entity);
        return entity.getId() == null ? 0L : entity.getId();
    }

    public List<Long> findRunnableJobIds(Timestamp now, int limit) {
        return notificationDispatchJobJpaRepository.findRunnableJobIds(
                CLAIMABLE_STATUSES,
                toInstant(now),
                PageRequest.of(0, Math.max(limit, 1))
        );
    }

    public boolean claimJob(long jobId, String workerId, Timestamp now, Timestamp leaseExpiresAt) {
        Instant updatedAt = Instant.now();
        return notificationDispatchJobJpaRepository.claimJob(
                jobId,
                workerId,
                toInstant(leaseExpiresAt),
                toInstant(now),
                updatedAt,
                NotificationDispatchStatus.RUNNING,
                CLAIMABLE_STATUSES
        ) > 0;
    }

    public Optional<DispatchJobRow> findById(long id) {
        return notificationDispatchJobJpaRepository.findById(id).map(this::toDispatchJobRow);
    }

    public Optional<DispatchJobRow> findByJobId(String jobId) {
        return notificationDispatchJobJpaRepository.findByJobId(jobId).map(this::toDispatchJobRow);
    }

    public List<Long> findTerminalJobIdsBefore(Timestamp beforeTime, int limit) {
        return notificationDispatchJobJpaRepository.findTerminalJobIdsBefore(
                TERMINAL_STATUSES,
                beforeTime == null ? Instant.now() : beforeTime.toInstant(),
                PageRequest.of(0, Math.max(limit, 1))
        );
    }

    public void markSent(long jobId, Timestamp sentAt) {
        notificationDispatchJobJpaRepository.markSent(
                jobId,
                toInstant(sentAt),
                Instant.now(),
                NotificationDispatchStatus.SENT
        );
    }

    public void markAcked(String jobId, long userId, Timestamp ackedAt) {
        notificationDispatchJobJpaRepository.markAcked(
                jobId,
                userId,
                toInstant(ackedAt),
                Instant.now(),
                NotificationChannel.WEBSOCKET,
                NotificationDispatchStatus.ACKED,
                ACKABLE_STATUSES
        );
    }

    public void markRetryWait(long jobId, String errorCode, String errorMessage, Timestamp nextRunAt) {
        notificationDispatchJobJpaRepository.markRetryWait(
                jobId,
                errorCode,
                errorMessage,
                toInstant(nextRunAt),
                Instant.now(),
                NotificationDispatchStatus.RETRY_WAIT
        );
    }

    public void markSkipped(long jobId, String errorCode, String errorMessage, Timestamp failedAt) {
        notificationDispatchJobJpaRepository.markSkipped(
                jobId,
                errorCode,
                errorMessage,
                toInstant(failedAt),
                Instant.now(),
                NotificationDispatchStatus.SKIPPED
        );
    }

    public void markDead(long jobId, String errorCode, String errorMessage, Timestamp failedAt) {
        notificationDispatchJobJpaRepository.markDead(
                jobId,
                errorCode,
                errorMessage,
                toInstant(failedAt),
                Instant.now(),
                NotificationDispatchStatus.DEAD
        );
    }

    public void requeueForAdmin(String jobId, Timestamp nextRunAt, Integer websocketMaxAttempts) {
        notificationDispatchJobJpaRepository.requeueForAdmin(
                jobId,
                toInstant(nextRunAt),
                websocketMaxAttempts,
                Instant.now(),
                NotificationChannel.WEBSOCKET,
                NotificationDispatchStatus.RETRY_WAIT,
                ADMIN_REQUEUE_STATUSES
        );
    }

    public void insertAttempt(DispatchAttemptInsertCommand command) {
        notificationDispatchAttemptJpaRepository.saveAndFlush(NotificationDispatchAttemptEntity.create(
                command.jobId(),
                command.attemptNo(),
                command.status(),
                command.requestSnapshotJson(),
                command.responseSnapshotJson(),
                command.errorCode(),
                command.errorMessage(),
                command.latencyMs()
        ));
    }

    public long deleteAttemptsByJobIds(List<Long> jobIds) {
        return executeChunkedDelete(jobIds, true);
    }

    public long deleteJobsByIds(List<Long> jobIds) {
        return executeChunkedDelete(jobIds, false);
    }

    private long executeChunkedDelete(List<Long> ids, boolean deleteAttempts) {
        if (ids == null || ids.isEmpty()) {
            return 0L;
        }
        long deletedCount = 0L;
        int chunkSize = 200;
        for (int start = 0; start < ids.size(); start += chunkSize) {
            List<Long> chunk = ids.subList(start, Math.min(start + chunkSize, ids.size()));
            deletedCount += deleteAttempts
                    ? notificationDispatchAttemptJpaRepository.deleteByJobIdIn(chunk)
                    : notificationDispatchJobJpaRepository.deleteByIdIn(chunk);
        }
        return deletedCount;
    }

    private DispatchJobRow toDispatchJobRow(NotificationDispatchJobEntity entity) {
        return new DispatchJobRow(
                entity.getId() == null ? 0L : entity.getId(),
                entity.getJobId(),
                entity.getNotificationId() == null ? 0L : entity.getNotificationId(),
                entity.getEventId(),
                entity.getUserId() == null ? 0L : entity.getUserId(),
                entity.getChannel(),
                entity.getStatus(),
                entity.getAttemptCount() == null ? 0 : entity.getAttemptCount(),
                entity.getMaxAttempts() == null ? 0 : entity.getMaxAttempts(),
                entity.getNextRunAt(),
                entity.getLeaseOwner(),
                entity.getLeaseExpiresAt(),
                entity.getSentAt(),
                entity.getAckedAt(),
                entity.getFailedAt(),
                entity.getErrorCode(),
                entity.getErrorMessage(),
                entity.getPayloadJson(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    /**
     * 任务写入命令。
     */
    public record DispatchJobInsertCommand(
            String jobId,
            long notificationId,
            String eventId,
            long userId,
            NotificationChannel channel,
            int maxAttempts,
            String payloadJson
    ) {
    }

    /**
     * 单次投递尝试命令。
     */
    public record DispatchAttemptInsertCommand(
            long jobId,
            int attemptNo,
            NotificationDispatchStatus status,
            String requestSnapshotJson,
            String responseSnapshotJson,
            String errorCode,
            String errorMessage,
            long latencyMs
    ) {
    }

    /**
     * 队列任务快照。
     */
    public record DispatchJobRow(
            long id,
            String jobId,
            long notificationId,
            String eventId,
            long userId,
            NotificationChannel channel,
            NotificationDispatchStatus status,
            int attemptCount,
            int maxAttempts,
            Instant nextRunAt,
            String leaseOwner,
            Instant leaseExpiresAt,
            Instant sentAt,
            Instant ackedAt,
            Instant failedAt,
            String errorCode,
            String errorMessage,
            String payloadJson,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
