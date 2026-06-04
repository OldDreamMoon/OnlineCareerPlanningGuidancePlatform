package com.bishe.server.profile.repository.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 学生资料中心安全验证码实体。
 */
@Entity
@Table(
        name = "student_profile_security_codes",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_student_profile_security_code",
                        columnNames = {"student_user_id", "purpose", "target_email"}
                )
        },
        indexes = {
                @Index(name = "idx_student_profile_security_expires_at", columnList = "expires_at")
        }
)
public class StudentProfileSecurityCodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "student_user_id", nullable = false)
    private Long studentUserId;

    @Column(name = "purpose", nullable = false, length = 64)
    private String purpose;

    @Column(name = "target_email", nullable = false, length = 255)
    private String targetEmail;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "next_send_at", nullable = false)
    private Instant nextSendAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StudentProfileSecurityCodeEntity() {
    }

    public static StudentProfileSecurityCodeEntity create(long studentUserId, String purpose, String targetEmail) {
        StudentProfileSecurityCodeEntity entity = new StudentProfileSecurityCodeEntity();
        entity.setStudentUserId(studentUserId);
        entity.setPurpose(purpose);
        entity.setTargetEmail(targetEmail);
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

    public Long getStudentUserId() {
        return studentUserId;
    }

    public void setStudentUserId(Long studentUserId) {
        this.studentUserId = studentUserId;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getTargetEmail() {
        return targetEmail;
    }

    public void setTargetEmail(String targetEmail) {
        this.targetEmail = targetEmail;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public void setCodeHash(String codeHash) {
        this.codeHash = codeHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getNextSendAt() {
        return nextSendAt;
    }

    public void setNextSendAt(Instant nextSendAt) {
        this.nextSendAt = nextSendAt;
    }
}
