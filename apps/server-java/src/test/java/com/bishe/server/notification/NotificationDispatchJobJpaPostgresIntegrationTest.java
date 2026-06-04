package com.bishe.server.notification;

import com.bishe.server.notification.model.NotificationChannel;
import com.bishe.server.notification.model.NotificationDispatchStatus;
import com.bishe.server.notification.repository.NotificationDispatchJobRepository;
import com.bishe.server.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(NotificationDispatchJobRepository.class)
class NotificationDispatchJobJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final long USER_ID = 4301L;
    private static final String EVENT_ID_WS = "PG-NOTIFY-DISPATCH-EVT-WS";
    private static final String EVENT_ID_EMAIL = "PG-NOTIFY-DISPATCH-EVT-EMAIL";
    private static final String EVENT_ID_REQUEUE = "PG-NOTIFY-DISPATCH-EVT-REQUEUE";
    private static final String EVENT_ID_LIMIT = "PG-NOTIFY-DISPATCH-EVT-LIMIT";
    private static final String JOB_ID_WS = "pg-notify-dispatch-ws";
    private static final String JOB_ID_EMAIL = "pg-notify-dispatch-email";
    private static final String JOB_ID_REQUEUE = "pg-notify-dispatch-requeue";
    private static final String JOB_ID_LIMIT = "pg-notify-dispatch-limit";

    @Autowired
    private NotificationDispatchJobRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                "DELETE FROM notification_dispatch_attempts WHERE job_id IN (SELECT id FROM notification_dispatch_jobs WHERE event_id IN (?, ?, ?))",
                EVENT_ID_WS,
                EVENT_ID_EMAIL,
                EVENT_ID_REQUEUE
        );
        jdbcTemplate.update(
                "DELETE FROM notification_dispatch_attempts WHERE job_id IN (SELECT id FROM notification_dispatch_jobs WHERE event_id = ?)",
                EVENT_ID_LIMIT
        );
        jdbcTemplate.update(
                "DELETE FROM notification_dispatch_jobs WHERE event_id IN (?, ?, ?)",
                EVENT_ID_WS,
                EVENT_ID_EMAIL,
                EVENT_ID_REQUEUE
        );
        jdbcTemplate.update(
                "DELETE FROM notification_dispatch_jobs WHERE event_id = ?",
                EVENT_ID_LIMIT
        );
        jdbcTemplate.update(
                "DELETE FROM notifications WHERE event_id IN (?, ?, ?)",
                EVENT_ID_WS,
                EVENT_ID_EMAIL,
                EVENT_ID_REQUEUE
        );
        jdbcTemplate.update(
                "DELETE FROM notifications WHERE event_id = ?",
                EVENT_ID_LIMIT
        );
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", USER_ID);
    }

    @Test
    void repositoryShouldPersistAndAdvanceDispatchJobStateOnPostgres() {
        insertUser(USER_ID, "pg-notify-dispatch@example.com", "通知派发 PG");
        long notificationId = insertNotification(EVENT_ID_WS, USER_ID, "PG 通知派发任务");
        long createdJobId = repository.createJob(new NotificationDispatchJobRepository.DispatchJobInsertCommand(
                JOB_ID_WS,
                notificationId,
                EVENT_ID_WS,
                USER_ID,
                NotificationChannel.WEBSOCKET,
                2,
                "{\"type\":\"NOTIFICATION_CREATED\"}"
        ));
        assertThat(createdJobId).isPositive();

        NotificationDispatchJobRepository.DispatchJobRow created = repository.findById(createdJobId).orElseThrow();
        assertThat(created.jobId()).isEqualTo(JOB_ID_WS);
        assertThat(created.status()).isEqualTo(NotificationDispatchStatus.PENDING);
        assertThat(created.attemptCount()).isZero();
        assertThat(created.maxAttempts()).isEqualTo(2);
        assertThat(created.payloadJson()).contains("NOTIFICATION_CREATED");

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        assertThat(repository.findRunnableJobIds(Timestamp.from(now.plusSeconds(5)), 10)).contains(createdJobId);
        assertThat(repository.claimJob(
                createdJobId,
                "pg-worker-a",
                Timestamp.from(now.plusSeconds(5)),
                Timestamp.from(now.plusSeconds(65))
        )).isTrue();

        NotificationDispatchJobRepository.DispatchJobRow running = repository.findById(createdJobId).orElseThrow();
        assertThat(running.status()).isEqualTo(NotificationDispatchStatus.RUNNING);
        assertThat(running.attemptCount()).isEqualTo(1);
        assertThat(running.leaseOwner()).isEqualTo("pg-worker-a");
        assertThat(running.leaseExpiresAt()).isEqualTo(now.plusSeconds(65));

        Instant retryAt = now.plusSeconds(120);
        repository.markRetryWait(createdJobId, "NOTIFY-WS-500", "postgres retry", Timestamp.from(retryAt));
        NotificationDispatchJobRepository.DispatchJobRow retryWait = repository.findById(createdJobId).orElseThrow();
        assertThat(retryWait.status()).isEqualTo(NotificationDispatchStatus.RETRY_WAIT);
        assertThat(retryWait.errorCode()).isEqualTo("NOTIFY-WS-500");
        assertThat(retryWait.errorMessage()).isEqualTo("postgres retry");
        assertThat(retryWait.nextRunAt()).isEqualTo(retryAt);
        assertThat(retryWait.leaseOwner()).isNull();

        assertThat(repository.findRunnableJobIds(Timestamp.from(now.plusSeconds(90)), 10)).doesNotContain(createdJobId);
        assertThat(repository.findRunnableJobIds(Timestamp.from(now.plusSeconds(180)), 10)).contains(createdJobId);
        assertThat(repository.claimJob(
                createdJobId,
                "pg-worker-b",
                Timestamp.from(now.plusSeconds(180)),
                Timestamp.from(now.plusSeconds(240))
        )).isTrue();

        Instant sentAt = now.plusSeconds(181);
        Instant ackedAt = now.plusSeconds(182);
        repository.markSent(createdJobId, Timestamp.from(sentAt));
        repository.markAcked(JOB_ID_WS, USER_ID, Timestamp.from(ackedAt));
        NotificationDispatchJobRepository.DispatchJobRow acked = repository.findById(createdJobId).orElseThrow();
        assertThat(acked.status()).isEqualTo(NotificationDispatchStatus.ACKED);
        assertThat(acked.sentAt()).isEqualTo(sentAt);
        assertThat(acked.ackedAt()).isEqualTo(ackedAt);
        assertThat(acked.errorCode()).isNull();
        assertThat(acked.leaseOwner()).isNull();
    }

    @Test
    void repositoryShouldSupportAdminRequeueAndCleanupOnPostgres() {
        insertUser(USER_ID, "pg-notify-cleanup@example.com", "通知清理 PG");
        long emailNotificationId = insertNotification(EVENT_ID_EMAIL, USER_ID, "PG 清理任务");
        long requeueNotificationId = insertNotification(EVENT_ID_REQUEUE, USER_ID, "PG 重试任务");

        long deadJobId = repository.createJob(new NotificationDispatchJobRepository.DispatchJobInsertCommand(
                JOB_ID_EMAIL,
                emailNotificationId,
                EVENT_ID_EMAIL,
                USER_ID,
                NotificationChannel.EMAIL,
                1,
                "{}"
        ));
        Instant failedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(30);
        repository.markDead(deadJobId, "NOTIFY-EMAIL-500", "postgres dead", Timestamp.from(failedAt));
        repository.insertAttempt(new NotificationDispatchJobRepository.DispatchAttemptInsertCommand(
                deadJobId,
                1,
                NotificationDispatchStatus.DEAD,
                "{}",
                "{}",
                "NOTIFY-EMAIL-500",
                "postgres dead",
                15L
        ));

        assertThat(repository.findTerminalJobIdsBefore(Timestamp.from(failedAt.plusSeconds(5)), 10)).contains(deadJobId);

        long requeueJobId = repository.createJob(new NotificationDispatchJobRepository.DispatchJobInsertCommand(
                JOB_ID_REQUEUE,
                requeueNotificationId,
                EVENT_ID_REQUEUE,
                USER_ID,
                NotificationChannel.WEBSOCKET,
                1,
                "{}"
        ));
        repository.markDead(requeueJobId, "NOTIFY-WS-501", "postgres requeue", Timestamp.from(failedAt.plusSeconds(1)));
        Instant requeueAt = failedAt.plusSeconds(120);
        repository.requeueForAdmin(JOB_ID_REQUEUE, Timestamp.from(requeueAt), 5);

        NotificationDispatchJobRepository.DispatchJobRow requeued = repository.findByJobId(JOB_ID_REQUEUE).orElseThrow();
        assertThat(requeued.status()).isEqualTo(NotificationDispatchStatus.RETRY_WAIT);
        assertThat(requeued.maxAttempts()).isEqualTo(5);
        assertThat(requeued.nextRunAt()).isEqualTo(requeueAt);
        assertThat(requeued.errorCode()).isNull();
        assertThat(requeued.failedAt()).isNull();

        assertThat(repository.deleteAttemptsByJobIds(List.of(deadJobId))).isEqualTo(1L);
        assertThat(repository.deleteJobsByIds(List.of(deadJobId))).isEqualTo(1L);
        assertThat(repository.findById(deadJobId)).isEmpty();
        Long remainingAttempts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_dispatch_attempts WHERE job_id = ?",
                Long.class,
                deadJobId
        );
        assertThat(remainingAttempts).isZero();
    }

    @Test
    void repositoryShouldLimitTerminalCleanupCandidatesOnPostgres() {
        insertUser(USER_ID, "pg-notify-limit@example.com", "通知批次清理 PG");
        long notificationA = insertNotification(EVENT_ID_EMAIL, USER_ID, "PG 批次清理 A");
        long notificationB = insertNotification(EVENT_ID_LIMIT, USER_ID, "PG 批次清理 B");

        long firstDeadJobId = repository.createJob(new NotificationDispatchJobRepository.DispatchJobInsertCommand(
                JOB_ID_EMAIL,
                notificationA,
                EVENT_ID_EMAIL,
                USER_ID,
                NotificationChannel.EMAIL,
                1,
                "{}"
        ));
        repository.markDead(firstDeadJobId, "NOTIFY-EMAIL-500", "postgres dead a", Timestamp.from(Instant.now().plusSeconds(10)));

        long secondDeadJobId = repository.createJob(new NotificationDispatchJobRepository.DispatchJobInsertCommand(
                JOB_ID_LIMIT,
                notificationB,
                EVENT_ID_LIMIT,
                USER_ID,
                NotificationChannel.EMAIL,
                1,
                "{}"
        ));
        repository.markDead(secondDeadJobId, "NOTIFY-EMAIL-501", "postgres dead b", Timestamp.from(Instant.now().plusSeconds(20)));

        List<Long> firstBatch = repository.findTerminalJobIdsBefore(Timestamp.from(Instant.now().plusSeconds(30)), 1);
        assertThat(firstBatch).containsExactly(firstDeadJobId);

        List<Long> allCandidates = repository.findTerminalJobIdsBefore(Timestamp.from(Instant.now().plusSeconds(30)), 10);
        assertThat(allCandidates).contains(firstDeadJobId, secondDeadJobId);
    }

    private void insertUser(long userId, String email, String displayName) {
        jdbcTemplate.update(
                """
                INSERT INTO users(
                    id,
                    email,
                    password_hash,
                    role,
                    tier,
                    status,
                    display_name,
                    real_name,
                    is_deleted,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, 'STUDENT', 'FREE', 'ACTIVE', ?, ?, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                userId,
                email,
                "$2a$10$seed",
                displayName,
                displayName
        );
    }

    private long insertNotification(String eventId, long userId, String title) {
        jdbcTemplate.update(
                """
                INSERT INTO notifications(
                    user_id,
                    type,
                    category,
                    title,
                    content,
                    ref_type,
                    ref_id,
                    action_code,
                    priority,
                    event_id,
                    payload_json,
                    is_read,
                    read_at,
                    archived_at,
                    created_at,
                    updated_at
                ) VALUES (?, 'SYSTEM_ANNOUNCEMENT', 'SYSTEM', ?, 'PG notification dispatch body', 'ANNOUNCEMENT', ?, 'VIEW_NOTIFICATION_CENTER', 'HIGH', ?, '{}', FALSE, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                userId,
                title,
                eventId,
                eventId
        );
        Long notificationId = jdbcTemplate.queryForObject(
                "SELECT id FROM notifications WHERE event_id = ? AND user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class,
                eventId,
                userId
        );
        assertThat(notificationId).isNotNull();
        return notificationId;
    }
}
