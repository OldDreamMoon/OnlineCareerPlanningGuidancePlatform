package com.bishe.server.mentor.schedule.repository.jpa.entity;

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
 * 导师排期时段实体。
 */
@Entity
@Table(
        name = "mentor_schedule_slots",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_mentor_schedule_slots",
                        columnNames = {"mentor_user_id", "start_at", "end_at"}
                )
        },
        indexes = {
                @Index(name = "idx_mentor_schedule_slots_mentor_time", columnList = "mentor_user_id, start_at"),
                @Index(name = "idx_mentor_schedule_slots_status_time", columnList = "status, start_at"),
                @Index(name = "idx_mentor_schedule_slots_booked_order_time", columnList = "booked_order_id, status, start_at")
        }
)
public class MentorScheduleSlotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "mentor_user_id", nullable = false)
    private Long mentorUserId;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "booked_order_no", length = 64)
    private String bookedOrderNo;

    @Column(name = "booked_order_id")
    private Long bookedOrderId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MentorScheduleSlotEntity() {
    }

    public static MentorScheduleSlotEntity create(long mentorUserId, Instant startAt, Instant endAt) {
        MentorScheduleSlotEntity entity = new MentorScheduleSlotEntity();
        entity.setMentorUserId(mentorUserId);
        entity.setStartAt(startAt);
        entity.setEndAt(endAt);
        entity.setStatus("AVAILABLE");
        return entity;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (status == null) {
            status = "AVAILABLE";
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

    public Long getMentorUserId() {
        return mentorUserId;
    }

    public void setMentorUserId(Long mentorUserId) {
        this.mentorUserId = mentorUserId;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public void setStartAt(Instant startAt) {
        this.startAt = startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public void setEndAt(Instant endAt) {
        this.endAt = endAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getBookedOrderNo() {
        return bookedOrderNo;
    }

    public Long getBookedOrderId() {
        return bookedOrderId;
    }

    public void setBookedOrderId(Long bookedOrderId) {
        this.bookedOrderId = bookedOrderId;
    }

    public void setBookedOrderNo(String bookedOrderNo) {
        this.bookedOrderNo = bookedOrderNo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
