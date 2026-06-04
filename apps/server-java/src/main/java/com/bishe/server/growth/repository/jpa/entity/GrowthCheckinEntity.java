package com.bishe.server.growth.repository.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 成长中心签到记录实体。
 */
@Entity
@Table(
        name = "checkins",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_checkins_user_date", columnNames = {"student_user_id", "checkin_date"})
        }
)
public class GrowthCheckinEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "student_user_id", nullable = false)
    private Long studentUserId;

    @Column(name = "checkin_date", nullable = false)
    private LocalDate checkinDate;

    @Column(name = "streak_count", nullable = false)
    private int streakCount;

    @Column(name = "points_earned", nullable = false)
    private int pointsEarned;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected GrowthCheckinEntity() {
    }

    public static GrowthCheckinEntity create(long studentUserId, LocalDate checkinDate, int streakCount, int pointsEarned) {
        GrowthCheckinEntity entity = new GrowthCheckinEntity();
        entity.studentUserId = studentUserId;
        entity.checkinDate = checkinDate;
        entity.streakCount = streakCount;
        entity.pointsEarned = pointsEarned;
        return entity;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public LocalDate getCheckinDate() {
        return checkinDate;
    }

    public int getStreakCount() {
        return streakCount;
    }

    public int getPointsEarned() {
        return pointsEarned;
    }
}
