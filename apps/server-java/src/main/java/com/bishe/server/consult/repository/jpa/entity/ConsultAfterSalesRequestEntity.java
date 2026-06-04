package com.bishe.server.consult.repository.jpa.entity;

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
 * 咨询售后申请实体。
 */
@Entity
@Table(
        name = "consult_after_sales_requests",
        indexes = {
                @Index(name = "idx_consult_after_sales_order_time", columnList = "order_no, created_at"),
                @Index(name = "idx_consult_after_sales_status_time", columnList = "status, created_at"),
                @Index(name = "idx_consult_after_sales_requester_time", columnList = "requester_user_id, created_at"),
                @Index(name = "idx_consult_after_sales_order_id_time", columnList = "order_id, created_at")
        }
)
public class ConsultAfterSalesRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "order_no", nullable = false, length = 64)
    private String orderNo;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "requester_user_id", nullable = false)
    private Long requesterUserId;

    @Column(name = "request_type", nullable = false, length = 20)
    private String requestType;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @Column(name = "reviewer_user_id")
    private Long reviewerUserId;

    @Column(name = "auto_triggered", nullable = false)
    private boolean autoTriggered;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ConsultAfterSalesRequestEntity() {
    }

    public static ConsultAfterSalesRequestEntity create(
            Long orderId,
            String orderNo,
            long requesterUserId,
            String requestType,
            String status,
            String reason,
            boolean autoTriggered
    ) {
        ConsultAfterSalesRequestEntity entity = new ConsultAfterSalesRequestEntity();
        entity.setOrderId(orderId);
        entity.setOrderNo(orderNo);
        entity.setRequesterUserId(requesterUserId);
        entity.setRequestType(requestType);
        entity.setStatus(status);
        entity.setReason(reason);
        entity.setAutoTriggered(autoTriggered);
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

    public Long getId() {
        return id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getRequesterUserId() {
        return requesterUserId;
    }

    public void setRequesterUserId(Long requesterUserId) {
        this.requesterUserId = requesterUserId;
    }

    public String getRequestType() {
        return requestType;
    }

    public void setRequestType(String requestType) {
        this.requestType = requestType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }

    public Long getReviewerUserId() {
        return reviewerUserId;
    }

    public void setReviewerUserId(Long reviewerUserId) {
        this.reviewerUserId = reviewerUserId;
    }

    public boolean isAutoTriggered() {
        return autoTriggered;
    }

    public void setAutoTriggered(boolean autoTriggered) {
        this.autoTriggered = autoTriggered;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
