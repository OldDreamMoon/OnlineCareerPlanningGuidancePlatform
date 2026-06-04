package com.bishe.server.mentor.repository.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 导师收藏关系实体。
 */
@Entity
@Table(
        name = "mentor_favorites",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_mentor_favorites_student_mentor",
                        columnNames = {"student_user_id", "mentor_user_id"}
                )
        },
        indexes = {
                @Index(name = "idx_mentor_favorites_student_time", columnList = "student_user_id, created_at"),
                @Index(name = "idx_mentor_favorites_mentor_time", columnList = "mentor_user_id, created_at")
        }
)
public class MentorFavoriteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "student_user_id", nullable = false)
    private Long studentUserId;

    @Column(name = "mentor_user_id", nullable = false)
    private Long mentorUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MentorFavoriteEntity() {
    }

    public static MentorFavoriteEntity create(long studentUserId, long mentorUserId) {
        MentorFavoriteEntity entity = new MentorFavoriteEntity();
        entity.studentUserId = studentUserId;
        entity.mentorUserId = mentorUserId;
        return entity;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getStudentUserId() {
        return studentUserId;
    }

    public Long getMentorUserId() {
        return mentorUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
