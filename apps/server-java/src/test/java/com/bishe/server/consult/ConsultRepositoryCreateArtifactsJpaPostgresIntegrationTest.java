package com.bishe.server.consult;

import com.bishe.server.consult.repository.ConsultRepository;
import com.bishe.server.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ConsultRepository 中创单、消息、附件读写在 PostgreSQL 下的最小验证。
 */
@DataJpaTest
@Import(ConsultRepository.class)
class ConsultRepositoryCreateArtifactsJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final long MENTOR_USER_ID = 6941L;
    private static final long STUDENT_USER_ID = 6942L;
    private static final String ORDER_NO = "PG-CONSULT-CREATE-ARTIFACTS-001";

    @Autowired
    private ConsultRepository consultRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM consult_order_attachments WHERE order_no = ?", ORDER_NO);
        jdbcTemplate.update("DELETE FROM consult_messages WHERE order_no = ?", ORDER_NO);
        jdbcTemplate.update("DELETE FROM consult_orders WHERE order_no = ?", ORDER_NO);
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", MENTOR_USER_ID, STUDENT_USER_ID);
    }

    @Test
    void repositoryShouldUseJpaForOrderCreateMessagesAndAttachments() {
        insertUser(MENTOR_USER_ID, "MENTOR", "consult-artifacts-mentor@example.com", "Artifacts Mentor");
        insertUser(STUDENT_USER_ID, "STUDENT", "consult-artifacts-student@example.com", "Artifacts Student");

        Instant appointmentStartAt = Instant.parse("2026-04-17T10:00:00Z");
        Instant appointmentEndAt = Instant.parse("2026-04-17T11:00:00Z");
        long orderId = consultRepository.createOrder(new ConsultRepository.CreateOrderCommand(
                ORDER_NO,
                STUDENT_USER_ID,
                MENTOR_USER_ID,
                "RESUME_DIAGNOSIS",
                "MENTOR_MARKETPLACE",
                12900,
                "想知道简历为什么拿不到面试",
                "{\"primaryConcern\":\"reply rate\"}",
                "希望先找出简历回复率低的主要原因",
                "[\"项目表达\",\"亮点排序\"]",
                "[\"简历修改建议\",\"投递优先级\"]",
                "RESUME,JOB_DESCRIPTION",
                "{\"scene\":\"简历诊断\"}",
                "{\"packageCode\":\"PKG-RESUME-001\"}",
                appointmentStartAt,
                appointmentEndAt
        ));

        assertThat(orderId).isPositive();
        Map<String, Object> orderRow = jdbcTemplate.queryForMap(
                """
                SELECT scene_code,
                       source_page,
                       amount_fen,
                       status,
                       question_payload_json,
                       problem_summary,
                       core_questions_json,
                       expected_outcomes_json,
                       selected_material_types,
                       prep_sheet_snapshot_json,
                       service_package_snapshot_json,
                       appointment_start_at,
                       appointment_end_at
                  FROM consult_orders
                 WHERE order_no = ?
                """,
                ORDER_NO
        );
        assertThat(orderRow.get("scene_code")).isEqualTo("RESUME_DIAGNOSIS");
        assertThat(orderRow.get("source_page")).isEqualTo("MENTOR_MARKETPLACE");
        assertThat(orderRow.get("amount_fen")).isEqualTo(12900);
        assertThat(orderRow.get("status")).isEqualTo("CREATED");
        assertThat(orderRow.get("question_payload_json")).isEqualTo("{\"primaryConcern\":\"reply rate\"}");
        assertThat(orderRow.get("problem_summary")).isEqualTo("希望先找出简历回复率低的主要原因");
        assertThat(orderRow.get("core_questions_json")).isEqualTo("[\"项目表达\",\"亮点排序\"]");
        assertThat(orderRow.get("expected_outcomes_json")).isEqualTo("[\"简历修改建议\",\"投递优先级\"]");
        assertThat(orderRow.get("selected_material_types")).isEqualTo("RESUME,JOB_DESCRIPTION");
        assertThat(orderRow.get("prep_sheet_snapshot_json")).isEqualTo("{\"scene\":\"简历诊断\"}");
        assertThat(orderRow.get("service_package_snapshot_json")).isEqualTo("{\"packageCode\":\"PKG-RESUME-001\"}");
        assertThat(orderRow.get("appointment_start_at")).isNotNull();
        assertThat(orderRow.get("appointment_end_at")).isNotNull();

        long studentMessageId = consultRepository.createMessage(
                orderId,
                ORDER_NO,
                STUDENT_USER_ID,
                "STUDENT",
                "先附上我最新的简历版本"
        );
        long mentorMessageId = consultRepository.createMessage(
                orderId,
                ORDER_NO,
                MENTOR_USER_ID,
                "MENTOR",
                "收到，我先看一下你的项目表达"
        );

        List<ConsultRepository.MessageRow> messages = consultRepository.findMessages(orderId, ORDER_NO);
        assertThat(messages).hasSize(2);
        assertThat(messages).extracting(ConsultRepository.MessageRow::id).containsExactly(studentMessageId, mentorMessageId);
        assertThat(messages).extracting(ConsultRepository.MessageRow::senderDisplayName)
                .containsExactly("Artifacts Student", "Artifacts Mentor");
        assertThat(messages).extracting(ConsultRepository.MessageRow::senderRole)
                .containsExactly("STUDENT", "MENTOR");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM consult_messages WHERE order_no = ? AND order_id = ?",
                Integer.class,
                ORDER_NO,
                orderId
        )).isEqualTo(2);

        long supersededResumeId = consultRepository.createAttachment(orderId, new ConsultRepository.CreateAttachmentCommand(
                ORDER_NO,
                STUDENT_USER_ID,
                "RESUME",
                "resume",
                "ORDER_CREATE",
                "resume-v1.pdf",
                "application/pdf",
                2048L,
                "test-bucket",
                "consult/resume-v1.pdf",
                "初版简历",
                "CURRENT",
                null
        ));
        assertThat(consultRepository.findCurrentAttachmentBySlot(orderId, ORDER_NO, "resume"))
                .isPresent()
                .get()
                .extracting(ConsultRepository.OrderAttachmentRow::id)
                .isEqualTo(supersededResumeId);

        assertThat(consultRepository.markAttachmentSuperseded(orderId, ORDER_NO, supersededResumeId)).isTrue();
        assertThat(consultRepository.markAttachmentSuperseded(orderId, ORDER_NO, supersededResumeId)).isFalse();

        long currentResumeId = consultRepository.createAttachment(orderId, new ConsultRepository.CreateAttachmentCommand(
                ORDER_NO,
                STUDENT_USER_ID,
                "RESUME",
                "resume",
                "CHAT_REPLACE",
                "resume-v2.pdf",
                "application/pdf",
                3072L,
                "test-bucket",
                "consult/resume-v2.pdf",
                "替换后的简历",
                "CURRENT",
                supersededResumeId
        ));
        long jobDescriptionId = consultRepository.createAttachment(orderId, new ConsultRepository.CreateAttachmentCommand(
                ORDER_NO,
                STUDENT_USER_ID,
                "JOB_DESCRIPTION",
                "job-description",
                "CHAT_APPEND",
                "jd.txt",
                "text/plain",
                512L,
                "test-bucket",
                "consult/jd.txt",
                "岗位描述",
                "CURRENT",
                null
        ));

        assertThat(consultRepository.countCurrentAttachments(orderId, ORDER_NO)).isEqualTo(2);
        assertThat(consultRepository.findCurrentAttachmentBySlot(orderId, ORDER_NO, "resume"))
                .isPresent()
                .get()
                .satisfies(attachment -> {
                    assertThat(attachment.id()).isEqualTo(currentResumeId);
                    assertThat(attachment.replacedAttachmentId()).isEqualTo(supersededResumeId);
                    assertThat(attachment.lifecycleStatus()).isEqualTo("CURRENT");
                });

        assertThat(consultRepository.markAttachmentDeleted(orderId, ORDER_NO, jobDescriptionId)).isTrue();
        assertThat(consultRepository.markAttachmentDeleted(orderId, ORDER_NO, jobDescriptionId)).isFalse();
        assertThat(consultRepository.countCurrentAttachments(orderId, ORDER_NO)).isEqualTo(1);

        assertThat(consultRepository.findAttachmentById(orderId, ORDER_NO, supersededResumeId))
                .isPresent()
                .get()
                .satisfies(attachment -> {
                    assertThat(attachment.lifecycleStatus()).isEqualTo("SUPERSEDED");
                    assertThat(attachment.originalFilename()).isEqualTo("resume-v1.pdf");
                });
        assertThat(consultRepository.findAttachmentById(orderId, ORDER_NO, jobDescriptionId))
                .isPresent()
                .get()
                .extracting(ConsultRepository.OrderAttachmentRow::lifecycleStatus)
                .isEqualTo("DELETED");

        List<ConsultRepository.OrderAttachmentRow> currentAttachments = consultRepository.findAttachments(orderId, ORDER_NO, true, false);
        assertThat(currentAttachments).extracting(ConsultRepository.OrderAttachmentRow::id).containsExactly(currentResumeId);

        List<ConsultRepository.OrderAttachmentRow> nonSupersededAttachments = consultRepository.findAttachments(orderId, ORDER_NO, false, false);
        assertThat(nonSupersededAttachments).hasSize(2);
        assertThat(nonSupersededAttachments).extracting(ConsultRepository.OrderAttachmentRow::lifecycleStatus)
                .doesNotContain("SUPERSEDED");

        List<ConsultRepository.OrderAttachmentRow> allAttachments = consultRepository.findAttachments(orderId, ORDER_NO, false, true);
        assertThat(allAttachments).hasSize(3);
        assertThat(allAttachments).extracting(ConsultRepository.OrderAttachmentRow::lifecycleStatus)
                .containsExactlyInAnyOrder("SUPERSEDED", "CURRENT", "DELETED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM consult_order_attachments WHERE order_no = ? AND order_id = ?",
                Integer.class,
                ORDER_NO,
                orderId
        )).isEqualTo(3);
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
