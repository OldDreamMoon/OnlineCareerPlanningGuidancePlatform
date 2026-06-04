package com.bishe.server.consult.repository.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(
        name = "consult_orders",
        indexes = {
                @Index(name = "idx_consult_orders_student_time", columnList = "student_user_id, created_at"),
                @Index(name = "idx_consult_orders_mentor_time", columnList = "mentor_user_id, created_at")
        }
)
public class ConsultOrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "order_no", nullable = false, length = 64)
    private String orderNo;

    @Column(name = "student_user_id", nullable = false)
    private Long studentUserId;

    @Column(name = "mentor_user_id", nullable = false)
    private Long mentorUserId;

    @Column(name = "scene_code", length = 60)
    private String sceneCode;

    @Column(name = "source_page", length = 80)
    private String sourcePage;

    @Column(name = "amount_fen", nullable = false)
    private Integer amountFen;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "question_text", columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "question_payload_json", columnDefinition = "TEXT")
    private String questionPayloadJson;

    @Column(name = "problem_summary", columnDefinition = "TEXT")
    private String problemSummary;

    @Column(name = "core_questions_json", columnDefinition = "TEXT")
    private String coreQuestionsJson;

    @Column(name = "expected_outcomes_json", columnDefinition = "TEXT")
    private String expectedOutcomesJson;

    @Column(name = "selected_material_types", length = 255)
    private String selectedMaterialTypes;

    @Column(name = "prep_sheet_snapshot_json", columnDefinition = "TEXT")
    private String prepSheetSnapshotJson;

    @Column(name = "service_package_snapshot_json", columnDefinition = "TEXT")
    private String servicePackageSnapshotJson;

    @Column(name = "appointment_start_at")
    private Instant appointmentStartAt;

    @Column(name = "appointment_end_at")
    private Instant appointmentEndAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "review_rating")
    private Short reviewRating;

    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    @Column(name = "review_created_at")
    private Instant reviewCreatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ConsultOrderEntity() {
    }

    public static ConsultOrderEntity create(
            String orderNo,
            long studentUserId,
            long mentorUserId,
            String sceneCode,
            String sourcePage,
            int amountFen,
            String questionText,
            String questionPayloadJson,
            String problemSummary,
            String coreQuestionsJson,
            String expectedOutcomesJson,
            String selectedMaterialTypes,
            String prepSheetSnapshotJson,
            String servicePackageSnapshotJson,
            Instant appointmentStartAt,
            Instant appointmentEndAt
    ) {
        ConsultOrderEntity entity = new ConsultOrderEntity();
        entity.orderNo = orderNo;
        entity.studentUserId = studentUserId;
        entity.mentorUserId = mentorUserId;
        entity.sceneCode = sceneCode;
        entity.sourcePage = sourcePage;
        entity.amountFen = amountFen;
        entity.status = "CREATED";
        entity.questionText = questionText;
        entity.questionPayloadJson = questionPayloadJson;
        entity.problemSummary = problemSummary;
        entity.coreQuestionsJson = coreQuestionsJson;
        entity.expectedOutcomesJson = expectedOutcomesJson;
        entity.selectedMaterialTypes = selectedMaterialTypes;
        entity.prepSheetSnapshotJson = prepSheetSnapshotJson;
        entity.servicePackageSnapshotJson = servicePackageSnapshotJson;
        entity.appointmentStartAt = appointmentStartAt;
        entity.appointmentEndAt = appointmentEndAt;
        return entity;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null) {
            status = "CREATED";
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean markPaying() {
        status = "PAYING";
        touchUpdatedAt();
        return true;
    }

    public boolean markPaid() {
        status = "PAID";
        if (paidAt == null) {
            paidAt = Instant.now();
        }
        touchUpdatedAt();
        return true;
    }

    public boolean markAnswered() {
        status = "ANSWERED";
        touchUpdatedAt();
        return true;
    }

    public boolean markFailed() {
        if (!"CREATED".equals(status)) {
            return false;
        }
        status = "FAILED";
        touchUpdatedAt();
        return true;
    }

    public boolean markClosed() {
        status = "CLOSED";
        closedAt = Instant.now();
        touchUpdatedAt();
        return true;
    }

    public boolean markCanceled() {
        if (paidAt != null) {
            return false;
        }
        if (!"CREATED".equals(status) && !"PAYING".equals(status)) {
            return false;
        }
        status = "CANCELED";
        touchUpdatedAt();
        return true;
    }

    public boolean markRefunded() {
        if (!"PAID".equals(status) && !"ANSWERED".equals(status) && !"CLOSED".equals(status)) {
            return false;
        }
        status = "REFUNDED";
        touchUpdatedAt();
        return true;
    }

    public boolean createReview(int rating, String comment) {
        if (reviewRating != null) {
            return false;
        }
        reviewRating = (short) rating;
        reviewComment = comment;
        reviewCreatedAt = Instant.now();
        touchUpdatedAt();
        return true;
    }

    public boolean deleteReview() {
        if (reviewRating == null && reviewComment == null && reviewCreatedAt == null) {
            return false;
        }
        reviewRating = null;
        reviewComment = null;
        reviewCreatedAt = null;
        touchUpdatedAt();
        return true;
    }

    private void touchUpdatedAt() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public Long getStudentUserId() {
        return studentUserId;
    }

    public Long getMentorUserId() {
        return mentorUserId;
    }

    public String getSceneCode() {
        return sceneCode;
    }

    public String getSourcePage() {
        return sourcePage;
    }

    public Integer getAmountFen() {
        return amountFen;
    }

    public String getStatus() {
        return status;
    }

    public String getQuestionText() {
        return questionText;
    }

    public String getQuestionPayloadJson() {
        return questionPayloadJson;
    }

    public String getProblemSummary() {
        return problemSummary;
    }

    public String getCoreQuestionsJson() {
        return coreQuestionsJson;
    }

    public String getExpectedOutcomesJson() {
        return expectedOutcomesJson;
    }

    public String getSelectedMaterialTypes() {
        return selectedMaterialTypes;
    }

    public String getPrepSheetSnapshotJson() {
        return prepSheetSnapshotJson;
    }

    public String getServicePackageSnapshotJson() {
        return servicePackageSnapshotJson;
    }

    public Instant getAppointmentStartAt() {
        return appointmentStartAt;
    }

    public Instant getAppointmentEndAt() {
        return appointmentEndAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Short getReviewRating() {
        return reviewRating;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public Instant getReviewCreatedAt() {
        return reviewCreatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
