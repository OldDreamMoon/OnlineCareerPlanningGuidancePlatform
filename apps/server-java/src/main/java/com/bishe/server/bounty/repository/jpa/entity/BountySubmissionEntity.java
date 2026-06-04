package com.bishe.server.bounty.repository.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(
        name = "bounty_submissions",
        indexes = {
                @Index(name = "idx_bounty_submissions_task_time", columnList = "task_id, created_at"),
                @Index(name = "idx_bounty_submissions_student_time", columnList = "student_user_id, created_at"),
                @Index(name = "idx_bounty_submissions_status_time", columnList = "status, created_at")
        }
)
public class BountySubmissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private BountyTaskEntity task;

    @Column(name = "task_id", nullable = false, insertable = false, updatable = false)
    private Long taskId;

    @Column(name = "student_user_id", nullable = false)
    private Long studentUserId;

    @Column(name = "content_text", columnDefinition = "TEXT")
    private String contentText;

    @Column(name = "attachment_links", length = 1000)
    private String attachmentLinks;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "review_comment", length = 500)
    private String reviewComment;

    @Column(name = "contact_intent", length = 80)
    private String contactIntent;

    @Column(name = "reject_template", length = 160)
    private String rejectTemplate;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @Column(name = "reviewer_user_id")
    private Long reviewerUserId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BountySubmissionEntity() {
    }

    public static BountySubmissionEntity create(
            BountyTaskEntity task,
            long studentUserId,
            String contentText,
            String attachmentLinks
    ) {
        BountySubmissionEntity entity = new BountySubmissionEntity();
        entity.task = task;
        entity.taskId = task.getId();
        entity.studentUserId = studentUserId;
        entity.contentText = contentText;
        entity.attachmentLinks = attachmentLinks;
        entity.status = "SUBMITTED";
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
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void review(
            String status,
            String reviewComment,
            String contactIntent,
            String rejectTemplate,
            String reviewNote,
            long reviewerUserId
    ) {
        Instant now = Instant.now();
        this.status = status;
        this.reviewComment = reviewComment;
        this.contactIntent = contactIntent;
        this.rejectTemplate = rejectTemplate;
        this.reviewNote = reviewNote;
        this.reviewerUserId = reviewerUserId;
        this.reviewedAt = now;
        this.updatedAt = now;
    }

    public void reject(String reviewComment, String rejectTemplate, long reviewerUserId) {
        Instant now = Instant.now();
        this.status = "REJECTED";
        this.reviewComment = reviewComment;
        this.contactIntent = null;
        this.rejectTemplate = rejectTemplate;
        this.reviewNote = null;
        this.reviewerUserId = reviewerUserId;
        this.reviewedAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public BountyTaskEntity getTask() {
        return task;
    }

    public Long getStudentUserId() {
        return studentUserId;
    }

    public String getContentText() {
        return contentText;
    }

    public String getAttachmentLinks() {
        return attachmentLinks;
    }

    public String getStatus() {
        return status;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public String getContactIntent() {
        return contactIntent;
    }

    public String getRejectTemplate() {
        return rejectTemplate;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public Long getReviewerUserId() {
        return reviewerUserId;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
