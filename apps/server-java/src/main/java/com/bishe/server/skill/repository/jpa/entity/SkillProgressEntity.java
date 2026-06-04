package com.bishe.server.skill.repository.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 学生技能进度实体。
 */
@Entity
@Table(
        name = "skill_progress",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_skill_progress_user_node",
                        columnNames = {"student_user_id", "node_code"}
                )
        }
)
public class SkillProgressEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "student_user_id", nullable = false)
    private Long studentUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_code", nullable = false)
    private SkillNodeEntity node;

    @Column(name = "progress_status", nullable = false, length = 20)
    private String progressStatus;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SkillProgressEntity() {
    }

    public static SkillProgressEntity create(long studentUserId, SkillNodeEntity node) {
        SkillProgressEntity entity = new SkillProgressEntity();
        entity.setStudentUserId(studentUserId);
        entity.setNode(node);
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

    public Long getId() {
        return id;
    }

    public Long getStudentUserId() {
        return studentUserId;
    }

    public void setStudentUserId(Long studentUserId) {
        this.studentUserId = studentUserId;
    }

    public SkillNodeEntity getNode() {
        return node;
    }

    public void setNode(SkillNodeEntity node) {
        this.node = node;
    }

    public String getProgressStatus() {
        return progressStatus;
    }

    public void setProgressStatus(String progressStatus) {
        this.progressStatus = progressStatus;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
