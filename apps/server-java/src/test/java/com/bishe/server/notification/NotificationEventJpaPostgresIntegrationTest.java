package com.bishe.server.notification;

import com.bishe.server.notification.model.NotificationCategory;
import com.bishe.server.notification.model.NotificationPriority;
import com.bishe.server.notification.repository.NotificationEventRepository;
import com.bishe.server.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通知事件仓储在 PostgreSQL 下的最小验证。
 */
@DataJpaTest
@Import(NotificationEventRepository.class)
class NotificationEventJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final long ACTOR_USER_ID = 811L;

    @Autowired
    private NotificationEventRepository notificationEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        insertActorUser();
        jdbcTemplate.update("DELETE FROM notification_events WHERE event_id LIKE 'ntf-pg-test-%'");
    }

    @Test
    void notificationEventRepositoryShouldCreateAndFindEventOnPostgres() {
        Instant occurredAt = Instant.parse("2026-04-16T11:20:30Z");

        long eventId = notificationEventRepository.createEvent(new NotificationEventRepository.NotificationEventInsertCommand(
                "ntf-pg-test-001",
                "SYSTEM_ALERT",
                NotificationCategory.COMMUNITY,
                "POST",
                "POST-1001",
                ACTOR_USER_ID,
                NotificationPriority.URGENT,
                "ntf-pg-dedupe-001",
                "{\"payload\":true}",
                occurredAt
        ));

        assertThat(eventId).isPositive();

        assertThat(notificationEventRepository.findByDedupeKey("  ntf-pg-dedupe-001  "))
                .isPresent()
                .get()
                .satisfies(row -> {
                    assertThat(row.id()).isEqualTo(eventId);
                    assertThat(row.eventId()).isEqualTo("ntf-pg-test-001");
                    assertThat(row.category()).isEqualTo(NotificationCategory.COMMUNITY);
                    assertThat(row.sourceType()).isEqualTo("POST");
                    assertThat(row.sourceId()).isEqualTo("POST-1001");
                    assertThat(row.actorUserId()).isEqualTo(ACTOR_USER_ID);
                    assertThat(row.priority()).isEqualTo(NotificationPriority.URGENT);
                    assertThat(row.payloadJson()).isEqualTo("{\"payload\":true}");
                    assertThat(row.occurredAt()).isEqualTo(occurredAt);
                    assertThat(row.createdAt()).isNotNull();
                });

        long noDedupeEventId = notificationEventRepository.createEvent(new NotificationEventRepository.NotificationEventInsertCommand(
                "ntf-pg-test-002",
                "SYSTEM_ALERT",
                NotificationCategory.CONSULT,
                null,
                null,
                null,
                NotificationPriority.HIGH,
                null,
                "{\"payload\":false}",
                null
        ));

        assertThat(noDedupeEventId).isPositive();
        assertThat(notificationEventRepository.findByDedupeKey("")).isEmpty();
    }

    private void insertActorUser() {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ?",
                Integer.class,
                ACTOR_USER_ID
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
                ACTOR_USER_ID,
                "notify-event-pg-%s@example.com".formatted(ACTOR_USER_ID),
                "$2a$10$seed",
                "PG Notify Event",
                "通知事件同学"
        );
    }
}
