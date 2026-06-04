package com.bishe.server.mentor;

import com.bishe.server.mentor.schedule.repository.MentorScheduleRepository;
import com.bishe.server.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 导师排期仓储在 PostgreSQL 下的最小验证。
 */
@DataJpaTest
@Import(MentorScheduleRepository.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MentorScheduleJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final long PRIMARY_MENTOR_USER_ID = 301L;
    private static final long SECONDARY_MENTOR_USER_ID = 302L;
    private static final long STUDENT_USER_ID = 303L;

    @Autowired
    private MentorScheduleRepository mentorScheduleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        insertMentorUser(PRIMARY_MENTOR_USER_ID, "pg-schedule-primary@example.com", "PG Schedule Primary");
        insertMentorUser(SECONDARY_MENTOR_USER_ID, "pg-schedule-secondary@example.com", "PG Schedule Secondary");
        insertStudentUser(STUDENT_USER_ID, "pg-schedule-student@example.com", "PG Schedule Student");
        jdbcTemplate.update("DELETE FROM mentor_schedule_slots WHERE mentor_user_id IN (?, ?)", PRIMARY_MENTOR_USER_ID, SECONDARY_MENTOR_USER_ID);
        jdbcTemplate.update("DELETE FROM consult_orders WHERE order_no IN (?, ?, ?, ?)", "ORD-PG-FUTURE", "ORD-PG-EXPIRED", "ORD-PG-RELEASE", "ORD-PG-BIND");
    }

    @Test
    void mentorScheduleRepositoryShouldSupportCreateReserveReleaseAndDeleteOnPostgres() {
        Instant queryStart = Instant.parse("2099-03-01T09:30:00Z");
        Instant futureStart = Instant.parse("2099-03-01T10:00:00Z");
        Instant futureEnd = Instant.parse("2099-03-01T11:00:00Z");
        Instant secondFutureStart = Instant.parse("2099-03-01T12:00:00Z");
        Instant secondFutureEnd = Instant.parse("2099-03-01T13:00:00Z");
        Instant expiredStart = Instant.parse("2099-03-01T08:00:00Z");
        Instant expiredEnd = Instant.parse("2099-03-01T09:00:00Z");

        long futureSlotId = mentorScheduleRepository.createSlot(PRIMARY_MENTOR_USER_ID, futureStart, futureEnd);
        long secondFutureSlotId = mentorScheduleRepository.createSlot(PRIMARY_MENTOR_USER_ID, secondFutureStart, secondFutureEnd);
        long expiredSlotId = mentorScheduleRepository.createSlot(PRIMARY_MENTOR_USER_ID, expiredStart, expiredEnd);
        long otherMentorSlotId = mentorScheduleRepository.createSlot(
                SECONDARY_MENTOR_USER_ID,
                Instant.parse("2099-03-01T10:30:00Z"),
                Instant.parse("2099-03-01T11:30:00Z")
        );
        long futureOrderId = insertOrder("ORD-PG-FUTURE", PRIMARY_MENTOR_USER_ID);
        long expiredOrderId = insertOrder("ORD-PG-EXPIRED", PRIMARY_MENTOR_USER_ID);
        long releaseOrderId = insertOrder("ORD-PG-RELEASE", PRIMARY_MENTOR_USER_ID);

        assertThatThrownBy(() -> mentorScheduleRepository.createSlot(PRIMARY_MENTOR_USER_ID, futureStart, futureEnd))
                .isInstanceOf(DuplicateKeyException.class);

        List<MentorScheduleRepository.ScheduleSlotRow> primarySlots =
                mentorScheduleRepository.findSlotsForMentor(PRIMARY_MENTOR_USER_ID, null, null, false);
        assertThat(primarySlots).hasSize(3);
        assertThat(primarySlots).extracting(MentorScheduleRepository.ScheduleSlotRow::id)
                .containsExactly(expiredSlotId, futureSlotId, secondFutureSlotId);

        assertThat(mentorScheduleRepository.findSlotsForMentor(PRIMARY_MENTOR_USER_ID, queryStart, secondFutureEnd, false))
                .extracting(MentorScheduleRepository.ScheduleSlotRow::id)
                .containsExactly(futureSlotId, secondFutureSlotId);
        assertThat(mentorScheduleRepository.findSlotsForMentor(SECONDARY_MENTOR_USER_ID, null, null, false))
                .singleElement()
                .satisfies(slot -> {
                    assertThat(slot.id()).isEqualTo(otherMentorSlotId);
                    assertThat(slot.status()).isEqualTo("AVAILABLE");
                });

        assertThat(mentorScheduleRepository.reserveSlot(futureSlotId, PRIMARY_MENTOR_USER_ID, futureOrderId, "ORD-PG-FUTURE")).isTrue();
        assertThat(mentorScheduleRepository.reserveSlot(futureSlotId, PRIMARY_MENTOR_USER_ID, "ORD-PG-RETRY")).isFalse();
        assertThat(mentorScheduleRepository.reserveSlot(expiredSlotId, PRIMARY_MENTOR_USER_ID, expiredOrderId, "ORD-PG-EXPIRED")).isTrue();

        assertThat(mentorScheduleRepository.findSlotById(futureSlotId))
                .isPresent()
                .get()
                .satisfies(slot -> {
                    assertThat(slot.status()).isEqualTo("BOOKED");
                    assertThat(slot.bookedOrderNo()).isEqualTo("ORD-PG-FUTURE");
                });
        assertThat(jdbcTemplate.queryForObject(
                "SELECT booked_order_id FROM mentor_schedule_slots WHERE id = ?",
                Long.class,
                futureSlotId
        )).isNotNull();
        assertThat(mentorScheduleRepository.findBookedMentorUserIds(futureOrderId, "ORD-PG-FUTURE"))
                .containsExactly(PRIMARY_MENTOR_USER_ID);

        assertThat(mentorScheduleRepository.findSlotsForMentor(PRIMARY_MENTOR_USER_ID, null, null, true))
                .extracting(MentorScheduleRepository.ScheduleSlotRow::id)
                .containsExactly(secondFutureSlotId);

        assertThat(mentorScheduleRepository.releaseUpcomingSlot(expiredOrderId, "ORD-PG-EXPIRED", queryStart)).isZero();
        assertThat(mentorScheduleRepository.releaseUpcomingSlot(futureOrderId, "ORD-PG-FUTURE", queryStart)).isEqualTo(1);
        assertThat(mentorScheduleRepository.findSlotById(futureSlotId))
                .isPresent()
                .get()
                .satisfies(slot -> {
                    assertThat(slot.status()).isEqualTo("AVAILABLE");
                    assertThat(slot.bookedOrderNo()).isNull();
                });

        assertThat(mentorScheduleRepository.reserveSlot(secondFutureSlotId, PRIMARY_MENTOR_USER_ID, releaseOrderId, "ORD-PG-RELEASE")).isTrue();
        assertThat(mentorScheduleRepository.releaseSlot(releaseOrderId, "ORD-PG-RELEASE")).isEqualTo(1);
        assertThat(mentorScheduleRepository.findSlotById(secondFutureSlotId))
                .isPresent()
                .get()
                .satisfies(slot -> assertThat(slot.status()).isEqualTo("AVAILABLE"));

        assertThat(mentorScheduleRepository.deleteAvailableSlot(secondFutureSlotId, PRIMARY_MENTOR_USER_ID)).isTrue();
        assertThat(mentorScheduleRepository.findSlotById(secondFutureSlotId)).isEmpty();
        assertThat(mentorScheduleRepository.deleteAvailableSlot(expiredSlotId, PRIMARY_MENTOR_USER_ID)).isFalse();
        assertThat(mentorScheduleRepository.findSlotById(expiredSlotId))
                .isPresent()
                .get()
                .satisfies(slot -> assertThat(slot.status()).isEqualTo("BOOKED"));
    }

    @Test
    void mentorScheduleRepositoryShouldBindOrderIdForSlotReservedByOrderNo() {
        Instant futureStart = Instant.parse("2099-03-02T10:00:00Z");
        Instant futureEnd = Instant.parse("2099-03-02T11:00:00Z");
        long slotId = mentorScheduleRepository.createSlot(PRIMARY_MENTOR_USER_ID, futureStart, futureEnd);

        assertThat(mentorScheduleRepository.reserveSlot(slotId, PRIMARY_MENTOR_USER_ID, "ORD-PG-BIND")).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT booked_order_id FROM mentor_schedule_slots WHERE id = ?",
                Long.class,
                slotId
        )).isNull();

        long orderId = insertOrder("ORD-PG-BIND", PRIMARY_MENTOR_USER_ID);
        assertThat(mentorScheduleRepository.bindReservedOrder(orderId, "ORD-PG-BIND")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT booked_order_id FROM mentor_schedule_slots WHERE id = ?",
                Long.class,
                slotId
        )).isEqualTo(orderId);
        assertThat(mentorScheduleRepository.findBookedMentorUserIds(orderId, "ORD-PG-BIND"))
                .containsExactly(PRIMARY_MENTOR_USER_ID);
    }

    private void insertMentorUser(long userId, String email, String displayName) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ?",
                Integer.class,
                userId
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
                VALUES (?, ?, ?, 'MENTOR', 'FREE', 'ACTIVE', ?, ?, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                userId,
                email,
                "$2a$10$seed",
                displayName,
                displayName
        );
    }

    private void insertStudentUser(long userId, String email, String displayName) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ?",
                Integer.class,
                userId
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
                userId,
                email,
                "$2a$10$seed",
                displayName,
                displayName
        );
    }

    private long insertOrder(String orderNo, long mentorUserId) {
        jdbcTemplate.update(
                """
                INSERT INTO consult_orders(
                    order_no,
                    student_user_id,
                    mentor_user_id,
                    amount_fen,
                    status,
                    question_text,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, 8800, 'CREATED', 'schedule test order', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                orderNo,
                STUDENT_USER_ID,
                mentorUserId
        );
        Long orderId = jdbcTemplate.queryForObject(
                "SELECT id FROM consult_orders WHERE order_no = ?",
                Long.class,
                orderNo
        );
        if (orderId == null) {
            throw new IllegalStateException("order id missing for " + orderNo);
        }
        return orderId;
    }
}
