package com.bishe.server.notification;

import com.bishe.server.notification.model.NotificationCategory;
import com.bishe.server.notification.model.NotificationPriority;
import com.bishe.server.notification.repository.NotificationPreferenceRepository;
import com.bishe.server.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用户通知偏好仓储在 PostgreSQL 下的最小验证。
 */
@DataJpaTest
@Import(NotificationPreferenceRepository.class)
class NotificationPreferenceJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final long STUDENT_USER_ID = 801L;

    @Autowired
    private NotificationPreferenceRepository notificationPreferenceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        insertStudentUser();
        jdbcTemplate.update("DELETE FROM notification_preferences WHERE user_id = ?", STUDENT_USER_ID);
    }

    @Test
    void notificationPreferenceRepositoryShouldSupportUpsertAndReadOnPostgres() {
        assertThat(notificationPreferenceRepository.findByUserId(STUDENT_USER_ID)).isEmpty();
        assertThat(notificationPreferenceRepository.findByUserIdAndCategory(STUDENT_USER_ID, NotificationCategory.CONSULT)).isEmpty();

        notificationPreferenceRepository.saveOrUpdate(new NotificationPreferenceRepository.NotificationPreferenceUpsertCommand(
                STUDENT_USER_ID,
                NotificationCategory.CONSULT,
                true,
                false,
                true,
                false,
                NotificationPriority.HIGH,
                "{\"quiet\":false}"
        ));
        notificationPreferenceRepository.saveOrUpdate(new NotificationPreferenceRepository.NotificationPreferenceUpsertCommand(
                STUDENT_USER_ID,
                NotificationCategory.AI_TASK,
                true,
                true,
                false,
                true,
                NotificationPriority.URGENT,
                "{\"start\":\"22:00\"}"
        ));

        assertThat(notificationPreferenceRepository.findByUserId(STUDENT_USER_ID))
                .extracting(NotificationPreferenceRepository.NotificationPreferenceRow::category)
                .containsExactly(NotificationCategory.AI_TASK, NotificationCategory.CONSULT);

        assertThat(notificationPreferenceRepository.findByUserIdAndCategory(STUDENT_USER_ID, NotificationCategory.CONSULT))
                .isPresent()
                .get()
                .satisfies(row -> {
                    assertThat(row.websocketEnabled()).isFalse();
                    assertThat(row.browserPopupEnabled()).isTrue();
                    assertThat(row.emailEnabled()).isFalse();
                    assertThat(row.emailUrgencyThreshold()).isEqualTo(NotificationPriority.HIGH);
                    assertThat(row.updatedAt()).isNotNull();
                });

        notificationPreferenceRepository.saveOrUpdate(new NotificationPreferenceRepository.NotificationPreferenceUpsertCommand(
                STUDENT_USER_ID,
                NotificationCategory.CONSULT,
                true,
                true,
                false,
                true,
                NotificationPriority.NORMAL,
                "{\"quiet\":true}"
        ));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_preferences WHERE user_id = ? AND category = 'CONSULT'",
                Integer.class,
                STUDENT_USER_ID
        )).isEqualTo(1);

        assertThat(notificationPreferenceRepository.findByUserIdAndCategory(STUDENT_USER_ID, NotificationCategory.CONSULT))
                .isPresent()
                .get()
                .satisfies(row -> {
                    assertThat(row.websocketEnabled()).isTrue();
                    assertThat(row.browserPopupEnabled()).isFalse();
                    assertThat(row.emailEnabled()).isTrue();
                    assertThat(row.emailUrgencyThreshold()).isEqualTo(NotificationPriority.NORMAL);
                    assertThat(row.quietHoursJson()).isEqualTo("{\"quiet\":true}");
                });
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
                "notify-pref-pg-%s@example.com".formatted(STUDENT_USER_ID),
                "$2a$10$seed",
                "PG Notify Pref",
                "通知偏好同学"
        );
    }
}
