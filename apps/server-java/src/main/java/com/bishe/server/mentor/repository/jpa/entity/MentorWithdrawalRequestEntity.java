package com.bishe.server.mentor.repository.jpa.entity;

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
 * 导师提现申请实体。
 */
@Entity
@Table(
        name = "mentor_withdrawal_requests",
        indexes = {
                @Index(name = "idx_mentor_withdrawal_requests_user_time", columnList = "mentor_user_id, created_at")
        }
)
public class MentorWithdrawalRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "mentor_user_id", nullable = false)
    private Long mentorUserId;

    @Column(name = "amount_fen", nullable = false)
    private Integer amountFen;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MentorWithdrawalRequestEntity() {
    }

    public static MentorWithdrawalRequestEntity create(long mentorUserId, int amountFen, String note) {
        MentorWithdrawalRequestEntity entity = new MentorWithdrawalRequestEntity();
        entity.mentorUserId = mentorUserId;
        entity.amountFen = amountFen;
        entity.status = "PENDING";
        entity.note = note;
        return entity;
    }

    public void updateStatus(String status, String note) {
        this.status = status;
        this.note = note;
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

    public Long getId() {
        return id;
    }

    public Long getMentorUserId() {
        return mentorUserId;
    }

    public Integer getAmountFen() {
        return amountFen;
    }

    public String getStatus() {
        return status;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
