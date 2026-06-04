package com.bishe.server.growth.repository.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 成长中心积分账本实体。
 */
@Entity
@Table(name = "points_ledger")
public class PointsLedgerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "student_user_id", nullable = false)
    private Long studentUserId;

    @Column(name = "delta_points", nullable = false)
    private int deltaPoints;

    @Column(name = "reason_code", nullable = false, length = 50)
    private String reasonCode;

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PointsLedgerEntity() {
    }

    public static PointsLedgerEntity create(long studentUserId, int deltaPoints, String reasonCode, int balanceAfter) {
        PointsLedgerEntity entity = new PointsLedgerEntity();
        entity.studentUserId = studentUserId;
        entity.deltaPoints = deltaPoints;
        entity.reasonCode = reasonCode;
        entity.balanceAfter = balanceAfter;
        return entity;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public int getDeltaPoints() {
        return deltaPoints;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public int getBalanceAfter() {
        return balanceAfter;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
