package com.bishe.server.certification.repository.jpa.entity;

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

/**
 * 认证提交实体。
 */
@Entity
@Table(
        name = "certification_submissions",
        indexes = {
                @Index(name = "idx_certification_submissions_user", columnList = "user_id, is_current, submitted_at"),
                @Index(name = "idx_certification_submissions_status", columnList = "status, submitted_at")
        }
)
public class CertificationSubmissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_role", nullable = false, length = 20)
    private String userRole;

    @Column(name = "real_name", nullable = false, length = 100)
    private String realName;

    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(name = "job_title", length = 100)
    private String jobTitle;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "previous_submission_id")
    private Long previousSubmissionId;

    @Column(name = "is_current", nullable = false)
    private boolean current;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CertificationSubmissionEntity() {
    }

    public static CertificationSubmissionEntity create(
            long userId,
            String userRole,
            String realName,
            String companyName,
            String jobTitle,
            String status,
            Long previousSubmissionId,
            Instant submittedAt
    ) {
        CertificationSubmissionEntity entity = new CertificationSubmissionEntity();
        entity.setUserId(userId);
        entity.setUserRole(userRole);
        entity.setRealName(realName);
        entity.setCompanyName(companyName);
        entity.setJobTitle(jobTitle);
        entity.setStatus(status);
        entity.setPreviousSubmissionId(previousSubmissionId);
        entity.setCurrent(true);
        entity.setSubmittedAt(submittedAt);
        return entity;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (status == null) {
            status = "PENDING";
        }
        if (submittedAt == null) {
            submittedAt = now;
        }
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

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserRole() {
        return userRole;
    }

    public void setUserRole(String userRole) {
        this.userRole = userRole;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }

    public Long getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(Long reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public Long getPreviousSubmissionId() {
        return previousSubmissionId;
    }

    public void setPreviousSubmissionId(Long previousSubmissionId) {
        this.previousSubmissionId = previousSubmissionId;
    }

    public boolean isCurrent() {
        return current;
    }

    public void setCurrent(boolean current) {
        this.current = current;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
