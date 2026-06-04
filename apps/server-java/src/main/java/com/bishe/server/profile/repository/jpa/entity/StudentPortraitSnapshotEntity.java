package com.bishe.server.profile.repository.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 学生画像快照实体。
 */
@Entity
@Table(name = "student_portrait_snapshots")
public class StudentPortraitSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "student_user_id", nullable = false)
    private Long studentUserId;

    @Column(name = "portrait_tags", nullable = false, columnDefinition = "TEXT")
    private String portraitTags;

    @Column(name = "evidence", nullable = false, columnDefinition = "TEXT")
    private String evidence;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StudentPortraitSnapshotEntity() {
    }

    public static StudentPortraitSnapshotEntity create(long studentUserId, String portraitTags, String evidence) {
        StudentPortraitSnapshotEntity entity = new StudentPortraitSnapshotEntity();
        entity.setStudentUserId(studentUserId);
        entity.setPortraitTags(portraitTags);
        entity.setEvidence(evidence);
        return entity;
    }

    @PrePersist
    void onCreate() {
        if (updatedAt == null) {
            updatedAt = Instant.now();
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

    public String getPortraitTags() {
        return portraitTags;
    }

    public void setPortraitTags(String portraitTags) {
        this.portraitTags = portraitTags;
    }

    public String getEvidence() {
        return evidence;
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
