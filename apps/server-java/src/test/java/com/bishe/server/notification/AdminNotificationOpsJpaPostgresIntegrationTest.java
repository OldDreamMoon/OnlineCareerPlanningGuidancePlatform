package com.bishe.server.notification;

import com.bishe.server.notification.model.NotificationChannel;
import com.bishe.server.notification.model.NotificationDispatchStatus;
import com.bishe.server.notification.repository.AdminNotificationOpsRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(AdminNotificationOpsRepository.class)
class AdminNotificationOpsJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final long USER_A_ID = 4201L;
    private static final long USER_B_ID = 4202L;

    @Autowired
    private AdminNotificationOpsRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM notification_dispatch_attempts WHERE job_id IN (SELECT id FROM notification_dispatch_jobs WHERE event_id IN (?, ?))", "PG-NOTIFY-EVT-NEW", "PG-NOTIFY-EVT-OLD");
        jdbcTemplate.update("DELETE FROM notification_dispatch_jobs WHERE event_id IN (?, ?)", "PG-NOTIFY-EVT-NEW", "PG-NOTIFY-EVT-OLD");
        jdbcTemplate.update("DELETE FROM notifications WHERE event_id IN (?, ?)", "PG-NOTIFY-EVT-NEW", "PG-NOTIFY-EVT-OLD");
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", USER_A_ID, USER_B_ID);
    }

    @Test
    void repositoryShouldAggregateAdminNotificationOpsDataOnPostgres() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        insertUser(USER_A_ID, "pg-notify-a@example.com", "通知甲");
        insertUser(USER_B_ID, "pg-notify-b@example.com", "通知乙");

        long latestNotificationId = insertAnnouncement("PG-NOTIFY-EVT-NEW", USER_A_ID, "最新公告", now.minusSeconds(300));
        insertAnnouncement("PG-NOTIFY-EVT-NEW", USER_B_ID, "最新公告", now.minusSeconds(290));
        long oldNotificationId = insertAnnouncement("PG-NOTIFY-EVT-OLD", USER_A_ID, "旧公告", now.minusSeconds(10L * 24 * 60 * 60));

        insertDispatchJob("pg-notify-dead", latestNotificationId, "PG-NOTIFY-EVT-NEW", USER_A_ID, NotificationChannel.EMAIL, NotificationDispatchStatus.DEAD, now.minusSeconds(120), now.minusSeconds(120));
        insertDispatchJob("pg-notify-retry", oldNotificationId, "PG-NOTIFY-EVT-OLD", USER_A_ID, NotificationChannel.EMAIL, NotificationDispatchStatus.RETRY_WAIT, now.minusSeconds(180), null);
        insertDispatchJob("pg-notify-pending", latestNotificationId, "PG-NOTIFY-EVT-NEW", USER_B_ID, NotificationChannel.WEBSOCKET, NotificationDispatchStatus.PENDING, now.minusSeconds(60), null);

        AdminNotificationOpsRepository.OverviewStatsRow overview = repository.fetchOverviewStats();
        assertThat(overview.announcementCount()).isEqualTo(2L);
        assertThat(overview.announcementsLast7Days()).isEqualTo(1L);
        assertThat(overview.pendingJobCount()).isEqualTo(1L);
        assertThat(overview.retryJobCount()).isEqualTo(1L);
        assertThat(overview.deadJobCount()).isEqualTo(1L);
        assertThat(overview.lastAnnouncementAt()).isEqualTo(now.minusSeconds(290));

        assertThat(repository.findRecentAnnouncements(5))
                .extracting(AdminNotificationOpsRepository.AnnouncementRow::eventId)
                .containsExactly("PG-NOTIFY-EVT-NEW", "PG-NOTIFY-EVT-OLD");

        assertThat(repository.findRecentAnnouncements(5))
                .first()
                .satisfies(row -> {
                    assertThat(row.title()).isEqualTo("最新公告");
                    assertThat(row.hasEmailDispatch()).isTrue();
                    assertThat(row.notificationCount()).isEqualTo(2L);
                });

        assertThat(repository.findChannelStatusCountsSince(now.minusSeconds(3600)))
                .extracting(AdminNotificationOpsRepository.ChannelStatusCountRow::channel, AdminNotificationOpsRepository.ChannelStatusCountRow::status, AdminNotificationOpsRepository.ChannelStatusCountRow::total)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(NotificationChannel.EMAIL, NotificationDispatchStatus.DEAD, 1L),
                        org.assertj.core.groups.Tuple.tuple(NotificationChannel.EMAIL, NotificationDispatchStatus.RETRY_WAIT, 1L),
                        org.assertj.core.groups.Tuple.tuple(NotificationChannel.WEBSOCKET, NotificationDispatchStatus.PENDING, 1L)
                );

        assertThat(repository.findDispatchJobs(null, null, 1, 10))
                .extracting(AdminNotificationOpsRepository.DispatchJobAdminRow::jobId)
                .containsExactly("pg-notify-dead", "pg-notify-retry", "pg-notify-pending");

        assertThat(repository.countDispatchJobs(NotificationDispatchStatus.DEAD, NotificationChannel.EMAIL)).isEqualTo(1L);
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

    private long insertAnnouncement(String eventId, long userId, String title, Instant createdAt) {
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
                ) VALUES (?, 'SYSTEM_ANNOUNCEMENT', 'SYSTEM', ?, 'PG 通知运营台公告', 'ANNOUNCEMENT', ?, 'VIEW_NOTIFICATION_CENTER', 'HIGH', ?, '{}', FALSE, NULL, NULL, ?, ?)
                """,
                userId,
                title,
                eventId,
                eventId,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
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

    private void insertDispatchJob(
            String jobId,
            long notificationId,
            String eventId,
            long userId,
            NotificationChannel channel,
            NotificationDispatchStatus status,
            Instant updatedAt,
            Instant failedAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO notification_dispatch_jobs(
                    job_id,
                    notification_id,
                    event_id,
                    user_id,
                    channel,
                    status,
                    attempt_count,
                    max_attempts,
                    next_run_at,
                    sent_at,
                    acked_at,
                    failed_at,
                    error_code,
                    error_message,
                    payload_json,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, 1, 3, ?, NULL, NULL, ?, 'PG-NOTIFY-500', 'postgres notify admin ops', '{}', ?, ?)
                """,
                jobId,
                notificationId,
                eventId,
                userId,
                channel.name(),
                status.name(),
                Timestamp.from(updatedAt),
                failedAt == null ? null : Timestamp.from(failedAt),
                Timestamp.from(updatedAt),
                Timestamp.from(updatedAt)
        );
    }
}
