package com.bishe.server.consult;

import com.bishe.server.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 PostgreSQL migration 已补齐咨询创单/消息/附件链路所需的最小 schema。
 */
@DataJpaTest
class ConsultCreateSchemaPgMigrationIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final long MENTOR_USER_ID = 6931L;
    private static final long STUDENT_USER_ID = 6932L;
    private static final String ORDER_NO = "PG-CONSULT-CREATE-SCHEMA-001";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void postgresMigrationShouldProvideConsultCreateMessagesAndAttachmentsSchema() {
        insertUser(MENTOR_USER_ID, "MENTOR", "consult-schema-mentor@example.com", "Consult Schema Mentor");
        insertUser(STUDENT_USER_ID, "STUDENT", "consult-schema-student@example.com", "Consult Schema Student");

        jdbcTemplate.update(
                """
                INSERT INTO consult_orders(
                    order_no,
                    student_user_id,
                    mentor_user_id,
                    scene_code,
                    source_page,
                    amount_fen,
                    status,
                    question_text,
                    question_payload_json,
                    problem_summary,
                    core_questions_json,
                    expected_outcomes_json,
                    selected_material_types,
                    prep_sheet_snapshot_json,
                    service_package_snapshot_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                ORDER_NO,
                STUDENT_USER_ID,
                MENTOR_USER_ID,
                "TEXT_QA",
                "mentor-profile",
                12800,
                "CREATED",
                "How should I prepare for the interview?",
                "{\"question\":\"How should I prepare for the interview?\"}",
                "Need a concise interview preparation plan",
                "[\"resume focus\",\"project story\"]",
                "[\"action plan\",\"mock interview\"]",
                "RESUME,JD",
                "{\"summary\":\"draft prep sheet\"}",
                "{\"packageCode\":\"PKG-001\"}"
        );

        jdbcTemplate.update(
                """
                INSERT INTO consult_messages(order_no, sender_user_id, sender_role, message_text)
                VALUES (?, ?, ?, ?)
                """,
                ORDER_NO,
                STUDENT_USER_ID,
                "STUDENT",
                "First consult message"
        );

        jdbcTemplate.update(
                """
                INSERT INTO consult_order_attachments(
                    order_no,
                    uploaded_by_user_id,
                    attachment_type,
                    slot_code,
                    source_stage,
                    original_filename,
                    content_type,
                    size_bytes,
                    storage_bucket,
                    object_key,
                    description,
                    lifecycle_status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                ORDER_NO,
                STUDENT_USER_ID,
                "RESUME",
                "resume",
                "ORDER_CREATE",
                "resume.pdf",
                "application/pdf",
                2048L,
                "test-bucket",
                "consult/resume.pdf",
                "Initial resume upload",
                "CURRENT"
        );

        Map<String, Object> orderRow = jdbcTemplate.queryForMap(
                """
                SELECT scene_code,
                       source_page,
                       question_payload_json,
                       problem_summary,
                       core_questions_json,
                       expected_outcomes_json,
                       selected_material_types,
                       prep_sheet_snapshot_json,
                       service_package_snapshot_json
                  FROM consult_orders
                 WHERE order_no = ?
                """,
                ORDER_NO
        );

        assertThat(orderRow.get("scene_code")).isEqualTo("TEXT_QA");
        assertThat(orderRow.get("source_page")).isEqualTo("mentor-profile");
        assertThat(orderRow.get("question_payload_json")).isEqualTo("{\"question\":\"How should I prepare for the interview?\"}");
        assertThat(orderRow.get("problem_summary")).isEqualTo("Need a concise interview preparation plan");
        assertThat(orderRow.get("core_questions_json")).isEqualTo("[\"resume focus\",\"project story\"]");
        assertThat(orderRow.get("expected_outcomes_json")).isEqualTo("[\"action plan\",\"mock interview\"]");
        assertThat(orderRow.get("selected_material_types")).isEqualTo("RESUME,JD");
        assertThat(orderRow.get("prep_sheet_snapshot_json")).isEqualTo("{\"summary\":\"draft prep sheet\"}");
        assertThat(orderRow.get("service_package_snapshot_json")).isEqualTo("{\"packageCode\":\"PKG-001\"}");

        Integer messageCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM consult_messages WHERE order_no = ?",
                Integer.class,
                ORDER_NO
        );
        Integer attachmentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM consult_order_attachments WHERE order_no = ?",
                Integer.class,
                ORDER_NO
        );

        assertThat(messageCount).isEqualTo(1);
        assertThat(attachmentCount).isEqualTo(1);
    }

    private void insertUser(long userId, String role, String email, String displayName) {
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
                ) VALUES (?, ?, ?, ?, 'FREE', 'ACTIVE', ?, ?, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                userId,
                email,
                "$2a$10$seed",
                role,
                displayName,
                displayName + " Real"
        );
    }
}
