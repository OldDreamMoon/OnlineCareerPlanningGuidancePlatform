package com.bishe.server.notification;

import com.bishe.server.notification.model.NotificationCategory;
import com.bishe.server.notification.model.NotificationPriority;
import com.bishe.server.notification.repository.NotificationRepository;
import com.bishe.server.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通知收件箱仓储在 PostgreSQL 下的最小验证。
 */
@DataJpaTest
@Import(NotificationRepository.class)
class NotificationJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final long STUDENT_USER_ID = 821L;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        insertStudentUser();
        jdbcTemplate.update("DELETE FROM notifications WHERE user_id = ?", STUDENT_USER_ID);
    }

    @Test
    void notificationRepositoryShouldSupportInboxReadAndCountOnPostgres() {
        long consultId = notificationRepository.createNotification(new NotificationRepository.NotificationInsertCommand(
                STUDENT_USER_ID,
                "CONSULT_REPLIED",
                NotificationCategory.CONSULT,
                "导师已回复你的咨询",
                "请回到订单查看最新回复。",
                "CONSULT_ORDER",
                "ORD-PG-1001",
                "VIEW_CONSULT_ORDER",
                NotificationPriority.HIGH,
                "ntfevt-pg-notification-001",
                "{\"orderNo\":\"ORD-PG-1001\"}"
        ));
        long systemId = notificationRepository.createNotification(new NotificationRepository.NotificationInsertCommand(
                STUDENT_USER_ID,
                "SYSTEM_ANNOUNCEMENT",
                NotificationCategory.SYSTEM,
                "平台公告",
                "今晚 23:00 将进行例行维护。",
                "NOTIFICATION_CENTER",
                "ANN-PG-1002",
                "VIEW_NOTIFICATION_CENTER",
                NotificationPriority.NORMAL,
                "ntfevt-pg-notification-002",
                "{\"announcementId\":\"ANN-PG-1002\"}"
        ));
        long aiId = notificationRepository.createNotification(new NotificationRepository.NotificationInsertCommand(
                STUDENT_USER_ID,
                "AI_RESUME_TASK_SUCCEEDED",
                NotificationCategory.AI_TASK,
                "简历诊断已完成",
                "你的 AI 简历任务已完成。",
                "AI_ASYNC_TASK",
                "AI-TASK-PG-1003",
                null,
                NotificationPriority.NORMAL,
                "ntfevt-pg-notification-003",
                "{\"taskId\":\"AI-TASK-PG-1003\"}"
        ));
        long archivedId = notificationRepository.createNotification(new NotificationRepository.NotificationInsertCommand(
                STUDENT_USER_ID,
                "CONSULT_CLOSED",
                NotificationCategory.CONSULT,
                "已归档的咨询通知",
                "这条通知用于验证 archived_at 过滤。",
                "CONSULT_ORDER",
                "ORD-PG-ARCHIVED",
                "VIEW_CONSULT_ORDER",
                NotificationPriority.NORMAL,
                "ntfevt-pg-notification-004",
                "{\"archived\":true}"
        ));
        jdbcTemplate.update(
                "UPDATE notifications SET archived_at = CURRENT_TIMESTAMP WHERE id = ?",
                archivedId
        );

        assertThat(notificationRepository.findNotifications(STUDENT_USER_ID, false, null, 1, 10))
                .extracting(NotificationRepository.NotificationRow::id)
                .containsExactly(aiId, systemId, consultId);

        assertThat(notificationRepository.findNotifications(STUDENT_USER_ID, true, NotificationCategory.CONSULT, 1, 10))
                .extracting(NotificationRepository.NotificationRow::id)
                .containsExactly(consultId);

        assertThat(notificationRepository.findNotificationsAfterId(STUDENT_USER_ID, consultId, 10))
                .extracting(NotificationRepository.NotificationRow::id)
                .containsExactly(systemId, aiId);

        assertThat(notificationRepository.countNotifications(STUDENT_USER_ID, false, null)).isEqualTo(3);
        assertThat(notificationRepository.countNotifications(STUDENT_USER_ID, true, null)).isEqualTo(3);
        assertThat(notificationRepository.countUnread(STUDENT_USER_ID)).isEqualTo(3);
        assertThat(notificationRepository.countUnreadActionable(STUDENT_USER_ID, null)).isEqualTo(1);
        assertThat(notificationRepository.countUnreadActionable(STUDENT_USER_ID, NotificationCategory.CONSULT)).isEqualTo(1);
        assertThat(notificationRepository.countUnreadActionableGrouped(STUDENT_USER_ID))
                .isEqualTo(Map.of(NotificationCategory.CONSULT, 1L));
        assertThat(notificationRepository.findLatestNotificationId(STUDENT_USER_ID)).isEqualTo(aiId);

        assertThat(notificationRepository.findById(consultId, STUDENT_USER_ID))
                .isPresent()
                .get()
                .satisfies(row -> {
                    assertThat(row.type()).isEqualTo("CONSULT_REPLIED");
                    assertThat(row.read()).isFalse();
                    assertThat(row.archivedAt()).isNull();
                });
        assertThat(notificationRepository.findById(archivedId, STUDENT_USER_ID)).isEmpty();

        notificationRepository.markRead(consultId, STUDENT_USER_ID);

        assertThat(notificationRepository.findById(consultId, STUDENT_USER_ID))
                .isPresent()
                .get()
                .satisfies(row -> {
                    assertThat(row.read()).isTrue();
                    assertThat(row.readAt()).isNotNull();
                });
        assertThat(notificationRepository.countUnread(STUDENT_USER_ID)).isEqualTo(2);
        assertThat(notificationRepository.countUnreadActionable(STUDENT_USER_ID, null)).isZero();

        long updatedCount = notificationRepository.markAllRead(STUDENT_USER_ID);
        assertThat(updatedCount).isEqualTo(2);
        assertThat(notificationRepository.countUnread(STUDENT_USER_ID)).isZero();
    }

    private void insertStudentUser() {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ?",
                Integer.class,
                STUDENT_USER_ID
        );
        if (existing != null && existing > 0) {
            return;
        }

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
                )
                VALUES (?, ?, ?, 'STUDENT', 'FREE', 'ACTIVE', ?, ?, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                STUDENT_USER_ID,
                "notify-inbox-pg-%s@example.com".formatted(STUDENT_USER_ID),
                "$2a$10$seed",
                "PG Notify Inbox",
                "通知收件箱同学"
        );
    }
}
